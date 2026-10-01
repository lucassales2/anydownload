package com.anydownload.core.extract.tubetugraz

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
 * Fixture cases for the TU Graz tube subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class TubetugrazIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val episodeId = "f2634392-e40e-4ac7-9ddc-47764aa23d40"
    private val seriesId = "0e6351b7-c372-491e-8a49-2c9b7e21c5a6"
    private val episodeUrl = "https://tube.tugraz.at/paella/ui/watch.html?id=$episodeId"
    private val seriesUrl = "https://tube.tugraz.at/paella/ui/browse.html?series=$seriesId"

    private val episodeJson = """
        {"search-results": {"result": {"id": "$episodeId", "mediapackage": {
          "title": "#6 (23.11.2017)", "seriestitle": "[INB03001UF] Fixture",
          "series": "b1192fff-2aa7-4bf0-a5cf-7b15c3bd3b34", "duration": 3295818,
          "creators": {"creator": ["Safran C"]},
          "media": {"track": [{"type": "presentation", "transport": "https",
            "url": "https://media.example/video.mp4",
            "video": {"bitrate": 1500000, "framerate": 25, "resolution": "1920x1080"},
            "audio": {"bitrate": 128000}}]}
        }}}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val episode = TubeTuGrazIE(http(transfer()))
        assertTrue(episode.suitable(episodeUrl))
        assertTrue(episode.suitable("https://tube.tugraz.at/portal/watch/ab28ec60-8cbe-4f1a-9b96-a95add56c612"))
        assertFalse(episode.suitable(seriesUrl))

        val series = TubeTuGrazSeriesIE(http(transfer()))
        assertTrue(series.suitable(seriesUrl))
        assertFalse(series.suitable(episodeUrl))
        assertFalse(series.suitable("https://www.example.com/paella/ui/browse.html?series=$seriesId"))
    }

    // ---------------------------------------------------------------- episode

    @Test
    fun episodeYieldsTheTrackRowAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://tube.tugraz.at/search/episode.json?id=$episodeId&limit=1",
                contentType = "application/json",
                body = episodeJson,
            ),
        )
        val info = TubeTuGrazIE(http(transfer)).extract(episodeUrl)
        assertEquals(episodeId, info.id)
        assertEquals("#6 (23.11.2017)", info.title)
        assertEquals("Safran C", info.uploader)
        assertEquals(3295818.0, info.duration)
        assertEquals("[INB03001UF] Fixture", info.channel)
        assertEquals("b1192fff-2aa7-4bf0-a5cf-7b15c3bd3b34", info.channelId)
        assertEquals(1, info.formats.size)
        assertEquals("presentation", info.formats[0].formatNote)
        assertEquals(128.0, info.formats[0].abr)
        assertEquals(1500.0, info.formats[0].vbr)
        assertEquals(25.0, info.formats[0].fps)
        assertEquals(1920L, info.formats[0].width)
        assertEquals(1080L, info.formats[0].height)
    }

    @Test
    fun wowzaFallbacksAreProbedAndCarryTheFormatType() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://tube.tugraz.at/search/episode.json?id=$episodeId&limit=1",
                contentType = "application/json",
                body = episodeJson,
            ),
            FixtureRoute(
                urlPattern = "https://wowza.tugraz.at/matterhorn_engage/smil:engage-player_${episodeId}_presentation.smil/playlist.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                body = "#EXTM3U",
            ),
            FixtureRoute(
                urlPattern = "https://wowza.tugraz.at/matterhorn_engage/smil:engage-player_${episodeId}_presenter.smil/manifest_mpm4sav_mvlist.mpd",
                contentType = "application/dash+xml",
                body = "<MPD></MPD>",
            ),
        )
        val info = TubeTuGrazIE(http(transfer)).extract(episodeUrl)
        assertEquals(3, info.formats.size)
        assertEquals("hls", info.formats[1].formatId)
        assertEquals("presentation", info.formats[1].formatNote)
        assertEquals("dash", info.formats[2].formatId)
        assertEquals("presenter", info.formats[2].formatNote)
        assertEquals(-2, info.formats[2].preference)
    }

    // ----------------------------------------------------------------- series

    @Test
    fun seriesYieldsTheEpisodeEntriesAndTitle() = runTest {
        val episodeList = """
            {"search-results": {"result": [
              {"id": "ee17ce5d-34e2-48b7-a76a-fed148614e11",
               "mediapackage": {"title": "#4 Detailprojekt", "seriestitle": "[209351] Strassenwesen",
                 "duration": 6127024}}
            ]}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://tube.tugraz.at/search/episode.json?sid=$seriesId",
                contentType = "application/json",
                body = episodeList,
            ),
            FixtureRoute(
                urlPattern = "https://tube.tugraz.at/series/series.json?seriesId=$seriesId&count=1&sort=TITLE",
                contentType = "application/json",
                body = """{"catalogs": [{"http://purl.org/dc/terms/": {"title": [{"value": "[209351] Strassenwesen"}]}}]}""",
            ),
        )
        val info = TubeTuGrazSeriesIE(http(transfer)).extract(seriesUrl)
        assertEquals(seriesId, info.id)
        assertEquals("[209351] Strassenwesen", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "https://tube.tugraz.at/paella/ui/watch.html?id=ee17ce5d-34e2-48b7-a76a-fed148614e11",
            info.entries[0].url,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = episodeUrl,
            infoDict = mapOf(
                "id" to Expect.Value(episodeId),
                "title" to Expect.Value("#6 (23.11.2017)"),
                "uploader" to Expect.Value("Safran C"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://tube.tugraz.at/search/episode.json?id=$episodeId&limit=1",
                    contentType = "application/json",
                    body = episodeJson,
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TubeTuGrazIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun seriesIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = seriesUrl,
            infoDict = mapOf(
                "id" to Expect.Value(seriesId),
                "title" to Expect.Value("[209351] Strassenwesen"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://tube.tugraz.at/search/episode.json?sid=$seriesId",
                    contentType = "application/json",
                    body = """
                        {"search-results": {"result": [
                          {"id": "ee17ce5d-34e2-48b7-a76a-fed148614e11",
                           "mediapackage": {"title": "#4 Detailprojekt", "seriestitle": "[209351] Strassenwesen"}}
                        ]}}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://tube.tugraz.at/series/series.json?seriesId=$seriesId&count=1&sort=TITLE",
                    contentType = "application/json",
                    body = """{"catalogs": [{"http://purl.org/dc/terms/": {"title": [{"value": "[209351] Strassenwesen"}]}}]}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TubeTuGrazSeriesIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
