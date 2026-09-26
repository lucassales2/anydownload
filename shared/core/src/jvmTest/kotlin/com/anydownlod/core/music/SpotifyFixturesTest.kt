package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.ClasspathFixtureStore
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The saved public Spotify Web API documents under
 * `commonTest/resources/fixtures/spotify/`. The shapes follow the public
 * object model; the only token-shaped value is the repository's `REDACTED`
 * redaction placeholder in `embed_page.html`. No live call is made.
 */
class SpotifyFixturesTest {

    private val allFixtures = listOf(
        "track.json",
        "album.json",
        "album_tracks.json",
        "playlist.json",
        "playlist_tracks.json",
        "artist.json",
        "artist_albums.json",
        "search_track.json",
        "search_album.json",
        "search_playlist.json",
        "search_artist.json",
    )

    private fun route(pattern: String, resource: String) = FixtureRoute(
        urlPattern = pattern,
        contentType = if (resource.endsWith(".html")) "text/html" else "application/json",
        bodyResource = resource,
    )

    private fun client(routes: List<FixtureRoute>): Pair<SpotifyWebApiClient, FixtureHttpTransfer> {
        val transfer = FixtureHttpTransfer(routes, ClasspathFixtureStore)
        return SpotifyWebApiClient(
            http = ExtractorHttp(transfer),
            tokens = SpotifyTokenSource { SpotifyAccessToken("REDACTED") },
            backend = SpotifyBackend.UNAUTHENTICATED,
        ) to transfer
    }

    private fun fixture(name: String) = "fixtures/spotify/$name"

    @Test
    fun aTrackFixtureResolvesEveryRequiredField() = runTest {
        val (client, transfer) = client(
            listOf(route("https://api.spotify.com/v1/tracks/*", fixture("track.json"))),
        )

        val song = client.resolve("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC").songs.single()

        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals(listOf("Rick Astley"), song.artists)
        assertEquals("Whenever You Need Somebody", song.album)
        assertEquals("Rick Astley", song.albumArtist)
        assertEquals(213_573L, song.durationMs)
        assertEquals("GBARL9300135", song.isrc)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273255e131abc1410833be95673",
            song.artworkUrl,
        )
        assertEquals(1, song.trackNumber)
        assertEquals(1, song.discNumber)
        assertEquals(1987, song.year)
        assertEquals(false, song.explicit)
        assertTrue(transfer.requests.single().authorization == "Bearer REDACTED")
    }

    @Test
    fun anAlbumFixtureExpandsWithPerEntryFailures() = runTest {
        val (client, _) = client(
            listOf(
                route("https://api.spotify.com/v1/albums/*/tracks*", fixture("album_tracks.json")),
                route("https://api.spotify.com/v1/albums/*", fixture("album.json")),
            ),
        )

        val result = client.resolve("https://open.spotify.com/album/1ATL5GLyefJaxhQzSPVrLX")

        assertEquals("Whenever You Need Somebody", result.name)
        assertEquals("https://open.spotify.com/album/1ATL5GLyefJaxhQzSPVrLX", result.url)
        assertEquals(3, result.entries.size)
        val first = assertIs<SongListEntry.Song>(result.entries[0]).record
        assertEquals("Fixture Records", first.publisher)
        assertEquals(3, first.tracksCount)
        assertEquals(2, first.discCount)
        assertEquals(
            UnavailableReason.NO_DURATION,
            assertIs<SongListEntry.Unavailable>(result.entries[2]).reason,
        )
    }

    @Test
    fun aPlaylistFixtureKeepsLocalAndEpisodeEntries() = runTest {
        val (client, _) = client(
            listOf(
                route("https://api.spotify.com/v1/playlists/*/tracks*", fixture("playlist_tracks.json")),
                route("https://api.spotify.com/v1/playlists/*", fixture("playlist.json")),
            ),
        )

        val result = client.resolve("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        assertEquals("Fixture Hits", result.name)
        assertEquals(3, result.entries.size)
        val song = assertIs<SongListEntry.Song>(result.entries[0]).record
        assertEquals("GBARL9300135", song.isrc)
        assertEquals(1, song.listPosition)
        assertEquals(3, song.listLength)
        assertEquals(UnavailableReason.LOCAL_TRACK, assertIs<SongListEntry.Unavailable>(result.entries[1]).reason)
        assertEquals(UnavailableReason.NOT_A_TRACK, assertIs<SongListEntry.Unavailable>(result.entries[2]).reason)
    }

    @Test
    fun anArtistFixtureExpandsAlbumsAndDedupes() = runTest {
        val (client, _) = client(
            listOf(
                route("https://api.spotify.com/v1/artists/*/albums*", fixture("artist_albums.json")),
                route("https://api.spotify.com/v1/albums/*/tracks*", fixture("album_tracks.json")),
                route("https://api.spotify.com/v1/albums/*", fixture("album.json")),
                route("https://api.spotify.com/v1/artists/*", fixture("artist.json")),
            ),
        )

        val result = client.resolve("https://open.spotify.com/artist/0gxyHStUsqpMadRV0Di1Qt")

        assertEquals("Rick Astley", result.name)
        // One album fetched; the same-name duplicate is skipped; the broken
        // duration entry keeps its typed place.
        assertEquals(3, result.entries.size)
        val song = assertIs<SongListEntry.Song>(result.entries.first()).record
        assertEquals("Rick Astley", song.listName)
        assertEquals("https://open.spotify.com/artist/0gxyHStUsqpMadRV0Di1Qt", song.listUrl)
    }

    @Test
    fun aTextSearchFixtureResolvesTheFirstTrack() = runTest {
        val (client, _) = client(
            listOf(route("https://api.spotify.com/v1/search?*", fixture("search_track.json"))),
        )
        val song = client.resolve("Rick Astley - Never Gonna Give You Up").songs.single()
        assertEquals("Never Gonna Give You Up", song.title)
        assertEquals("GBARL9300135", song.isrc)
    }

    @Test
    fun typedPrefixSearchesResolveTheirListKinds() = runTest {
        val (albumClient, _) = client(
            listOf(
                route("https://api.spotify.com/v1/search?*", fixture("search_album.json")),
                route("https://api.spotify.com/v1/albums/*/tracks*", fixture("album_tracks.json")),
                route("https://api.spotify.com/v1/albums/*", fixture("album.json")),
            ),
        )
        assertEquals("Whenever You Need Somebody", albumClient.resolve("album:whenever").name)

        val (playlistClient, _) = client(
            listOf(
                route("https://api.spotify.com/v1/search?*", fixture("search_playlist.json")),
                route("https://api.spotify.com/v1/playlists/*/tracks*", fixture("playlist_tracks.json")),
                route("https://api.spotify.com/v1/playlists/*", fixture("playlist.json")),
            ),
        )
        assertEquals("Fixture Hits", playlistClient.resolve("playlist:fixture").name)

        val (artistClient, _) = client(
            listOf(
                route("https://api.spotify.com/v1/search?*", fixture("search_artist.json")),
                route("https://api.spotify.com/v1/artists/*/albums*", fixture("artist_albums.json")),
                route("https://api.spotify.com/v1/albums/*/tracks*", fixture("album_tracks.json")),
                route("https://api.spotify.com/v1/albums/*", fixture("album.json")),
                route("https://api.spotify.com/v1/artists/*", fixture("artist.json")),
            ),
        )
        assertEquals("Rick Astley", artistClient.resolve("artist:rick").name)
    }

    @Test
    fun theEmbedPageFixtureCarriesOnlyTheRedactionPlaceholder() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(route("https://open.spotify.com/embed/*", fixture("embed_page.html"))),
            ClasspathFixtureStore,
        )
        val source = SpotifyEmbedTokenSource(ExtractorHttp(transfer))
        val token = source.token(SpotifyQueryParser.parse("https://open.spotify.com/track/fixture")!!)
        assertEquals("REDACTED", token.value)
        assertNotNull(token.expiresAtEpochMs)
    }

    @Test
    fun noSavedSpotifyFixtureCarriesASecretOrAToken() {
        val store = ClasspathFixtureStore
        for (name in allFixtures) {
            val text = store.read(fixture(name))
            assertNotNull(text, "missing fixture $name")
            assertTrue("\"access_token\"" !in text, "$name must not carry an OAuth token response")
            assertTrue("Bearer " !in text, "$name must not carry an Authorization value")
            assertTrue("client_secret" !in text, "$name must not carry a client secret")
            assertTrue("p.scdn.co/mp3-preview" !in text, "$name must not carry a Spotify preview URL")
            val accessTokenValues = Regex("\"accessToken\"\\s*:\\s*\"([^\"]*)\"")
                .findAll(text)
                .map { it.groupValues[1] }
                .toList()
            assertTrue(accessTokenValues.all { it == "REDACTED" }, "$name has a non-placeholder access token")
        }
        // The embed page is the one fixture that keeps the placeholder field.
        assertTrue(store.read(fixture("embed_page.html"))!!.contains("\"accessToken\":\"REDACTED\""))
    }
}
