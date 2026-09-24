package com.anydownlod.desktop.engine

import com.anydownlod.core.MediaPreviewResult
import com.anydownlod.core.PreviewFailure
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * T-063 desktop routing: a YouTube link previews from the Kotlin extractor and
 * never starts a process; an unmatched URL falls through to the installed CLI.
 */
class DesktopPreviewSourceTest {

    private val videoId = "YE7VzlLtp-4"
    private val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"

    private class PreviewTransfer : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val body = if (request.method == "POST") PLAYER_JSON else WATCH_HTML
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = if (request.method == "POST") "application/json" else "text/html",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }

        companion object {
            const val WATCH_HTML =
                "<html><head><meta itemprop=\"uploadDate\" content=\"2008-05-29T04:24:26-07:00\"></head></html>"

            val PLAYER_JSON = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {
                    "videoId": "YE7VzlLtp-4",
                    "title": "Big Buck Bunny",
                    "author": "Fixture Channel",
                    "channelId": "UCfixturechannel000000000",
                    "lengthSeconds": "596",
                    "viewCount": "123456",
                    "isLiveContent": false,
                    "thumbnail": {"thumbnails": [
                      {"url": "https://i.example/large.jpg", "width": 1280, "height": 720}
                    ]}
                  },
                  "microformat": {"playerMicroformatRenderer": {"publishDate": "2008-05-29", "isFamilySafe": true}},
                  "streamingData": {
                    "formats": [
                      {"itag": 18, "url": "https://cdn.fixtures.example.net/clip.mp4",
                       "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 360}
                    ],
                    "adaptiveFormats": []
                  }
                }
            """.trimIndent()
        }
    }

    private class RecordingRunner(private val process: CliProcess) : CliProcessRunner {
        val commands = mutableListOf<List<String>>()

        override fun start(command: List<String>, workingDirectory: Path): CliProcess {
            commands += command
            return process
        }
    }

    private val cliJson =
        """{"id":"cli","title":"CLI Title","webpage_url":"https://example.org/article","extractor_key":"Generic"}"""

    private fun source(transfer: PreviewTransfer, runner: RecordingRunner) = DesktopPreviewSource.create(
        runner = runner,
        resolveExecutable = { "yt-dlp" },
        workingDirectory = { Path.of("/tmp") },
        transfer = transfer,
    )

    @Test
    fun aYoutubeLinkPreviewsFromKotlinWithoutStartingAProcess() = runBlocking {
        val transfer = PreviewTransfer()
        val runner = RecordingRunner(FakeCliProcess(listOf(cliJson)))
        val result = assertIs<MediaPreviewResult.Ready>(source(transfer, runner).load(youtubeUrl))

        assertEquals("Big Buck Bunny", result.preview.title)
        assertEquals("Fixture Channel", result.preview.channel)
        assertEquals(596L, result.preview.durationSeconds)
        assertEquals("2008-05-29", result.preview.uploadDate)
        assertEquals("Youtube", result.preview.extractor)
        assertEquals(listOf(360), result.preview.availableFormats?.videoHeights)

        assertTrue(runner.commands.isEmpty(), "a matched YouTube URL must never start yt-dlp")
        assertTrue(transfer.requests.none { it.url.contains("clip.mp4") }, "preview must not fetch media")
    }

    @Test
    fun anUnmatchedUrlFallsThroughToTheCli() = runBlocking {
        val transfer = PreviewTransfer()
        val runner = RecordingRunner(FakeCliProcess(listOf(cliJson)))
        val result = assertIs<MediaPreviewResult.Ready>(source(transfer, runner).load("https://example.org/article"))

        assertEquals("CLI Title", result.preview.title)
        val command = runner.commands.single()
        assertEquals("yt-dlp", command.first())
        assertTrue("https://example.org/article" in command, "the URL is one argument, never a shell string")
        // The Kotlin side saw no match and fetched nothing.
        assertTrue(transfer.requests.isEmpty())
    }

    @Test
    fun aMatchedButFailingSourceDoesNotReachTheCli() = runBlocking {
        val transfer = object : HttpTransfer {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                val bytes = """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in"}}""".encodeToByteArray()
                return HttpResponse.Final(200, "application/json", bytes.size.toLong(), ByteArrayHttpBody(bytes))
            }
        }
        val runner = RecordingRunner(FakeCliProcess(listOf(cliJson)))
        val source = DesktopPreviewSource.create(
            runner = runner,
            resolveExecutable = { "yt-dlp" },
            workingDirectory = { Path.of("/tmp") },
            transfer = transfer,
        )
        val result = assertIs<MediaPreviewResult.Failed>(source.load(youtubeUrl))
        assertEquals(PreviewFailure.Failed, result.failure)
        assertTrue(runner.commands.isEmpty(), "a matched URL must never reach the fallback")
    }
}
