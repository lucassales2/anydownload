package com.anydownlod.core.music

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lyrics fetcher: provider order, first-hit wins, and a failing provider
 * that does not stop the rest. No network.
 */
class LyricsFetcherTest {

    private val record = SongRecord(
        songId = "track1",
        title = "Fixture Song",
        artists = listOf("Fixture Artist"),
    )

    private class FakeProvider(
        override val name: String,
        private val result: LyricsResult? = null,
        private val fail: Boolean = false,
    ) : LyricsProvider {
        val calls = mutableListOf<String>()
        override suspend fun fetch(title: String, artists: List<String>): LyricsResult? {
            calls += name
            if (fail) error("fixture provider failure")
            return result
        }
    }

    @Test
    fun theDefaultOrderIsGeniusAzlyricsMusixmatchSynced() = runTest {
        val calls = mutableListOf<String>()
        val providers = LyricsProviders.DEFAULT_ORDER.associateWith { name ->
            FakeProvider(name, result = if (name == LyricsProviders.SYNCED) LyricsResult("hit") else null)
        }
        val fetcher = LyricsFetcher(providers)
        // Record the call order through a provider that logs into the shared list.
        val logging = providers.mapValues { (name, provider) ->
            object : LyricsProvider {
                override val name = name
                override suspend fun fetch(title: String, artists: List<String>): LyricsResult? {
                    calls += name
                    return provider.fetch(title, artists)
                }
            }
        }

        val result = LyricsFetcher(logging).fetch(record)

        assertEquals("hit", result?.text)
        assertEquals(LyricsProviders.DEFAULT_ORDER, calls)
    }

    @Test
    fun theFirstNonBlankHitWins() = runTest {
        val genius = FakeProvider(LyricsProviders.GENIUS, result = LyricsResult("genius lyrics"))
        val azlyrics = FakeProvider(LyricsProviders.AZLYRICS, result = LyricsResult("azlyrics lyrics"))
        val fetcher = LyricsFetcher(mapOf(genius.name to genius, azlyrics.name to azlyrics))

        val result = fetcher.fetch(record)

        assertEquals("genius lyrics", result?.text)
        assertEquals(listOf(LyricsProviders.GENIUS), genius.calls)
        assertTrue(azlyrics.calls.isEmpty())
    }

    @Test
    fun aFailingProviderDoesNotStopTheNextOne() = runTest {
        val genius = FakeProvider(LyricsProviders.GENIUS, fail = true)
        val azlyrics = FakeProvider(LyricsProviders.AZLYRICS, result = LyricsResult("azlyrics lyrics"))
        val fetcher = LyricsFetcher(mapOf(genius.name to genius, azlyrics.name to azlyrics))

        val result = fetcher.fetch(record)

        assertEquals("azlyrics lyrics", result?.text)
    }

    @Test
    fun theUserCanSetAnotherOrder() = runTest {
        val genius = FakeProvider(LyricsProviders.GENIUS, result = LyricsResult("genius lyrics"))
        val synced = FakeProvider(LyricsProviders.SYNCED, result = LyricsResult("[00:01.00] synced", synced = true))
        val fetcher = LyricsFetcher(mapOf(genius.name to genius, synced.name to synced))

        val result = fetcher.fetch(record, order = listOf(LyricsProviders.SYNCED, LyricsProviders.GENIUS))

        assertEquals("[00:01.00] synced", result?.text)
        assertEquals(true, result?.synced)
        assertTrue(genius.calls.isEmpty())
    }

    @Test
    fun aMissReturnsNull() = runTest {
        val fetcher = LyricsFetcher(
            mapOf(LyricsProviders.GENIUS to FakeProvider(LyricsProviders.GENIUS)),
        )
        assertNull(fetcher.fetch(record))
    }

    @Test
    fun aBlankHitIsNotAWin() = runTest {
        val blank = FakeProvider(LyricsProviders.GENIUS, result = LyricsResult("   "))
        val azlyrics = FakeProvider(LyricsProviders.AZLYRICS, result = LyricsResult("azlyrics lyrics"))
        val fetcher = LyricsFetcher(mapOf(blank.name to blank, azlyrics.name to azlyrics))

        assertEquals("azlyrics lyrics", fetcher.fetch(record)?.text)
    }
}
