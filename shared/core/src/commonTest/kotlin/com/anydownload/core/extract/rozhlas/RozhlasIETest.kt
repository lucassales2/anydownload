package com.anydownload.core.extract.rozhlas

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
 * Fixture cases for the Rozhlas subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class RozhlasIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val audioId = "3421320"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RozhlasIE(http(transfer())) to "http://prehravac.rozhlas.cz/audio/$audioId",
            RozhlasVltavaIE(http(transfer())) to "https://wave.rozhlas.cz/fixture-article-8891337",
            MujRozhlasIE(http(transfer())) to "https://www.mujrozhlas.cz/vykopavky/fixture-episode",
            MujRozhlasIE(http(transfer())) to "https://www.mujrozhlas.cz/nespavci",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RozhlasIE(http(transfer())).suitable("https://wave.rozhlas.cz/x-1"))
    }

    // ------------------------------------------------------------- prehravac

    @Test
    fun prehravacPageYieldsMp3() = runTest {
        val url = "http://prehravac.rozhlas.cz/audio/$audioId"
        val page = """
            <html><head><meta property="og:title" content="Radio Wave - Fixture Title"></head>
            <body><h3>Fixture Title</h3><p title="Fixture description">x</p>
            <div id="player-track" data-duration="1574"></div></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = RozhlasIE(http(transfer)).extract(url)
        assertEquals(audioId, info.id)
        assertEquals("Fixture Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1574.0, info.duration)
        assertEquals("http://media.rozhlas.cz/_audio/$audioId.mp3", info.formats.single().url)
    }

    // ---------------------------------------------------------------- vltava

    @Test
    fun vltavaPlayerYieldsEntries() = runTest {
        val url = "https://wave.rozhlas.cz/fixture-article-8891337"
        val player = """
            {"data": {"embedId": "8891337", "series": {"title": "Fixture Series"},
             "playlist": [{"title": "Fixture episode description", "duration": 1574,
               "meta": {"ga": {"contentId": "10520988", "contentName": "Fixture Episode",
                               "contentAuthor": "Fixture Author", "contentCreator": "radio-wave"}},
               "audioLinks": [{"url": "https://media.example/audio/episode.mp3", "variant": "mp3",
                               "bitrate": 128}]}]}}
        """.trimIndent()
        val page = """
            <html><body><div class="mujRozhlasPlayer" data-player='$player'></div></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = RozhlasVltavaIE(http(transfer)).extract(url)
        assertEquals("8891337", info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("10520988", info.entries.single().id)
    }

    // ------------------------------------------------------------- mujrozhlas

    @Test
    fun mujRozhlasEpisodeYieldsFormats() = runTest {
        val url = "https://www.mujrozhlas.cz/vykopavky/fixture-episode"
        val page = """
            <html><body><script>var dl = {"siteEntityBundle": "episode", "contentId": "10787730"};</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://api.mujrozhlas.cz/episodes/10787730",
                contentType = "application/json",
                body = """
                    {"data": {"attributes": {"title": "Fixture Episode", "description": "Fixture description",
                       "since": "2023-05-24T00:00:00Z",
                       "audioLinks": [{"url": "https://media.example/audio/episode.mp3", "variant": "mp3",
                                       "bitrate": 128},
                                      {"url": "https://media.example/hls/master.m3u8", "variant": "hls"}]},
                     "meta": {"ga": {"contentId": "10787730"}}}}
                """.trimIndent(),
            ),
        )
        val info = MujRozhlasIE(http(transfer)).extract(url)
        assertEquals("10787730", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("20230524", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("mp3", info.formats[0].ext)
        assertEquals("m3u8_native", info.formats[1].protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun prehravacIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://prehravac.rozhlas.cz/audio/$audioId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(audioId),
                "title" to Expect.Value("Fixture Title"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = "<html><body><h3>Fixture Title</h3><p title=\"Fixture description\">x</p>" +
                        "<div id=\"player-track\" data-duration=\"1574\"></div></body></html>",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RozhlasIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
