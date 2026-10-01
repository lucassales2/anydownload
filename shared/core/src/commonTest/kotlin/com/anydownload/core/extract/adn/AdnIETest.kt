package com.anydownload.core.extract.adn

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
 * Fixture cases for the ADN subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class AdnIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ADNIE(http(transfer())) to
                "https://animationdigitalnetwork.com/video/558-fruits-basket/9841-episode-1-a-ce-soir",
            ADNIE(http(transfer())) to
                "https://animationdigitalnetwork.com/de/video/973-the-eminence-in-shadow/23550-folge-1",
            ADNSeasonIE(http(transfer())) to "https://animationdigitalnetwork.com/video/911-tokyo-mew-mew-new",
            ADNSeasonIE(http(transfer())) to "https://animationdigitalnetwork.com/de/video/911-tokyo-mew-mew-new",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ADNIE(http(transfer())).suitable("https://animationdigitalnetwork.com/video/911-tokyo-mew-mew-new"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun episodeFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            ADNIE(http(transfer())).extract(
                "https://animationdigitalnetwork.com/video/558-fruits-basket/9841-episode-1-a-ce-soir",
            )
        }
    }

    // --------------------------------------------------------------- listing

    @Test
    fun seasonListingYieldsEpisodeEntries() = runTest {
        val url = "https://animationdigitalnetwork.com/video/911-tokyo-mew-mew-new"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gw.api.animationdigitalnetwork.fr/show/911/",
                contentType = "application/json",
                body = """{"show": {"id": 911, "title": "Fixture Show"}}""",
            ),
            FixtureRoute(
                urlPattern = "https://gw.api.animationdigitalnetwork.fr/video/show/911?order=asc&limit=-1",
                contentType = "application/json",
                body = """{"videos": [{"id": 23550, "name": "Episode 1"}]}""",
            ),
        )
        val info = ADNSeasonIE(http(transfer)).extract(url)
        assertEquals("911", info.id)
        assertEquals("Fixture Show", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "https://animationdigitalnetwork.com/video/911/23550",
            info.entries.single().url,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun seasonIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://animationdigitalnetwork.com/video/911-tokyo-mew-mew-new"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("911"),
                "title" to Expect.Value("Fixture Show"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://gw.api.animationdigitalnetwork.fr/show/911/",
                    contentType = "application/json",
                    body = """{"show": {"id": 911, "title": "Fixture Show"}}""",
                ),
                FixtureRoute(
                    urlPattern = "https://gw.api.animationdigitalnetwork.fr/video/show/911?order=asc&limit=-1",
                    contentType = "application/json",
                    body = """{"videos": [{"id": 23550, "name": "Episode 1"}]}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ADNSeasonIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
