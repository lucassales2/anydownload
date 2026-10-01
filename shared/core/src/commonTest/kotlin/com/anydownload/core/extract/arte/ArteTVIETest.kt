package com.anydownload.core.extract.arte

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
 * Fixture cases for the Arte subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class ArteTVIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "109067-000-A"
    private val configUrl = "https://api.arte.tv/api/player/v2/config/fr/$videoId"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ArteTVIE(http(transfer())) to "https://www.arte.tv/fr/videos/$videoId/fixture/",
            ArteTVIE(http(transfer())) to configUrl,
            ArteTVEmbedIE(http(transfer())) to
                "https://www.arte.tv/player/v5/index.php?json_url=https%3A%2F%2Fapi.arte.tv%2Fapi%2Fplayer%2Fv2%2Fconfig%2Fde%2F100605-013-A&lang=de",
            ArteTVPlaylistIE(http(transfer())) to "https://www.arte.tv/en/videos/RC-016954/earn-a-living/",
            ArteTVCategoryIE(http(transfer())) to "https://www.arte.tv/en/videos/politics-and-society/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ArteTVCategoryIE(http(transfer())).suitable("https://www.arte.tv/fr/videos/$videoId/fixture/"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun configApiYieldsFormatsAndMetadata() = runTest {
        val url = "https://www.arte.tv/fr/videos/$videoId/fixture/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = configUrl,
                contentType = "application/json",
                body = """
                    {"data": {"attributes": {
                      "streams": [
                        {"protocol": "HLS", "url": "https://media.example/hls/master.m3u8",
                         "versions": [{"eStat": {"ml5": "VF-STF"}, "shortLabel": "VO", "label": "French"}]},
                        {"protocol": "HTTPS", "url": "https://media.example/video/fr.mp4",
                         "versions": [{"eStat": {"ml5": "VOF"}, "shortLabel": "VF", "label": "French"}]}],
                      "restriction": {},
                      "rights": {"begin": "2024-04-24T00:00:00Z"},
                      "live": false,
                      "metadata": {"providerId": "$videoId", "title": "Fixture Arte",
                                   "subtitle": "Fixture Subtitle", "description": "Fixture description",
                                   "duration": {"seconds": 7599},
                                   "images": [{"url": "https://media.example/thumb.jpg", "caption": "cover"}]}}}}
                """.trimIndent(),
            ),
        )
        val info = ArteTVIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Subtitle", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(7599.0, info.duration)
        assertEquals("20240424", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("HTTPS-VOF", info.formats[1].formatId)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun geoRestrictedConfigFailsTyped() = runTest {
        val url = "https://www.arte.tv/fr/videos/$videoId/fixture/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = configUrl,
                contentType = "application/json",
                body = """
                    {"data": {"attributes": {"streams": [],
                      "restriction": {"geoblocking": {"restrictedArea": true, "code": "DE_FR"}}}}}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            ArteTVIE(http(transfer)).extract(url)
        }
    }

    // ---------------------------------------------------------------- embed

    @Test
    fun embedRedirectsToTheConfigUrl() = runTest {
        val url = "https://www.arte.tv/player/v5/index.php?json_url=$configUrl&lang=fr"
        val info = ArteTVEmbedIE(http(transfer())).extract(url)
        assertEquals(configUrl, info.redirectUrl)
    }

    // ------------------------------------------------------------- playlist

    @Test
    fun playlistFailsTypedOnTheBearerToken() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            ArteTVPlaylistIE(http(transfer())).extract("https://www.arte.tv/en/videos/RC-016954/earn-a-living/")
        }
        assertTrue(error.message!!.contains("bearer token"))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun configIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.arte.tv/fr/videos/$videoId/fixture/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Subtitle"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = configUrl,
                    contentType = "application/json",
                    body = """
                        {"data": {"attributes": {
                          "streams": [{"protocol": "HLS", "url": "https://media.example/hls/master.m3u8",
                                       "versions": [{"eStat": {"ml5": "VF"}, "shortLabel": "VO"}]}],
                          "restriction": {}, "rights": {},
                          "metadata": {"providerId": "$videoId", "subtitle": "Fixture Subtitle"}}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ArteTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
