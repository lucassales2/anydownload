package com.anydownload.core.extract.rtlnl

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
 * Fixture cases for the RTL subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`/`manifest.example`, and no
 * cookie, token, or signed URL appears.
 */
class RtlNlIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val uuid = "c603c9c2-601d-4b5e-8175-64f1e942dc7d"
    private val rtlNlUrl = "https://www.rtl.nl/video/$uuid/"
    private val teleVodUrl = "https://www.rtl.lu/video/3295215"
    private val articleUrl = "https://www.rtl.lu/sport/news/a/1934360.html"
    private val liveUrl = "https://www.rtl.lu/tele/live"
    private val radioUrl = "https://www.rtl.lu/radio/5-vir-12/s/4033058.html"

    private fun videoPage(title: String = "Fixture RTL Title") = """
        <html><head>
        <meta property="og:title" content="$title">
        <meta property="og:description" content="Fixture RTL description">
        </head><body>
        <rtl-player hls="https://media.example/master.m3u8" poster="https://static.example/poster.jpg"></rtl-player>
        </body></html>
    """.trimIndent()

    private fun audioPage() = """
        <html><head>
        <meta property="og:title" content="Fixture Audio Title">
        <meta property="og:description" content="Fixture audio description">
        </head><body>
        <rtl-audioplayer src="https://media.example/audio.mp3"></rtl-audioplayer>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val rtlNl = RtlNlIE(http(transfer()))
        val rtlNlCases = listOf(
            "https://www.rtlxl.nl/programma/rtl-nieuws/0bd1384d-d970-3086-98bb-5c104e10c26f",
            "http://www.rtlxl.nl/#!/rtl-nieuws-132237/82b1aad1-4a14-3d7b-b554-b0aed1b2c416",
            "http://www.rtl.nl/system/videoplayer/derden/rtlnieuws/video_embed.html#uuid=84ae5571-ac25-4225-ae0c-ef8d9efb2aed/autoplay=false",
            "https://www.rtl.nl/video/$uuid/",
            "https://static.rtl.nl/embed/?uuid=1a2970fc-5c0b-43ff-9fdc-927e39e6d1bc&autoplay=false",
            "https://embed.rtl.nl/#uuid=84ae5571-ac25-4225-ae0c-ef8d9efb2aed/autoplay=false",
        )
        for (url in rtlNlCases) {
            assertTrue(rtlNl.suitable(url), "RtlNl must match: $url")
        }
        assertFalse(rtlNl.suitable("https://www.example.com/video/$uuid/"))

        val teleVod = RTLLuTeleVODIE(http(transfer()))
        assertTrue(teleVod.suitable("https://www.rtl.lu/tele/de-journal-vun-der-tele/v/3266757.html"))
        assertTrue(teleVod.suitable(teleVodUrl))

        val article = RTLLuArticleIE(http(transfer()))
        assertTrue(article.suitable(articleUrl))
        assertTrue(article.suitable("https://5minutes.rtl.lu/espace-frontaliers/frontaliers-en-questions/a/1853173.html"))
        assertTrue(article.suitable("https://today.rtl.lu/entertainment/news/a/1936203.html"))

        val live = RTLLuLiveIE(http(transfer()))
        assertTrue(live.suitable(liveUrl))
        assertTrue(live.suitable("https://www.rtl.lu/tele/live-2"))
        assertTrue(live.suitable("https://www.rtl.lu/radio/lauschteren"))

        val radio = RTLLuRadioIE(http(transfer()))
        assertTrue(radio.suitable(radioUrl))
        assertFalse(radio.suitable(liveUrl))
    }

    // ---------------------------------------------------------------- rtl.nl

    @Test
    fun rtlNlAdaptiveJsonYieldsTheHlsRow() = runTest {
        val apiRoute = FixtureRoute(
            urlPattern = "http://www.rtl.nl/system/s4m/vfd/version=2/uuid=$uuid*",
            contentType = "application/json",
            body = """
                {
                  "material": [{
                    "title": "Fixture Subtitle",
                    "synopsis": "Fixture description",
                    "videopath": "/path/master.m3u8",
                    "original_date": 1593293400,
                    "duration": "661.08"
                  }],
                  "abstracts": [{"name": "Fixture Title"}],
                  "meta": {
                    "videohost": "https://manifest.example",
                    "poster_base_url": "https://screenshots.example/sz=640x360/uuid=",
                    "\"thumb_base_url\"": "https://screenshots.example/sz=320x180/uuid="
                  }
                }
            """.trimIndent(),
        )
        val info = RtlNlIE(http(transfer(apiRoute))).extract(rtlNlUrl)
        assertEquals(uuid, info.id)
        assertEquals("Fixture Title - Fixture Subtitle", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(661.08, info.duration)
        assertEquals("20200627", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("https://manifest.example/path/master.m3u8", info.formats[0].url)
        assertEquals(2, info.thumbnails.size)
        assertEquals(640L, info.thumbnails[0].width)
        assertEquals(360L, info.thumbnails[0].height)
        assertEquals("https://screenshots.example/sz=640x360/uuid=$uuid", info.thumbnails[0].url)
        assertEquals(320L, info.thumbnails[1].width)
    }

    // ---------------------------------------------------------------- rtl.lu

    @Test
    fun teleVodPageYieldsTheHlsRow() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = teleVodUrl, contentType = "text/html", body = videoPage()))
        val info = RTLLuTeleVODIE(http(transfer)).extract(teleVodUrl)
        assertEquals("3295215", info.id)
        assertEquals("Fixture RTL Title", info.title)
        assertEquals("Fixture RTL description", info.description)
        assertEquals("https://static.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(false, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun articleAudioPageYieldsTheMp3Row() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = articleUrl, contentType = "text/html", body = audioPage()))
        val info = RTLLuArticleIE(http(transfer)).extract(articleUrl)
        assertEquals("1934360", info.id)
        assertEquals("Fixture Audio Title", info.title)
        assertEquals(1, info.formats.size)
        assertEquals("mp3", info.formats[0].ext)
        assertEquals("none", info.formats[0].vcodec)
        assertEquals("https://media.example/audio.mp3", info.formats[0].url)
    }

    @Test
    fun livePageMarksTheStreamLive() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = liveUrl, contentType = "text/html", body = videoPage("RTL - Télé LIVE")))
        val info = RTLLuLiveIE(http(transfer)).extract(liveUrl)
        assertEquals("live", info.id)
        assertEquals(true, info.isLive)
        assertEquals(1, info.formats.size)
    }

    @Test
    fun radioPageYieldsTheAudioRow() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = radioUrl, contentType = "text/html", body = audioPage()))
        val info = RTLLuRadioIE(http(transfer)).extract(radioUrl)
        assertEquals("4033058", info.id)
        assertEquals("Fixture Audio Title", info.title)
        assertEquals("mp3", info.formats.single().ext)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun rtlNlIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = rtlNlUrl,
            infoDict = mapOf(
                "id" to Expect.Value(uuid),
                "title" to Expect.Value("Fixture Title - Fixture Subtitle"),
                "duration" to Expect.Value(661.08),
                "upload_date" to Expect.Value("20200627"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "http://www.rtl.nl/system/s4m/vfd/version=2/uuid=$uuid*",
                    contentType = "application/json",
                    body = """
                        {"material": [{"title": "Fixture Subtitle", "synopsis": "Fixture description",
                          "videopath": "/path/master.m3u8", "original_date": 1593293400, "duration": "661.08"}],
                         "abstracts": [{"name": "Fixture Title"}],
                         "meta": {"videohost": "https://manifest.example"}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RtlNlIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun teleVodIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = teleVodUrl,
            infoDict = mapOf(
                "id" to Expect.Value("3295215"),
                "title" to Expect.Value("Fixture RTL Title"),
                "is_live" to Expect.Value(false),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = teleVodUrl, contentType = "text/html", body = videoPage())),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RTLLuTeleVODIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
