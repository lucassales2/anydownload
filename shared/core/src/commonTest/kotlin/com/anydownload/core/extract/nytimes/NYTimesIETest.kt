package com.anydownload.core.extract.nytimes

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
 * Fixture cases for the NYTimes subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class NYTimesIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NYTimesIE(http(transfer())) to "https://www.nytimes.com/video/opinion/100000002847155/fixture.html",
            NYTimesArticleIE(http(transfer())) to "https://www.nytimes.com/2023/06/25/world/fixture-article.html",
            NYTimesCookingIE(http(transfer())) to "https://cooking.nytimes.com/guides/fixture-guide",
            NYTimesCookingRecipeIE(http(transfer())) to "https://cooking.nytimes.com/recipes/1017817-cranberry-curd-tart",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NYTimesCookingRecipeIE(http(transfer())).suitable("https://cooking.nytimes.com/guides/x"))
    }

    @Test
    fun tokenClassesFailTyped() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NYTimesIE(http(transfer())) to "https://www.nytimes.com/video/opinion/100000002847155/fixture.html",
            NYTimesArticleIE(http(transfer())) to "https://www.nytimes.com/2023/06/25/world/fixture-article.html",
            NYTimesCookingIE(http(transfer())) to "https://cooking.nytimes.com/guides/fixture-guide",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("Nyt-Token"), "${extractor.ieKey}: ${error.message}")
        }
    }

    // ---------------------------------------------------------------- recipe

    @Test
    fun recipePageYieldsTheVideo() = runTest {
        val url = "https://cooking.nytimes.com/recipes/1017817-cranberry-curd-tart"
        val page = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"recipe": {
              "id": 1017817, "title": "Cranberry Curd Tart",
              "topnote": "<p>A fixture recipe.</p>",
              "publishedAt": 1447804800,
              "videoSrc": "https://media.example/hls/recipe.m3u8",
              "contentAttribution": {"cardByline": "David Tanis"},
              "image": {"crops": {"recipe": ["https://media.example/recipe.jpg"]}}
            }}}}
            </script></head></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NYTimesCookingRecipeIE(http(transfer)).extract(url)
        assertEquals("1017817", info.id)
        assertEquals("Cranberry Curd Tart", info.title)
        assertEquals("A fixture recipe.", info.description)
        assertEquals("20151118", info.uploadDate)
        assertEquals("David Tanis", info.uploader)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/recipe.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun recipeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://cooking.nytimes.com/recipes/1017817-cranberry-curd-tart"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("1017817"),
                "title" to Expect.Value("Cranberry Curd Tart"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><script id="__NEXT_DATA__" type="application/json">
                        {"props": {"pageProps": {"recipe": {
                          "id": 1017817, "title": "Cranberry Curd Tart",
                          "videoSrc": "https://media.example/hls/recipe.m3u8"}}}}
                        </script></head></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NYTimesCookingRecipeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
