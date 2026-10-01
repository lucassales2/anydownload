package com.anydownload.core.extract.art19

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
 * Fixture cases for the ART19 subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class Art19IETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val episodeId = "5ba1413c-48b8-472b-9cc3-cfd952340bdb"
    private val seriesId = "ed52a0ab-08b1-4def-8afc-549e4d93296d"

    private fun playerRoute() = FixtureRoute(
        urlPattern = "https://art19.com/episodes/$episodeId",
        contentType = "application/json",
        body = """
            {"episode": {"id": "$episodeId", "title": "Fixture Episode",
              "description_plain": "Fixture description", "episode_number": 582,
              "series_id": "$seriesId", "created_at": "2024-01-22T00:00:00Z"}}
        """.trimIndent(),
    )

    private fun rssRoute() = FixtureRoute(
        urlPattern = "https://rss.art19.com/episodes/$episodeId.json",
        contentType = "application/json",
        body = """
            {"content": {"episode_title": "Fixture Episode",
              "episode_description_plain": "Fixture description", "duration": 527.4,
              "cover_image": "https://media.example/cover.jpeg",
              "series_title": "The Daily Briefing",
              "media": {"waveform_bin": {"url": "https://media.example/wave.bin"},
                        "mp3": {"url": "https://media.example/episode.mp3"}}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            Art19IE(http(transfer())) to "https://rss.art19.com/episodes/$episodeId.mp3",
            Art19IE(http(transfer())) to "https://art19.com/shows/scamfluencers/episodes/$episodeId",
            Art19ShowIE(http(transfer())) to "https://www.art19.com/shows/scamfluencers",
            Art19ShowIE(http(transfer())) to "https://art19.com/shows/enthuellt/embed",
            Art19ShowIE(http(transfer())) to "https://rss.art19.com/scamfluencers",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(Art19IE(http(transfer())).suitable("https://www.art19.com/shows/scamfluencers"))
    }

    // --------------------------------------------------------------- episode

    @Test
    fun episodeApisYieldFormatsAndMetadata() = runTest {
        val url = "https://rss.art19.com/episodes/$episodeId.mp3"
        val transfer = transfer(playerRoute(), rssRoute())
        val info = Art19IE(http(transfer)).extract(url)
        assertEquals(episodeId, info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(527.4, info.duration)
        assertEquals("20240122", info.uploadDate)
        assertEquals("The Daily Briefing", info.channel)
        assertEquals(2, info.formats.size)
        assertEquals("mp3", info.formats[0].acodec)
        assertEquals("https://media.example/episode.mp3", info.formats[1].url)
        assertEquals("https://media.example/cover.jpeg", info.thumbnails.single().url)
    }

    // ------------------------------------------------------------------ show

    @Test
    fun showListingYieldsEpisodeEntries() = runTest {
        val url = "https://www.art19.com/shows/scamfluencers"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://art19.com/series/scamfluencers",
                contentType = "application/json",
                body = """
                    {"series": {"id": "$seriesId", "title": "Scamfluencers",
                      "description_plain": "Fixture description", "created_at": "2022-03-15T00:00:00Z",
                      "episode_ids": ["$episodeId"]}}
                """.trimIndent(),
            ),
        )
        val info = Art19ShowIE(http(transfer)).extract(url)
        assertEquals(seriesId, info.id)
        assertEquals("Scamfluencers", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://rss.art19.com/episodes/$episodeId.mp3", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://rss.art19.com/episodes/$episodeId.mp3"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(episodeId),
                "title" to Expect.Value("Fixture Episode"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(playerRoute(), rssRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> Art19IE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
