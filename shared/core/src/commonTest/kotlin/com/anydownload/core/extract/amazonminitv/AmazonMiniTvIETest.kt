package com.anydownload.core.extract.amazonminitv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
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
 * Fixture cases for the Amazon MiniTV subset. Ids and media paths are
 * synthesized on `media.example`; the GraphQL fixtures carry an empty
 * `sessionIdToken` because the port cannot read the guest cookie. No cookie,
 * token, or signed URL appears.
 */
class AmazonMiniTvIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val episodeUrl =
        "https://www.amazon.in/minitv/tp/75fe3a75-b8fe-4499-8100-5c9424344840?referrer=https%3A%2F%2Fwww.example.in%2Fminitv"
    private val seasonUrl = "amazonminitv:season:amzn1.dv.gti.0aa996eb-6a1b-4886-a342-387fbd2f1db0"
    private val seriesUrl = "amazonminitv:series:amzn1.dv.gti.56521d46-b040-4fd5-872e-3e70476a04b0"

    private val prsRoute = FixtureRoute(
        urlPattern = "https://www.amazon.in/minitv/api/web/prs*",
        contentType = "application/json",
        body = """
            {"playbackAssets": {
              "hls": {"manifestUrl": "https://media.example/master.m3u8"},
              "dash": {"manifestUrl": "https://media.example/manifest.mpd"},
              "other": {"manifestUrl": "https://media.example/other.bin"}}}
        """.trimIndent(),
    )

    private val contentRoute = FixtureRoute(
        urlPattern = "https://www.amazon.in/minitv/api/web/graphql",
        method = "POST",
        contentType = "application/json",
        body = """
            {"data": {"content": {
              "contentId": "amzn1.dv.gti.75fe3a75-b8fe-4499-8100-5c9424344840",
              "vodType": "EPISODE", "name": "Fixture Episode",
              "images": {"cover": "https://media.example/cover.jpg"},
              "description": {"synopsis": "Fixture synopsis", "contentLengthInSeconds": 846},
              "publicReleaseDateUTC": 1644710400000,
              "audioTracks": ["Hindi"],
              "seasonId": "amzn1.dv.gti.20331016-d9b9-4968-b991-c89fa4927a36",
              "seriesId": "amzn1.dv.gti.56521d46-b040-4fd5-872e-3e70476a04b0",
              "seriesName": "Fixture Series", "seasonNumber": 3, "episodeNumber": 2,
              "timecode": {"endCreditsTime": 800000}}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val episode = AmazonMiniTvIE(http(transfer()))
        assertTrue(episode.suitable(episodeUrl))
        assertTrue(episode.suitable("amazonminitv:amzn1.dv.gti.280d2564-584f-452f-9c98-7baf906e01ab"))
        assertTrue(episode.suitable("amazonminitv:280d2564-584f-452f-9c98-7baf906e01ab"))
        assertFalse(episode.suitable(seasonUrl))
        assertFalse(episode.suitable("https://www.example.com/minitv/tp/abc"))

        val season = AmazonMiniTvSeasonIE(http(transfer()))
        assertTrue(season.suitable(seasonUrl))
        assertTrue(season.suitable("amazonminitv:season:0aa996eb-6a1b-4886-a342-387fbd2f1db0"))
        assertFalse(season.suitable(seriesUrl))

        val series = AmazonMiniTvSeriesIE(http(transfer()))
        assertTrue(series.suitable(seriesUrl))
        assertTrue(series.suitable("amazonminitv:series:56521d46-b040-4fd5-872e-3e70476a04b0"))
        assertFalse(series.suitable(seasonUrl))
    }

    // ---------------------------------------------------------------- episode

    @Test
    fun episodeYieldsBothManifestRowsAndMetadata() = runTest {
        val info = AmazonMiniTvIE(http(transfer(prsRoute, contentRoute))).extract(episodeUrl)
        assertEquals("amzn1.dv.gti.75fe3a75-b8fe-4499-8100-5c9424344840", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture synopsis", info.description)
        assertEquals(846.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("dash", info.formats[1].formatId)
        assertEquals("https://media.example/manifest.mpd", info.formats[1].url)
        assertEquals("mpd", info.formats[1].protocol)
        assertEquals(1, info.thumbnails.size)
        assertEquals("cover", info.thumbnails[0].id)
        assertEquals(1, info.chapters.size)
        assertEquals(800.0, info.chapters[0].startTime)
        assertEquals("End Credits", info.chapters[0].title)
    }

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = episodeUrl,
            infoDict = mapOf(
                "id" to Expect.Value("amzn1.dv.gti.75fe3a75-b8fe-4499-8100-5c9424344840"),
                "title" to Expect.Value("Fixture Episode"),
                "duration" to Expect.Value(846.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(prsRoute, contentRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> AmazonMiniTvIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun apiErrorBecomesTypedUnavailable() = runTest {
        val errorRoute = FixtureRoute(
            urlPattern = "https://www.amazon.in/minitv/api/web/graphql",
            method = "POST",
            contentType = "application/json",
            body = """{"errors": [{"message": "Fixture refusal"}]}""",
        )
        val error = runCatching {
            AmazonMiniTvIE(http(transfer(prsRoute, errorRoute))).extract(episodeUrl)
        }.exceptionOrNull()
        assertIs<ExtractionError.Unavailable>(error)
        assertTrue(error.message?.contains("MiniTV said: Fixture refusal") == true)
    }

    // ------------------------------------------------------- season and series

    @Test
    fun seasonYieldsTheEpisodeEntries() = runTest {
        val seasonRoute = FixtureRoute(
            urlPattern = "https://www.amazon.in/minitv/api/web/graphql",
            method = "POST",
            contentType = "application/json",
            body = """
                {"data": {"getEpisodes": {"episodes": [
                  {"contentId": "amzn1.dv.gti.aaaa-1"},
                  {"contentId": "amzn1.dv.gti.bbbb-2"}]}}}
            """.trimIndent(),
        )
        val info = AmazonMiniTvSeasonIE(http(transfer(seasonRoute))).extract(seasonUrl)
        assertEquals("amzn1.dv.gti.0aa996eb-6a1b-4886-a342-387fbd2f1db0", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("amazonminitv:amzn1.dv.gti.aaaa-1", info.entries[0].url)
        assertEquals("amazonminitv:amzn1.dv.gti.bbbb-2", info.entries[1].url)
    }

    @Test
    fun seriesYieldsTheSeasonEntries() = runTest {
        val seriesRoute = FixtureRoute(
            urlPattern = "https://www.amazon.in/minitv/api/web/graphql",
            method = "POST",
            contentType = "application/json",
            body = """
                {"data": {"getSeasons": {"seasons": [
                  {"seasonId": "amzn1.dv.gti.cccc-3"}]}}}
            """.trimIndent(),
        )
        val info = AmazonMiniTvSeriesIE(http(transfer(seriesRoute))).extract(seriesUrl)
        assertEquals("amzn1.dv.gti.56521d46-b040-4fd5-872e-3e70476a04b0", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("amazonminitv:season:amzn1.dv.gti.cccc-3", info.entries[0].url)
    }
}
