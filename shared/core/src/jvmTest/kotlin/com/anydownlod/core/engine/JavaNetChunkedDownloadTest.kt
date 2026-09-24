package com.anydownlod.core.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.extract.DownloaderOptions
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.JavaNetFileStore
import com.anydownlod.core.platform.JavaNetHttpTransfer
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * T-061 ranged-chunk download against a local `HttpServer`: a format with
 * `http_chunk_size` is fetched as sequential `Range` requests into one file.
 */
class JavaNetChunkedDownloadTest {

    private val payload = ByteArray(9_000) { (it % 251).toByte() }

    private fun testServer(): HttpServer =
        HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun baseUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}"

    private fun fixtureCheck(base: String): (String) -> UrlCheck = { url ->
        if (url.startsWith(base)) UrlCheck.Allowed(url) else UrlPolicy.check(url)
    }

    private class ChunkedExtractor(private val url: String, private val size: Long, private val chunk: Long) :
        InfoExtractor(
            ieKey = ExtractorRegistry.GENERIC_KEY,
            http = ExtractorHttp(JavaNetHttpTransfer()),
            validUrl = Regex("""https?://chunked\.example/.+"""),
        ) {
        override suspend fun extract(unused: String): InfoDict = InfoDict(
            id = "chunked",
            title = "Chunked Fixture",
            formats = listOf(
                MediaFormat(
                    formatId = "1",
                    url = url,
                    ext = "mp4",
                    vcodec = "avc1",
                    acodec = "mp4a",
                    filesize = size,
                    downloaderOptions = DownloaderOptions(httpChunkSize = chunk),
                ),
            ),
        )
    }

    private fun engine(
        server: HttpServer,
        root: Path,
        extractor: InfoExtractor,
    ): HttpDownloadEngine {
        val base = baseUrl(server)
        return HttpDownloadEngine(
            transfer = JavaNetHttpTransfer(),
            fileStore = JavaNetFileStore(root),
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            urlCheck = fixtureCheck(base),
            registry = ExtractorRegistry(listOf(extractor)),
        )
    }

    private suspend fun waitForTerminal(engine: HttpDownloadEngine, jobId: String): DownloadJob =
        withTimeout(30_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state.isTerminal } }
                .first { it.id == jobId }
        }

    private fun rangeHandler(
        ranges: MutableList<String>,
        statusFor: (index: Int) -> Int = { 206 },
        gate: CountDownLatch? = null,
    ): (com.sun.net.httpserver.HttpExchange) -> Unit = { exchange ->
        val range = exchange.requestHeaders.getFirst("Range") ?: "bytes=0-${payload.size - 1}"
        val requestIndex = synchronized(ranges) { ranges += range; ranges.size - 1 }
        val status = statusFor(requestIndex)
        if (status != 206) {
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        } else {
            val match = Regex("bytes=(\\d+)-(\\d+)").find(range)
            val start = match?.groupValues?.get(1)?.toInt() ?: 0
            val end = match?.groupValues?.get(2)?.toInt()?.coerceAtMost(payload.size - 1) ?: payload.size - 1
            if (requestIndex > 0) gate?.await(15, TimeUnit.SECONDS)
            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            exchange.responseHeaders.set("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, (end - start + 1).toLong())
            exchange.responseBody.use { it.write(payload, start, end - start + 1) }
        }
    }

    @Test
    fun rangedChunksAssembleByteExact() = runBlocking {
        val server = testServer()
        val ranges = Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/files/chunked.bin", rangeHandler(ranges))
        val root = Files.createTempDirectory("anydownlod-chunked")
        try {
            val base = baseUrl(server)
            val engine = engine(
                server,
                root,
                ChunkedExtractor("$base/files/chunked.bin", payload.size.toLong(), chunk = 2048),
            )
            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "https://chunked.example/watch",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "chunked-key",
                ),
            )
            val finished = waitForTerminal(engine, job.id)

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(payload.size.toLong(), finished.progress?.downloadedBytes)
            assertEquals(payload.size.toLong(), finished.progress?.totalBytes)
            assertEquals("Chunked Fixture.mp4", finished.artifacts.single().relativePath)
            assertTrue(Files.readAllBytes(root.resolve("Chunked Fixture.mp4")).contentEquals(payload))
            // 9000 bytes / 2048 per chunk => 5 requests.
            assertTrue(ranges.size >= 4, "expected several ranged requests, got ${ranges.size}")
            assertEquals("bytes=0-2047", ranges.first())
            assertTrue(ranges.last().startsWith("bytes=8192-"), ranges.last())
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun a403OnALaterChunkIsUnavailableOrPrivate() = runBlocking {
        val server = testServer()
        val ranges = Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/files/blocked.bin", rangeHandler(ranges, statusFor = { index -> if (index == 0) 206 else 403 }))
        val root = Files.createTempDirectory("anydownlod-chunked-403")
        try {
            val base = baseUrl(server)
            val engine = engine(
                server,
                root,
                ChunkedExtractor("$base/files/blocked.bin", payload.size.toLong(), chunk = 2048),
            )
            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "https://chunked.example/watch",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "chunked-403",
                ),
            )
            val finished = waitForTerminal(engine, job.id)

            assertEquals(JobState.FAILED, finished.state)
            assertEquals(JobErrorCode.UNAVAILABLE_OR_PRIVATE, finished.error?.code)
            assertTrue(finished.artifacts.isEmpty())
            assertEquals(0L, Files.list(root).use { it.count() })
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelBetweenChunksLeavesNoFile() = runBlocking {
        val server = testServer()
        val ranges = Collections.synchronizedList(mutableListOf<String>())
        val gate = CountDownLatch(1)
        server.createContext("/files/gated.bin", rangeHandler(ranges, gate = gate))
        val root = Files.createTempDirectory("anydownlod-chunked-cancel")
        try {
            val base = baseUrl(server)
            val engine = engine(
                server,
                root,
                ChunkedExtractor("$base/files/gated.bin", payload.size.toLong(), chunk = 2048),
            )
            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "https://chunked.example/watch",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "chunked-cancel",
                ),
            )
            // Wait until the first chunk reached the file and the next request
            // is parked on the gate.
            withTimeout(10_000) {
                while (engine.jobs.value.first { it.id == job.id }.progress?.downloadedBytes == null) {
                    delay(10)
                }
            }
            engine.cancel(job.id)
            gate.countDown()
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.CANCELLED, finished.state)
            assertTrue(finished.artifacts.isEmpty())
            withTimeout(10_000) {
                while (Files.list(root).use { it.count() } != 0L) delay(10)
            }
            assertEquals(0L, Files.list(root).use { it.count() })
            assertNotNull(engine.jobs.value.first { it.id == job.id })
            Unit
        } finally {
            gate.countDown()
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }
}
