package com.anydownload.core.extract.nba

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
 * Fixture cases for the NBA subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class NbaIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NBAWatchEmbedIE(http(transfer())) to "https://watch.nba.com/embed?id=659395",
            NBAWatchIE(http(transfer())) to "https://www.nba.com/watch/video/teams/cavaliers/2012/10/15/sloan121015mov-2249106",
            NBAWatchCollectionIE(http(transfer())) to "https://watch.nba.com/list/collection/season-preview-2020",
            NBAEmbedIE(http(transfer())) to "https://secure.nba.com/assets/amp/include/video/iframe.html?contentId=fixture-id&team=bulls",
            NBAIE(http(transfer())) to "https://www.nba.com/bulls/video/teams/bulls/2020/12/04/3478774/fixture-video",
            NBAChannelIE(http(transfer())) to "https://www.nba.com/blazers/video/channel/summer_league",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NBAChannelIE(http(transfer())).suitable("https://www.nba.com/blazers/video/teams/bulls/x"))
    }

    // ------------------------------------------------------------- watch video

    @Test
    fun watchVideoYieldsHlsAndHttpFormats() = runTest {
        val url = "https://watch.nba.com/embed?id=659395"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://neulionscnbav2-a.akamaihd.net/solr/nbad_program/usersearch*",
                contentType = "application/json",
                body = """
                    {"response": {"docs": [{"pid": 659395, "name": "Fixture Watch Video",
                      "description": "Fixture description", "runtime": 181,
                      "releaseDate": "2012-12-04T00:00:00Z", "image": "thumb.jpg"}]}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://watch.nba.com/service/publishpoint*",
                contentType = "application/json",
                body = """{"path": "https://media.example/hls/master_iphone.m3u8"}""",
            ),
        )
        val info = NBAWatchEmbedIE(http(transfer)).extract(url)
        assertEquals("659395", info.id)
        assertEquals("Fixture Watch Video", info.title)
        assertEquals(181.0, info.duration)
        assertEquals("20121204", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("https://media.example/hls/master.m3u8", info.formats[0].url)
        assertEquals("https://media.example/hls/master", info.formats[1].url)
    }

    @Test
    fun watchVideoWithoutPublishpointFailsTyped() = runTest {
        val url = "https://watch.nba.com/embed?id=659395"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://neulionscnbav2-a.akamaihd.net/solr/nbad_program/usersearch*",
                contentType = "application/json",
                body = """{"response": {"docs": [{"pid": 659395, "name": "Fixture Watch Video"}]}}""",
            ),
            FixtureRoute(
                urlPattern = "https://watch.nba.com/service/publishpoint*",
                contentType = "application/json",
                body = """{}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            NBAWatchEmbedIE(http(transfer)).extract(url)
        }
    }

    // ----------------------------------------------------------- watch redirect

    @Test
    fun watchCollectionParamRedirectsToTheCollection() = runTest {
        val url = "https://watch.nba.com/video/top-100-dunks?plsrc=nba&collection=2019-20-season-highlights"
        val info = NBAWatchIE(http(transfer())).extract(url)
        assertEquals("2019-20-season-highlights", info.id)
        assertEquals(
            "https://www.nba.com/watch/list/collection/2019-20-season-highlights",
            info.redirectUrl,
        )
    }

    @Test
    fun collectionApiYieldsEntries() = runTest {
        val url = "https://watch.nba.com/list/collection/season-preview-2020"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://content-api-prod.nba.com/public/1/endeavor/video-list/collection/season-preview-2020?count=100&page=1",
                contentType = "application/json",
                body = """
                    {"results": {"videos": [{"program": {"id": "program-1", "title": "Fixture Program",
                      "seoName": "fixture-program"}}]}}
                """.trimIndent(),
            ),
        )
        val info = NBAWatchCollectionIE(http(transfer)).extract(url)
        assertEquals("season-preview-2020", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.nba.com/watch/video/fixture-program", info.entries.single().url)
    }

    // ------------------------------------------------------------ typed walls

    @Test
    fun accountApiClassesFailTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            NBAEmbedIE(http(transfer())).extract(
                "https://secure.nba.com/assets/amp/include/video/iframe.html?contentId=x&team=bulls",
            )
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            NBAChannelIE(http(transfer())).extract("https://www.nba.com/blazers/video/channel/summer_league")
        }
    }

    @Test
    fun embedWithoutTeamRedirectsToTheWatchPage() = runTest {
        val url = "https://secure.nba.com/assets/amp/include/video/iframe.html?contentId=fixture-id"
        val info = NBAEmbedIE(http(transfer())).extract(url)
        assertEquals("https://watch.nba.com/video/fixture-id", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun watchEmbedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://watch.nba.com/embed?id=659395",
            infoDict = mapOf(
                "id" to Expect.Value("659395"),
                "title" to Expect.Value("Fixture Watch Video"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://neulionscnbav2-a.akamaihd.net/solr/nbad_program/usersearch*",
                    contentType = "application/json",
                    body = """{"response": {"docs": [{"pid": 659395, "name": "Fixture Watch Video"}]}}""",
                ),
                FixtureRoute(
                    urlPattern = "https://watch.nba.com/service/publishpoint*",
                    contentType = "application/json",
                    body = """{"path": "https://media.example/hls/master_iphone.m3u8"}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NBAWatchEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
