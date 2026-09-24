package com.anydownlod.core.platform

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * T-056 shared request contract for the JVM/Android `java.net` transport
 * (`JavaNetHttpTransfer` lives in `jvmAndroidMain`, so the same source is
 * compiled for Android). The server is a loopback fixture; loopback is only
 * reachable here because this test drives the transfer directly, not through
 * the engine's destination policy.
 */
class JavaNetRequestContractTest {

    private fun server(): HttpServer =
        HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun base(server: HttpServer) = "http://127.0.0.1:${server.address.port}"

    private fun readAll(body: HttpBody?): ByteArray {
        if (body == null) return ByteArray(0)
        val out = mutableListOf<Byte>()
        val buffer = ByteArray(512)
        runBlocking {
            while (true) {
                val count = body.readNext(buffer)
                if (count == -1) break
                for (index in 0 until count) out += buffer[index]
            }
            body.close()
        }
        return out.toByteArray()
    }

    @Test
    fun declaredHeadersArriveAndRefusedHeadersNeverLeave() = runBlocking {
        val server = server()
        val seenHeaders = AtomicReference<Map<String, List<String>>>(emptyMap())
        server.createContext("/headers") { exchange ->
            seenHeaders.set(exchange.requestHeaders.mapValues { it.value.toList() })
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        try {
            val dropped = mutableListOf<List<String>>()
            val transfer = JavaNetHttpTransfer(onDroppedHeaders = { dropped += it })
            val response = transfer.execute(
                HttpRequest(
                    url = "${base(server)}/headers",
                    headers = mapOf(
                        "X-YouTube-Client-Name" to "101",
                        "X-YouTube-Client-Version" to "1.02",
                        "Cookie" to "session=secret",
                        "Authorization" to "Bearer secret",
                    ),
                ),
            )
            assertIs<HttpResponse.Final>(response)
            assertEquals(204, response.statusCode)

            val headers = seenHeaders.get().keys.map { it.lowercase() }
            assertTrue("x-youtube-client-name" in headers, "the declared extractor header must reach the server")
            assertTrue("x-youtube-client-version" in headers)
            assertFalse("cookie" in headers, "a refused header must never leave the device")
            assertFalse("authorization" in headers)
            assertEquals(listOf(listOf("authorization", "cookie")), dropped)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun postBodyArrivesAndTheResponseBodyStreamsBack() = runBlocking {
        val server = server()
        val seen = AtomicReference<Triple<String, String?, ByteArray>>(Triple("", null, ByteArray(0)))
        server.createContext("/post") { exchange ->
            val body = exchange.requestBody.readBytes()
            seen.set(
                Triple(
                    exchange.requestMethod,
                    exchange.requestHeaders.getFirst("Content-Type"),
                    body,
                ),
            )
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        try {
            val transfer = JavaNetHttpTransfer()
            val payload = """{"videoId":"fixture"}""".encodeToByteArray()
            val response = assertIs<HttpResponse.Final>(
                transfer.execute(
                    HttpRequest(
                        url = "${base(server)}/post",
                        method = HttpMethods.POST,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = payload,
                    ),
                ),
            )
            assertEquals("POST", seen.get().first)
            assertEquals("application/json", seen.get().second)
            assertContentEquals(payload, seen.get().third)
            assertEquals(200, response.statusCode)
            assertContentEquals(payload, readAll(response.body))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun byteRangeYields206WithContentRangeAndTheFullTotal() = runBlocking {
        val server = server()
        server.createContext("/files/ranged.bin") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            assertEquals("bytes=0-1023", range)
            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            exchange.responseHeaders.set("Content-Range", "bytes 0-1023/4096")
            exchange.sendResponseHeaders(206, 1024L)
            exchange.responseBody.use { it.write(ByteArray(1024) { 7 }) }
        }
        try {
            val transfer = JavaNetHttpTransfer()
            val response = assertIs<HttpResponse.Final>(
                transfer.execute(
                    HttpRequest(url = "${base(server)}/files/ranged.bin", range = 0L..1023L),
                ),
            )
            assertEquals(206, response.statusCode)
            assertEquals("bytes 0-1023/4096", response.contentRange)
            assertEquals(4096L, response.totalBytes)
            assertEquals("bytes 0-1023/4096", response.headers["content-range"])
            assertEquals(1024, readAll(response.body).size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun responseHeadersAreAllowlisted() = runBlocking {
        val server = server()
        server.createContext("/headers/response") { exchange ->
            exchange.responseHeaders.set("Content-Type", "text/plain")
            exchange.responseHeaders.set("Set-Cookie", "session=secret")
            exchange.responseHeaders.set("X-Custom", "fixture")
            exchange.responseHeaders.set("ETag", "\"abc\"")
            exchange.sendResponseHeaders(200, 2L)
            exchange.responseBody.use { it.write("ok".encodeToByteArray()) }
        }
        try {
            val response = assertIs<HttpResponse.Final>(
                JavaNetHttpTransfer().execute(HttpRequest("${base(server)}/headers/response")),
            )
            assertFalse("set-cookie" in response.headers, "a Set-Cookie must never cross the port")
            assertFalse("x-custom" in response.headers)
            assertEquals("text/plain", response.headers["content-type"])
            assertEquals("\"abc\"", response.headers["etag"])
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun postRedirectIsSurfacedForTheCallerToRefuse() = runBlocking {
        val server = server()
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.set("Location", "/result")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        try {
            val post = HttpRequest(
                url = "${base(server)}/redirect",
                method = HttpMethods.POST,
                body = byteArrayOf(1, 2, 3),
            )
            val redirect = assertIs<HttpResponse.Redirect>(
                JavaNetHttpTransfer().execute(post),
            )
            assertEquals("${base(server)}/result", redirect.location)
            val decision = assertIs<RedirectDecision.Fails>(
                HttpRedirects.afterRedirect(post, 302, redirect.location),
            )
            assertEquals(RedirectFailure.POST_REDIRECT_NEEDS_303, decision.reason)
        } finally {
            server.stop(0)
        }
    }
}