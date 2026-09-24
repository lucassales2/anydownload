package com.anydownlod.ui.preview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.ui.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-063 click-through, headless: a YouTube link opens the preview from the
 * Kotlin extractor (no process, no download) with real metadata and
 * format-driven Edit choices.
 */
@OptIn(ExperimentalTestApi::class)
class PreviewFromExtractorUiTest {

    private val youtubeUrl = "https://www.youtube.com/watch?v=YE7VzlLtp-4"

    private class PlayerTransfer : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val body = if (request.method == "POST") PLAYER_JSON else "<html></html>"
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = if (request.method == "POST") "application/json" else "text/html",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }

        companion object {
            val PLAYER_JSON = """
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {
                    "videoId": "YE7VzlLtp-4",
                    "title": "Big Buck Bunny",
                    "author": "Fixture Channel",
                    "lengthSeconds": "596",
                    "viewCount": "123456",
                    "isLiveContent": false,
                    "thumbnail": {"thumbnails": [{"url": "https://i.example/large.jpg", "width": 1280, "height": 720}]}
                  },
                  "microformat": {"playerMicroformatRenderer": {"publishDate": "2008-05-29", "isFamilySafe": true}},
                  "streamingData": {
                    "formats": [
                      {"itag": 18, "url": "https://cdn.fixtures.example.net/clip.mp4",
                       "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 360}
                    ],
                    "adaptiveFormats": [
                      {"itag": 136, "signatureCipher": "s=REDACTED", "mimeType": "video/mp4; codecs=\"avc1\""}
                    ]
                  }
                }
            """.trimIndent()
        }
    }

    @Test
    fun aYoutubeLinkPreviewsFromKotlinAndEditReflectsTheFormats() = runComposeUiTest {
        val transfer = PlayerTransfer()
        val registry = ExtractorRegistry(listOf(YoutubeIE(ExtractorHttp(transfer))))
        val graph = InMemoryAppGraph(previews = ExtractorMediaPreviewSource(registry))

        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput(youtubeUrl)
        onNodeWithTag("add-download-button").performClick()

        onNodeWithText("Big Buck Bunny").assertExists()
        onNodeWithText("Fixture Channel").assertExists()
        assertEquals(0, graph.engine.jobs.value.size, "the preview must not start a download")

        // The Edit panel reflects the extracted formats: 1080p is not a
        // single-file format here and carries the toolkit reason, and the
        // dropped cipher format is reported.
        onNodeWithTag("preview-edit-toggle").performClick()
        onNodeWithTag("preview-edit-quality-res-360").assertExists()
        onNodeWithTag("preview-edit-quality-res-1080").assertIsNotEnabled()
        onNodeWithText("Needs the media toolkit (not built yet)", substring = true).assertExists()
        onNodeWithTag("preview-js-formats").assertExists()

        assertTrue(transfer.requests.none { it.url.contains("clip.mp4") }, "no media request during preview")
    }
}
