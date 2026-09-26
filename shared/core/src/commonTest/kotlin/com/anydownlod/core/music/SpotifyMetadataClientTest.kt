package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The metadata client over synthesized Web API documents. Every URL is a
 * public Spotify fixture; no token, secret, signed URL, or media URL appears
 * in the fixture bodies. No live call is made.
 */
class SpotifyMetadataClientTest {

    // ------------------------------------------------------------- fixtures

    private val track = """
        {"id":"track1","name":"Fixture Song","type":"track","duration_ms":200000,"explicit":true,
        "disc_number":1,"track_number":2,"external_ids":{"isrc":"ISRCFIXTURE01"},
        "external_urls":{"spotify":"https://open.spotify.com/track/track1"},
        "artists":[{"name":"Fixture Artist","id":"artist1"}],
        "album":{"id":"album1","name":"Fixture Album","album_type":"album","release_date":"2020-05-01",
        "total_tracks":4,"artists":[{"name":"Fixture Artist","id":"artist1"}],
        "images":[{"url":"https://i.scdn.co/image/fixture-cover","width":640,"height":640}]}}
    """.trimIndent()

    private val album = """
        {"id":"album1","name":"Fixture Album","album_type":"album","release_date":"2020-05-01",
        "total_tracks":2,"label":"Fixture Records","genres":["fixture pop"],
        "artists":[{"name":"Fixture Artist","id":"artist1"}],
        "images":[{"url":"https://i.scdn.co/image/fixture-cover","width":640,"height":640}],
        "external_urls":{"spotify":"https://open.spotify.com/album/album1"}}
    """.trimIndent()

    private val albumTracks = """
        {"items":[
        {"id":"track1","name":"Fixture Song","type":"track","duration_ms":200000,"explicit":false,
        "disc_number":1,"track_number":1,"artists":[{"name":"Fixture Artist","id":"artist1"}],
        "external_urls":{"spotify":"https://open.spotify.com/track/track1"}},
        {"id":"track2","name":"Fixture Broken","type":"track","duration_ms":0,"explicit":false,
        "disc_number":2,"track_number":2,"artists":[{"name":"Fixture Artist","id":"artist1"}]}
        ],"next":null}
    """.trimIndent()

    private val playlist = """
        {"id":"playlist1","name":"Fixture Playlist","type":"playlist",
        "external_urls":{"spotify":"https://open.spotify.com/playlist/playlist1"},
        "images":[{"url":"https://i.scdn.co/image/fixture-list","width":300,"height":300}],
        "tracks":{"total":3,"items":[],"next":null}}
    """.trimIndent()

    private val playlistTracks = """
        {"items":[
        {"is_local":false,"track":$track},
        {"is_local":true,"track":{"id":"local1","name":"Local Fixture","type":"track","duration_ms":0}},
        {"is_local":false,"track":{"id":"ep1","name":"Fixture Episode","type":"episode","duration_ms":1000}}
        ],"next":null}
    """.trimIndent()

    private val artist = """
        {"id":"artist1","name":"Fixture Artist","genres":["fixture pop"],
        "images":[{"url":"https://i.scdn.co/image/fixture-artist","width":640,"height":640}],
        "external_urls":{"spotify":"https://open.spotify.com/artist/artist1"}}
    """.trimIndent()

    private val artistAlbums = """
        {"items":[{"id":"album1","name":"Fixture Album","release_date":"2020-05-01","total_tracks":2,
        "artists":[{"name":"Fixture Artist","id":"artist1"}],"images":[]}],"next":null}
    """.trimIndent()

    // -------------------------------------------------------------- harness

    private class FakeTokenSource : SpotifyTokenSource {
        var calls = 0
        override suspend fun token(query: SpotifyQuery): SpotifyAccessToken {
            calls++
            return SpotifyAccessToken("fixture-token")
        }
    }

    private fun client(
        routes: List<FixtureRoute>,
        tokens: SpotifyTokenSource = FakeTokenSource(),
        backend: SpotifyBackend = SpotifyBackend.UNAUTHENTICATED,
    ): Pair<SpotifyWebApiClient, FixtureHttpTransfer> {
        val transfer = FixtureHttpTransfer(routes)
        return SpotifyWebApiClient(ExtractorHttp(transfer), tokens, backend) to transfer
    }

    private fun route(pattern: String, body: String, status: Int = 200) = FixtureRoute(
        urlPattern = pattern,
        contentType = "application/json",
        statusCode = status,
        body = body,
    )

    // ----------------------------------------------------------- single track

    @Test
    fun trackUrlReturnsEveryRecordedField() = runTest {
        val (client, transfer) = client(
            listOf(route("https://api.spotify.com/v1/tracks/*", track)),
        )

        val result = client.resolve("https://open.spotify.com/track/track1")
        val song = result.songs.single()
        assertEquals("Fixture Song", song.title)
        assertEquals(listOf("Fixture Artist"), song.artists)
        assertEquals("Fixture Artist", song.artist)
        assertEquals("Fixture Album", song.album)
        assertEquals("Fixture Artist", song.albumArtist)
        assertEquals("album1", song.albumId)
        assertEquals(200_000L, song.durationMs)
        assertEquals(200, song.durationSeconds)
        assertEquals("ISRCFIXTURE01", song.isrc)
        assertEquals("https://i.scdn.co/image/fixture-cover", song.artworkUrl)
        assertEquals(2, song.trackNumber)
        assertEquals(1, song.discNumber)
        assertEquals(2020, song.year)
        assertEquals("2020-05-01", song.releaseDate)
        assertEquals(true, song.explicit)
        assertEquals("https://open.spotify.com/track/track1", song.spotifyUrl)
        assertTrue(song.genres.isEmpty())

        // The token reaches the transport only through the explicit field.
        val request = transfer.requests.single()
        assertEquals("Bearer fixture-token", request.authorization)
        assertTrue(request.headers.keys.none { it.equals("authorization", ignoreCase = true) })
    }

    // ------------------------------------------------------------------ album

    @Test
    fun albumExpansionKeepsUnavailableEntriesInPlace() = runTest {
        val (client, _) = client(
            listOf(
                route("https://api.spotify.com/v1/albums/*/tracks*", albumTracks),
                route("https://api.spotify.com/v1/albums/*", album),
            ),
        )

        val result = client.resolve("https://open.spotify.com/album/album1")
        assertEquals("Fixture Album", result.name)
        assertEquals("https://open.spotify.com/album/album1", result.url)
        assertEquals("https://i.scdn.co/image/fixture-cover", result.coverUrl)
        assertEquals(2, result.entries.size)

        val song = assertIs<SongListEntry.Song>(result.entries[0]).record
        assertEquals("Fixture Song", song.title)
        assertEquals("Fixture Album", song.album)
        assertEquals("Fixture Records", song.publisher)
        assertEquals(listOf("fixture pop"), song.genres)
        assertEquals(2, song.tracksCount)
        assertEquals(2, song.discCount)
        assertEquals("Fixture Album", song.listName)
        assertEquals("https://open.spotify.com/album/album1", song.listUrl)
        assertNull(song.isrc, "album track pages do not carry ISRC")

        val unavailable = assertIs<SongListEntry.Unavailable>(result.entries[1])
        assertEquals(UnavailableReason.NO_DURATION, unavailable.reason)
        assertEquals("Fixture Broken", unavailable.title)
    }

    // --------------------------------------------------------------- playlist

    @Test
    fun playlistExpansionKeepsLocalAndEpisodeEntries() = runTest {
        val (client, _) = client(
            listOf(
                route("https://api.spotify.com/v1/playlists/*/tracks*", playlistTracks),
                route("https://api.spotify.com/v1/playlists/*", playlist),
            ),
        )

        val result = client.resolve("https://open.spotify.com/playlist/playlist1")
        assertEquals(3, result.entries.size)

        val song = assertIs<SongListEntry.Song>(result.entries[0]).record
        assertEquals("ISRCFIXTURE01", song.isrc)
        assertEquals("Fixture Playlist", song.listName)
        assertEquals(1, song.listPosition)
        assertEquals(3, song.listLength)

        assertEquals(
            UnavailableReason.LOCAL_TRACK,
            assertIs<SongListEntry.Unavailable>(result.entries[1]).reason,
        )
        assertEquals(
            UnavailableReason.NOT_A_TRACK,
            assertIs<SongListEntry.Unavailable>(result.entries[2]).reason,
        )
    }

    // ----------------------------------------------------------------- artist

    @Test
    fun artistExpansionUsesTheArtistAsListContext() = runTest {
        val (client, _) = client(
            listOf(
                route("https://api.spotify.com/v1/artists/*/albums*", artistAlbums),
                route("https://api.spotify.com/v1/albums/*/tracks*", albumTracks),
                route("https://api.spotify.com/v1/albums/*", album),
                route("https://api.spotify.com/v1/artists/*", artist),
            ),
        )

        val result = client.resolve("https://open.spotify.com/artist/artist1")
        assertEquals("Fixture Artist", result.name)
        val song = assertIs<SongListEntry.Song>(result.entries.first()).record
        assertEquals("Fixture Artist", song.listName)
        assertEquals("https://open.spotify.com/artist/artist1", song.listUrl)
        assertEquals("Fixture Song", song.title)
    }

    // ----------------------------------------------------------------- search

    @Test
    fun plainTextResolvesTheFirstTrackResult() = runTest {
        val (client, _) = client(
            listOf(route("https://api.spotify.com/v1/search?*", """{"tracks":{"items":[$track],"next":null}}""")),
        )

        val result = client.resolve("Fixture Artist - Fixture Song")
        assertEquals("Fixture Song", result.songs.single().title)
    }

    @Test
    fun albumPrefixSearchesThenExpandsTheBestAlbum() = runTest {
        val (client, _) = client(
            listOf(
                route(
                    "https://api.spotify.com/v1/search?*",
                    """{"albums":{"items":[{"id":"album1","name":"Fixture Album"}],"next":null}}""",
                ),
                route("https://api.spotify.com/v1/albums/*/tracks*", albumTracks),
                route("https://api.spotify.com/v1/albums/*", album),
            ),
        )

        val result = client.resolve("album:Fixture Album")
        assertEquals("Fixture Album", result.name)
        assertEquals(2, result.entries.size)
    }

    @Test
    fun anEmptySearchFailsTyped() = runTest {
        val (client, _) = client(
            listOf(route("https://api.spotify.com/v1/search?*", """{"tracks":{"items":[],"next":null}}""")),
        )
        assertFailsWith<SpotifyMetadataError.NoResults> {
            client.resolve("nothing at all")
        }
    }

    @Test
    fun aBlankQueryFailsTyped() = runTest {
        val (client, _) = client(emptyList())
        assertFailsWith<SpotifyMetadataError.BadQuery> { client.resolve("   ") }
    }

    // ------------------------------------------------------------ token retry

    @Test
    fun anExpiredSessionIsRefreshedOnceAndTheCallRetried() = runTest {
        val tokens = FakeTokenSource()
        val transfer = SequenceTransfer(
            first = HttpResponse.Final(401, body = ByteArrayHttpBody("{}".encodeToByteArray())),
            second = HttpResponse.Final(
                200,
                contentType = "application/json",
                body = ByteArrayHttpBody(track.encodeToByteArray()),
            ),
        )
        val client = SpotifyWebApiClient(ExtractorHttp(transfer), tokens, SpotifyBackend.UNAUTHENTICATED)

        val result = client.resolve("https://open.spotify.com/track/track1")

        assertEquals("Fixture Song", result.songs.single().title)
        assertEquals(2, transfer.calls)
        assertEquals(2, tokens.calls)
    }

    // ------------------------------------------------------- client selection

    @Test
    fun theDefaultIsUnauthenticatedUntilBothCredentialsExist() {
        val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

        assertEquals(
            SpotifyBackend.UNAUTHENTICATED,
            SpotifyMetadataClients.default(http).backend,
        )
        assertEquals(
            SpotifyBackend.UNAUTHENTICATED,
            SpotifyMetadataClients.default(http, SpotifyCredentials("client-id", "")).backend,
        )
        assertEquals(
            SpotifyBackend.OFFICIAL_WEB_API,
            SpotifyMetadataClients.default(http, SpotifyCredentials("client-id", "client-secret")).backend,
        )
    }

    private class SequenceTransfer(
        private val first: HttpResponse,
        private val second: HttpResponse,
    ) : HttpTransfer {
        var calls = 0
        override suspend fun execute(request: HttpRequest): HttpResponse {
            calls++
            return if (calls == 1) first else second
        }
    }
}
