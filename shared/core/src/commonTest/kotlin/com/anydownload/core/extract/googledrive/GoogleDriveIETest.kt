package com.anydownload.core.extract.googledrive

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Google Drive subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no user credential
 * appears (the public browser app key is the same constant the public API
 * always receives).
 */
class GoogleDriveIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val fileId = "0ByeS4oOUV-49Zzh4R1J6R09zazQ"

    private fun playbackJson() = """
        {"mediaMetadata": {"title": "Fixture Drive.mp4", "duration": "45.069s"},
         "mediaStreamingData": {"formatStreamingData": {
           "adaptiveTranscodes": [{"itag": 137, "url": "https://media.example/video/1080.mp4",
             "transcodeMetadata": {"mimeType": "video/mp4", "width": 1920, "height": 1080,
                                   "videoFps": 30, "contentLength": 1000,
                                   "videoCodecString": "avc1", "audioCodecString": null}}],
           "progressiveTranscodes": [{"itag": 18, "url": "https://media.example/video/360.mp4",
             "transcodeMetadata": {"mimeType": "video/mp4", "width": 640, "height": 360}}]}},
         "thumbnails": [{"url": "https://media.example/thumb.jpg", "width": 100, "height": 50}]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            GoogleDriveIE(http(transfer())) to "https://drive.google.com/file/d/$fileId/edit?pli=1",
            GoogleDriveIE(http(transfer())) to "https://drive.google.com/uc?id=$fileId",
            GoogleDriveIE(http(transfer())) to "https://drive.usercontent.google.com/download?id=$fileId",
            GoogleDriveIE(http(transfer())) to "https://video.google.com/get_player?docid=$fileId",
            GoogleDriveFolderIE(http(transfer())) to "https://drive.google.com/drive/folders/1dQ4sx0-__Nvg65rxTSgQrl7VyW_FZ9QI",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(GoogleDriveIE(http(transfer())).suitable("https://drive.google.com/drive/folders/1dQ4sx0-__Nvg65rxTSgQrl7VyW_FZ9QI"))
    }

    // ------------------------------------------------------------------- file

    @Test
    fun playbackApiYieldsFormatsAndMetadata() = runTest {
        val url = "https://drive.google.com/file/d/$fileId/edit?pli=1"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://content-workspacevideo-pa.googleapis.com/v1/drive/media/$fileId/playback?key=*",
                contentType = "application/json",
                body = playbackJson(),
            ),
        )
        val info = GoogleDriveIE(http(transfer)).extract(url)
        assertEquals(fileId, info.id)
        assertEquals("Fixture Drive.mp4", info.title)
        assertEquals(45.069, info.duration)
        assertEquals(3, info.formats.size)
        assertEquals("137", info.formats[0].formatId)
        assertEquals(1080L, info.formats[0].height)
        assertEquals("none", info.formats[0].acodec)
        assertEquals("source", info.formats[2].formatId)
        assertEquals("mp4", info.formats[2].ext)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun folderFailsTyped() = runTest {
        assertFailsWith<ExtractionError.Unavailable> {
            GoogleDriveFolderIE(http(transfer())).extract(
                "https://drive.google.com/drive/folders/1dQ4sx0-__Nvg65rxTSgQrl7VyW_FZ9QI",
            )
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun fileIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://drive.google.com/file/d/$fileId/edit?pli=1"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(fileId),
                "title" to Expect.Value("Fixture Drive.mp4"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://content-workspacevideo-pa.googleapis.com/v1/drive/media/$fileId/playback?key=*",
                    contentType = "application/json",
                    body = playbackJson(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> GoogleDriveIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
