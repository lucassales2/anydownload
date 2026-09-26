package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four lyrics providers over synthesized public page shapes. The only
 * token-shaped value is the fake `fixture-token`; it rides in the explicit
 * authorization field and never in a URL. No live scrape.
 */
class LyricsProvidersTest {

    private val record = SongRecord(
        songId = "track1",
        title = "Fixture Song",
        artists = listOf("Fixture Artist"),
    )

    private fun route(pattern: String, body: String, contentType: String = "text/html") = FixtureRoute(
        urlPattern = pattern,
        contentType = contentType,
        body = body,
    )

    // --------------------------------------------------------------- genius

    private val geniusSearch = """
        {"response":{"hits":[{"result":{"full_title":"Fixture Song by Fixture Artist","id":123}}]}}
    """.trimIndent()

    private val geniusSong = """
        {"response":{"song":{"url":"https://genius.example/song/fixture"}}}
    """.trimIndent()

    private val geniusPage = """
        <html><body><div class="lyrics">Fixture line one<br/>Fixture line two</div></body></html>
    """.trimIndent()

    @Test
    fun geniusUsesTheTokenAndExtractsTheLyrics() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://api.genius.com/search*", geniusSearch, "application/json"),
                route("https://api.genius.com/songs/*", geniusSong, "application/json"),
                route("https://genius.example/song/*", geniusPage),
            ),
        )
        val provider = GeniusLyricsProvider(ExtractorHttp(transfer), "fixture-token")

        val result = provider.fetch(record.title, record.artists)

        assertEquals("Fixture line one\nFixture line two", result?.text)
        assertEquals(false, result?.synced)
        val apiRequests = transfer.requests.filter { it.url.contains("api.genius.com") }
        assertEquals(2, apiRequests.size)
        assertTrue(apiRequests.all { it.authorization == "Bearer fixture-token" })
        assertTrue(apiRequests.none { it.url.contains("fixture-token") }, "the token must never enter a URL")
    }

    @Test
    fun geniusWithoutATokenMissesWithoutARequest() = runTest {
        val transfer = FixtureHttpTransfer(emptyList())
        val provider = GeniusLyricsProvider(ExtractorHttp(transfer), token = null)
        assertNull(provider.fetch(record.title, record.artists))
        assertTrue(transfer.requests.isEmpty())
    }

    // ------------------------------------------------------------- azlyrics

    private val azlyricsSearch = """
        <html><body><table><tr>
        <td><a href="https://www.azlyrics.com/lyrics/fixtureartist/fixturesong.html">Fixture Artist - Fixture Song</a></td>
        </tr></table></body></html>
    """.trimIndent()

    private val azlyricsPage = """
        <html><body>
        <!-- Usage of azlyrics.com content by any third-party lyrics provider is prohibited by our licensing agreement. -->
        <div>Az lyrics line one<br>Az lyrics line two</div>
        </body></html>
    """.trimIndent()

    @Test
    fun azlyricsFollowsTheSearchHitAndExtractsTheLyrics() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://www.azlyrics.com/search/*", azlyricsSearch),
                route("https://www.azlyrics.com/lyrics/*", azlyricsPage),
            ),
        )
        val provider = AzlyricsLyricsProvider(ExtractorHttp(transfer))

        val result = provider.fetch(record.title, record.artists)

        assertEquals("Az lyrics line one\nAz lyrics line two", result?.text)
        assertEquals(2, transfer.requests.size)
    }

    // ------------------------------------------------------------ musixmatch

    private val musixmatchSearch = """
        <html><body><a href="/lyrics/FixtureArtist/FixtureSong">Fixture Song</a></body></html>
    """.trimIndent()

    private val musixmatchPage = """
        <html><body>
        <p class="mxm-lyrics__content">Mxm line one</p>
        <p class="mxm-lyrics__content">Mxm line two</p>
        </body></html>
    """.trimIndent()

    @Test
    fun musixmatchFollowsTheSearchHitAndExtractsTheLyrics() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://www.musixmatch.com/search/*", musixmatchSearch),
                route("https://www.musixmatch.com/lyrics/*", musixmatchPage),
            ),
        )
        val provider = MusixmatchLyricsProvider(ExtractorHttp(transfer))

        val result = provider.fetch(record.title, record.artists)

        assertEquals("Mxm line one\nMxm line two", result?.text)
        assertEquals(2, transfer.requests.size)
    }

    // ---------------------------------------------------------------- synced

    @Test
    fun syncedPrefersTimedLines() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route(
                    "https://lrclib.net/api/search*",
                    """[{"trackName":"Fixture Song","artistName":"Fixture Artist","syncedLyrics":"[00:01.00] line","plainLyrics":"line"}]""",
                    "application/json",
                ),
            ),
        )
        val provider = SyncedLyricsProvider(ExtractorHttp(transfer))

        val result = provider.fetch(record.title, record.artists)

        assertEquals("[00:01.00] line", result?.text)
        assertEquals(true, result?.synced)
    }

    @Test
    fun syncedFallsBackToPlainLinesWithoutMarkingThemTimed() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route(
                    "https://lrclib.net/api/search*",
                    """[{"trackName":"Fixture Song","artistName":"Fixture Artist","syncedLyrics":null,"plainLyrics":"plain line"}]""",
                    "application/json",
                ),
            ),
        )
        val provider = SyncedLyricsProvider(ExtractorHttp(transfer))

        val result = provider.fetch(record.title, record.artists)

        assertEquals("plain line", result?.text)
        assertEquals(false, result?.synced)
    }

    // --------------------------------------------------------------- parsing

    @Test
    fun theHtmlExtractorsIgnoreEmptyPages() {
        assertNull(extractGeniusLyrics("<html><body>nothing</body></html>"))
        assertNull(extractAzlyricsLyrics("<html><body>nothing</body></html>"))
        assertNull(extractMusixmatchLyrics("<html><body>nothing</body></html>"))
        assertTrue(parseAzlyricsSearch("<html></html>").isEmpty())
        assertTrue(parseMusixmatchSearch("<html></html>").isEmpty())
    }
}
