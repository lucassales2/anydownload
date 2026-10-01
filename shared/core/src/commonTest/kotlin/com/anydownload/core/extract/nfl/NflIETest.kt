package com.anydownload.core.extract.nfl

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
 * Fixture cases for the NFL subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no secret or token
 * appears.
 */
class NflIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val configId = "11111111-2222-3333-4444-555555555555"

    private fun configScript(body: String) =
        """<script type="application/json" id="video-config-$configId">$body</script>"""

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NFLIE(http(transfer())) to "https://www.nfl.com/videos/fixture-video",
            NFLIE(http(transfer())) to "https://www.chiefs.com/listen/fixture-audio",
            NFLArticleIE(http(transfer())) to "https://www.buffalobills.com/news/fixture-article",
            NFLPlusReplayIE(http(transfer())) to "https://www.nfl.com/plus/games/fixture-game-2022-post-1/1572108",
            NFLPlusEpisodeIE(http(transfer())) to "https://www.nfl.com/plus/episodes/fixture-episode",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NFLIE(http(transfer())).suitable("https://www.nfl.com/plus/episodes/fixture-episode"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoConfigYieldsM3u8Format() = runTest {
        val url = "https://www.nfl.com/videos/fixture-video"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = configScript(
                    """{"live": false, "playlist": [{"id": "fixture-item",
                       "url": "https://media.example/hls/master.m3u8",
                       "title": "Fixture NFL Video", "description": "<p>Fixture description</p>",
                       "imageSrc": "https://media.example/thumb.jpg"}]}""",
                ),
            ),
        )
        val info = NFLIE(http(transfer)).extract(url)
        assertEquals("fixture-item", info.id)
        assertEquals("Fixture NFL Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(false, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun audioItemYieldsAnAudioOnlyFormat() = runTest {
        val url = "https://www.chiefs.com/listen/fixture-audio"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = configScript(
                    """{"playlist": [{"entityId": "fixture-audio-item",
                       "url": "https://media.example/audio/item.mp3", "audio": true,
                       "title": "Fixture NFL Audio"}]}""",
                ),
            ),
        )
        val info = NFLIE(http(transfer)).extract(url)
        assertEquals("fixture-audio-item", info.id)
        assertEquals("mp3", info.formats.single().ext)
        assertEquals("none", info.formats.single().vcodec)
    }

    @Test
    fun mcpIdConfigFailsTyped() = runTest {
        val url = "https://www.nfl.com/videos/fixture-video"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = configScript(
                    """{"playlist": [{"id": "fixture-item", "mcpID": "899441",
                       "url": "https://media.example/hls/master.m3u8"}]}""",
                ),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            NFLIE(http(transfer)).extract(url)
        }
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articleCollectsVideoConfigEntries() = runTest {
        val url = "https://www.buffalobills.com/news/fixture-article"
        val page = """
            <html><head><meta property="og:title" content="Fixture Article Title - Buffalo Bills"></head>
            <body>
            <h1 class="nfl-c-article__title">Fixture Article Title</h1>
            ${configScript("""{"playlist": [{"id": "item-1", "url": "https://media.example/hls/one.m3u8"}]}""")}
            ${configScript("""{"playlist": [{"id": "item-2", "url": "https://media.example/hls/two.m3u8"}]}""")}
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NFLArticleIE(http(transfer)).extract(url)
        assertEquals("Fixture Article Title", info.title)
        assertEquals(2, info.entries.size)
    }

    // ------------------------------------------------------------ typed walls

    @Test
    fun nflPlusClassesFailTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            NFLPlusReplayIE(http(transfer())).extract("https://www.nfl.com/plus/games/fixture-game/1572108")
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            NFLPlusEpisodeIE(http(transfer())).extract("https://www.nfl.com/plus/episodes/fixture-episode")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoConfigIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.nfl.com/videos/fixture-video"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("fixture-item"),
                "title" to Expect.Value("Fixture NFL Video"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = configScript(
                        """{"playlist": [{"id": "fixture-item",
                           "url": "https://media.example/hls/master.m3u8",
                           "title": "Fixture NFL Video"}]}""",
                    ),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NFLIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
