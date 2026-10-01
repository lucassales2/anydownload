package com.anydownload.core.extract.youtube

import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobState
import com.anydownload.core.engine.HttpDownloadEngine
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
import com.anydownload.core.extract.harness.ClasspathFixtureStore
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.platform.JavaNetFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-108 engine gate: the T-107 expander over a real [YoutubeTabIE] fixture
 * result creates two child jobs, and each child downloads through the existing
 * [YoutubeIE] single-video path (watch page, player request, media GET).
 */
class YoutubePlaylistEngineTest {

    private val playlistUrl = "https://www.youtube.com/playlist?list=PLfixture"
    private val mediaUrl = "https://cdn.fixtures.example.net/clip.mp4"
    private val mediaBytes = "fixture-media-bytes".encodeToByteArray()

    private class PlaylistTransfer(
        private val playlistJson: String,
        private val mediaUrl: String,
        private val mediaBytes: ByteArray,
        private val playerJsonFor: (String) -> String,
    ) : HttpTransfer {
        // Jobs run concurrently; the recorder must be thread-safe.
        val requests: MutableCollection<String> = java.util.concurrent.ConcurrentLinkedQueue()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request.url
            return when {
                request.url.contains("/youtubei/v1/browse") -> json(playlistJson)
                request.url.contains("/youtubei/v1/player") -> {
                    val videoId = Regex("\"videoId\"\\s*:\\s*\"([0-9A-Za-z_-]+)\"")
                        .find(request.body?.decodeToString().orEmpty())
                        ?.groupValues?.get(1)
                    json(playerJsonFor(videoId ?: "unknown"))
                }

                request.url.startsWith("https://www.youtube.com/watch") -> json("<html></html>", "text/html")
                request.url == mediaUrl -> HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/octet-stream",
                    totalBytes = mediaBytes.size.toLong(),
                    body = ByteArrayHttpBody(mediaBytes),
                )

                else -> HttpResponse.Final(statusCode = 404, contentType = "text/plain")
            }
        }

        private fun json(body: String, contentType: String = "application/json"): HttpResponse {
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = contentType,
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private fun playerJson(videoId: String): String {
        val title = when (videoId) {
            "AAAAAAAAAAA" -> "Fixture One"
            "BBBBBBBBBBB" -> "Fixture Two"
            else -> "Fixture $videoId"
        }
        return """
            {
              "playabilityStatus": {"status": "OK"},
              "videoDetails": {
                "videoId": "$videoId",
                "title": "$title",
                "author": "Fixture Channel",
                "lengthSeconds": "10",
                "isLiveContent": false,
                "isLive": false
              },
              "streamingData": {
                "formats": [
                  {
                    "itag": 18,
                    "url": "$mediaUrl",
                    "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                    "contentLength": "${mediaBytes.size}"
                  }
                ],
                "adaptiveFormats": []
              }
            }
        """.trimIndent()
    }

    @Test
    fun aFixturePlaylistExpandsAndEachChildDownloadsThroughYoutubeIE() = runBlocking {
        val downloadRoot = Files.createTempDirectory("anydownlod-youtube-playlist-")
        val playlistJson = ClasspathFixtureStore.read("fixtures/youtube-playlist/playlist_page.json")
            ?: error("missing playlist fixture")
        val transfer = PlaylistTransfer(playlistJson, mediaUrl, mediaBytes) { videoId -> playerJson(videoId) }
        val registry = ExtractorRegistry(
            listOf(
                YoutubeIE(ExtractorHttp(transfer)),
                YoutubeTabIE(ExtractorHttp(transfer)),
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var idCounter = 0
        val engine = HttpDownloadEngine(
            transfer = transfer,
            fileStore = JavaNetFileStore(downloadRoot),
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = downloadRoot.toString())),
            scope = scope,
            registry = registry,
            idGenerator = { "yt-${++idCounter}" },
            ioDispatcher = Dispatchers.Default,
        )

        val parent = engine.submit(
            DownloadRequest(
                sourceUrl = playlistUrl,
                options = DownloadOptions(playlistItemLimit = 2),
                idempotencyKey = "youtube-playlist-fixture",
            ),
        )

        val terminal = withTimeout(30_000) {
            engine.jobs.first { jobs -> jobs.count { it.state.isTerminal } == 3 }
        }
        val parentJob = terminal.first { it.id == parent.id }
        assertEquals(JobState.COMPLETED, parentJob.state, parentJob.error?.message)
        assertTrue(parentJob.artifacts.isEmpty(), "the playlist parent must not download media")

        val children = terminal.filter { it.parentBatchId == parent.id }
        assertEquals(2, children.size)
        assertEquals(
            setOf(
                "https://www.youtube.com/watch?v=AAAAAAAAAAA",
                "https://www.youtube.com/watch?v=BBBBBBBBBBB",
            ),
            children.map { it.request.sourceUrl }.toSet(),
        )
        assertTrue(children.all { it.state == JobState.COMPLETED })

        val files = Files.list(downloadRoot).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }
        assertEquals(
            setOf("Fixture One.mp4", "Fixture Two.mp4"),
            files.map { it.fileName.toString() }.toSet(),
        )
        assertEquals(mediaBytes.size.toLong(), Files.size(files.first()))

        // One browse for the playlist, then one watch page and one player
        // request per child, and one media GET per child.
        assertEquals(1, transfer.requests.count { it.contains("/youtubei/v1/browse") })
        assertEquals(2, transfer.requests.count { it.contains("/youtubei/v1/player") })
        assertEquals(2, transfer.requests.count { it.startsWith("https://www.youtube.com/watch") })
        assertEquals(2, transfer.requests.count { it == mediaUrl })

        scope.cancel()
    }
}
