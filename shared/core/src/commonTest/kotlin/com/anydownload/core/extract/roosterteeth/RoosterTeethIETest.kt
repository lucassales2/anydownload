package com.anydownload.core.extract.roosterteeth

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Rooster Teeth subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no login or token
 * appears.
 */
class RoosterTeethIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val displayId = "lets-play-2013-126"

    private fun videoJson() = """
        {"data": [{"id": "17845", "uuid": "ffdbe55e-464d-11e7-a302-065410f210c4",
          "attributes": {"url": "https://media.example/hls/master.m3u8",
                         "encoding_pipeline": "standard"}}]}
    """.trimIndent()

    private fun episodeJson() = """
        {"data": [{"id": "17845", "uuid": "ffdbe55e-464d-11e7-a302-065410f210c4",
          "attributes": {"title": "Fixture Episode", "description": "Fixture description",
                         "length": 189, "original_air_date": "2013-02-04T00:00:00Z",
                         "channel_id": "fixture-channel", "is_sponsors_only": false},
          "included": {"images": [{"type": "episode_image",
                                   "attributes": {"small": "https://media.example/small.jpg"}}]}}]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RoosterTeethIE(http(transfer())) to "https://roosterteeth.com/episode/fixture-episode",
            RoosterTeethIE(http(transfer())) to "https://www.roosterteeth.com/watch/$displayId",
            RoosterTeethIE(http(transfer())) to "https://achievementhunter.roosterteeth.com/episode/fixture",
            RoosterTeethSeriesIE(http(transfer())) to "https://roosterteeth.com/series/rwby",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RoosterTeethIE(http(transfer())).suitable("https://roosterteeth.com/series/rwby"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun watchApiYieldsHlsAndMetadata() = runTest {
        val url = "https://www.roosterteeth.com/watch/$displayId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://svod-be.roosterteeth.com/api/v1/watch/$displayId/videos",
                contentType = "application/json",
                body = videoJson(),
            ),
            FixtureRoute(
                urlPattern = "https://svod-be.roosterteeth.com/api/v1/watch/$displayId",
                contentType = "application/json",
                body = episodeJson(),
            ),
        )
        val info = RoosterTeethIE(http(transfer)).extract(url)
        assertEquals("17845", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(189.0, info.duration)
        assertEquals("20130204", info.uploadDate)
        assertEquals("public", info.availability)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/small.jpg", info.thumbnails.single().url)
    }

    // ----------------------------------------------------------------- series

    @Test
    fun seriesListingYieldsEpisodeEntries() = runTest {
        val url = "https://roosterteeth.com/series/rwby?season=7"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://svod-be.roosterteeth.com/api/v1/shows/rwby/seasons?order=asc&order_by",
                contentType = "application/json",
                body = """
                    {"data": [{"attributes": {"number": 7},
                               "links": {"episodes": "/api/v1/seasons/season-7/episodes"}}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://svod-be.roosterteeth.com/api/v1/seasons/season-7/episodes?per_page=1000",
                contentType = "application/json",
                body = """
                    {"data": [{"id": "ep-1", "attributes": {"title": "Fixture Episode"},
                               "canonical_links": {"self": "/watch/fixture-episode"}}]}
                """.trimIndent(),
            ),
        )
        val info = RoosterTeethSeriesIE(http(transfer)).extract(url)
        assertEquals("rwby-7", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.roosterteeth.com/watch/fixture-episode", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun watchIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.roosterteeth.com/watch/$displayId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("17845"),
                "title" to Expect.Value("Fixture Episode"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://svod-be.roosterteeth.com/api/v1/watch/$displayId/videos",
                    contentType = "application/json",
                    body = videoJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://svod-be.roosterteeth.com/api/v1/watch/$displayId",
                    contentType = "application/json",
                    body = episodeJson(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RoosterTeethIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
