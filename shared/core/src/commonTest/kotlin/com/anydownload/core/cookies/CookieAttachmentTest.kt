package com.anydownload.core.cookies

import com.anydownload.core.download.FragmentDownloader
import com.anydownload.core.download.FragmentOutcome
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.MediaFragment
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The T-018 attachment path: an active job jar must ride extractor, media,
 * and fragment requests through the trusted `HttpRequest.cookie` field, and
 * must be recomputed per redirect hop. Synthetic cookie text only.
 */
class CookieAttachmentTest {

    private val now = 1_800_000_000L

    private val cookieFile = buildString {
        appendLine("# Netscape HTTP Cookie File")
        appendLine(".example.com\tTRUE\t/\tFALSE\t0\tfake_session\tfake_value")
        appendLine("cdn.example.com\tFALSE\t/\tFALSE\t0\tfake_cdn\tfake_cdn_value")
    }

    private class RecordingTransfer(private val responder: (HttpRequest) -> HttpResponse) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return responder(request)
        }
    }

    private fun ok(body: String = "ok") = HttpResponse.Final(
        statusCode = 200,
        contentType = "text/html; charset=utf-8",
        body = ByteArrayHttpBody(body.encodeToByteArray()),
    )

    @Test
    fun extractorRequestCarriesTheActiveJarHeader() = runTest {
        val transfer = RecordingTransfer { ok() }
        val jar = CookieJar.fromText(cookieFile)

        val body = withContext(ActiveCookieJar(jar, now)) {
            ExtractorHttp(transfer).downloadWebpage("https://media.example.com/page")
        }

        assertEquals("ok", body)
        val sanitized = transfer.requests.single().sanitized()
        assertEquals("fake_session=fake_value", sanitized.request.headers["cookie"])
        assertFalse(sanitized.droppedHeaders.any { it.contains("fake_value") })
    }

    @Test
    fun noActiveJarMeansNoCookieHeader() = runTest {
        val transfer = RecordingTransfer { ok() }

        ExtractorHttp(transfer).downloadWebpage("https://media.example.com/page")

        assertFalse(transfer.requests.single().sanitized().request.headers.containsKey("cookie"))
    }

    @Test
    fun extractorRedirectRecomputesTheHeaderForTheNewHost() = runTest {
        val transfer = RecordingTransfer { request ->
            if (request.url.startsWith("https://media.example.com/")) {
                HttpResponse.Redirect(location = "https://cdn.example.com/landing", statusCode = 302)
            } else {
                ok()
            }
        }
        val jar = CookieJar.fromText(cookieFile)

        withContext(ActiveCookieJar(jar, now)) {
            ExtractorHttp(transfer).downloadWebpage("https://media.example.com/page")
        }

        assertEquals(2, transfer.requests.size)
        assertEquals("fake_session=fake_value", transfer.requests[0].sanitized().request.headers["cookie"])
        val second = transfer.requests[1].sanitized().request.headers["cookie"].orEmpty()
        // The cdn-specific row only exists on the redirected host.
        assertTrue(second.contains("fake_cdn=fake_cdn_value"))
        assertTrue(second.contains("fake_session=fake_value"))
    }

    @Test
    fun fragmentRequestsCarryTheActiveJarHeader() = runTest {
        val transfer = RecordingTransfer {
            HttpResponse.Final(statusCode = 200, body = ByteArrayHttpBody(byteArrayOf(1, 2, 3)))
        }
        val jar = CookieJar.fromText(cookieFile)

        val outcome = withContext(ActiveCookieJar(jar, now)) {
            FragmentDownloader(transfer, concurrency = 2).download(
                fragments = listOf(
                    MediaFragment("https://cdn.example.com/a.ts"),
                    MediaFragment("https://cdn.example.com/b.ts"),
                ),
                onChunk = {},
                onProgress = { _, _, _ -> },
            )
        }

        assertTrue(outcome is FragmentOutcome.Completed)
        assertEquals(2, transfer.requests.size)
        assertTrue(
            transfer.requests.all {
                it.sanitized().request.headers["cookie"].orEmpty().contains("fake_cdn=fake_cdn_value")
            },
        )
    }

    @Test
    fun fragmentRedirectRecomputesTheHeaderPerHop() = runTest {
        val transfer = RecordingTransfer { request ->
            if (request.url.startsWith("https://cdn.example.com/")) {
                HttpResponse.Redirect(location = "https://media.example.com/seg.ts", statusCode = 302)
            } else {
                HttpResponse.Final(statusCode = 200, body = ByteArrayHttpBody(byteArrayOf(9)))
            }
        }
        val jar = CookieJar.fromText(cookieFile)

        withContext(ActiveCookieJar(jar, now)) {
            FragmentDownloader(transfer, concurrency = 1).download(
                fragments = listOf(MediaFragment("https://cdn.example.com/a.ts")),
                onChunk = {},
                onProgress = { _, _, _ -> },
            )
        }

        assertEquals(2, transfer.requests.size)
        assertTrue(
            transfer.requests[0].sanitized().request.headers["cookie"].orEmpty()
                .contains("fake_cdn=fake_cdn_value"),
        )
        assertEquals("fake_session=fake_value", transfer.requests[1].sanitized().request.headers["cookie"])
    }
}
