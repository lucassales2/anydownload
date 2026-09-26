package com.anydownlod.desktop.engine

import com.anydownlod.core.MediaPreviewResult
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.twitter.TwitterIE
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * T-096 desktop gate: a public-status fixture previews through [TwitterIE],
 * two selected videos download as two files through the shared engine, and
 * the fake CLI process factory is never invoked. The syndication JSON and the
 * media bytes are synthesized; only the local fixture server is touched.
 */
class DesktopXStatusGateTest {

    private val statusId = "9999999999999999999"
    private val statusUrl = "https://x.com/fixture/status/$statusId"
    private val firstBytes = ByteArray(4096) { 11 }
    private val secondBytes = ByteArray(8192) { 22 }

    private fun testServer(): HttpServer = HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun baseUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}"

    private class XStatusTransfer(private val statusJson: String) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val bytes = statusJson.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/json",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private fun statusJson(base: String) = """
        {
          "__typename": "Tweet",
          "id_str": "$statusId",
          "text": "Fixture status with two videos",
          "created_at": "2026-09-01T12:00:00.000Z",
          "possibly_sensitive": false,
          "view_count": 10,
          "user": {
            "id_str": "2222222222222222222",
            "name": "Fixture Poster",
            "screen_name": "fixture_poster",
            "protected": false
          },
          "mediaDetails": [
            {
              "type": "video",
              "id_str": "3333333333333333333",
              "media_url_https": "https://pbs.example/media/first.jpg",
              "sizes": {"small": {"w": 680, "h": 383}},
              "video_info": {
                "duration_millis": 5000,
                "variants": [
                  {"bitrate": 832000, "content_type": "video/mp4", "url": "$base/media/first.mp4"}
                ]
              }
            },
            {
              "type": "video",
              "id_str": "4444444444444444444",
              "media_url_https": "https://pbs.example/media/second.jpg",
              "sizes": {"small": {"w": 680, "h": 383}},
              "video_info": {
                "duration_millis": 7000,
                "variants": [
                  {"bitrate": 256000, "content_type": "video/mp4", "url": "$base/media/second.mp4"}
                ]
              }
            }
          ]
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
    fun selectedStatusVideosDownloadTwoFilesWithoutAProcess() = runBlocking {
        val server = testServer()
        server.createContext("/media/first.mp4") { exchange ->
            exchange.responseHeaders.set("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, firstBytes.size.toLong())
            exchange.responseBody.use { it.write(firstBytes) }
        }
        server.createContext("/media/second.mp4") { exchange ->
            exchange.responseHeaders.set("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, secondBytes.size.toLong())
            exchange.responseBody.use { it.write(secondBytes) }
        }
        val root = Files.createTempDirectory("anydownlod-desktop-x-status")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val processStarted = AtomicBoolean(false)
        try {
            val base = baseUrl(server)
            val transfer = XStatusTransfer(statusJson(base))
            val registry = ExtractorRegistry(listOf(TwitterIE(ExtractorHttp(transfer))))
            val classifier = DesktopRouteClassifier(registry = registry)
            assertEquals(DesktopRoute.KOTLIN, classifier.route(statusUrl))

            // Preview first, exactly like the app: the selection comes from
            // the preview's stable media ids.
            val previewSource = DesktopPreviewSource.create(
                runner = CliProcessRunner { _, _ -> error("the CLI must not start") },
                resolveExecutable = { null },
                workingDirectory = { root },
                transfer = transfer,
            )
            val preview = assertIs<MediaPreviewResult.Ready>(previewSource.load(statusUrl)).preview
            assertEquals(
                listOf("3333333333333333333", "4444444444444444444"),
                preview.videos.map { it.mediaId },
            )

            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val http = HttpDownloadEngine(
                transfer = JavaNetHttpTransfer(),
                fileStore = DesktopFileStore { root.toString() },
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

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = statusUrl,
                    selectedMediaIds = listOf("3333333333333333333", "4444444444444444444"),
                ),
            )
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(statusUrl, finished.request.sourceUrl)
            assertEquals(2, finished.artifacts.size)
            assertFalse(processStarted.get(), "no yt-dlp process may start for a matched status URL")

            // Preview and download each performed one guest lookup.
            assertEquals(2, transfer.requests.size)

            val files = finished.artifacts.map { root.resolve(it.relativePath) }
            assertTrue(files.all { Files.exists(it) }, "both artifacts must be on disk")
            assertEquals(firstBytes.size.toLong(), Files.size(files[0]))
            assertEquals(secondBytes.size.toLong(), Files.size(files[1]))
            val names = finished.artifacts.map { it.fileName }
            assertTrue(names.any { it.contains("#1") }, names.toString())
            assertTrue(names.any { it.contains("#2") }, names.toString())
        } finally {
            scope.cancel()
            server.stop(0)
        }
    }
}
