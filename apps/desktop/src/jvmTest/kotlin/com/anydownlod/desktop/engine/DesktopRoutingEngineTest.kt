package com.anydownlod.desktop.engine

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
import kotlin.test.assertTrue

class DesktopRoutingEngineTest {

    private fun cliEngine(
        root: Path,
        runner: CliProcessRunner,
        scope: CoroutineScope,
        resolve: (String) -> String? = { "/fake/yt-dlp" },
    ): YtDlpCliEngine = YtDlpCliEngine(
        settingsRepository = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
        scope = scope,
        runner = runner,
        resolveExecutable = resolve,
        ioDispatcher = Dispatchers.Default,
    )

    private fun httpEngine(
        transfer: HttpTransfer,
        fileStore: FileStore,
        root: Path,
        scope: CoroutineScope,
        urlCheck: (String) -> UrlCheck = UrlPolicy::check,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = fileStore,
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
        scope = scope,
        ioDispatcher = Dispatchers.Default,
        urlCheck = urlCheck,
    )

    private fun routing(
        http: HttpDownloadEngine,
        cli: YtDlpCliEngine,
        classify: (String) -> DesktopRoute,
        scope: CoroutineScope,
    ): DesktopRoutingEngine = DesktopRoutingEngine(http = http, cli = cli, classify = classify, scope = scope)

    @Test
    fun directFileCompletesWithoutSpawningAnyProcess() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-")
        val processStarted = AtomicBoolean(false)
        val payload = ByteArray(4096) { 1 }
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", payload.size.toLong(), FakeBody(listOf(payload)))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val http = httpEngine(transfer, JavaNetFileStore(root), root, scope)
            val cli = cliEngine(root, CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) }, scope)
            val engine = routing(http, cli, { DesktopRoute.DIRECT_FILE }, scope)

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "https://fixtures.example.com/files/tiny.bin",
                    options = DownloadOptions(),
                    idempotencyKey = "direct-1",
                )
            )
            val finished = waitFor(engine, job.id, JobState.COMPLETED)

            assertEquals(JobState.COMPLETED, finished.state)
            assertEquals(payload.size.toLong(), finished.progress?.downloadedBytes)
            assertEquals("tiny.bin", finished.artifacts.single().relativePath)
            assertTrue(Files.isRegularFile(root.resolve("tiny.bin")))
            assertEquals(payload.size.toLong(), Files.size(root.resolve("tiny.bin")))
            assertFalse(processStarted.get(), "a direct file must never spawn yt-dlp")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun siteOrHtmlUrlStaysOnTheCliPath() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-")
        val file = root.resolve("One public video.mp4")
        Files.writeString(file, "fake media")
        val lines = listOf(
            "TITLE|One public video",
            "DL|downloading|10485760|NA|10485760|1048576.0|5",
            "PP|started",
            "FILE|$file",
        )
        val cliSeen = AtomicBoolean(false)
        val httpTouched = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cli = cliEngine(root, CliProcessRunner { _, _ -> cliSeen.set(true); FakeCliProcess(lines, exitCode = 0) }, scope)
            val http = httpEngine(
                FakeTransfer { httpTouched.set(true); HttpResponse.Unavailable("must not be used") },
                JavaNetFileStore(root),
                root,
                scope,
            )
            val engine = routing(http, cli, { DesktopRoute.YTDLP_CLI }, scope)

            val job = engine.submit(
                DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture", idempotencyKey = "cli-1")
            )
            val finished = waitFor(engine, job.id, JobState.COMPLETED)

            assertEquals("One public video.mp4", finished.artifacts.single().fileName)
            assertTrue(cliSeen.get())
            assertFalse(httpTouched.get(), "the http engine must not be asked for a site URL")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelOfDirectFileStopsTheStreamAndLeavesNoCompletedFile() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-")
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", null, GatedBody(listOf(ByteArray(1024)), gate))
        }
        val processStarted = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val http = httpEngine(transfer, JavaNetFileStore(root), root, scope)
            val cli = cliEngine(root, CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) }, scope)
            val engine = routing(http, cli, { DesktopRoute.DIRECT_FILE }, scope)

            val job = engine.submit(
                DownloadRequest(sourceUrl = "https://fixtures.example.com/files/stall.bin", idempotencyKey = "cancel-1")
            )
            withTimeout(10_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
            }

            val cancelled = engine.cancel(job.id)
            withTimeout(10_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.CANCELLED } }
            }

            assertEquals(JobState.CANCELLED, cancelled?.state)
            assertEquals(JobErrorCode.CANCELLED, engine.jobs.value.first { it.id == job.id }.error?.code)
            // No published artifact and the temp file was discarded: nothing
            // under the download root survives.
            withTimeout(10_000) {
                while (Files.list(root).use { it.count() } != 0L) delay(20)
            }
            assertTrue(engine.jobs.value.first { it.id == job.id }.artifacts.isEmpty())
            assertFalse(processStarted.get())
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingYtDlpStillFailsSiteUrlsThroughTheCliPath() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-")
        val httpTouched = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cli = cliEngine(root, CliProcessRunner { _, _ -> FakeCliProcess(emptyList()) }, scope, resolve = { null })
            val http = httpEngine(
                FakeTransfer { httpTouched.set(true); HttpResponse.Unavailable("must not be used") },
                JavaNetFileStore(root),
                root,
                scope,
            )
            val engine = routing(http, cli, { DesktopRoute.YTDLP_CLI }, scope)

            val job = engine.submit(
                DownloadRequest(sourceUrl = "https://example.com/watch?v=secret", idempotencyKey = "missing-1")
            )

            assertEquals(JobState.FAILED, job.state)
            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, job.error?.code)
            assertTrue(job.error!!.message.contains("yt-dlp"))
            assertFalse(httpTouched.get())
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun htmlFixtureRoutedByClassifierCompletesWithoutProcess() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-html-")
        val processStarted = AtomicBoolean(false)
        val mediaUrl = "https://fixtures.example.net/clip.bin"
        val payload = ByteArray(4096) { 2 }
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

            val http = httpEngine(
                FakeTransfer { url ->
                    if (url.startsWith("http://127.0.0.1:")) {
                        // The engine re-fetches the page itself (T-046 route).
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
            val cli = cliEngine(
                root,
                CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) },
                scope,
            )
            val classifier = DesktopRouteClassifier(
                connectTimeoutMillis = 2_000,
                readTimeoutMillis = 2_000,
                urlCheck = fixtureCheck,
            )
            val engine = routing(http, cli, classifier::route, scope)

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "http://127.0.0.1:${server.address.port}/watch",
                    options = DownloadOptions(),
                    idempotencyKey = "html-fixture-1",
                )
            )
            val finished = waitFor(engine, job.id, JobState.COMPLETED)

            assertEquals(JobState.COMPLETED, finished.state)
            assertEquals("clip.bin", finished.artifacts.single().relativePath)
            assertTrue(Files.isRegularFile(root.resolve("clip.bin")))
            assertEquals(payload.size.toLong(), Files.size(root.resolve("clip.bin")))
            assertFalse(processStarted.get(), "a fixture media page must never spawn yt-dlp")
        } finally {
            server.stop(0)
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun nonMatchingHtmlPageStillReachesTheCliPath() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-nomatch-")
        val file = root.resolve("One public video.mp4")
        Files.writeString(file, "fake media")
        val lines = listOf(
            "TITLE|One public video",
            "DL|downloading|10485760|NA|10485760|1048576.0|5",
            "PP|started",
            "FILE|$file",
        )
        val cliSeen = AtomicBoolean(false)
        val httpTouched = AtomicBoolean(false)
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

            val cli = cliEngine(root, CliProcessRunner { _, _ -> cliSeen.set(true); FakeCliProcess(lines, exitCode = 0) }, scope)
            val http = httpEngine(
                FakeTransfer { httpTouched.set(true); HttpResponse.Unavailable("must not be used") },
                JavaNetFileStore(root),
                root,
                scope,
            )
            val classifier = DesktopRouteClassifier(
                connectTimeoutMillis = 2_000,
                readTimeoutMillis = 2_000,
                urlCheck = fixtureCheck,
            )
            val engine = routing(http, cli, classifier::route, scope)

            val job = engine.submit(
                DownloadRequest(sourceUrl = "http://127.0.0.1:${server.address.port}/page", idempotencyKey = "html-nomatch-1")
            )
            val finished = waitFor(engine, job.id, JobState.COMPLETED)

            assertEquals("One public video.mp4", finished.artifacts.single().fileName)
            assertTrue(cliSeen.get())
            assertFalse(httpTouched.get(), "an unresolved page must stay on the CLI path")
        } finally {
            server.stop(0)
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelOfHtmlFixtureJobLeavesNoCompletedFile() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-routing-html-cancel-")
        val processStarted = AtomicBoolean(false)
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
            val cli = cliEngine(root, CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) }, scope)
            val classifier = DesktopRouteClassifier(
                connectTimeoutMillis = 2_000,
                readTimeoutMillis = 2_000,
                urlCheck = fixtureCheck,
            )
            val engine = routing(http, cli, classifier::route, scope)

            val job = engine.submit(
                DownloadRequest(sourceUrl = "http://127.0.0.1:${server.address.port}/watch", idempotencyKey = "html-cancel-1")
            )
            withTimeout(10_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
            }

            val cancelled = engine.cancel(job.id)
            waitFor(engine, job.id, JobState.CANCELLED)

            assertEquals(JobState.CANCELLED, cancelled?.state)
            assertEquals(JobErrorCode.CANCELLED, engine.jobs.value.first { it.id == job.id }.error?.code)
            withTimeout(10_000) {
                while (Files.list(root).use { it.count() } != 0L) delay(20)
            }
            assertTrue(engine.jobs.value.first { it.id == job.id }.artifacts.isEmpty())
            assertFalse(processStarted.get())
        } finally {
            server.stop(0)
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    private suspend fun waitFor(engine: DesktopRoutingEngine, jobId: String, state: JobState): DownloadJob =
        withTimeout(10_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state == state } }
                .first { it.id == jobId }
        }
}

// ---------------------------------------------------------------- in-memory fakes

private class FakeTransfer(
    val requested: MutableList<String> = mutableListOf(),
    private val responder: (url: String) -> HttpResponse,
) : HttpTransfer {
    override suspend fun execute(url: String): HttpResponse {
        requested.add(url)
        return responder(url)
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

/** Suspends on the gate before every read, so a test can cancel mid-transfer. */
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