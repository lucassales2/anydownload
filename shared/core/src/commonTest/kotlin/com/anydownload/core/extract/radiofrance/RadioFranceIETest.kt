package com.anydownload.core.extract.radiofrance

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
 * Fixture cases for the Radio France subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class RadioFranceIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RadioFranceIE(http(transfer())) to "http://maison.radiofrance.fr/radiovisions/one-one",
            FranceCultureIE(http(transfer())) to
                "https://www.radiofrance.fr/franceculture/podcasts/fixture/fixture-episode-8440487",
            RadioFranceLiveIE(http(transfer())) to "https://www.radiofrance.fr/franceinter/",
            RadioFranceLiveIE(http(transfer())) to "https://www.radiofrance.fr/mouv/radio-rock",
            RadioFrancePodcastIE(http(transfer())) to "https://www.radiofrance.fr/fip/podcasts/fixture-podcast",
            RadioFranceProfileIE(http(transfer())) to "https://www.radiofrance.fr/personnes/fixture-person",
            RadioFranceProgramScheduleIE(http(transfer())) to "https://www.radiofrance.fr/franceinter/grille-programmes",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RadioFranceProfileIE(http(transfer())).suitable("https://www.radiofrance.fr/franceinter/"))
    }

    // -------------------------------------------------------------- episode

    @Test
    fun franceCultureEpisodeYieldsTheAudioObject() = runTest {
        val url = "https://www.radiofrance.fr/franceculture/podcasts/fixture/fixture-episode-8440487"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture Episode">
            <meta property="og:image" content="https://media.example/episode.jpg">
            </head><body>
            <h1 itemprop="name">Fixture Episode</h1>
            <span class="author">Fixture Author</span>
            <script>{"@type": "AudioObject", "contentUrl": "https://media.example/audio/episode.mp3",
                     "encodingFormat": "mp3", "duration": "00:45:50", "datePublished": "2022-05-14"}</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = FranceCultureIE(http(transfer)).extract(url)
        assertEquals("8440487", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture Author", info.uploader)
        assertEquals("20220514", info.uploadDate)
        assertEquals(2750.0, info.duration)
        assertEquals("https://media.example/audio/episode.mp3", info.formats.single().url)
    }

    // ----------------------------------------------------------------- live

    @Test
    fun liveApiYieldsTheStream() = runTest {
        val url = "https://www.radiofrance.fr/franceinter/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.radiofrance.fr/franceinter/api/live",
                contentType = "application/json",
                body = """
                    {"now": {"media": {"sources": [{"url": "https://media.example/live/master.m3u8",
                                                   "format": "hls"}]}},
                     "visual": {"legend": "Fixture Live"}}
                """.trimIndent(),
            ),
        )
        val info = RadioFranceLiveIE(http(transfer)).extract(url)
        assertEquals("franceinter", info.id)
        assertEquals("Fixture Live", info.title)
        assertEquals(true, info.isLive)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // ------------------------------------------------------------- playlists

    @Test
    fun podcastPlaylistYieldsEntries() = runTest {
        val url = "https://www.radiofrance.fr/fip/podcasts/fixture-podcast"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.radiofrance.fr/api/v2.1/path?value=/fip/podcasts/fixture-podcast",
                contentType = "application/json",
                body = """
                    {"content": {"id": "143dff38-e956-4a5d-8576-1c0b7242b99e", "title": "Fixture Podcast",
                                 "standFirst": "Fixture description",
                                 "visual": {"src": "https://media.example/podcast.jpg"},
                                 "expressions": {"items": [
                                    {"path": "fip/podcasts/fixture-podcast/episode-1", "title": "Episode 1"}],
                                   "pagination": {}}}}
                """.trimIndent(),
            ),
        )
        val info = RadioFrancePodcastIE(http(transfer)).extract(url)
        assertEquals("143dff38-e956-4a5d-8576-1c0b7242b99e", info.id)
        assertEquals("Fixture Podcast", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.radiofrance.fr/fip/podcasts/fixture-podcast/episode-1", info.entries.single().url)
    }

    @Test
    fun programGridYieldsEntries() = runTest {
        val url = "https://www.radiofrance.fr/franceinter/grille-programmes"
        val page = """
            <html><body><script>
            const data = [{"data": {"grid": {"date": 1676592000, "steps": [
              {"expression": {"path": "franceinter/emission/fixture", "title": "Fixture Show"}}]}}}];
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = RadioFranceProgramScheduleIE(http(transfer)).extract(url)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.radiofrance.fr/franceinter/emission/fixture", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.radiofrance.fr/franceculture/podcasts/fixture/fixture-episode-8440487"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("8440487"),
                "title" to Expect.Value("Fixture Episode"),
                "duration" to Expect.Value(2750.0),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><body><h1 itemprop="name">Fixture Episode</h1>
                        <script>{"@type": "AudioObject", "contentUrl": "https://media.example/audio/episode.mp3",
                                 "encodingFormat": "mp3", "duration": "00:45:50"}</script>
                        </body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> FranceCultureIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
