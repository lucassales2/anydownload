@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anydownload.ui

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
import com.anydownload.core.platform.IosFileStore
import kotlin.random.Random
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
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

/**
 * T-098 simulator fixture: the same [productionExtractorRegistry] registry the
 * app graph builds previews a two-video status and downloads both selected videos
 * through the shared engine and the real sandbox [IosFileStore]. The
 * syndication JSON and media bytes are synthesized; no live X/Twitter call.
 * Work is foreground-only, as on the iOS host.
 */
class IosXStatusTest {

    private val statusId = "9999999999999999999"
    private val statusUrl = "https://x.com/fixture/status/$statusId"
    private val firstBytes = ByteArray(1024) { 3 }
    private val secondBytes = ByteArray(2048) { 4 }

    private class FixtureTransfer(private val statusJson: String) : HttpTransfer {
        val requests = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request.url
            val lookup = request.url.contains("cdn.syndication.twimg.com")
            val bytes = when {
                lookup -> statusJson.encodeToByteArray()
                request.url.endsWith("first.mp4") -> ByteArray(1024) { 3 }
                request.url.endsWith("second.mp4") -> ByteArray(2048) { 4 }
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

    private suspend fun waitFor(engine: HttpDownloadEngine, jobId: String): DownloadJob =
        withTimeout(30_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state.isTerminal } }
                .first { it.id == jobId }
        }

    @Test
    fun theFixturePathPreviewsAndDownloadsTwoSelectedVideos() = runBlocking {
        val root = NSTemporaryDirectory().trimEnd('/') +
            "/anydownlod-ios-x-${Random.nextLong().toULong().toString(16)}"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transfer = FixtureTransfer(statusJson())
        val registry = productionExtractorRegistry(transfer, NoJsRuntime)
        assertIs<TwitterIE>(registry.suitableFor(statusUrl))
        try {
            val preview = assertIs<MediaPreviewResult.Ready>(
                ExtractorMediaPreviewSource(registry).load(statusUrl),
            ).preview
            assertEquals(
                listOf("3333333333333333333", "4444444444444444444"),
                preview.videos.map { it.mediaId },
            )

            val fileStore = IosFileStore(root)
            val engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = fileStore,
                settings = InMemorySettingsRepository(AppSettings(downloadRoot = root)),
                scope = scope,
                registry = registry,
                urlCheck = { UrlCheck.Allowed(it) },
            )
            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = statusUrl,
                    selectedMediaIds = listOf("3333333333333333333", "4444444444444444444"),
                ),
            )
            val finished = waitFor(engine, job.id)

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(statusUrl, finished.request.sourceUrl)
            assertEquals(2, finished.artifacts.size)
            assertEquals(firstBytes.size.toLong(), fileStore.size(finished.artifacts[0].relativePath))
            assertEquals(secondBytes.size.toLong(), fileStore.size(finished.artifacts[1].relativePath))
            // Re-extraction: preview and download each performed one lookup.
            assertEquals(2, transfer.requests.count { it.contains("cdn.syndication.twimg.com") })
        } finally {
            scope.cancel()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }
}
