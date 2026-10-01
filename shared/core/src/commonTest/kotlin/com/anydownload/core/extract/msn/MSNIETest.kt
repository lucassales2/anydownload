package com.anydownload.core.extract.msn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
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
 * Fixture cases for the MSN subset. Ids and media paths are synthesized on
 * `media.example`; the page ids are fake values. No cookie, token, or signed
 * URL appears.
 */
class MSNIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl =
        "https://www.msn.com/en-gb/video/news/president-macron-interrupts-trump/vi-AA1zMcD7"

    private val videoRoute = FixtureRoute(
        urlPattern = "https://assets.msn.com/content/view/v2/Detail/en-gb/AA1zMcD7",
        contentType = "application/json",
        body = """
            {"type": "video", "title": "Fixture Video", "abstract": "Fixture description",
             "createdDateTime": "2025-02-25T10:00:00Z",
             "thumbnail": {"image": {"url": "https://media.example/thumb.img"}},
             "provider": {"name": "Fixture Provider", "id": "BB1hz5Rj"},
             "videoMetadata": {"playTime": 59,
               "externalVideoFiles": [
                 {"url": "https://media.example/master.m3u8"},
                 {"url": "https://media.example/video.mp4", "format": "mp4-720",
                  "fileSize": 1234, "height": 720, "width": 1280}],
               "closedCaptions": [{"href": "https://media.example/captions.ttml", "locale": "en-gb"}]}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = MSNIE(http(transfer()))
        assertTrue(ie.suitable(videoUrl))
        assertTrue(ie.suitable(
            "https://www.msn.com/en-in/news/techandscience/watch-earth-sets/ar-AA1zKoAc?ocid=hpmsn",
        ))
        assertTrue(ie.suitable("https://preview.msn.com/de-de/nachrichten/other/trailer/vi-AA1B1d06"))
        assertFalse(ie.suitable("https://www.example.com/en-gb/video/news/x/vi-AA1zMcD7"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun videoYieldsTheFormatRowsAndMetadata() = runTest {
        val info = MSNIE(http(transfer(videoRoute))).extract(videoUrl)
        assertEquals("AA1zMcD7", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20250225", info.uploadDate)
        assertEquals(59.0, info.duration)
        assertEquals("Fixture Provider", info.uploader)
        assertEquals("https://media.example/thumb.img", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4-720", info.formats[1].formatId)
        assertEquals(1234L, info.formats[1].filesize)
        assertEquals(720L, info.formats[1].height)
        assertEquals(1, info.subtitles.size)
        assertEquals("en-gb", info.subtitles[0].language)
        assertEquals("ttml", info.subtitles[0].formats.single().ext)
    }

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("AA1zMcD7"),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(59.0),
                "upload_date" to Expect.Value("20250225"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(videoRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MSNIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun thirdPartyVideoYieldsOneChildEntry() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://assets.msn.com/content/view/v2/Detail/en-gb/AA1zMcD7",
            contentType = "application/json",
            body = """
                {"type": "video", "title": "Fixture Video",
                 "sourceHref": "https://www.dailymotion.com/video/x9g6oli",
                 "thirdPartyVideoPlayer": {"enabled": true}}
            """.trimIndent(),
        )
        val info = MSNIE(http(transfer(route))).extract(videoUrl)
        assertEquals("AA1zMcD7", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.dailymotion.com/video/x9g6oli", info.entries[0].url)
    }

    // ------------------------------------------------------------ webcontent

    @Test
    fun webcontentYieldsOneChildEntry() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://assets.msn.com/content/view/v2/Detail/en-gb/AA1ybFaJ",
            contentType = "application/json",
            body = """
                {"type": "webcontent", "title": "Fixture Web",
                 "sourceHref": "https://www.youtube.com/watch?v=kQSChWu95nE"}
            """.trimIndent(),
        )
        val info = MSNIE(http(transfer(route))).extract(
            "https://www.msn.com/en-gb/video/webcontent/web-content/vi-AA1ybFaJ",
        )
        assertEquals("AA1ybFaJ", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.youtube.com/watch?v=kQSChWu95nE", info.entries[0].url)
    }

    @Test
    fun webcontentWithoutSourceFailsTyped() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://assets.msn.com/content/view/v2/Detail/en-gb/AA1ybFaJ",
            contentType = "application/json",
            body = """{"type": "webcontent", "title": "Fixture Web"}""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            MSNIE(http(transfer(route))).extract(
                "https://www.msn.com/en-gb/video/webcontent/web-content/vi-AA1ybFaJ",
            )
        }
        assertTrue(error.message!!.contains("Could not find source URL"), error.message)
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articleYieldsTheSocialEmbedEntries() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://assets.msn.com/content/view/v2/Detail/en-in/AA1zKoAc",
            contentType = "application/json",
            body = """
                {"type": "article", "title": "Fixture Article", "abstract": "Fixture description",
                 "socialEmbeds": [{"postUrl": "https://www.youtube.com/watch?v=abc123"},
                                  {"postUrl": "https://twitter.com/x/status/1"}]}
            """.trimIndent(),
        )
        val info = MSNIE(http(transfer(route))).extract(
            "https://www.msn.com/en-in/news/techandscience/watch-earth-sets/ar-AA1zKoAc",
        )
        assertEquals("AA1zKoAc", info.id)
        assertEquals("Fixture Article", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.youtube.com/watch?v=abc123", info.entries[0].url)
    }

    @Test
    fun unsupportedPageTypeFailsTyped() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://assets.msn.com/content/view/v2/Detail/en-gb/AA1zMcD7",
            contentType = "application/json",
            body = """{"type": "gallery", "title": "Fixture"}""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            MSNIE(http(transfer(route))).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("Unsupported page type"), error.message)
    }
}
