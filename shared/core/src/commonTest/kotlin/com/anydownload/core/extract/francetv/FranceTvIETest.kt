package com.anydownload.core.extract.francetv

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
 * Fixture cases for the France TV subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or signed media
 * URL appears (the token endpoint returns a fake fixture URL).
 */
class FranceTvIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "ec217ecc-0733-48cf-ac06-af1347b849d1"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            FranceTVIE(http(transfer())) to "francetv:$videoId",
            FranceTVIE(http(transfer())) to "francetv:NI_1004933@Zouzous",
            FranceTVSiteIE(http(transfer())) to "https://www.france.tv/france-2/fixture/140921-fixture.html",
            FranceTVInfoIE(http(transfer())) to "https://www.francetvinfo.fr/fixture_3561461.html",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(FranceTVIE(http(transfer())).suitable("https://www.france.tv/x.html"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun tokenEndpointSignsTheManifestUrl() = runTest {
        val api = "https://k7.ftven.fr/videos/$videoId?device_type=desktop&browser=chrome&domain=www.france.tv"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = api,
                contentType = "application/json",
                body = """
                    {"video": {"url": "https://media.example/hls/master.m3u8", "format": "hls",
                               "duration": 2580, "is_live": false,
                               "token": "https://media.example/token"},
                     "meta": {"title": "Fixture Title", "additional_title": "Fixture Sub",
                              "image_url": "https://media.example/thumb.jpg",
                              "broadcasted_at": "2017-08-13T10:00:00Z"}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://media.example/token?format=json&url=https://media.example/hls/master.m3u8",
                contentType = "application/json",
                body = """{"url": "https://media.example/hls/signed.m3u8"}""",
            ),
        )
        val info = FranceTVIE(http(transfer)).extract("francetv:$videoId")
        assertEquals(videoId, info.id)
        assertEquals("Fixture Title - Fixture Sub", info.title)
        assertEquals(2580.0, info.duration)
        assertEquals("20170813", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/hls/signed.m3u8", info.formats.single().url)
    }

    @Test
    fun geoAndDrmCodesFailTyped() = runTest {
        val api = "https://k7.ftven.fr/videos/$videoId?device_type=desktop&browser=chrome&domain=www.france.tv"
        val geo = transfer(
            FixtureRoute(urlPattern = api, contentType = "application/json", body = """{"code": 2009}"""),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            FranceTVIE(http(geo)).extract("francetv:$videoId")
        }

        val drm = transfer(
            FixtureRoute(urlPattern = api, contentType = "application/json", body = """{"code": 2015}"""),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            FranceTVIE(http(drm)).extract("francetv:$videoId")
        }
        assertTrue(error.message!!.contains("DRM"))
    }

    // -------------------------------------------------------------- site page

    @Test
    fun sitePageRedirectsToTheVideoId() = runTest {
        val url = "https://www.france.tv/france-2/fixture/140921-fixture.html"
        val page = """
            <html><body><script>{"options":{"id":"$videoId","other":1}}</script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = FranceTVSiteIE(http(transfer)).extract(url)
        assertEquals("francetv:$videoId", info.redirectUrl)
    }

    // -------------------------------------------------------------- info page

    @Test
    fun infoPageListsDailymotionEmbedsOrRedirects() = runTest {
        val dmUrl = "https://www.francetvinfo.fr/fixture_1520091.html"
        val dmPage = """
            <html><body><iframe src="//www.dailymotion.com/embed/video/x4iiko0"></iframe></body></html>
        """.trimIndent()
        val dmTransfer = transfer(FixtureRoute(urlPattern = dmUrl, contentType = "text/html", body = dmPage))
        val dm = FranceTVInfoIE(http(dmTransfer)).extract(dmUrl)
        assertEquals(1, dm.entries.size)
        assertEquals("https://www.dailymotion.com/video/x4iiko0", dm.entries.single().url)

        val infoUrl = "https://www.francetvinfo.fr/fixture_3561461.html"
        val infoPage = """
            <html><body><div data-id="$videoId"></div></body></html>
        """.trimIndent()
        val infoTransfer = transfer(FixtureRoute(urlPattern = infoUrl, contentType = "text/html", body = infoPage))
        val info = FranceTVInfoIE(http(infoTransfer)).extract(infoUrl)
        assertEquals("francetv:$videoId", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoApiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "francetv:$videoId",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Title"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://k7.ftven.fr/videos/$videoId?device_type=desktop&browser=chrome&domain=www.france.tv",
                    contentType = "application/json",
                    body = """
                        {"video": {"url": "https://media.example/hls/master.m3u8", "format": "hls"},
                         "meta": {"title": "Fixture Title"}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> FranceTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
