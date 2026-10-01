package com.anydownload.core.extract.pinterest

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Pinterest subset. Ids and media paths are synthesized
 * on `media.example`; the resource URLs carry a fake options payload. No
 * cookie, token, or signed URL appears.
 */
class PinterestIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val pinUrl = "https://www.pinterest.com/pin/664281013778109217/"
    private val embedPinUrl = "https://www.pinterest.ca/pin/441282463481903715/"
    private val boardUrl = "https://www.pinterest.ca/mashal0407/cool-diys/"

    private val pinRoute = FixtureRoute(
        urlPattern = "https://www.pinterest.com/resource/PinResource/get/*",
        contentType = "application/json",
        body = """
            {"resource_response": {"data": {
              "id": "664281013778109217",
              "title": "Fixture Pin",
              "seo_description": "Fixture description",
              "created_at": "2020-06-25T18:27:02+00:00",
              "domain": "uploaded by user",
              "images": {"orig": {"url": "https://media.example/thumb.jpg",
                                  "width": 1080, "height": 1080}},
              "closeup_attribution": {"full_name": "Fixture Uploader", "id": "1084664028912989237"},
              "videos": {"video_list": {
                "hls-720p": {"url": "https://media.example/master.m3u8",
                             "duration": 57700, "width": 1280, "height": 720},
                "mp4-480p": {"url": "https://media.example/video.mp4",
                             "duration": 57700, "width": 854, "height": 480}}}}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val pin = PinterestIE(http(transfer()))
        assertTrue(pin.suitable(pinUrl))
        assertTrue(pin.suitable("https://co.pinterest.com/pin/824721750502199491/"))
        assertTrue(pin.suitable("https://pinterest.com/pin/dive-into-serenity--2885187256207927"))
        assertFalse(pin.suitable(boardUrl))
        assertFalse(pin.suitable("https://www.example.com/pin/664281013778109217/"))

        val board = PinterestCollectionIE(http(transfer()))
        assertTrue(board.suitable(boardUrl))
        assertTrue(board.suitable("https://www.pinterest.ca/fudohub/videos/"))
        assertFalse(board.suitable(pinUrl), "the board class must yield pin URLs")
    }

    // ------------------------------------------------------------------- pin

    @Test
    fun pinYieldsTheManifestAndDirectRows() = runTest {
        val info = PinterestIE(http(transfer(pinRoute))).extract(pinUrl)
        assertEquals("664281013778109217", info.id)
        assertEquals("Fixture Pin", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(57.7, info.duration)
        assertEquals("20200625", info.uploadDate)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("hls-720p", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4-480p", info.formats[1].formatId)
        assertEquals("https://media.example/video.mp4", info.formats[1].url)
        assertEquals(854L, info.formats[1].width)
    }

    @Test
    fun pinIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = pinUrl,
            infoDict = mapOf(
                "id" to Expect.Value("664281013778109217"),
                "title" to Expect.Value("Fixture Pin"),
                "duration" to Expect.Value(57.7),
                "upload_date" to Expect.Value("20200625"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(pinRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PinterestIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun embedPinYieldsOneChildEntry() = runTest {
        val embedRoute = FixtureRoute(
            urlPattern = "https://www.pinterest.com/resource/PinResource/get/*",
            contentType = "application/json",
            body = """
                {"resource_response": {"data": {
                  "id": "111691128",
                  "title": "Fixture Embed Pin",
                  "domain": "vimeo.com",
                  "embed": {"src": "https://player.vimeo.com/video/111691128"}}}}
            """.trimIndent(),
        )
        val info = PinterestIE(http(transfer(embedRoute))).extract(embedPinUrl)
        assertEquals("111691128", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://player.vimeo.com/video/111691128", info.entries[0].url)
    }

    // ----------------------------------------------------------------- board

    @Test
    fun boardPaginatesPinEntries() = runTest {
        val boardRoute = FixtureRoute(
            urlPattern = "https://www.pinterest.com/resource/BoardResource/get/*",
            contentType = "application/json",
            body = """
                {"resource_response": {"data": {"id": "585890301462791043", "name": "cool diys"}}}
            """.trimIndent(),
        )
        val secondPageRoute = FixtureRoute(
            urlPattern = "https://www.pinterest.com/resource/BoardFeedResource/get/*%22bookmarks%22*",
            contentType = "application/json",
            body = """
                {"resource_response": {"data": [{"type": "pin", "id": "333"}], "bookmark": null}}
            """.trimIndent(),
        )
        val firstPageRoute = FixtureRoute(
            urlPattern = "https://www.pinterest.com/resource/BoardFeedResource/get/*",
            contentType = "application/json",
            body = """
                {"resource_response": {"data": [
                  {"type": "pin", "id": "111"},
                  {"type": "pin", "id": "222"},
                  {"type": "story", "id": "999"}], "bookmark": "bm1"}}
            """.trimIndent(),
        )
        val info = PinterestCollectionIE(
            http(transfer(boardRoute, secondPageRoute, firstPageRoute)),
        ).extract(boardUrl)
        assertEquals("585890301462791043", info.id)
        assertEquals("cool diys", info.title)
        assertEquals(3, info.entries.size)
        assertEquals("https://www.pinterest.com/pin/111/", info.entries[0].url)
        assertEquals("https://www.pinterest.com/pin/222/", info.entries[1].url)
        assertEquals("https://www.pinterest.com/pin/333/", info.entries[2].url)
    }
}
