package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.ClasspathFixtureStore
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The saved lyric pages under `commonTest/resources/fixtures/lyrics/`. They
 * follow the public page shapes but carry no token, cookie, or signed URL.
 */
class LyricsFixturesTest {

    private val record = SongRecord(
        songId = "track1",
        title = "Fixture Song",
        artists = listOf("Fixture Artist"),
    )

    private val fixtures = listOf(
        "genius_search.json",
        "genius_song.json",
        "genius_page.html",
        "azlyrics_search.html",
        "azlyrics_page.html",
        "musixmatch_search.html",
        "musixmatch_page.html",
        "lrclib_search.json",
    )

    private fun route(pattern: String, resource: String) = FixtureRoute(
        urlPattern = pattern,
        contentType = if (resource.endsWith(".json")) "application/json" else "text/html",
        bodyResource = "fixtures/lyrics/$resource",
    )

    @Test
    fun geniusFixtureResolvesWithAFakeToken() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://api.genius.com/search*", "genius_search.json"),
                route("https://api.genius.com/songs/*", "genius_song.json"),
                route("https://genius.example/song/*", "genius_page.html"),
            ),
            ClasspathFixtureStore,
        )
        val provider = GeniusLyricsProvider(ExtractorHttp(transfer), "fixture-token")

        val result = provider.fetch(record.title, record.artists)

        assertEquals("Fixture line one\nFixture line two", result?.text)
        assertEquals(false, result?.synced)
        assertTrue(
            transfer.requests.filter { it.url.contains("api.genius.com") }
                .all { it.authorization == "Bearer fixture-token" },
        )
    }

    @Test
    fun azlyricsFixtureResolves() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://www.azlyrics.com/search/*", "azlyrics_search.html"),
                route("https://www.azlyrics.com/lyrics/*", "azlyrics_page.html"),
            ),
            ClasspathFixtureStore,
        )
        val result = AzlyricsLyricsProvider(ExtractorHttp(transfer)).fetch(record.title, record.artists)
        assertEquals("Az lyrics line one\nAz lyrics line two", result?.text)
    }

    @Test
    fun musixmatchFixtureResolves() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://www.musixmatch.com/search/*", "musixmatch_search.html"),
                route("https://www.musixmatch.com/lyrics/*", "musixmatch_page.html"),
            ),
            ClasspathFixtureStore,
        )
        val result = MusixmatchLyricsProvider(ExtractorHttp(transfer)).fetch(record.title, record.artists)
        assertEquals("Mxm line one\nMxm line two", result?.text)
    }

    @Test
    fun lrclibFixtureResolvesAsSynced() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(route("https://lrclib.net/api/search*", "lrclib_search.json")),
            ClasspathFixtureStore,
        )
        val result = SyncedLyricsProvider(ExtractorHttp(transfer)).fetch(record.title, record.artists)
        assertEquals("[00:01.00] line", result?.text)
        assertEquals(true, result?.synced)
    }

    @Test
    fun noSavedLyricsFixtureCarriesATokenOrCookie() {
        for (name in fixtures) {
            val text = ClasspathFixtureStore.read("fixtures/lyrics/$name")
            assertNotNull(text, "missing fixture $name")
            for (forbidden in listOf("Bearer ", "fixture-token", "Authorization", "access_token", "client_secret")) {
                assertTrue(forbidden !in text, "$name must not contain '$forbidden'")
            }
        }
    }
}
