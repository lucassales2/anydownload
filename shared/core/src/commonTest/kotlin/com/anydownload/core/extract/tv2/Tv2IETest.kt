package com.anydownload.core.extract.tv2

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
 * Fixture cases for the TV2 subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class Tv2IETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "1791207"

    private val assetJson = """
        {"title": "Fixture TV2", "description": "Fixture description", "live": false,
         "duration": 146, "update_time": "2022-09-27T00:00:00Z", "views": 42,
         "images": {"medium": "https://media.example/thumb.jpg"}}
    """.trimIndent()

    private fun playJson() = """
        {"playback": {"drmProtected": false, "streams": [
          {"url": "https://media.example/hls/master.m3u8", "type": "hls"}]}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TV2IE(http(transfer())) to "http://www.tv2.no/v/$videoId/",
            TV2IE(http(transfer())) to "https://www.tv2.no/video/nyhetene/fixture/$videoId/",
            TV2ArticleIE(http(transfer())) to "https://www.tv2.no/underholdning/fixture-article/15095188/",
            KatsomoIE(http(transfer())) to "https://www.mtvuutiset.fi/video/prog1311159",
            KatsomoIE(http(transfer())) to "https://www.katsomo.fi/#!/jakso/1311159",
            MTVUutisetArticleIE(http(transfer())) to "https://www.mtvuutiset.fi/artikkeli/fixture-article/7931384",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TV2IE(http(transfer())).suitable("https://www.tv2.no/underholdning/fixture-article/15095188/"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoApiYieldsHlsAndMetadata() = runTest {
        val url = "http://www.tv2.no/v/$videoId/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://sumo.tv2.no/rest/assets/$videoId",
                contentType = "application/json",
                body = assetJson,
            ),
            FixtureRoute(
                urlPattern = "https://api.sumo.tv2.no/play/$videoId?stream=HLS",
                method = "POST",
                contentType = "application/json",
                body = playJson(),
            ),
        )
        val info = TV2IE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture TV2", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(146.0, info.duration)
        assertEquals(42L, info.viewCount)
        assertEquals("20220927", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articleScansAssetIds() = runTest {
        val url = "https://www.tv2.no/underholdning/fixture-article/15095188/"
        val page = """
            <html><head><meta property="og:title" content="Fixture Article - TV2.no"></head>
            <body><div data-assetid="111111"></div><div data-assetid="222222"></div></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = TV2ArticleIE(http(transfer)).extract(url)
        assertEquals("15095188", info.id)
        assertEquals("Fixture Article", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.tv2.no/v/111111", info.entries[0].url)
    }

    @Test
    fun mtvUutisetArticleYieldsEntries() = runTest {
        val url = "https://www.mtvuutiset.fi/artikkeli/fixture-article/7931384"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://api.mtvuutiset.fi/mtvuutiset/api/json/7931384",
                contentType = "application/json",
                body = """
                    {"videos": [{"videotype": "katsomo", "video_id": "1311159",
                                 "url": "https://www.katsomo.fi/#!/jakso/1311159"},
                                {"videotype": "youtube", "video_id": "abc",
                                 "url": "https://www.youtube.com/watch?v=abc"}]}
                """.trimIndent(),
            ),
        )
        val info = MTVUutisetArticleIE(http(transfer)).extract(url)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.katsomo.fi/#!/jakso/1311159", info.entries[0].url)
    }

    @Test
    fun katsomoFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            KatsomoIE(http(transfer())).extract("https://www.mtvuutiset.fi/video/prog1311159")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://www.tv2.no/v/$videoId/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture TV2"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://sumo.tv2.no/rest/assets/$videoId",
                    contentType = "application/json",
                    body = assetJson,
                ),
                FixtureRoute(
                    urlPattern = "https://api.sumo.tv2.no/play/$videoId?stream=HLS",
                    method = "POST",
                    contentType = "application/json",
                    body = playJson(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TV2IE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
