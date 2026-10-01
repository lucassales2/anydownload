package com.anydownload.core.extract.tenplay

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
 * Fixture cases for the 10play subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class TenPlayIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TenPlayIE(http(transfer())) to
                "https://10.com.au/australian-survivor/web-extras/season-10/fixture/tpv250414jdmtf",
            TenPlayIE(http(transfer())) to
                "https://10play.com.au/how-to-stay-married/web-extras/season-1/fixture/tpv190915ylupc",
            TenPlaySeasonIE(http(transfer())) to "https://10.com.au/masterchef/episodes/season-15",
            TenPlaySeasonIE(http(transfer())) to "https://10play.com.au/fixture-show/episodes/season-2024",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TenPlayIE(http(transfer())).suitable("https://10.com.au/masterchef/episodes/season-15"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun videoFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            TenPlayIE(http(transfer())).extract(
                "https://10.com.au/australian-survivor/web-extras/season-10/fixture/tpv250414jdmtf",
            )
        }
    }

    // --------------------------------------------------------------- listing

    @Test
    fun seasonListingYieldsEpisodeEntries() = runTest {
        val url = "https://10.com.au/masterchef/episodes/season-15"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://10.com.au/api/shows/masterchef/episodes/season-15",
                contentType = "application/json",
                body = """
                    {"content": [{"title": "Season 15", "components": [
                      {"title": "Episodes", "tpId": "MTQ2NjMxOQ==",
                       "loadMoreUrl": "/api/carousel/episodes"}]}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://10.com.au/api/carousel/episodes",
                contentType = "application/json",
                body = """
                    {"hasMore": false, "items": [
                      {"id": "ep-1", "cardLink": "/masterchef/episodes/season-15/fixture/tpv240228pofvt"}]}
                """.trimIndent(),
            ),
        )
        val info = TenPlaySeasonIE(http(transfer)).extract(url)
        assertEquals("MTQ2NjMxOQ==", info.id)
        assertEquals("Season 15", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "https://10.com.au/masterchef/episodes/season-15/fixture/tpv240228pofvt",
            info.entries.single().url,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun seasonIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://10.com.au/masterchef/episodes/season-15"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("MTQ2NjMxOQ=="),
                "title" to Expect.Value("Season 15"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://10.com.au/api/shows/masterchef/episodes/season-15",
                    contentType = "application/json",
                    body = """
                        {"content": [{"title": "Season 15", "components": [
                          {"title": "Episodes", "tpId": "MTQ2NjMxOQ==",
                           "loadMoreUrl": "/api/carousel/episodes"}]}]}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://10.com.au/api/carousel/episodes",
                    contentType = "application/json",
                    body = """
                        {"hasMore": false, "items": [{"id": "ep-1", "cardLink": "/watch/ep-1"}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TenPlaySeasonIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
