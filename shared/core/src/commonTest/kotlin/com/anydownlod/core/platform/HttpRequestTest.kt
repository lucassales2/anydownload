package com.anydownlod.core.platform

import com.anydownlod.core.engine.WebExtensionBridge
import com.anydownlod.core.engine.WebDownload
import com.anydownlod.core.engine.WebFailureCode
import com.anydownlod.core.engine.WebFetch
import com.anydownlod.core.engine.WebPage
import com.anydownlod.core.engine.WebProbe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpRequestTest {

    // ------------------------------------------------------------- allowlist

    @Test
    fun allowlistKeepsDeclaredExtractorHeaders() {
        val sanitized = HttpHeaders.sanitize(
            mapOf(
                "X-YouTube-Client-Name" to "101",
                "x-youtube-client-version" to "1.02",
                "Content-Type" to "application/json",
                "Accept-Language" to "en",
                "X-Goog-Visitor-Id" to "visitor-fixture",
                "Range" to "bytes=0-1023",
            ),
        )
        assertEquals(
            mapOf(
                "x-youtube-client-name" to "101",
                "x-youtube-client-version" to "1.02",
                "content-type" to "application/json",
                "accept-language" to "en",
                "x-goog-visitor-id" to "visitor-fixture",
                "range" to "bytes=0-1023",
            ),
            sanitized.accepted,
        )
        assertTrue(sanitized.dropped.isEmpty())
    }

    @Test
    fun allowlistRefusesCookiesAuthorizationAndOtherGoogHeaders() {
        val sanitized = HttpHeaders.sanitize(
            mapOf(
                "Cookie" to "session=secret",
                "Authorization" to "Bearer secret",
                "Proxy-Authorization" to "secret",
                "X-Goog-Api-Key" to "secret",
                "X-Goog-PageId" to "secret",
                "X-Custom" to "value",
                "accept" to "*/*",
            ),
        )
        assertEquals(mapOf("accept" to "*/*"), sanitized.accepted)
        assertEquals(
            listOf("authorization", "cookie", "proxy-authorization", "x-custom", "x-goog-api-key", "x-goog-pageid"),
            sanitized.dropped,
        )
        // The visitor id is the one x-goog name upstream may send.
        assertTrue(HttpHeaders.isAllowedRequestHeader("X-Goog-Visitor-Id"))
        assertFalse(HttpHeaders.isAllowedRequestHeader("Cookie"))
        assertFalse(HttpHeaders.isAllowedRequestHeader("Authorization"))
    }

    @Test
    fun responseFilterKeepsOnlyTheAllowlistAndLowercasesNames() {
        val filtered = HttpHeaders.filterResponse(
            mapOf(
                "Content-Type" to "text/html",
                "Content-Range" to "bytes 0-9/100",
                "Set-Cookie" to "session=secret",
                "X-Powered-By" to "fixture",
                "ETag" to "\"abc\"",
            ),
        )
        assertEquals(mapOf("content-type" to "text/html", "content-range" to "bytes 0-9/100", "etag" to "\"abc\""), filtered)
    }

    // ----------------------------------------------------------- sanitizing

    @Test
    fun sanitizedRequestBuildsRangeHeaderAndDropsTheRest() {
        val sanitized = HttpRequest(
            url = "https://fixtures.example.com/files/tiny.bin",
            headers = mapOf("Cookie" to "secret", "Accept" to "*/*"),
            range = 0L..1023L,
        ).sanitized()
        assertEquals("bytes=0-1023", sanitized.request.headers["range"])
        assertEquals("*/*", sanitized.request.headers["accept"])
        assertFalse(sanitized.request.headers.containsKey("cookie"))
        assertEquals(listOf("cookie"), sanitized.droppedHeaders)
    }

    @Test
    fun explicitRangeHeaderWinsOverTheRangeField() {
        val sanitized = HttpRequest(
            url = "https://fixtures.example.com/files/tiny.bin",
            headers = mapOf("Range" to "bytes=100-199"),
            range = 0L..1023L,
        ).sanitized()
        assertEquals("bytes=100-199", sanitized.request.headers["range"])
    }

    @Test
    fun contentRangeTotalParsesAndRejectsWildcards() {
        assertEquals(100L, ContentRange.totalBytes("bytes 0-9/100"))
        assertEquals(100L, ContentRange.totalBytes("bytes 0-9/100"))
        assertEquals(null, ContentRange.totalBytes("bytes 0-9/*"))
        assertEquals(null, ContentRange.totalBytes(null))
        assertEquals(null, ContentRange.totalBytes("bytes"))
    }

    // ------------------------------------------------------------ redirects

    @Test
    fun seeOtherBecomesABodyLessGet() {
        val original = HttpRequest(
            url = "https://fixtures.example.com/post",
            method = HttpMethods.POST,
            headers = mapOf("content-type" to "application/json"),
            body = byteArrayOf(1, 2, 3),
        )
        val decision = HttpRedirects.afterRedirect(original, 303, "https://fixtures.example.com/result")
        val follow = assertIs<RedirectDecision.Follow>(decision)
        assertEquals("GET", follow.request.method)
        assertEquals("https://fixtures.example.com/result", follow.request.url)
        assertEquals(null, follow.request.body)
        assertTrue(follow.request.headers.isEmpty())
    }

    @Test
    fun getFollowsWithTheSameHeadersAndPostFailsTyped() {
        val get = HttpRequest("https://fixtures.example.com/a", headers = mapOf("accept" to "*/*"))
        val getFollow = assertIs<RedirectDecision.Follow>(
            HttpRedirects.afterRedirect(get, 302, "https://fixtures.example.com/b"),
        )
        assertEquals(get.headers, getFollow.request.headers)

        val post = HttpRequest("https://fixtures.example.com/a", method = HttpMethods.POST, body = byteArrayOf(1))
        val failed = assertIs<RedirectDecision.Fails>(
            HttpRedirects.afterRedirect(post, 302, "https://fixtures.example.com/b"),
        )
        assertEquals(RedirectFailure.POST_REDIRECT_NEEDS_303, failed.reason)

        val missing = assertIs<RedirectDecision.Fails>(
            HttpRedirects.afterRedirect(get, 302, ""),
        )
        assertEquals(RedirectFailure.MISSING_LOCATION, missing.reason)
    }

    // ----------------------------------------------------------------- body

    @Test
    fun byteArrayBodyStreamsInChunks() = runTest {
        val body = ByteArrayHttpBody(ByteArray(10) { it.toByte() })
        val buffer = ByteArray(4)

        assertEquals(4, body.readNext(buffer))
        assertContentEquals(byteArrayOf(0, 1, 2, 3), buffer)
        assertEquals(4, body.readNext(buffer))
        assertContentEquals(byteArrayOf(4, 5, 6, 7), buffer)
        assertEquals(2, body.readNext(buffer))
        assertEquals(-1, body.readNext(buffer))
        body.close()
    }

    // ------------------------------------------------------------ web bridge

    @Test
    fun webTransferSanitizesAndMapsAFinalFetch() = runTest {
        val bridge = FakeExtensionBridge(
            fetchReply = WebFetch.Final(
                statusCode = 200,
                contentType = "application/json",
                totalBytes = 3L,
                contentRange = null,
                headers = mapOf("content-type" to "application/json"),
                body = byteArrayOf(1, 2, 3),
                finalUrl = "https://fixtures.example.com/api",
                sentHeaders = mapOf("accept" to "*/*"),
            ),
        )
        val dropped = mutableListOf<List<String>>()
        val transfer = WebExtensionTransfer(bridge, onDroppedHeaders = { dropped += it })

        val response = assertIs<HttpResponse.Final>(
            transfer.execute(
                HttpRequest(
                    url = "https://fixtures.example.com/api",
                    method = HttpMethods.POST,
                    headers = mapOf("Cookie" to "secret", "Accept" to "*/*", "User-Agent" to "fixture-agent"),
                    body = byteArrayOf(9),
                ),
            ),
        )
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.contentType)
        assertEquals(3L, response.totalBytes)
        // The bridge never saw the refused header; the MV3-refused
        // User-Agent is reported as declared-but-not-sent, names only.
        assertEquals(mapOf("accept" to "*/*", "user-agent" to "fixture-agent"), bridge.requests.single().headers)
        assertEquals(listOf(listOf("cookie", "user-agent")), dropped)
        assertContentEquals(byteArrayOf(1, 2, 3), readAll(response.body!!))
    }

    @Test
    fun webTransferMapsFailuresAndUnavailability() = runTest {
        val blocked = WebExtensionTransfer(
            FakeExtensionBridge(
                fetchReply = WebFetch.Failed(WebFailureCode.BLOCKED_DESTINATION, "This address was refused."),
            ),
        )
        val failed = assertIs<HttpResponse.Failed>(blocked.execute("https://fixtures.example.com/a"))
        assertEquals(HttpFailureReason.BLOCKED_DESTINATION, failed.reason)

        val missing = WebExtensionTransfer().execute("https://fixtures.example.com/a")
        assertIs<HttpResponse.Unavailable>(missing)
    }

    private suspend fun readAll(body: HttpBody): ByteArray {
        val chunks = mutableListOf<Byte>()
        val buffer = ByteArray(2)
        while (true) {
            val count = body.readNext(buffer)
            if (count == -1) break
            for (index in 0 until count) chunks += buffer[index]
        }
        body.close()
        return chunks.toByteArray()
    }

    private class FakeExtensionBridge(
        private val fetchReply: WebFetch = WebFetch.Failed(WebFailureCode.OTHER, "not used"),
    ) : WebExtensionBridge {
        override val available: Boolean = true
        val requests: MutableList<HttpRequest> = mutableListOf()

        override suspend fun probe(url: String): WebProbe = WebProbe.Failed(WebFailureCode.OTHER, "not used")
        override suspend fun download(
            url: String,
            jobId: String,
            headers: Map<String, String>,
            saveViaBlob: Boolean,
            onProgress: (downloaded: Long, total: Long?) -> Unit,
        ): WebDownload = WebDownload.Failed(WebFailureCode.OTHER, "not used")

        override suspend fun fetchPage(url: String): WebPage = WebPage.Failed(WebFailureCode.OTHER, "not used")

        override suspend fun fetch(request: HttpRequest): WebFetch {
            requests += request
            return fetchReply
        }

        override suspend fun cancelDownload(jobId: String) = Unit
    }
}