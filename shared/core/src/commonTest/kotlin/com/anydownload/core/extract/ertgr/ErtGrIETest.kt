package com.anydownload.core.extract.ertgr

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
 * Fixture cases for the ERT subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class ErtGrIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val codename = "monogramma-praxitelis-tzanoylinos"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ERTFlixCodenameIE(http(transfer())) to "ertflix:$codename",
            ERTFlixIE(http(transfer())) to "https://www.ertflix.gr/vod/vod.173258-fixture",
            ERTFlixIE(http(transfer())) to "https://www.ertflix.gr/series/ser.3448-monogramma",
            ERTFlixIE(http(transfer())) to "https://www.ertflix.gr/en/vod/vod.127652-fixture",
            ERTWebtvEmbedIE(http(transfer())) to
                "https://www.ert.gr/webtv/live-uni/vod/dt-uni-vod.php?f=trailers/E2251_FIXTURE.mp4&bgimg=/photos/fixture.jpg",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ERTFlixIE(http(transfer())).suitable("ertflix:$codename"))
    }

    // -------------------------------------------------------------- codename

    @Test
    fun codenameApiYieldsFormats() = runTest {
        val url = "ertflix:$codename"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.app.ertflix.gr/v1/Player/AcquireContent?*codename=$codename*",
                contentType = "application/json",
                body = """
                    {"Result": {"Success": true},
                     "MediaFiles": [{"Id": "1", "RoleCodename": "main", "Formats": [
                       {"Url": "https://media.example/hls/master.m3u8"},
                       {"Url": "https://media.example/video/720.mp4"}]}]}
                """.trimIndent(),
            ),
        )
        val info = ERTFlixCodenameIE(http(transfer)).extract(url)
        assertEquals(codename, info.id)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4", info.formats[1].ext)
    }

    // ------------------------------------------------------------------- vod

    @Test
    fun vodTileRedirectsToTheCodename() = runTest {
        val url = "https://www.ertflix.gr/vod/vod.173258-fixture"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.app.ertflix.gr/v2/Tile/GetTiles?*",
                method = "POST",
                contentType = "application/json",
                body = """
                    {"Result": {"Success": true},
                     "Tiles": [{"Id": "vod.173258", "Codename": "$codename",
                       "Title": "Fixture VOD", "ShortDescription": "Fixture description",
                       "DurationSeconds": 3166, "PublishDate": "2021-12-16T00:00:00Z",
                       "Images": [{"Url": "https://media.example/thumb.jpg", "IsMain": true}]}]}
                """.trimIndent(),
            ),
        )
        val info = ERTFlixIE(http(transfer)).extract(url)
        assertEquals(codename, info.id)
        assertEquals("Fixture VOD", info.title)
        assertEquals(3166.0, info.duration)
        assertEquals("20211216", info.uploadDate)
        assertEquals("ertflix:$codename", info.redirectUrl)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // ---------------------------------------------------------------- series

    @Test
    fun seriesApiYieldsEpisodeEntries() = runTest {
        val url = "https://www.ertflix.gr/series/ser.3448-monogramma"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.app.ertflix.gr/v1/Tile/GetSeriesDetails?*id=ser.3448*",
                contentType = "application/json",
                body = """
                    {"Result": {"Success": true},
                     "Series": {"Title": "Monogramma", "ShortDescription": "Fixture description"},
                     "EpisodeGroups": [{"Title": "Season 1", "Episodes": [
                       {"Codename": "$codename", "Title": "Episode 1"}]}]}
                """.trimIndent(),
            ),
        )
        val info = ERTFlixIE(http(transfer)).extract(url)
        assertEquals("ser.3448", info.id)
        assertEquals("Monogramma", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("ertflix:$codename", info.entries.single().url)
    }

    // ----------------------------------------------------------------- webtv

    @Test
    fun webtvEmbedYieldsHlsAndThumbnail() = runTest {
        val url = "https://www.ert.gr/webtv/live-uni/vod/dt-uni-vod.php?f=trailers/E2251_FIXTURE.mp4&bgimg=/photos/fixture.jpg"
        val info = ERTWebtvEmbedIE(http(transfer())).extract(url)
        assertEquals("trailers/E2251_FIXTURE.mp4", info.id)
        assertEquals("VOD - trailers/E2251_FIXTURE.mp4", info.title)
        assertEquals(
            "https://mediastream.ert.gr/vodedge/_definst_/mp4:dvrorigin/trailers/E2251_FIXTURE.mp4/playlist.m3u8",
            info.formats.single().url,
        )
        assertEquals("https://program.ert.gr/photos/fixture.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun codenameIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "ertflix:$codename"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(codename),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.app.ertflix.gr/v1/Player/AcquireContent?*codename=$codename*",
                    contentType = "application/json",
                    body = """
                        {"Result": {"Success": true},
                         "MediaFiles": [{"Id": "1", "RoleCodename": "main",
                           "Formats": [{"Url": "https://media.example/hls/master.m3u8"}]}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ERTFlixCodenameIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
