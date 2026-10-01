package com.anydownload.core.extract.brainpop

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
 * Fixture cases for the BrainPOP subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and the access token is a
 * fake value served by the fixture (never stored by the port).
 */
class BrainPopIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val slug = "health/conflictresolution/martinlutherkingjr"
    private val displayId = "martinlutherkingjr"

    private fun movieRoute() = FixtureRoute(
        urlPattern = "https://api.brainpop.com/api/content/published/bp/en/$slug/movie?full=1",
        contentType = "application/json",
        body = """
            {"data": {"access": {"allow": true},
              "feature": {"language": "en", "data": {"high_v2": "movie/high", "low_v2": "movie/low",
                "token": "fake_value", "subtitles_en": "/sub/en.vtt"}},
              "topic": {"topic_id": "1f3259fa457292b4"}}}
        """.trimIndent(),
    )

    private fun topicRoute() = FixtureRoute(
        urlPattern = "https://api.brainpop.com/api/content/published/bp/en/$slug?full=1",
        contentType = "application/json",
        body = """
            {"data": {"topic": {"topic_id": "1f3259fa457292b4", "name": "Martin Luther King, Jr.",
                      "synopsis": "Fixture description"}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            BrainPOPIE(http(transfer())) to "https://www.brainpop.com/$slug/movie",
            BrainPOPJrIE(http(transfer())) to "https://jr.brainpop.com/health/feelingsandsel/emotions/",
            BrainPOPELLIE(http(transfer())) to "https://ell.brainpop.com/level1/unit1/lesson1/",
            BrainPOPEspIE(http(transfer())) to "https://esp.brainpop.com/ciencia/la_diversidad/ecosistemas/",
            BrainPOPFrIE(http(transfer())) to "https://fr.brainpop.com/sciencesdelaterre/energie/sourcesdenergie/",
            BrainPOPIlIE(http(transfer())) to "https://il.brainpop.com/category_9/subcategory_150/subjects_3782/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(BrainPOPIE(http(transfer())).suitable("https://jr.brainpop.com/health/feelingsandsel/emotions/"))
    }

    // ------------------------------------------------------------------ movie

    @Test
    fun publishedApiYieldsAdaptiveFormats() = runTest {
        val url = "https://www.brainpop.com/$slug/movie"
        val transfer = transfer(movieRoute(), topicRoute())
        val info = BrainPOPIE(http(transfer)).extract(url)
        assertEquals("1f3259fa457292b4", info.id)
        assertEquals("Martin Luther King, Jr.", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(4, info.formats.size)
        assertEquals("high_v2-hls", info.formats[0].formatId)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("en", info.formats[0].language)
        assertEquals("en", info.subtitles.single().language)
        assertEquals("https://cdn.brainpop.com/sub/en.vtt", info.subtitles.single().formats.single().url)
    }

    @Test
    fun gatedMovieFailsTyped() = runTest {
        val url = "https://www.brainpop.com/$slug/movie"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.brainpop.com/api/content/published/bp/en/$slug/movie?full=1",
                contentType = "application/json",
                body = """
                    {"data": {"access": {"allow": false, "reason": "You must be logged in"},
                      "topic": {"topic_id": "1f3259fa457292b4"}}}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            BrainPOPIE(http(transfer)).extract(url)
        }
    }

    // ----------------------------------------------------------------- legacy

    @Test
    fun legacyPageYieldsAdaptiveFormats() = runTest {
        val url = "https://jr.brainpop.com/health/feelingsandsel/emotions/"
        val page = """
            <html><body><script>
            var content = {"category": {"unit": {"topic": {"EntryID": "347", "name": "Emotions",
              "synopsis": "Fixture description",
              "movies": {"high": "movie/high", "low": "movie/low"}}}}};
            ec_token: "fake_value",
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = BrainPOPJrIE(http(transfer)).extract(url)
        assertEquals("347", info.id)
        assertEquals("Emotions", info.title)
        assertEquals(4, info.formats.size)
        assertEquals("https://hls-jr.brainpop.com/movie/high.m3u8?fake_value", info.formats[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun movieIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.brainpop.com/$slug/movie"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("1f3259fa457292b4"),
                "title" to Expect.Value("Martin Luther King, Jr."),
                "formats" to Expect.Count(4),
            ),
            routes = listOf(movieRoute(), topicRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BrainPOPIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
