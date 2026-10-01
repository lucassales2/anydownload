package com.anydownload.core.extract

import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `GenericIE` on the extractor base. The fixture HTML stays on
 * example.org/example.net; the candidate policy never sees a live host.
 */
class GenericIETest {

    private fun html(body: String) =
        "<html><head><title>Fixture &amp; Clip</title></head><body>$body</body></html>"

    private fun ieWith(page: String): GenericIE =
        GenericIE(ExtractorHttp(FixedTransfer(page)))

    @Test
    fun oneMediaCandidateBecomesAnInfoDictWithOneFormat() = runTest {
        val page = html("""<video src="https://cdn.fixtures.example.net/clip.mp4"></video>""")
        val registry = ExtractorRegistry(listOf(ieWith(page)))

        val info = registry.extract("https://example.org/watch")

        assertEquals("Fixture & Clip", info.title)
        assertEquals("https://example.org/watch", info.webpageUrl)
        assertEquals(ExtractorRegistry.GENERIC_KEY, info.extractorKey)
        assertEquals(1, info.formats.size)
        val format = info.formats.single()
        assertEquals("https://cdn.fixtures.example.net/clip.mp4", format.url)
        assertEquals("mp4", format.ext)
        assertEquals("0", format.formatId)
        assertTrue(format.isAudioOnly.not() && format.isVideoOnly.not())
    }

    @Test
    fun noMediaFailsWithNoFormats() = runTest {
        val registry = ExtractorRegistry(listOf(ieWith(html("<p>nothing here</p>"))))
        assertFailsWith<ExtractionError.NoFormats> {
            registry.extract("https://example.org/watch")
        }
    }

    @Test
    fun severalMediaCandidatesFailTyped() = runTest {
        val page = html(
            """<video src="https://cdn.fixtures.example.net/a.mp4"></video>""" +
                """<video src="https://cdn.fixtures.example.net/b.mp4"></video>""",
        )
        val registry = ExtractorRegistry(listOf(ieWith(page)))
        val error = assertFailsWith<ExtractionError.Unavailable> {
            registry.extract("https://example.org/watch")
        }
        assertTrue(error.message!!.contains("more than one media"))
    }

    @Test
    fun aNonHttpUrlNeverReachesTheExtractor() = runTest {
        val registry = ExtractorRegistry(listOf(ieWith(html("<p>x</p>"))))
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            registry.extract("mailto:someone@example.com")
        }
    }

    @Test
    fun aPolicyRejectedCandidateMeansNoFormats() = runTest {
        // ftp is outside UrlPolicy, so the only candidate is dropped.
        val page = html("""<video src="ftp://cdn.example.net/clip.mp4"></video>""")
        val registry = ExtractorRegistry(listOf(ieWith(page)))
        assertFailsWith<ExtractionError.NoFormats> {
            registry.extract("https://example.org/watch")
        }
    }

    @Test
    fun extractorHttpReportsAnHttpErrorAsUnavailable() = runTest {
        val http = ExtractorHttp(FailingTransfer(statusCode = 404))
        assertFailsWith<ExtractionError.Unavailable> {
            http.downloadWebpage("https://example.org/gone")
        }
    }

    @Test
    fun extractorHttpFollowsARedirectAndCapsTheBody() = runTest {
        val transfer = RedirectingTransfer(
            finalBody = "abcdefghij",
            redirectStatus = 303,
        )
        val http = ExtractorHttp(transfer)
        val body = http.downloadWebpage("https://example.org/start", maxBytes = 4)
        assertEquals("abcd", body)
        assertEquals(2, transfer.requests.size)
        assertEquals("GET", transfer.requests[0].method)
        assertEquals("GET", transfer.requests[1].method)
    }

    @Test
    fun extractorHttpStopsAfterTooManyRedirects() = runTest {
        val http = ExtractorHttp(LoopingTransfer(), maxRedirects = 2)
        assertFailsWith<ExtractionError.Malformed> {
            http.downloadWebpage("https://example.org/loop")
        }
    }

    @Test
    fun extractorHttpParsesJsonAndRejectsMalformedJson() = runTest {
        val http = ExtractorHttp(FixedTransfer("""{"videoId": "fixture"}"""))
        val json = http.downloadJson("https://example.org/api")
        assertNotNull(json)

        val bad = ExtractorHttp(FixedTransfer("not json"))
        assertFailsWith<ExtractionError.Malformed> { bad.downloadJson("https://example.org/api") }
    }

    @Test
    fun aMetaRefreshToAnotherPageIsFollowedOnce() = runTest {
        val transfer = RoutingTransfer(
            mapOf(
                "https://example.org/watch" to
                    """<html><body><meta http-equiv="refresh" content="0; url=/next"></body></html>""",
                "https://example.org/next" to
                    """<html><head><title>Next</title></head><body><video src="clip.mp4"></video></body></html>""",
            ),
        )
        val registry = ExtractorRegistry(listOf(GenericIE(ExtractorHttp(transfer))))

        val info = registry.extract("https://example.org/watch")

        assertEquals("https://example.org/clip.mp4", info.formats.single().url)
        assertEquals("https://example.org/watch", info.webpageUrl)
        assertEquals("Next", info.title)
        assertEquals(listOf("https://example.org/watch", "https://example.org/next"), transfer.requests)
    }

    @Test
    fun aMetaRefreshDirectlyToMediaSkipsTheSecondFetch() = runTest {
        val transfer = RoutingTransfer(
            mapOf(
                "https://example.org/watch" to
                    """<meta http-equiv="refresh" content="0; url=/media/clip.mp4">""",
            ),
        )
        val registry = ExtractorRegistry(listOf(GenericIE(ExtractorHttp(transfer))))

        val info = registry.extract("https://example.org/watch")

        assertEquals("https://example.org/media/clip.mp4", info.formats.single().url)
        assertEquals(listOf("https://example.org/watch"), transfer.requests)
    }

    @Test
    fun aSecondMetaRefreshIsNotFollowed() = runTest {
        val transfer = RoutingTransfer(
            mapOf(
                "https://example.org/watch" to
                    """<meta http-equiv="refresh" content="0; url=/next">""",
                "https://example.org/next" to
                    """<meta http-equiv="refresh" content="0; url=/third.mp4">""",
            ),
        )
        val registry = ExtractorRegistry(listOf(GenericIE(ExtractorHttp(transfer))))

        assertFailsWith<ExtractionError.NoFormats> {
            registry.extract("https://example.org/watch")
        }
        assertEquals(2, transfer.requests.size)
    }

    @Test
    fun aPolicyRejectedMetaRefreshTargetIsNotFollowed() = runTest {
        val transfer = RoutingTransfer(
            mapOf(
                "https://example.org/watch" to
                    """<meta http-equiv="refresh" content="0; url=ftp://example.org/clip.mp4">""",
            ),
        )
        val registry = ExtractorRegistry(listOf(GenericIE(ExtractorHttp(transfer))))

        assertFailsWith<ExtractionError.NoFormats> {
            registry.extract("https://example.org/watch")
        }
        assertEquals(1, transfer.requests.size)
    }

    // ------------------------------------------------------------------ fakes

    private class RoutingTransfer(private val pages: Map<String, String>) : HttpTransfer {
        val requests = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request.url
            val body = pages[request.url] ?: return HttpResponse.Final(statusCode = 404)
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private class FixedTransfer(private val body: String) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private class FailingTransfer(private val statusCode: Int) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            HttpResponse.Final(statusCode = statusCode)
    }

    private class RedirectingTransfer(
        private val finalBody: String,
        private val redirectStatus: Int,
    ) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            if (requests.size == 1) {
                return HttpResponse.Redirect("https://example.org/final", redirectStatus)
            }
            val bytes = finalBody.encodeToByteArray()
            return HttpResponse.Final(statusCode = 200, body = ByteArrayHttpBody(bytes), totalBytes = bytes.size.toLong())
        }
    }

    private class LoopingTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            HttpResponse.Redirect("https://example.org/loop", 302)
    }
}
