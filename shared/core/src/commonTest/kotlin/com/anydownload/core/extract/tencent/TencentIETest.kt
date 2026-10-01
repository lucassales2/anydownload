package com.anydownload.core.extract.tencent

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
 * Fixture cases for the Tencent Video / WeTV / Iflix subset. Ids, titles, and
 * media paths are synthesized; media lives on `media.example`, and no cookie,
 * key, or token appears.
 */
class TencentIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            VQQVideoIE(http(transfer())) to "https://v.qq.com/x/page/q326831cny0.html",
            VQQVideoIE(http(transfer())) to "https://v.qq.com/x/cover/7ce5noezvafma27/abc123.html",
            VQQSeriesIE(http(transfer())) to "https://v.qq.com/x/cover/7ce5noezvafma27.html",
            WeTvEpisodeIE(http(transfer())) to "https://wetv.vip/play/abc123/fixture-ep",
            WeTvSeriesIE(http(transfer())) to "https://wetv.vip/play/abc123",
            IflixEpisodeIE(http(transfer())) to "https://www.iflix.com/play/abc123/fixture-ep",
            IflixSeriesIE(http(transfer())) to "https://www.iflix.com/play/abc123",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(VQQSeriesIE(http(transfer())).suitable("https://v.qq.com/x/page/q326831cny0.html"))
    }

    // --------------------------------------------------------------- series

    @Test
    fun vqqSeriesScansTheDataVidEntries() = runTest {
        val url = "https://v.qq.com/x/cover/7ce5noezvafma27.html"
        val page = """
            <html><head><meta property="og:title" content="Fixture Series"></head><body>
            <div data-vid="abc123" class="episode-item episode-item-rect--number"></div>
            <div data-vid="def456" class="episode-item episode-item-rect--number"></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = VQQSeriesIE(http(transfer)).extract(url)
        assertEquals("7ce5noezvafma27", info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://v.qq.com/x/cover/7ce5noezvafma27/abc123.html", info.entries[0].url)
    }

    @Test
    fun weTvAndIflixSeriesScanTheNextJsVideoList() = runTest {
        fun page(series: String) = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"data": {"coverInfo": {"title": "Fixture Series",
                                                           "description": "Fixture description"},
                                             "videoList": [{"vid": "vid-one"}, {"vid": "vid-two"}]}}}}
            </script></head></html>
        """.trimIndent()

        val wetvUrl = "https://wetv.vip/play/abc123"
        val wetv = WeTvSeriesIE(
            http(transfer(FixtureRoute(urlPattern = wetvUrl, contentType = "text/html", body = page("abc123")))),
        ).extract(wetvUrl)
        assertEquals("abc123", wetv.id)
        assertEquals("Fixture Series", wetv.title)
        assertEquals(2, wetv.entries.size)
        assertEquals("https://wetv.vip/play/abc123/vid-one", wetv.entries[0].url)

        val iflixUrl = "https://www.iflix.com/play/xyz789"
        val iflix = IflixSeriesIE(
            http(transfer(FixtureRoute(urlPattern = iflixUrl, contentType = "text/html", body = page("xyz789")))),
        ).extract(iflixUrl)
        assertEquals("https://www.iflix.com/play/xyz789/vid-two", iflix.entries[1].url)
    }

    // ---------------------------------------------------------------- walls

    @Test
    fun episodeClassesFailTypedOnTheSignedApi() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            VQQVideoIE(http(transfer())) to "https://v.qq.com/x/page/q326831cny0.html",
            WeTvEpisodeIE(http(transfer())) to "https://wetv.vip/play/abc123/fixture-ep",
            IflixEpisodeIE(http(transfer())) to "https://www.iflix.com/play/abc123/fixture-ep",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("ckey"), "${extractor.ieKey}: ${error.message}")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun vqqSeriesIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://v.qq.com/x/cover/7ce5noezvafma27.html"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("7ce5noezvafma27"),
                "title" to Expect.Value("Fixture Series"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><meta property="og:title" content="Fixture Series"></head><body>
                        <div data-vid="abc123" class="episode-item episode-item-rect--number"></div>
                        </body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VQQSeriesIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun weTvSeriesIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://wetv.vip/play/abc123"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("abc123"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><script id="__NEXT_DATA__" type="application/json">
                        {"props": {"pageProps": {"data": {"coverInfo": {"title": "Fixture Series"},
                          "videoList": [{"vid": "vid-one"}, {"vid": "vid-two"}]}}}}
                        </script></head></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> WeTvSeriesIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
