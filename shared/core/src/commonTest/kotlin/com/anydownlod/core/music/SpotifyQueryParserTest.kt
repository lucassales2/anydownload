package com.anydownlod.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Query parsing mirrors spotDL v4.5.2 `utils/search.py`: Spotify URLs,
 * `spotify:` URIs, and the `album:`/`playlist:`/`artist:` prefixes. No live
 * call and no real URL beyond public `open.spotify.com` pages.
 */
class SpotifyQueryParserTest {

    @Test
    fun trackUrlWithLocaleAndQueryStringParsesToTheId() {
        val query = SpotifyQueryParser.parse(
            "https://open.spotify.com/intl-pt/track/4uLU6hMCjMI75M1A2tKUQC?si=fixture",
        )
        val track = assertIs<SpotifyQuery.Track>(query)
        assertEquals("4uLU6hMCjMI75M1A2tKUQC", track.id)
    }

    @Test
    fun albumPlaylistAndArtistUrlsParse() {
        assertEquals(
            "1ATL5GLyefJaxhQzSPVrLX",
            assertIs<SpotifyQuery.Album>(
                SpotifyQueryParser.parse("https://open.spotify.com/album/1ATL5GLyefJaxhQzSPVrLX"),
            ).id,
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            assertIs<SpotifyQuery.Playlist>(
                SpotifyQueryParser.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"),
            ).id,
        )
        assertEquals(
            "0gxyHStUsqpMadRV0Di1Qt",
            assertIs<SpotifyQuery.Artist>(
                SpotifyQueryParser.parse("https://open.spotify.com/artist/0gxyHStUsqpMadRV0Di1Qt"),
            ).id,
        )
    }

    @Test
    fun aUserPlaylistUrlStillFindsThePlaylistSegment() {
        val query = SpotifyQueryParser.parse(
            "https://open.spotify.com/user/fixture/playlist/37i9dQZF1DXcBWIGoYBM5M",
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            assertIs<SpotifyQuery.Playlist>(query).id,
        )
    }

    @Test
    fun spotifyUrisParse() {
        assertEquals(
            "4uLU6hMCjMI75M1A2tKUQC",
            assertIs<SpotifyQuery.Track>(SpotifyQueryParser.parse("spotify:track:4uLU6hMCjMI75M1A2tKUQC")).id,
        )
        assertEquals(
            "1ATL5GLyefJaxhQzSPVrLX",
            assertIs<SpotifyQuery.Album>(SpotifyQueryParser.parse("spotify:album:1ATL5GLyefJaxhQzSPVrLX")).id,
        )
    }

    @Test
    fun plainTextBecomesATrackSearch() {
        val query = SpotifyQueryParser.parse("Rick Astley - Never Gonna Give You Up")
        assertEquals(
            "Rick Astley - Never Gonna Give You Up",
            assertIs<SpotifyQuery.TextSearch>(query).term,
        )
    }

    @Test
    fun typedPrefixesSelectTheListKind() {
        assertEquals(
            "Whenever You Need Somebody",
            assertIs<SpotifyQuery.AlbumSearch>(SpotifyQueryParser.parse("album:Whenever You Need Somebody")).term,
        )
        assertEquals(
            "Fixture Hits",
            assertIs<SpotifyQuery.PlaylistSearch>(SpotifyQueryParser.parse("playlist:Fixture Hits")).term,
        )
        assertEquals(
            "Rick Astley",
            assertIs<SpotifyQuery.ArtistSearch>(SpotifyQueryParser.parse("artist:Rick Astley")).term,
        )
        // Case-insensitive, like spotDL's substring check.
        assertIs<SpotifyQuery.AlbumSearch>(SpotifyQueryParser.parse("ALBUM:fixture"))
    }

    @Test
    fun aNonSpotifyUrlIsPlainSearchText() {
        val query = SpotifyQueryParser.parse("https://example.org/watch?v=fixture")
        assertEquals(
            "https://example.org/watch?v=fixture",
            assertIs<SpotifyQuery.TextSearch>(query).term,
        )
    }

    @Test
    fun blankInputHasNoQuery() {
        assertNull(SpotifyQueryParser.parse(""))
        assertNull(SpotifyQueryParser.parse("   "))
    }

    @Test
    fun entityQueriesKnowTheirPublicEmbedPage() {
        assertEquals(
            "https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC",
            (SpotifyQueryParser.parse("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC") as SpotifyQuery).embedUrl(),
        )
        assertNull(SpotifyQueryParser.parse("album:fixture")?.embedUrl())
    }

    @Test
    fun spotifyInputIsDistinguishedFromOtherPageUrls() {
        assertTrue(SpotifyQueryParser.isSpotifyInput("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue(SpotifyQueryParser.isSpotifyInput("spotify:album:1ATL5GLyefJaxhQzSPVrLX"))
        assertTrue(SpotifyQueryParser.isSpotifyInput("album:Whenever You Need Somebody"))
        assertTrue(SpotifyQueryParser.isSpotifyInput("Rick Astley - Never Gonna Give You Up"))
        assertFalse(SpotifyQueryParser.isSpotifyInput("https://www.youtube.com/watch?v=fixture"))
        assertFalse(SpotifyQueryParser.isSpotifyInput("https://example.org/watch"))
        assertFalse(SpotifyQueryParser.isSpotifyInput("   "))
    }
}
