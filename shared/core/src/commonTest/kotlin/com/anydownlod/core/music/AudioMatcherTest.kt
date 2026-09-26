package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.youtube.YoutubeSearch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The matcher over fake providers. Every URL is a public fixture URL; no
 * media is written and no network is touched.
 */
class AudioMatcherTest {

    private val record = SongRecord(
        songId = "4uLU6hMCjMI75M1A2tKUQC",
        title = "Never Gonna Give You Up",
        artists = listOf("Rick Astley"),
        album = "Whenever You Need Somebody",
        durationMs = 213_573,
        isrc = "GBARL9300135",
        year = 1987,
    )

    private class FakeProvider(
        override val source: AudioSource,
        override val supportsIsrc: Boolean = false,
        private val failure: Boolean = false,
        private val results: (Boolean) -> List<AudioCandidate> = { emptyList() },
    ) : AudioProvider {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> {
            queries += query
            if (failure) throw ExtractionError.Unavailable("fixture provider failure")
            return results(matchedByIsrc)
        }
    }

    private fun song(
        source: AudioSource = AudioSource.YOUTUBE_MUSIC,
        url: String = "https://music.youtube.com/watch?v=lYBUbBu4W08",
        title: String = "Never Gonna Give You Up",
        artist: String = "Rick Astley",
        duration: Double? = 214.0,
        album: String? = "Whenever You Need Somebody",
        verified: Boolean = true,
    ) = AudioCandidate(
        source = source,
        url = url,
        title = title,
        artists = listOf(artist),
        durationSeconds = duration,
        album = album,
        verified = verified,
    )

    // ----------------------------------------------------------- provider order

    @Test
    fun youtubeMusicIsTriedBeforeYoutube() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) { listOf(song()) }
        val youtube = FakeProvider(AudioSource.YOUTUBE) {
            listOf(song(source = AudioSource.YOUTUBE, url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        }

        val match = AudioMatcher(listOf(music, youtube)).match(record)

        assertEquals(AudioSource.YOUTUBE_MUSIC, match.source)
        assertEquals("https://music.youtube.com/watch?v=lYBUbBu4W08", match.url)
        assertTrue(youtube.queries.isEmpty(), "YouTube must not be searched after a Music hit")
    }

    @Test
    fun youtubeRunsWhenMusicMisses() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) { emptyList() }
        val youtube = FakeProvider(AudioSource.YOUTUBE) {
            listOf(
                song(
                    source = AudioSource.YOUTUBE,
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    title = "Rick Astley - Never Gonna Give You Up",
                    verified = false,
                ),
            )
        }

        val match = AudioMatcher(listOf(music, youtube)).match(record)

        assertEquals(AudioSource.YOUTUBE, match.source)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", match.url)
        assertEquals(1, music.queries.size)
        assertEquals(1, youtube.queries.size)
    }

    @Test
    fun aFailedProviderDoesNotStopTheNextOne() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC, failure = true)
        val youtube = FakeProvider(AudioSource.YOUTUBE) {
            listOf(
                song(
                    source = AudioSource.YOUTUBE,
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    title = "Rick Astley - Never Gonna Give You Up",
                    verified = false,
                ),
            )
        }

        val match = AudioMatcher(listOf(music, youtube)).match(record)
        assertEquals(AudioSource.YOUTUBE, match.source)
    }

    // ------------------------------------------------------------------ misses

    @Test
    fun aWrongDurationIsRejectedAndNoUrlIsInvented() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) { listOf(song(duration = 30.0)) }
        val matcher = AudioMatcher(listOf(music))

        val error = assertFailsWith<SpotifyMatchError.NoMatch> { matcher.match(record) }
        assertTrue(error.message!!.contains("Rick Astley - Never Gonna Give You Up"))
    }

    @Test
    fun aWrongTitleFailsTyped() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) {
            listOf(song(title = "Completely Different Song", artist = "Someone Else"))
        }
        val matcher = AudioMatcher(listOf(music))

        val error = assertFailsWith<SpotifyMatchError.NoMatch> { matcher.match(record) }
        assertTrue(error.message!!.contains("No audio match"))
        assertTrue(error.message!!.contains("YouTube Music"), "the tried providers are named")
    }

    @Test
    fun noResultsAtAllFailsTyped() = runTest {
        val matcher = AudioMatcher(listOf(FakeProvider(AudioSource.YOUTUBE_MUSIC)))
        assertFailsWith<SpotifyMatchError.NoMatch> { matcher.match(record) }
    }

    // ----------------------------------------------------------------- options

    @Test
    fun dontFilterResultsTakesTheFirstResult() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) {
            listOf(song(title = "Completely Different Song", artist = "Someone Else"))
        }
        val matcher = AudioMatcher(
            providers = listOf(music),
            options = MatcherOptions(filterResults = false),
        )

        val match = matcher.match(record)
        assertEquals("https://music.youtube.com/watch?v=lYBUbBu4W08", match.url)
        assertEquals(100.0, match.score)
    }

    @Test
    fun onlyVerifiedFiltersUnverifiedResults() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) {
            listOf(song(verified = false))
        }

        assertFailsWith<SpotifyMatchError.NoMatch> {
            AudioMatcher(
                providers = listOf(music),
                options = MatcherOptions(onlyVerified = true),
            ).match(record)
        }
        val match = AudioMatcher(providers = listOf(music)).match(record)
        assertEquals("https://music.youtube.com/watch?v=lYBUbBu4W08", match.url)
    }

    @Test
    fun anIsrcSearchReturnsTheSingleVerifiedResult() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC, supportsIsrc = true) { matchedByIsrc ->
            if (matchedByIsrc) {
                listOf(
                    song(
                        url = "https://music.youtube.com/watch?v=isrc-hit",
                        title = "Never Gonna Give You Up",
                    ),
                )
            } else {
                emptyList()
            }
        }
        val youtube = FakeProvider(AudioSource.YOUTUBE)

        val match = AudioMatcher(listOf(music, youtube)).match(record)

        assertEquals("https://music.youtube.com/watch?v=isrc-hit", match.url)
        assertEquals(100.0, match.score)
        assertEquals(listOf("GBARL9300135"), music.queries)
        assertTrue(youtube.queries.isEmpty())
    }

    @Test
    fun anEnabledFallbackRunsAfterYouTubeMisses() = runTest {
        val music = FakeProvider(AudioSource.YOUTUBE_MUSIC) { emptyList() }
        val youtube = FakeProvider(AudioSource.YOUTUBE) { emptyList() }
        val soundcloud = FakeProvider(AudioSource.SOUNDCLOUD) {
            listOf(song(source = AudioSource.SOUNDCLOUD, url = "https://soundcloud.com/fixture/track"))
        }

        val match = AudioMatcher(listOf(music, youtube, soundcloud)).match(record)

        assertEquals(AudioSource.SOUNDCLOUD, match.source)
        assertEquals("https://soundcloud.com/fixture/track", match.url)
    }

    @Test
    fun anUnsupportedUrlFailsThatAttemptOnly() = runTest {
        val soundcloud = FakeProvider(AudioSource.SOUNDCLOUD) {
            listOf(song(source = AudioSource.SOUNDCLOUD, url = "https://soundcloud.com/unsupported"))
        }
        val bandcamp = FakeProvider(AudioSource.BANDCAMP) {
            listOf(song(source = AudioSource.BANDCAMP, url = "https://fixture.bandcamp.com/track/song"))
        }
        val matcher = AudioMatcher(
            providers = listOf(soundcloud, bandcamp),
            canDownload = { url -> !url.contains("unsupported") },
        )

        val match = matcher.match(record)

        assertEquals(AudioSource.BANDCAMP, match.source)
        assertEquals("https://fixture.bandcamp.com/track/song", match.url)
    }

    @Test
    fun aFallbackTheHostCannotFetchDoesNotStopTheNextOne() = runTest {
        val soundcloud = FakeProvider(AudioSource.SOUNDCLOUD, failure = true)
        val bandcamp = FakeProvider(AudioSource.BANDCAMP) {
            listOf(song(source = AudioSource.BANDCAMP, url = "https://fixture.bandcamp.com/track/song"))
        }

        val match = AudioMatcher(listOf(soundcloud, bandcamp)).match(record)

        assertEquals(AudioSource.BANDCAMP, match.source)
    }

    @Test
    fun theDefaultMatcherNeverCallsTheFallbackProviders() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://music.youtube.com/youtubei/v1/search*",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"contents":{}}""",
                ),
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/search*",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"contents":{}}""",
                ),
            ),
        )
        val matcher = AudioMatcher.default(YoutubeSearch(ExtractorHttp(transfer)))

        assertFailsWith<SpotifyMatchError.NoMatch> { matcher.match(record) }

        val fallbackHosts = listOf("soundcloud", "bandcamp", "piped", "slider")
        assertTrue(
            transfer.requests.none { request -> fallbackHosts.any { it in request.url } },
            "the default matcher must not touch a fallback provider",
        )
    }

    // ------------------------------------------------------------ manual pair

    @Test
    fun aManualPairSkipsTheSearchAndKeepsTheRecord() {
        val pair = ManualPairParser.parse(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ|https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            pair?.audioUrl,
        )
        assertEquals(
            "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
            pair?.spotifyUrl,
        )

        val matcher = AudioMatcher(listOf(FakeProvider(AudioSource.YOUTUBE_MUSIC)))
        val match = matcher.manual(record, pair!!.audioUrl)

        assertEquals(pair.audioUrl, match.url)
        assertEquals(AudioSource.YOUTUBE, match.source)
        assertEquals(record, match.record)
        assertTrue(match.manual)
    }

    @Test
    fun aManualPairParserRejectsMalformedInput() {
        assertNull(ManualPairParser.parse("https://www.youtube.com/watch?v=abc"))
        assertNull(ManualPairParser.parse("https://example.org/a|https://open.spotify.com/track/x"))
        assertNull(ManualPairParser.parse("https://youtu.be/abc|https://example.org/track/x"))
        assertNull(
            ManualPairParser.parse(
                "https://www.youtube.com/watch?v=abc|https://open.spotify.com/album/abc",
            ),
        )
        // A spotify: URI is accepted like spotDL's pair parser.
        assertEquals(
            "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            ManualPairParser.parse(
                "https://youtu.be/dQw4w9WgXcQ|spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            )?.spotifyUrl,
        )
    }
}
