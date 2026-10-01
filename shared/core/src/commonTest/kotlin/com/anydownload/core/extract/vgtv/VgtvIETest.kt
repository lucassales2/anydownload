package com.anydownload.core.extract.vgtv

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
 * Fixture cases for the VGTV/BT subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class VgtvIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "http://www.vgtv.no/#!/video/84196/fixture"
    private val articleUrl = "http://www.bt.no/nyheter/lokalt/Kjemper-for-internatet-1788214.html"
    private val vestlendingenUrl = "http://www.bt.no/spesial/vestlendingen/#!/86588"

    private val assetRoute = FixtureRoute(
        urlPattern = "http://svp.vg.no/svp/api/v1/vgtv/assets/84196?appName=vgtv-website",
        contentType = "application/json",
        body = """
            {"status": "active", "streamType": "vod",
             "streamUrls": {
               "hls": "https://media.example/master.m3u8",
               "pseudostreaming": ["https://media.example/1280_720_2000.mp4"],
               "mp4": "https://media.example/video.mp4"
             },
             "title": "Fixture VGTV Title", "description": "Fixture description",
             "images": {"main": "https://media.example/main.jpg"},
             "published": 1404626400, "duration": 648000, "displays": 123}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val vgtv = VGTVIE(http(transfer()))
        val vgtvCases = listOf(
            videoUrl,
            "https://tv.vg.no/video/241779/politiets-ekstremkjoering",
            "http://www.bt.no/tv/#!/video/100250/fixture",
            "http://ap.vgtv.no/webtv#!/video/111084/fixture",
            "https://tv.aftonbladet.se/video/36015/fixture",
            "abtv:140026",
        )
        for (url in vgtvCases) {
            assertTrue(vgtv.suitable(url), "VGTV must match: $url")
        }
        assertFalse(vgtv.suitable(articleUrl))

        assertTrue(BTArticleIE(http(transfer())).suitable(articleUrl))
        assertTrue(BTVestlendingenIE(http(transfer())).suitable(vestlendingenUrl))
        assertFalse(BTArticleIE(http(transfer())).suitable(videoUrl))
    }

    // ---------------------------------------------------------------- assets

    @Test
    fun assetYieldsTheHlsAndMp4Rows() = runTest {
        val info = VGTVIE(http(transfer(assetRoute))).extract(videoUrl)
        assertEquals("84196", info.id)
        assertEquals("Fixture VGTV Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/main.jpg?t[]=900x506q80", info.thumbnails.single().url)
        assertEquals("20140706", info.uploadDate)
        assertEquals(648.0, info.duration)
        assertEquals(123L, info.viewCount)
        assertEquals(false, info.isLive)
        assertEquals(3, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4-2000", info.formats[1].formatId)
        assertEquals(1280L, info.formats[1].width)
        assertEquals(720L, info.formats[1].height)
        assertEquals(2000.0, info.formats[1].tbr)
        assertEquals("https://media.example/video.mp4", info.formats[2].url)
    }

    @Test
    fun geoblockedAssetFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://svp.vg.no/svp/api/v1/vgtv/assets/84196?appName=vgtv-website",
                contentType = "application/json",
                body = """
                    {"status": "active", "streamType": "vod", "streamUrls": {},
                     "streamConfiguration": {"properties": ["geoblocked"]},
                     "title": "Fixture", "images": {"main": "https://media.example/main.jpg"}}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            VGTVIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun inactiveAssetFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://svp.vg.no/svp/api/v1/vgtv/assets/84196?appName=vgtv-website",
                contentType = "application/json",
                body = """{"status": "inactive"}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            VGTVIE(http(transfer)).extract(videoUrl)
        }
    }

    // ------------------------------------------------------------- articles

    @Test
    fun articleRedirectsToTheBttvScheme() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = articleUrl,
                contentType = "text/html",
                body = "<html><body><video data-id=\"23199\"></video></body></html>",
            ),
        )
        val info = BTArticleIE(http(transfer)).extract(articleUrl)
        assertEquals("bttv:23199", info.redirectUrl)
    }

    @Test
    fun vestlendingenRedirectsToTheBttvScheme() = runTest {
        val info = BTVestlendingenIE(http(transfer())).extract(vestlendingenUrl)
        assertEquals("bttv:86588", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun assetIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("84196"),
                "title" to Expect.Value("Fixture VGTV Title"),
                "upload_date" to Expect.Value("20140706"),
                "duration" to Expect.Value(648.0),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(assetRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VGTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun articleIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = articleUrl,
            infoDict = mapOf("id" to Expect.Value("Kjemper-for-internatet")),
            routes = listOf(
                FixtureRoute(
                    urlPattern = articleUrl,
                    contentType = "text/html",
                    body = "<html><body><video data-id=\"23199\"></video></body></html>",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BTArticleIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
