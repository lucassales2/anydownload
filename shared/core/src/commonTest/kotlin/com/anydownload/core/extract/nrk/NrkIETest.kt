package com.anydownload.core.extract.nrk

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
 * Fixture cases for the NRK subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class NrkIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val uuid = "ecc1b952-96dc-4a98-81b9-5296dc7a98d9"

    private fun manifestJson(playability: String = "playable", messageType: String? = null): String =
        if (playability == "nonPlayable") {
            """{"id": "$uuid", "playability": "nonPlayable",
                "nonPlayable": {"messageType": "$messageType"}}"""
        } else {
            """
                {"id": "$uuid", "playability": "playable",
                 "playable": {
                   "duration": "00:04:22",
                   "assets": [{"url": "https://media.example/hls/master.m3u8?adap=large&bw_low=100",
                               "format": "HLS"}],
                   "subtitles": [{"webVtt": "https://media.example/subs.vtt",
                                  "language": "nb", "type": "nor"}]
                 },
                 "availability": {"onDemand": {"from": "2014-03-25T12:00:00Z"}}}
            """.trimIndent()
        }

    private val metadataJson = """
        {"preplay": {
           "titles": {"title": "Fixture NRK Title", "subtitle": "Fixture subtitle"},
           "description": "Fixture description",
           "poster": {"images": [{"url": "https://img.example/poster.jpg",
                                  "pixelWidth": 1280, "pixelHeight": 720}]}
         },
         "duration": "00:04:22",
         "legalAge": {"body": {"rating": {"code": "6"}}}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NRKIE(http(transfer())) to "nrk:$uuid",
            NRKIE(http(transfer())) to "https://v8-psapi.nrk.no/mediaelement/$uuid",
            NRKTVIE(http(transfer())) to "https://tv.nrk.no/program/MDDP12000117",
            NRKTVEpisodeIE(http(transfer())) to "https://tv.nrk.no/serie/hellums-kro/sesong/1/episode/2",
            NRKTVSeasonIE(http(transfer())) to "https://tv.nrk.no/serie/backstage/sesong/1",
            NRKTVSeasonIE(http(transfer())) to "https://tv.nrk.no/serie/lindmo/2016",
            NRKTVSeriesIE(http(transfer())) to "https://tv.nrk.no/serie/backstage",
            NRKTVDirekteIE(http(transfer())) to "https://tv.nrk.no/direkte/nrk1",
            NRKRadioPodkastIE(http(transfer())) to
                "https://radio.nrk.no/podkast/ulrikkes_univers/l_96f4f1b0-de54-4e6a-b4f1-b0de54fe6af8",
            NRKPlaylistIE(http(transfer())) to "https://www.nrk.no/troms/fixture-article-1.12270763",
            NRKTVEpisodesIE(http(transfer())) to "https://tv.nrk.no/program/episodes/nytt-paa-nytt/69031",
            NRKSkoleIE(http(transfer())) to "https://www.nrk.no/skole/?page=search&q=&mediaId=14099",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NRKTVSeriesIE(http(transfer())).suitable("https://tv.nrk.no/serie/backstage/sesong/1"))
        assertFalse(NRKPlaylistIE(http(transfer())).suitable("https://www.nrk.no/video/fixture_150533"))
    }

    // ----------------------------------------------------------------- NRKIE

    @Test
    fun nrkPlaybackMapsFormatsSubtitlesAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://psapi.nrk.no/playback/manifest/program/$uuid*",
                contentType = "application/json",
                body = manifestJson(),
            ),
            FixtureRoute(
                urlPattern = "https://psapi.nrk.no/playback/metadata/program/$uuid*",
                contentType = "application/json",
                body = metadataJson,
            ),
        )
        val info = NRKIE(http(transfer)).extract("nrk:$uuid")

        assertEquals(uuid, info.id)
        assertEquals("Fixture NRK Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(262.0, info.duration)
        assertEquals(6, info.ageLimit)
        assertEquals("20140325", info.uploadDate)
        assertEquals("https://img.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(1280L, info.thumbnails.single().width)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/hls/master.m3u8?s=0", info.formats.single().url)
        assertEquals("nb-nor", info.subtitles.single().language)
        assertTrue(
            transfer.requests.any {
                it.headers["accept"]?.startsWith("application/vnd.nrk.psapi+json") == true
            },
        )
    }

    @Test
    fun nrkGeoBlockedManifestFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://psapi.nrk.no/playback/manifest/program/$uuid*",
                contentType = "application/json",
                body = manifestJson(playability = "nonPlayable", messageType = "ProgramIsGeoBlocked"),
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            NRKIE(http(transfer)).extract("nrk:$uuid")
        }
    }

    @Test
    fun nrkTvRedispatchesToTheNrkId() = runTest {
        val info = NRKTVIE(http(transfer())).extract("https://tv.nrk.no/program/MDDP12000117")
        assertEquals("MDDP12000117", info.id)
        assertEquals("nrk:MDDP12000117", info.redirectUrl)
    }

    // ------------------------------------------------------------- listings

    @Test
    fun seasonListsEmbeddedEpisodesAcrossPages() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://psapi.nrk.no/tv/catalog/series/backstage/seasons/1?pageSize=50",
                contentType = "application/json",
                body = """
                    {"titles": {"title": "Sesong 1"},
                     "_embedded": {"episodes": {
                       "_embedded": {"episodes": [{"prfId": "MUHH36005220"}, {"episodeId": "MUHH36005221"}]},
                       "_links": {"next": {"href": "tv/catalog/series/backstage/seasons/1?page=2"}}
                     }}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://psapi.nrk.no/tv/catalog/series/backstage/seasons/1?page=2",
                contentType = "application/json",
                body = """
                    {"_embedded": {"episodes": {"_embedded": {"episodes": [{"prfId": "MUHH36005222"}]}}}}
                """.trimIndent(),
            ),
        )
        val info = NRKTVSeasonIE(http(transfer)).extract("https://tv.nrk.no/serie/backstage/sesong/1")
        assertEquals("backstage/1", info.id)
        assertEquals("Sesong 1", info.title)
        assertEquals(3, info.entries.size)
        assertEquals("https://tv.nrk.no/program/MUHH36005222", info.entries[2].url)
    }

    @Test
    fun seriesListsInstalments() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://psapi.nrk.no/tv/catalog/series/backstage?embeddedInstalmentsPageSize=50",
                contentType = "application/json",
                body = """
                    {"titles": {"title": "Backstage", "subtitle": "Fixture series description"},
                     "_embedded": {"instalments": {
                       "_embedded": {"instalments": [{"prfId": "MUHH36005220"}]}
                     }}}
                """.trimIndent(),
            ),
        )
        val info = NRKTVSeriesIE(http(transfer)).extract("https://tv.nrk.no/serie/backstage")
        assertEquals("backstage", info.id)
        assertEquals("Backstage", info.title)
        assertEquals("Fixture series description", info.description)
        assertEquals(1, info.entries.size)
    }

    @Test
    fun playlistScansTheRichVideoIds() = runTest {
        val url = "https://www.nrk.no/troms/fixture-article-1.12270763"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture Article">
            <meta property="og:description" content="Fixture article description">
            </head><body>
            <div class="rich something" data-video-id="150533"></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NRKPlaylistIE(http(transfer)).extract(url)
        assertEquals("fixture-article-1.12270763", info.id)
        assertEquals("Fixture Article", info.title)
        assertEquals("https://tv.nrk.no/program/150533", info.entries.single().url)
    }

    @Test
    fun skoleRedispatchesThroughThePsId() = runTest {
        val url = "https://www.nrk.no/skole/?page=search&q=&mediaId=14099"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://nrkno-skole-prod.kube.nrk.no/skole/api/media/14099",
                contentType = "application/json",
                body = """{"psId": "6021"}""",
            ),
        )
        val info = NRKSkoleIE(http(transfer)).extract(url)
        assertEquals("6021", info.id)
        assertEquals("nrk:6021", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun nrkIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "nrk:$uuid",
            infoDict = mapOf(
                "id" to Expect.Value(uuid),
                "title" to Expect.Value("Fixture NRK Title"),
                "duration" to Expect.Value(262.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://psapi.nrk.no/playback/manifest/program/$uuid*",
                    contentType = "application/json",
                    body = manifestJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://psapi.nrk.no/playback/metadata/program/$uuid*",
                    contentType = "application/json",
                    body = metadataJson,
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NRKIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun seasonIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://tv.nrk.no/serie/backstage/sesong/1",
            infoDict = mapOf(
                "id" to Expect.Value("backstage/1"),
                "title" to Expect.Value("Sesong 1"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://psapi.nrk.no/tv/catalog/series/backstage/seasons/1?pageSize=50",
                    contentType = "application/json",
                    body = """
                        {"titles": {"title": "Sesong 1"},
                         "_embedded": {"episodes": {"_embedded": {"episodes": [{"prfId": "MUHH36005220"}]}}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NRKTVSeasonIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
