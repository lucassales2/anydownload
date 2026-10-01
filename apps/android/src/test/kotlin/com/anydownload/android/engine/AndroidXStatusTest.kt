package com.anydownload.android.engine

import com.anydownload.core.ExtractorMediaPreviewSource
import com.anydownload.core.MediaPreviewResult
import com.anydownload.core.di.productionExtractorRegistry
import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobState
import com.anydownload.core.engine.HttpDownloadEngine
import com.anydownload.core.engine.UrlCheck
import com.anydownload.core.extract.twitter.TwitterIE
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.jsc.NoJsRuntime
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.platform.JavaNetFileStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * T-097 JVM-equivalent Android fixture path: the same [productionExtractorRegistry]
 * registry the app graph builds routes a matched X status to Kotlin, previews
 * its two videos, and downloads both through the shared engine while the
 * Chaquopy port stays untouched. The syndication JSON and media bytes are
 * synthesized; no Python and no live X/Twitter call.
 */
class AndroidXStatusTest {

    private val statusId = "9999999999999999999"
    private val statusUrl = "https://x.com/fixture/status/$statusId"
    private val firstBytes = ByteArray(2048) { 1 }
    private val secondBytes = ByteArray(4096) { 2 }

    private class StatusFixtureTransfer(private val statusJson: String) : HttpTransfer {
        val requests = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request.url
            val lookup = request.url.contains("cdn.syndication.twimg.com")
            val bytes = when {
                lookup -> statusJson.encodeToByteArray()
                request.url.endsWith("first.mp4") -> ByteArray(2048) { 1 }
                request.url.endsWith("second.mp4") -> ByteArray(4096) { 2 }
                else -> ByteArray(0)
            }
            return HttpResponse.Final(
                statusCode = 200,
                contentType = if (lookup) "application/json" else "video/mp4",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private class RecordingPort : ChaquopyPort {
        val received = mutableListOf<DownloadRequest>()
        override val available: Boolean = true

        override suspend fun runDownload(request: DownloadRequest, downloadRoot: String): ChaquopyResult {
            received += request
            return ChaquopyResult.Finished("out.bin", 1L)
        }
    }

    private fun statusJson() = """
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
                  {"bitrate": 832000, "content_type": "video/mp4", "url": "https://video.example/first.mp4"}
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
                  {"bitrate": 256000, "content_type": "video/mp4", "url": "https://video.example/second.mp4"}
                ]
              }
            }
          ]
        }
    """.trimIndent()

    private suspend fun waitFor(engine: AndroidRoutingEngine, jobId: String): DownloadJob =
        withTimeout(30_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state.isTerminal } }
                .first { it.id == jobId }
        }

    @Test
    fun aMatchedStatusRoutesToKotlinWithoutAProbe() {
        val registry = productionExtractorRegistry(StatusFixtureTransfer(statusJson()), NoJsRuntime)
        assertIs<TwitterIE>(registry.suitableFor(statusUrl))

        val classifier = AndroidRouteClassifier(registry = registry)
        assertEquals(AndroidRoute.KOTLIN, classifier.route(statusUrl))
    }

    @Test
    fun theFixturePathPreviewsAndDownloadsTwoSelectedVideosWithoutPython() = runBlocking {
        val root: Path = Files.createTempDirectory("anydownlod-android-x-status")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transfer = StatusFixtureTransfer(statusJson())
        val registry = productionExtractorRegistry(transfer, NoJsRuntime)
        val port = RecordingPort()
        try {
            val preview = assertIs<MediaPreviewResult.Ready>(
                ExtractorMediaPreviewSource(registry).load(statusUrl),
            ).preview
            assertEquals(
                listOf("3333333333333333333", "4444444444444444444"),
                preview.videos.map { it.mediaId },
            )

            val http = HttpDownloadEngine(
                transfer = transfer,
                fileStore = JavaNetFileStore(root),
                settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                urlCheck = { UrlCheck.Allowed(it) },
                registry = registry,
            )
            val chaquopy = ChaquopyEngine(
                port = port,
                downloadRoot = { root.toString() },
                scope = scope,
                ioDispatcher = Dispatchers.Default,
            )
            val routing = AndroidRoutingEngine(
                http = http,
                chaquopy = chaquopy,
                classify = AndroidRouteClassifier(registry = registry)::route,
                scope = scope,
            )

            val job = routing.submit(
                DownloadRequest(
                    sourceUrl = statusUrl,
                    selectedMediaIds = listOf("3333333333333333333", "4444444444444444444"),
                ),
            )
            val finished = waitFor(routing, job.id)

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(statusUrl, finished.request.sourceUrl)
            assertEquals(2, finished.artifacts.size)
            val sizes = finished.artifacts.map { Files.size(root.resolve(it.relativePath)) }
            assertEquals(listOf(firstBytes.size.toLong(), secondBytes.size.toLong()), sizes)
            assertEquals(0, port.received.size, "a registry-matched status must never reach Chaquopy")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }
}
