package com.anydownload.core.extract.tvplay

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
 * Fixture cases for the TVPlay subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class TvplayIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mtgUrl = "http://www.tvplay.lv/parraides/vinas-melo-labak/418113?autostart=true"
    private val homeUrl = "https://play.tv3.lt/series/gauju-karai-karveliai,serial-2343791/serija-8,episode-2343828"
    private val liveUrl = "https://play.tv3.lt/lives/tv6-lt,live-2838694/optibet-a-lygos-rungtynes,programme-3422014"

    private val mtgVideo = """
        {"title": "Kādi ir īri? - Viņas melo labāk", "description": "Fixture description",
         "format_title": "Viņas melo labāk", "format_position": {"episode": 2, "season": 2},
         "_embedded": {"season": {"title": "2.sezona"}}, "duration": 25,
         "created_at": "2014-07-23T00:00:00+00:00", "views": {"total": 1000}, "age_limit": 0,
         "sami_path": "https://media.example/subs_en.xml"}
    """.trimIndent()

    private val mtgStreams = """
        {"streams": {
          "hls": "https://media.example/master.m3u8",
          "high": "https://media.example/high.mp4",
          "rtmp": "rtmp://example.com/app/play"
        }}
    """.trimIndent()

    private val homeProduct = """
        {"title": "Serija 8", "description": "Fixture description", "duration": 2710, "episode": 8,
         "season": {"number": 1, "serial": {"title": "Gaujų karai. Karveliai", "year": 2021}},
         "galary": {"images": {"artworks": [
           {"miniUrl": "https://media.example/mini.jpg", "mainUrl": "https://media.example/main.jpg"}
         ]}}}
    """.trimIndent()

    private val playlist = """{"sources": {"HLS": [{"src": "https://media.example/master.m3u8"}]}}"""

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val mtg = TVPlayIE(http(transfer()))
        val mtgCases = listOf(
            mtgUrl,
            "http://play.tv3.lt/programos/moterys-meluoja-geriau/409229?autostart=true",
            "http://tv3play.tv3.ee/sisu/kodu-keset-linna/238551?autostart=true",
            "https://tvplay.skaties.lv/vinas-melo-labak/418113/?autostart=true",
            "mtg:418113",
        )
        for (url in mtgCases) {
            assertTrue(mtg.suitable(url), "TVPlay must match: $url")
        }
        assertFalse(mtg.suitable("https://www.example.com/video/418113"))

        val home = TVPlayHomeIE(http(transfer()))
        assertTrue(home.suitable(homeUrl))
        assertTrue(home.suitable(liveUrl))
        assertTrue(home.suitable("https://tv3play.skaties.lv/clips/tv3-zinas-valsti,clip-3464509"))
        assertFalse(home.suitable(mtgUrl))
    }

    // -------------------------------------------------------------------- mtg

    @Test
    fun mtgApiYieldsTheHlsAndDirectRows() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "http://playapi.mtgx.tv/v3/videos/418113", contentType = "application/json", body = mtgVideo),
            FixtureRoute(urlPattern = "http://playapi.mtgx.tv/v3/videos/stream/418113", contentType = "application/json", body = mtgStreams),
        )
        val info = TVPlayIE(http(transfer)).extract(mtgUrl)
        assertEquals("418113", info.id)
        assertEquals("Kādi ir īri? - Viņas melo labāk", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Viņas melo labāk", info.channel)
        assertEquals(25.0, info.duration)
        assertEquals("20140723", info.uploadDate)
        assertEquals(1000L, info.viewCount)
        assertEquals(0, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("high", info.formats[1].formatId)
        assertEquals(2, info.formats[1].preference)
        assertEquals(1, info.subtitles.size)
        assertEquals("en", info.subtitles[0].language)
        assertEquals("https://media.example/subs_en.xml", info.subtitles[0].formats.single().url)
    }

    @Test
    fun geoBlockedVideoFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://playapi.mtgx.tv/v3/videos/418113",
                contentType = "application/json",
                body = """{"title": "Fixture", "is_geo_blocked": true}""",
            ),
            FixtureRoute(
                urlPattern = "http://playapi.mtgx.tv/v3/videos/stream/418113",
                contentType = "application/json",
                body = """{"streams": {}}""",
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            TVPlayIE(http(transfer)).extract(mtgUrl)
        }
    }

    // ------------------------------------------------------------------- home

    @Test
    fun homeVodYieldsTheHlsRowAndResolvedTitle() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://play.tv3.lt/api/products/vods/2343828*", contentType = "application/json", body = homeProduct),
            FixtureRoute(urlPattern = "https://play.tv3.lt/api/products/2343828/videos/playlist*", contentType = "application/json", body = playlist),
        )
        val info = TVPlayHomeIE(http(transfer)).extract(homeUrl)
        assertEquals("2343828", info.id)
        assertEquals("Gaujų karai. Karveliai (2021) | S01E08: Serija 8", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Gaujų karai. Karveliai", info.channel)
        assertEquals(2710.0, info.duration)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(2, info.thumbnails.size)
        assertEquals("https://media.example/main.jpg", info.thumbnails[1].url)
    }

    @Test
    fun homeLiveYieldsTheCatchupPlaylist() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://play.tv3.lt/api/products/lives/programmes/3422014*",
                contentType = "application/json",
                body = """{"programRecordingId": "5555", "title": "Live Fixture", "duration": 100}""",
            ),
            FixtureRoute(
                urlPattern = "https://play.tv3.lt/api/products/5555/videos/playlist?videoType=CATCHUP*",
                contentType = "application/json",
                body = playlist,
            ),
        )
        val info = TVPlayHomeIE(http(transfer)).extract(liveUrl)
        assertEquals("3422014", info.id)
        assertEquals("Live Fixture", info.title)
        assertEquals(1, info.formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun mtgIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = mtgUrl,
            infoDict = mapOf(
                "id" to Expect.Value("418113"),
                "title" to Expect.Value("Kādi ir īri? - Viņas melo labāk"),
                "upload_date" to Expect.Value("20140723"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "http://playapi.mtgx.tv/v3/videos/418113", contentType = "application/json", body = mtgVideo),
                FixtureRoute(urlPattern = "http://playapi.mtgx.tv/v3/videos/stream/418113", contentType = "application/json", body = mtgStreams),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TVPlayIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun homeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = homeUrl,
            infoDict = mapOf(
                "id" to Expect.Value("2343828"),
                "title" to Expect.Value("Gaujų karai. Karveliai (2021) | S01E08: Serija 8"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "https://play.tv3.lt/api/products/vods/2343828*", contentType = "application/json", body = homeProduct),
                FixtureRoute(urlPattern = "https://play.tv3.lt/api/products/2343828/videos/playlist*", contentType = "application/json", body = playlist),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TVPlayHomeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
