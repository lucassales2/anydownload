package com.anydownload.core.extract.onet

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
 * Fixture cases for the Onet subset. Ids and media paths are synthesized on
 * `media.example`; the mvp ids are fake values. No cookie, token, or signed
 * URL appears.
 */
class OnetIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val clipUrl = "http://onet.tv/k/openerfestival/open-er-festival-2016-najdziwniejsze-wymagania-gwiazd/qbpyqc"
    private val channelUrl = "http://onet.tv/k/openerfestival"
    private val onetPlUrl = "http://film.onet.pl/zwiastuny/ghost-in-the-shell-drugi-zwiastun-pl/5q6yl3"

    private val apiRoute = FixtureRoute(
        urlPattern = "http://qi.ckm.onetapi.pl/*",
        contentType = "application/json",
        body = """
            {"result": {"0": {
              "formats": {
                "video": {
                  "hls": [{"url": "https://media.example/master.m3u8"}],
                  "mp4": [{"url": "https://media.example/video.mp4",
                           "vertical_resolution": 720, "horizontal_resolution": 1280,
                           "video_bitrate": 2000}]},
                "audio": {"mp3": [{"url": "https://media.example/audio.mp3",
                                   "audio_bitrate": 128}]}},
              "meta": {"title": "Fixture Title", "description": "Fixture description",
                       "length": 300, "addDate": "2016-07-05 14:00:00"}}}}
        """.trimIndent(),
    )

    private val clipPage = FixtureRoute(
        urlPattern = "http://onet.tv/k/openerfestival/*",
        contentType = "text/html",
        body = """
            <html><body><div id="mvp:381027.1509591944"></div></body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val mvp = OnetMVPIE(http(transfer()))
        assertTrue(mvp.suitable("onetmvp:381027.1509591944"))
        assertFalse(mvp.suitable(clipUrl))

        val clip = OnetIE(http(transfer()))
        assertTrue(clip.suitable(clipUrl))
        assertTrue(clip.suitable("https://onet100.vod.pl/k/openerfestival/open-er-festival-2016/qbpyqc"))
        assertFalse(clip.suitable(channelUrl))

        val channel = OnetChannelIE(http(transfer()))
        assertTrue(channel.suitable(channelUrl))
        assertFalse(channel.suitable(clipUrl))

        val onetPl = OnetPlIE(http(transfer()))
        assertTrue(onetPl.suitable(onetPlUrl))
        assertTrue(onetPl.suitable("http://businessinsider.com.pl/wideo/scenariusz-na-koniec-swiata/dwnqptk"))
        assertTrue(onetPl.suitable("http://plejada.pl/weronika-rosati-o-swoim-domniemanym-slubie/n2bq89"))
        assertFalse(onetPl.suitable("https://www.example.com/wideo/x/y"))
    }

    // ---------------------------------------------------------------- clip

    @Test
    fun clipYieldsTheFormatRowsAndMetadata() = runTest {
        val info = OnetIE(http(transfer(clipPage, apiRoute))).extract(clipUrl)
        assertEquals("qbpyqc", info.id)
        assertEquals("Fixture Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(300.0, info.duration)
        assertEquals("20160705", info.uploadDate)
        assertEquals(3, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4", info.formats[1].formatId)
        assertEquals(720L, info.formats[1].height)
        assertEquals(1280L, info.formats[1].width)
        assertEquals(2000.0, info.formats[1].vbr)
        assertEquals("mp3", info.formats[2].formatId)
        assertEquals("none", info.formats[2].vcodec)
        assertEquals(128.0, info.formats[2].abr)
    }

    @Test
    fun clipIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = clipUrl,
            infoDict = mapOf(
                "id" to Expect.Value("qbpyqc"),
                "title" to Expect.Value("Fixture Title"),
                "duration" to Expect.Value(300.0),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(clipPage, apiRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> OnetIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun mvpIdYieldsTheSameInfo() = runTest {
        val info = OnetMVPIE(http(transfer(apiRoute))).extract("onetmvp:381027.1509591944")
        assertEquals("381027.1509591944", info.id)
        assertEquals("Fixture Title", info.title)
        assertEquals(3, info.formats.size)
    }

    // ---------------------------------------------------------------- onet.pl

    @Test
    fun onetPlDispatchesToTheMvpId() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://film.onet.pl/zwiastuny/*",
            contentType = "text/html",
            body = """<html><body><div data-mvp="381027.1509591944"></div></body></html>""",
        )
        val info = OnetPlIE(http(transfer(page, apiRoute))).extract(onetPlUrl)
        assertEquals("5q6yl3", info.id)
        assertEquals("Fixture Title", info.title)
        assertEquals(onetPlUrl, info.webpageUrl)
    }

    @Test
    fun onetPlFallsBackToPulsembed() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://film.onet.pl/zwiastuny/*",
            contentType = "text/html",
            body = """
                <html><body><div data-src="//pulsembed.eu/embed/fixture"></div></body></html>
            """.trimIndent(),
        )
        val pulsembedPage = FixtureRoute(
            urlPattern = "https://pulsembed.eu/embed/fixture",
            contentType = "text/html",
            body = """<html><body><div data-params-mvp="501235.965429946"></div></body></html>""",
        )
        val info = OnetPlIE(http(transfer(page, pulsembedPage, apiRoute))).extract(onetPlUrl)
        assertEquals("5q6yl3", info.id)
        assertEquals("Fixture Title", info.title)
    }

    // ---------------------------------------------------------------- channel

    @Test
    fun channelYieldsTheVideoLinks() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://onet.tv/k/openerfestival",
            contentType = "text/html",
            body = """
                <html><body>
                <div class="o_channelName">Open'er Festival</div>
                <div class="o_channelDesc">Fixture channel description</div>
                <a href="http://onet.tv/k/openerfestival/open-er-festival-2016/qbpyqc">One</a>
                <a href="http://onet.tv/k/openerfestival/open-er-festival-2016/qbpyqd">Two</a>
                </body></html>
            """.trimIndent(),
        )
        val info = OnetChannelIE(http(transfer(page))).extract(channelUrl)
        assertEquals("openerfestival", info.id)
        assertEquals("Open'er Festival", info.title)
        assertEquals("Fixture channel description", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("http://onet.tv/k/openerfestival/open-er-festival-2016/qbpyqc", info.entries[0].url)
        assertEquals("http://onet.tv/k/openerfestival/open-er-festival-2016/qbpyqd", info.entries[1].url)
    }
}
