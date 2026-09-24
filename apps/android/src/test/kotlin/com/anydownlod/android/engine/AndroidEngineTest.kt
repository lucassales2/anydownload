package com.anydownlod.android.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.JavaNetFileStore
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-equivalent Android engine tests: direct-file success through the shared
 * HTTP engine and NeedsExtractor dispatch into a fake [ChaquopyPort]. No
 * Python runtime is required, and the Python seam is never imported outside
 * `apps/android`.
 */
class AndroidEngineTest {

    private fun httpEngine(
        transfer: HttpTransfer,
        fileStore: FileStore,
        root: Path,
        scope: CoroutineScope,
        urlCheck: (String) -> UrlCheck = UrlPolicy::check,
        registry: com.anydownlod.core.extract.ExtractorRegistry? = null,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = fileStore,
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
        scope = scope,
        ioDispatcher = Dispatchers.Default,
        urlCheck = urlCheck,
        registry = registry,
    )

    private fun chaquopyEngine(
        port: ChaquopyPort,
        root: Path,
        scope: CoroutineScope,
    ): ChaquopyEngine = ChaquopyEngine(
        port = port,
        downloadRoot = { root.toString() },
        scope = scope,
        ioDispatcher = Dispatchers.Default,
    )

    @Test
    fun directFileCompletesThroughHttpEngineAndNeverTouchesPython() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-")
        val payload = ByteArray(4096) { 7 }
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", payload.size.toLong(), FakeBody(listOf(payload)))
        }
        val port = FakeChaquopyPort(available = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val http = httpEngine(transfer, JavaNetFileStore(root), root, scope)
            val chaquopy = chaquopyEngine(port, root, scope)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = { AndroidRoute.DIRECT_FILE }, scope = scope)

            val job = routing.submit(
                DownloadRequest(
                    sourceUrl = "https://fixtures.example.com/files/tiny.bin",
                    options = DownloadOptions(),
                    idempotencyKey = "android-direct-1",
                )
            )
            val finished = waitFor(routing, job.id, JobState.COMPLETED)

            assertEquals(JobState.COMPLETED, finished.state)
            assertEquals(payload.size.toLong(), finished.progress?.downloadedBytes)
            assertTrue(Files.isRegularFile(root.resolve("tiny.bin")))
            assertEquals(0, port.received.size, "a direct file must never reach the Chaquopy port")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun kotlinRouteCompletesThroughTheHttpEngineAndNeverTouchesPython() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-kotlin")
        val payload = ByteArray(2048) { 3 }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fakeExtractor = object : com.anydownlod.core.extract.InfoExtractor(
                ieKey = com.anydownlod.core.extract.ExtractorRegistry.GENERIC_KEY,
                http = com.anydownlod.core.extract.ExtractorHttp(
                    FakeTransfer { HttpResponse.Final(404) },
                ),
                validUrl = Regex("""https?://fixtures\.example\.com/watch.*"""),
            ) {
                override suspend fun extract(url: String): com.anydownlod.core.extract.InfoDict =
                    com.anydownlod.core.extract.InfoDict(
                        id = "fixture",
                        title = "Fixture Clip",
                        formats = listOf(
                            com.anydownlod.core.extract.MediaFormat(
                                formatId = "18",
                                url = "https://cdn.fixtures.example.com/clip.mp4",
                                ext = "mp4",
                                vcodec = "avc1",
                                acodec = "mp4a",
                            ),
                        ),
                    )
            }
            val registry = com.anydownlod.core.extract.ExtractorRegistry(listOf(fakeExtractor))
            val classifier = AndroidRouteClassifier(registry = registry)
            val mediaTransfer = FakeTransfer {
                HttpResponse.Final(200, "application/octet-stream", payload.size.toLong(), FakeBody(listOf(payload)))
            }
            val port = FakeChaquopyPort(available = true)
            val http = httpEngine(mediaTransfer, JavaNetFileStore(root), root, scope, registry = registry)
            val chaquopy = chaquopyEngine(port, root, scope)
            val routing = AndroidRoutingEngine(
                http = http,
                chaquopy = chaquopy,
                classify = { url -> classifier.route(url) },
                scope = scope,
            )
            val url = "https://fixtures.example.com/watch?v=fixture"
            assertEquals(AndroidRoute.KOTLIN, classifier.route(url))

            val job = routing.submit(
                DownloadRequest(sourceUrl = url, options = DownloadOptions(), idempotencyKey = "android-kotlin"),
            )
            val finished = waitFor(routing, job.id, JobState.COMPLETED)

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals("Fixture Clip", finished.title)
            assertEquals("Fixture Clip.mp4", finished.artifacts.single().relativePath)
            assertTrue(Files.isRegularFile(root.resolve("Fixture Clip.mp4")))
            assertEquals(0, port.received.size, "a registry-matched URL must never reach Chaquopy")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aRegistryMatchedUrlSkipsTheProbeAndKeepsUnmatchedUrlsOnChaquopy() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(0), 0).also { it.start() }
        val requests = java.util.concurrent.atomic.AtomicInteger(0)
        server.createContext("/youtube/watch") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        try {
            val matchedUrl = "http://127.0.0.1:${server.address.port}/youtube/watch"
            val fakeExtractor = object : com.anydownlod.core.extract.InfoExtractor(
                ieKey = com.anydownlod.core.extract.ExtractorRegistry.GENERIC_KEY,
                http = com.anydownlod.core.extract.ExtractorHttp(FakeTransfer { HttpResponse.Final(404) }),
                validUrl = Regex("""http://127\.0\.0\.1:\d+/youtube/.*"""),
            ) {
                override suspend fun extract(url: String): com.anydownlod.core.extract.InfoDict =
                    com.anydownlod.core.extract.InfoDict()
            }
            val registry = com.anydownlod.core.extract.ExtractorRegistry(listOf(fakeExtractor))
            val classifier = AndroidRouteClassifier(registry = registry)

            assertEquals(AndroidRoute.KOTLIN, classifier.route(matchedUrl))
            assertEquals(0, requests.get(), "a registry-matched URL must not probe")

            val unmatched = AndroidRouteClassifier(
                urlCheck = { url ->
                    if (url.startsWith("http://127.0.0.1:${server.address.port}")) {
                        UrlCheck.Allowed(url)
                    } else {
                        UrlPolicy.check(url)
                    }
                },
            )
            assertEquals(
                AndroidRoute.CHAQUOPY,
                unmatched.route("http://127.0.0.1:${server.address.port}/other"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun needsExtractorDispatchesIntoTheFakeChaquopyPort() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-")
        val port = FakeChaquopyPort(
            available = true,
            result = ChaquopyResult.Finished("media/One public video.mp4", 42L),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // The HTTP engine would see nothing; the classifier routes to Chaquopy.
            val httpSeen = AtomicBoolean(false)
            val http = httpEngine(
                FakeTransfer { httpSeen.set(true); HttpResponse.Unavailable("unused") },
                JavaNetFileStore(root),
                root,
                scope,
            )
            val chaquopy = chaquopyEngine(port, root, scope)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = { AndroidRoute.CHAQUOPY }, scope = scope)

            val request = DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(),
                idempotencyKey = "android-chaquopy-1",
            )
            val job = routing.submit(request)
            val finished = waitFor(routing, job.id, JobState.COMPLETED)

            assertEquals(listOf(request), port.received, "the fake Chaquopy port must receive the NeedsExtractor request")
            assertEquals("media/One public video.mp4", finished.artifacts.single().relativePath)
            assertFalse(httpSeen.get())
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingChaquopyFailsSiteUrlsHonestlyWithoutPretending() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-")
        val port = FakeChaquopyPort(available = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val httpSeen = AtomicBoolean(false)
            val http = httpEngine(
                FakeTransfer { httpSeen.set(true); HttpResponse.Unavailable("unused") },
                JavaNetFileStore(root),
                root,
                scope,
            )
            val chaquopy = chaquopyEngine(port, root, scope)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = { AndroidRoute.CHAQUOPY }, scope = scope)

            val job = routing.submit(
                DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture", idempotencyKey = "android-missing-1")
            )
            val finished = waitFor(routing, job.id, JobState.FAILED)

            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, finished.error?.code)
            assertFalse(finished.error?.retryable ?: true)
            assertTrue(finished.artifacts.isEmpty())
            assertFalse(httpSeen.get())
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelOfDirectFileStopsTheStreamAndLeavesNothingBehind() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-")
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", null, GatedBody(listOf(ByteArray(1024)), gate))
        }
        val port = FakeChaquopyPort(available = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val http = httpEngine(transfer, JavaNetFileStore(root), root, scope)
            val chaquopy = chaquopyEngine(port, root, scope)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = { AndroidRoute.DIRECT_FILE }, scope = scope)

            val job = routing.submit(
                DownloadRequest(sourceUrl = "https://fixtures.example.com/files/stall.bin", idempotencyKey = "android-cancel-1")
            )
            withTimeout(10_000) {
                routing.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
            }

            val cancelled = routing.cancel(job.id)
            assertNotNull(cancelled)
            withTimeout(10_000) {
                routing.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.CANCELLED } }
            }
            withTimeout(10_000) {
                while (Files.list(root).use { it.count() } != 0L) delay(20)
            }
            assertEquals(JobState.CANCELLED, cancelled.state)
            assertTrue(routing.jobs.value.first { it.id == job.id }.artifacts.isEmpty())
            assertEquals(0, port.received.size)
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun htmlFixtureCompletesThroughHttpEngineWithoutPython() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-html-")
        val mediaUrl = "https://fixtures.example.net/clip.bin"
        val payload = ByteArray(4096) { 3 }
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val fixtureCheck: (String) -> UrlCheck = { url ->
            if (url.startsWith("http://127.0.0.1:${server.address.port}")) {
                UrlCheck.Allowed(url)
            } else {
                UrlPolicy.check(url)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            server.createContext("/watch") { exchange ->
                val body = """<html><body><video src="$mediaUrl"></video></body></html>""".toByteArray()
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
                exchange.close()
            }
            server.start()

            val port = FakeChaquopyPort(available = true)
            val http = httpEngine(
                FakeTransfer { url ->
                    if (url.startsWith("http://127.0.0.1:")) {
                        val html = """<html><body><video src="$mediaUrl"></video></body></html>""".toByteArray()
                        HttpResponse.Final(200, "text/html; charset=utf-8", html.size.toLong(), FakeBody(listOf(html)))
                    } else {
                        HttpResponse.Final(200, "application/octet-stream", payload.size.toLong(), FakeBody(listOf(payload)))
                    }
                },
                JavaNetFileStore(root),
                root,
                scope,
                urlCheck = fixtureCheck,
            )
            val chaquopy = chaquopyEngine(port, root, scope)
            val classifier = AndroidRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = classifier::route, scope = scope)

            val job = routing.submit(
                DownloadRequest(
                    sourceUrl = "http://127.0.0.1:${server.address.port}/watch",
                    options = DownloadOptions(),
                    idempotencyKey = "android-html-1",
                )
            )
            val finished = waitFor(routing, job.id, JobState.COMPLETED)

            assertEquals(JobState.COMPLETED, finished.state)
            assertEquals("clip.bin", finished.artifacts.single().relativePath)
            assertTrue(Files.isRegularFile(root.resolve("clip.bin")))
            assertEquals(0, port.received.size, "a matching page must never reach the Chaquopy port")
        } finally {
            server.stop(0)
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun unresolvedHtmlStillDispatchesIntoTheFakeChaquopyPort() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-html2-")
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val fixtureCheck: (String) -> UrlCheck = { url ->
            if (url.startsWith("http://127.0.0.1:${server.address.port}")) {
                UrlCheck.Allowed(url)
            } else {
                UrlPolicy.check(url)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            server.createContext("/page") { exchange ->
                val body = "<html><body><p>no media here</p></body></html>".toByteArray()
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
                exchange.close()
            }
            server.start()

            val port = FakeChaquopyPort(
                available = true,
                result = ChaquopyResult.Finished("media/One site video.mp4", 42L),
            )
            val httpSeen = AtomicBoolean(false)
            val http = httpEngine(
                FakeTransfer { httpSeen.set(true); HttpResponse.Unavailable("unused") },
                JavaNetFileStore(root),
                root,
                scope,
            )
            val chaquopy = chaquopyEngine(port, root, scope)
            val classifier = AndroidRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = classifier::route, scope = scope)

            val request = DownloadRequest(
                sourceUrl = "http://127.0.0.1:${server.address.port}/page",
                options = DownloadOptions(),
                idempotencyKey = "android-html-2",
            )
            val job = routing.submit(request)
            val finished = waitFor(routing, job.id, JobState.COMPLETED)

            assertEquals(listOf(request), port.received, "unresolved HTML must reach the Chaquopy port")
            assertEquals("media/One site video.mp4", finished.artifacts.single().relativePath)
            assertFalse(httpSeen.get())
        } finally {
            server.stop(0)
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelOfHtmlFixtureJobLeavesNothingBehind() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-android-html-cancel-")
        val mediaUrl = "https://fixtures.example.net/stall.bin"
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val fixtureCheck: (String) -> UrlCheck = { url ->
            if (url.startsWith("http://127.0.0.1:${server.address.port}")) {
                UrlCheck.Allowed(url)
            } else {
                UrlPolicy.check(url)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            server.createContext("/watch") { exchange ->
                val body = """<html><body><video src="$mediaUrl"></video></body></html>""".toByteArray()
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
                exchange.close()
            }
            server.start()

            val port = FakeChaquopyPort(available = true)
            val http = httpEngine(
                FakeTransfer { url ->
                    if (url.startsWith("http://127.0.0.1:")) {
                        val html = """<html><body><video src="$mediaUrl"></video></body></html>""".toByteArray()
                        HttpResponse.Final(200, "text/html; charset=utf-8", html.size.toLong(), FakeBody(listOf(html)))
                    } else {
                        HttpResponse.Final(
                            statusCode = 200,
                            contentType = "application/octet-stream",
                            totalBytes = null,
                            body = GatedBody(listOf(ByteArray(1024), ByteArray(1024)), gate),
                        )
                    }
                },
                JavaNetFileStore(root),
                root,
                scope,
                urlCheck = fixtureCheck,
            )
            val chaquopy = chaquopyEngine(port, root, scope)
            val classifier = AndroidRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck)
            val routing = AndroidRoutingEngine(http = http, chaquopy = chaquopy, classify = classifier::route, scope = scope)

            val job = routing.submit(
                DownloadRequest(
                    sourceUrl = "http://127.0.0.1:${server.address.port}/watch",
                    options = DownloadOptions(),
                    idempotencyKey = "android-html-cancel-1",
                )
            )
            withTimeout(10_000) {
                routing.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
            }

            val cancelled = routing.cancel(job.id)
            assertNotNull(cancelled)
            withTimeout(10_000) {
                routing.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.CANCELLED } }
            }
            withTimeout(10_000) {
                while (Files.list(root).use { it.count() } != 0L) delay(20)
            }
            assertEquals(JobState.CANCELLED, cancelled.state)
            assertTrue(routing.jobs.value.first { it.id == job.id }.artifacts.isEmpty())
            assertEquals(0, port.received.size)
        } finally {
            server.stop(0)
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    private suspend fun waitFor(engine: AndroidRoutingEngine, jobId: String, state: JobState): DownloadJob =
        withTimeout(10_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state == state } }.first { it.id == jobId }
        }
}

// ---------------------------------------------------------------- in-memory fakes

private class FakeChaquopyPort(
    val received: MutableList<DownloadRequest> = mutableListOf(),
    val result: ChaquopyResult = ChaquopyResult.Finished("out.bin", 1L),
    override val available: Boolean,
) : ChaquopyPort {
    override suspend fun runDownload(request: DownloadRequest, downloadRoot: String): ChaquopyResult {
        received += request
        return result
    }
}

private class FakeTransfer(
    val requested: MutableList<String> = mutableListOf(),
    private val responder: (url: String) -> HttpResponse,
) : HttpTransfer {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requested.add(request.url)
        return responder(request.url)
    }
}

private class FakeBody(private val chunks: List<ByteArray>) : HttpBody {
    private var index = 0
    override suspend fun readNext(buffer: ByteArray): Int {
        currentCoroutineContext().ensureActive()
        if (index >= chunks.size) return -1
        val chunk = chunks[index++]
        val count = minOf(chunk.size, buffer.size)
        chunk.copyInto(buffer, 0, 0, count)
        return count
    }

    override suspend fun close() = Unit
}

private class GatedBody(
    private val chunks: List<ByteArray>,
    private val gate: Channel<Unit>,
) : HttpBody {
    private var index = 0
    override suspend fun readNext(buffer: ByteArray): Int {
        gate.receive()
        currentCoroutineContext().ensureActive()
        if (index >= chunks.size) return -1
        val chunk = chunks[index++]
        val count = minOf(chunk.size, buffer.size)
        chunk.copyInto(buffer, 0, 0, count)
        return count
    }

    override suspend fun close() = Unit
}