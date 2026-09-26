package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.JavaNetHttpTransfer
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * T-064 desktop click-through with the real engines: the Kotlin YouTube
 * extractor + shared download engine save an M4A audio stream and a
 * progressive video under the download root, and the fake process factory is
 * never invoked. The player JSON and media bytes are synthesized; only the
 * local fixture server is touched.
 */
class DesktopKotlinDownloadIntegrationTest {

    private val videoId = "YE7VzlLtp-4"
    private val audioBytes = ByteArray(4096) { 11 }
    private val videoBytes = ByteArray(8192) { 22 }

    private fun testServer(): HttpServer = HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun baseUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}"

    private class PreviewTransfer(private val playerJson: String) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            val body = if (request.method == "POST") playerJson else "<html></html>"
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = if (request.method == "POST") "application/json" else "text/html",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private fun playerJson(base: String) = """
        {
          "playabilityStatus": {"status": "OK"},
          "videoDetails": {
            "videoId": "$videoId",
            "title": "Big Buck Bunny",
            "author": "Fixture Channel",
            "channelId": "UCfixturechannel000000000",
            "lengthSeconds": "596",
            "viewCount": "123456",
            "isLiveContent": false,
            "thumbnail": {"thumbnails": [{"url": "https://i.example/large.jpg", "width": 1280, "height": 720}]}
          },
          "microformat": {"playerMicroformatRenderer": {"publishDate": "2008-05-29", "isFamilySafe": true}},
          "streamingData": {
            "formats": [
              {"itag": 18, "url": "$base/media/video.mp4",
               "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 360, "averageBitrate": 500000}
            ],
            "adaptiveFormats": [
              {"itag": 140, "url": "$base/media/audio.m4a",
               "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "averageBitrate": 128000}
            ]
          }
        }
    """.trimIndent()

    private fun fixtureCheck(base: String): (String) -> UrlCheck = { url ->
        if (url.startsWith(base)) UrlCheck.Allowed(url) else UrlPolicy.check(url)
    }

    private suspend fun waitForTerminal(engine: DesktopRoutingEngine, jobId: String) =
        withTimeout(30_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state.isTerminal } }
                .first { it.id == jobId }
        }

    @Test
    fun kotlinRouteSavesM4aAudioAndProgressiveVideoWithoutAProcess() = runBlocking {
        val server = testServer()
        server.createContext("/media/audio.m4a") { exchange ->
            exchange.responseHeaders.set("Content-Type", "audio/mp4")
            exchange.sendResponseHeaders(200, audioBytes.size.toLong())
            exchange.responseBody.use { it.write(audioBytes) }
        }
        server.createContext("/media/video.mp4") { exchange ->
            exchange.responseHeaders.set("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, videoBytes.size.toLong())
            exchange.responseBody.use { it.write(videoBytes) }
        }
        val root = Files.createTempDirectory("anydownlod-desktop-kotlin")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val processStarted = AtomicBoolean(false)
        try {
            val base = baseUrl(server)
            val registry = ExtractorRegistry(
                listOf(YoutubeIE(ExtractorHttp(PreviewTransfer(playerJson(base))))),
            )
            val classifier = DesktopRouteClassifier(registry = registry)
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val http = com.anydownlod.core.engine.HttpDownloadEngine(
                transfer = JavaNetHttpTransfer(),
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                urlCheck = fixtureCheck(base),
                registry = registry,
            )
            val cli = YtDlpCliEngine(
                settingsRepository = settings,
                scope = scope,
                runner = CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) },
                resolveExecutable = { "/fake/yt-dlp" },
                ioDispatcher = Dispatchers.Default,
            )
            val engine = DesktopRoutingEngine(http = http, cli = cli, classify = classifier::route, scope = scope)
            val url = "https://www.youtube.com/watch?v=$videoId"
            assertEquals(DesktopRoute.KOTLIN, classifier.route(url))

            val audioJob = engine.submit(
                DownloadRequest(
                    sourceUrl = url,
                    options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A),
                    idempotencyKey = "desktop-audio",
                ),
            )
            val audio = waitForTerminal(engine, audioJob.id)
            assertEquals(JobState.COMPLETED, audio.state, audio.error?.message)
            assertEquals("Big Buck Bunny.m4a", audio.artifacts.single().relativePath)
            assertTrue(Files.readAllBytes(root.resolve("Big Buck Bunny.m4a")).contentEquals(audioBytes))

            val videoJob = engine.submit(
                DownloadRequest(
                    sourceUrl = url,
                    options = DownloadOptions(),
                    idempotencyKey = "desktop-video",
                ),
            )
            val video = waitForTerminal(engine, videoJob.id)
            assertEquals(JobState.COMPLETED, video.state, video.error?.message)
            assertEquals("Big Buck Bunny.mp4", video.artifacts.single().relativePath)
            assertTrue(Files.readAllBytes(root.resolve("Big Buck Bunny.mp4")).contentEquals(videoBytes))

            assertFalse(processStarted.get(), "no yt-dlp process may run for a matched URL")
            assertTrue(cli.jobs.value.isEmpty())
            assertEquals("Big Buck Bunny", video.title)
        } finally {
            scope.cancel()
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun mp3OnTheKotlinRouteFailsTypedWithoutAProcess() = runBlocking {
        val server = testServer()
        val root = Files.createTempDirectory("anydownlod-desktop-kotlin-mp3")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val processStarted = AtomicBoolean(false)
        try {
            val base = baseUrl(server)
            val registry = ExtractorRegistry(
                listOf(YoutubeIE(ExtractorHttp(PreviewTransfer(playerJson(base))))),
            )
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val http = com.anydownlod.core.engine.HttpDownloadEngine(
                transfer = JavaNetHttpTransfer(),
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                urlCheck = fixtureCheck(base),
                registry = registry,
            )
            val cli = YtDlpCliEngine(
                settingsRepository = settings,
                scope = scope,
                runner = CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) },
                resolveExecutable = { "/fake/yt-dlp" },
                ioDispatcher = Dispatchers.Default,
            )
            val engine = DesktopRoutingEngine(http = http, cli = cli, classify = DesktopRouteClassifier(registry = registry)::route, scope = scope)

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                    options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                    idempotencyKey = "desktop-mp3",
                ),
            )
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.FAILED, finished.state)
            assertEquals(com.anydownlod.core.domain.JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
            assertTrue(finished.error?.message?.contains("cannot write MP3") == true, finished.error?.message)
            assertFalse(processStarted.get(), "MP3 must fail typed, not fall back to a process")
            assertEquals(0L, Files.list(root).use { it.count() })
        } finally {
            scope.cancel()
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }
}
