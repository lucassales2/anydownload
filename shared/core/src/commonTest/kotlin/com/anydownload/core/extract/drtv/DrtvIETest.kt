package com.anydownload.core.extract.drtv

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
 * Fixture cases for the DR TV subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class DrtvIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            DRTVIE(http(transfer())) to "https://www.dr.dk/drtv/se/frank-and-kastaniegaarden_71769",
            DRTVIE(http(transfer())) to "https://www.dr.dk/tv/se/boern/ultra/klassen-ultra/klassen-darlig-taber-10",
            DRTVLiveIE(http(transfer())) to "https://www.dr.dk/tv/live/dr1",
            DRTVSeasonIE(http(transfer())) to "https://www.dr.dk/drtv/saeson/frank-and-kastaniegaarden_9008",
            DRTVSeriesIE(http(transfer())) to "https://www.dr.dk/drtv/serie/frank-and-kastaniegaarden_6954",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(DRTVLiveIE(http(transfer())).suitable("https://www.dr.dk/drtv/serie/x_1"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun videoPagesFailTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            DRTVIE(http(transfer())).extract("https://www.dr.dk/drtv/se/frank-and-kastaniegaarden_71769")
        }
    }

    // -------------------------------------------------------------------- live

    @Test
    fun liveChannelYieldsHlsFormats() = runTest {
        val url = "https://www.dr.dk/tv/live/dr1"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.dr.dk/mu-online/api/1.0/channel/dr1",
                contentType = "application/json",
                body = """
                    {"Title": "DR1", "PrimaryImageUri": "https://media.example/dr1.jpg",
                     "StreamingServers": [{"Server": "https://media.example", "LinkType": "HLS",
                       "Qualities": [{"Streams": [{"Stream": "hls/master.m3u8"}]}]}]}
                """.trimIndent(),
            ),
        )
        val info = DRTVLiveIE(http(transfer)).extract(url)
        assertEquals("dr1", info.id)
        assertEquals("DR1", info.title)
        assertEquals(true, info.isLive)
        assertEquals("https://media.example/hls/master.m3u8?b=", info.formats.single().url)
        assertEquals("https://media.example/dr1.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- listings

    @Test
    fun seasonListingYieldsEpisodeEntries() = runTest {
        val url = "https://www.dr.dk/drtv/saeson/frank-and-kastaniegaarden_9008"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://production-cdn.dr-massive.com/api/page*path=/saeson/frank-and-kastaniegaarden_9008",
                contentType = "application/json",
                body = """
                    {"entries": [{"item": {"title": "Fixture Season", "seasonNumber": 2008,
                      "episodes": {"items": [{"id": "ep-1", "title": "Fixture Episode",
                                               "path": "/drtv/se/fixture-episode_71769"}]}}}]}
                """.trimIndent(),
            ),
        )
        val info = DRTVSeasonIE(http(transfer)).extract(url)
        assertEquals("9008", info.id)
        assertEquals("Fixture Season", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.dr.dk/drtv/drtv/se/fixture-episode_71769", info.entries.single().url)
    }

    @Test
    fun seriesListingYieldsSeasonEntries() = runTest {
        val url = "https://www.dr.dk/drtv/serie/frank-and-kastaniegaarden_6954"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://production-cdn.dr-massive.com/api/page*path=/serie/frank-and-kastaniegaarden_6954",
                contentType = "application/json",
                body = """
                    {"entries": [{"item": {"title": "Fixture Series",
                      "show": {"seasons": {"items": [{"id": "9008", "title": "Season 2008",
                                                     "path": "/drtv/saeson/fixture_9008"}]}}}}]}
                """.trimIndent(),
            ),
        )
        val info = DRTVSeriesIE(http(transfer)).extract(url)
        assertEquals("6954", info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.dr.dk/drtv/drtv/saeson/fixture_9008", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun liveIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.dr.dk/tv/live/dr1"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("dr1"),
                "title" to Expect.Value("DR1"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.dr.dk/mu-online/api/1.0/channel/dr1",
                    contentType = "application/json",
                    body = """
                        {"Title": "DR1",
                         "StreamingServers": [{"Server": "https://media.example", "LinkType": "HLS",
                           "Qualities": [{"Streams": [{"Stream": "hls/master.m3u8"}]}]}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> DRTVLiveIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
