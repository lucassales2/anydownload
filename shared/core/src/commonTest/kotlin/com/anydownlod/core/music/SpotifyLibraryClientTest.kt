package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.fake.InMemorySpotifyListStore
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.postprocess.ToolkitCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four library queries and the on-device login. Every case uses a fake
 * token and public fixture JSON; no real Spotify login and no token in a file.
 */
class SpotifyLibraryClientTest {

    private val trackJson = """
        {"id":"track1","name":"Fixture Song","type":"track","duration_ms":200000,"explicit":false,
        "disc_number":1,"track_number":1,"external_ids":{"isrc":"ISRCFIXTURE01"},
        "external_urls":{"spotify":"https://open.spotify.com/track/track1"},
        "artists":[{"name":"Fixture Artist","id":"artist1"}],
        "album":{"id":"album1","name":"Fixture Album","album_type":"album","release_date":"2020-05-01",
        "total_tracks":1,"artists":[{"name":"Fixture Artist","id":"artist1"}],
        "images":[{"url":"https://i.scdn.co/image/fixture-cover","width":640,"height":640}]}}
    """.trimIndent()

    private val albumJson = """
        {"id":"album1","name":"Fixture Album","album_type":"album","release_date":"2020-05-01",
        "total_tracks":1,"artists":[{"name":"Fixture Artist","id":"artist1"}],"images":[],
        "external_urls":{"spotify":"https://open.spotify.com/album/album1"}}
    """.trimIndent()

    private val albumTracksJson = """
        {"items":[{"id":"track1","name":"Fixture Song","type":"track","duration_ms":200000,"explicit":false,
        "disc_number":1,"track_number":1,"artists":[{"name":"Fixture Artist","id":"artist1"}],
        "external_urls":{"spotify":"https://open.spotify.com/track/track1"}}],"next":null}
    """.trimIndent()

    private val playlistJson = """
        {"id":"playlist1","name":"Fixture Playlist","type":"playlist","images":[],
        "external_urls":{"spotify":"https://open.spotify.com/playlist/playlist1"},
        "tracks":{"total":1,"items":[],"next":null}}
    """.trimIndent()

    private val playlistTracksJson = """
        {"items":[{"is_local":false,"track":$trackJson}],"next":null}
    """.trimIndent()

    private val artistJson = """
        {"id":"artist1","name":"Fixture Artist","genres":["fixture pop"],"images":[],
        "external_urls":{"spotify":"https://open.spotify.com/artist/artist1"}}
    """.trimIndent()

    private val artistAlbumsJson = """
        {"items":[{"id":"album1","name":"Fixture Album","release_date":"2020-05-01","total_tracks":1,
        "artists":[{"name":"Fixture Artist","id":"artist1"}],"images":[]}],"next":null}
    """.trimIndent()

    private fun route(pattern: String, body: String) = FixtureRoute(
        urlPattern = pattern,
        contentType = "application/json",
        body = body,
    )

    private fun routes(): List<FixtureRoute> = listOf(
        route("https://api.spotify.com/v1/me/tracks*", """{"items":[{"track":$trackJson}],"next":null,"total":1}"""),
        route("https://api.spotify.com/v1/me/playlists*", """{"items":[{"id":"playlist1","name":"Fixture Playlist"}],"next":null}"""),
        route("https://api.spotify.com/v1/me/albums*", """{"items":[{"album":{"id":"album1","name":"Fixture Album"}}],"next":null}"""),
        route("https://api.spotify.com/v1/me/following*", """{"artists":{"items":[{"id":"artist1","name":"Fixture Artist"}],"next":null}}"""),
        route("https://api.spotify.com/v1/playlists/playlist1/tracks*", playlistTracksJson),
        route("https://api.spotify.com/v1/playlists/playlist1", playlistJson),
        route("https://api.spotify.com/v1/albums/album1/tracks*", albumTracksJson),
        route("https://api.spotify.com/v1/albums/album1", albumJson),
        route("https://api.spotify.com/v1/artists/artist1/albums*", artistAlbumsJson),
        route("https://api.spotify.com/v1/artists/artist1", artistJson),
        route("https://api.spotify.com/v1/tracks/*", trackJson),
        FixtureRoute(
            urlPattern = "https://open.spotify.com/embed/*",
            contentType = "text/html",
            body = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"settings":{"session":{"accessToken":"REDACTED"}}}}}}</script>""",
        ),
    )

    private fun client(
        tokenStore: SpotifyTokenStore,
        transfer: FixtureHttpTransfer = FixtureHttpTransfer(routes()),
    ): Pair<SpotifyLibraryClient, FixtureHttpTransfer> =
        SpotifyLibraryClient(ExtractorHttp(transfer), tokenStore) to transfer

    // ------------------------------------------------------------ no token

    @Test
    fun theFourLibraryQueriesFailTypedWithoutAToken() = runTest {
        val (library, transfer) = client(InMemorySpotifyTokenStore())
        for (query in listOf(
            SpotifyLibraryQuery.Saved,
            SpotifyLibraryQuery.UserPlaylists,
            SpotifyLibraryQuery.SavedAlbums,
            SpotifyLibraryQuery.FollowedArtists,
        )) {
            assertFailsWith<SpotifyMetadataError.NotAuthorized> { library.resolve(query) }
        }
        assertTrue(transfer.requests.isEmpty(), "no request may leave without a token")
    }

    // ------------------------------------------------------------ fixture token

    @Test
    fun savedReturnsRecordsWithAFixtureToken() = runTest {
        val store = InMemorySpotifyTokenStore().apply { save("fixture-token") }
        val (library, transfer) = client(store)

        val result = library.resolve(SpotifyLibraryQuery.Saved)

        assertEquals("Saved", result.name)
        val song = result.songs.single()
        assertEquals("Fixture Song", song.title)
        assertEquals(listOf("Fixture Artist"), song.artists)
        assertEquals("ISRCFIXTURE01", song.isrc)
        assertEquals("Saved", song.listName)
        assertEquals(1, song.listPosition)
        assertEquals(1, song.listLength)
        assertTrue(transfer.requests.all { it.authorization == "Bearer fixture-token" })
        assertTrue(transfer.requests.none { it.url.contains("fixture-token") }, "the token never enters a URL")
    }

    @Test
    fun userPlaylistsSavedAlbumsAndFollowedArtistsExpand() = runTest {
        val store = InMemorySpotifyTokenStore().apply { save("fixture-token") }
        val (library, _) = client(store)

        val playlists = library.resolve(SpotifyLibraryQuery.UserPlaylists)
        assertEquals("Fixture Playlist", playlists.songs.single().listName)
        assertEquals("Fixture Song", playlists.songs.single().title)

        val albums = library.resolve(SpotifyLibraryQuery.SavedAlbums)
        assertEquals("Fixture Album", albums.songs.single().album)
        assertEquals("Fixture Song", albums.songs.single().title)

        val artists = library.resolve(SpotifyLibraryQuery.FollowedArtists)
        assertEquals("Fixture Song", artists.songs.single().title)
    }

    @Test
    fun theLibraryQueryWordsParse() {
        assertEquals(SpotifyLibraryQuery.Saved, SpotifyLibraryQueries.parse("saved"))
        assertEquals(SpotifyLibraryQuery.UserPlaylists, SpotifyLibraryQueries.parse("all-user-playlists"))
        assertEquals(SpotifyLibraryQuery.SavedAlbums, SpotifyLibraryQueries.parse("all-user-saved-albums"))
        assertEquals(SpotifyLibraryQuery.FollowedArtists, SpotifyLibraryQueries.parse("all-user-followed-artists"))
        assertNull(SpotifyLibraryQueries.parse("https://open.spotify.com/track/x"))
    }

    // ------------------------------------------------------------------ auth

    @Test
    fun loginStoresTheTokenAndLogoutRemovesIt() = runTest {
        val store = InMemorySpotifyTokenStore()
        val auth = SpotifyAuthService(store)

        assertTrue(!auth.isLoggedIn())
        auth.login("fixture-token")
        assertTrue(auth.isLoggedIn())
        assertEquals("fixture-token", store.load())

        auth.logout()
        assertTrue(!auth.isLoggedIn())
        assertNull(store.load())

        val (library, _) = client(store)
        assertFailsWith<SpotifyMetadataError.NotAuthorized> { library.resolve(SpotifyLibraryQuery.Saved) }
    }

    @Test
    fun thePkceExchangeStoresTheToken() = runTest {
        val store = InMemorySpotifyTokenStore()
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://accounts.spotify.com/api/token",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"access_token":"fixture-access-token","token_type":"Bearer","expires_in":3600}""",
                ),
            ),
        )
        val auth = SpotifyAuthService(store, ExtractorHttp(transfer))

        val token = auth.exchangeCode(
            clientId = "fixture-client",
            code = "fixture-code",
            verifier = "fixture-verifier",
            redirectUri = "http://127.0.0.1:9900/callback",
        )

        assertEquals("fixture-access-token", token)
        assertEquals("fixture-access-token", store.load())
        val body = transfer.requests.single().body?.decodeToString().orEmpty()
        assertTrue(body.contains("code=fixture-code"), body)
        assertTrue(body.contains("code_verifier=fixture-verifier"), body)
        assertTrue("client_secret" !in body, "PKCE must not send a client secret")
    }

    @Test
    fun theAuthorizeUrlCarriesPkceAndScopes() {
        val auth = SpotifyAuthService(InMemorySpotifyTokenStore())
        val url = auth.authorizeUrl(
            clientId = "fixture-client",
            redirectUri = "http://127.0.0.1:9900/callback",
            codeChallenge = "fixture-challenge",
        )
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=fixture-challenge"))
        assertTrue(url.contains("scope=user-library-read%20user-follow-read%20playlist-read-private"))
    }

    // ------------------------------------------------- service integration

    @Test
    fun aPublicTrackStillWorksWithoutALogin() = runTest {
        val engine = InMemoryDownloadEngine()
        val tokenStore = InMemorySpotifyTokenStore()
        val transfer = FixtureHttpTransfer(routes())
        val service = SpotifyDownloadService(
            metadata = SpotifyMetadataClients.anonymous(ExtractorHttp(transfer)),
            matcher = AudioMatcher(listOf()),
            engine = engine,
            library = SpotifyLibraryClient(ExtractorHttp(transfer), tokenStore),
        )

        // The public path never touches the library client.
        val publicPreview = service.preview("https://open.spotify.com/track/track1")
        assertEquals("Fixture Song", publicPreview.songs.single().title)

        // The library path fails typed without a login.
        assertFailsWith<SpotifyMetadataError.NotAuthorized> { service.preview("saved") }
        assertTrue(engine.jobs.value.isEmpty())
    }

    @Test
    fun aFixtureTokenCanPreviewAndQueueTheLibrary() = runTest {
        val engine = InMemoryDownloadEngine()
        val tokenStore = InMemorySpotifyTokenStore().apply { save("fixture-token") }
        val transfer = FixtureHttpTransfer(routes())
        val provider = object : AudioProvider {
            override val source: AudioSource = AudioSource.YOUTUBE_MUSIC
            override val supportsIsrc: Boolean = false
            override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> = listOf(
                AudioCandidate(
                    source = source,
                    url = "https://music.youtube.com/watch?v=fixture",
                    title = "Fixture Song",
                    artists = listOf("Fixture Artist"),
                    durationSeconds = 200.0,
                    verified = true,
                ),
            )
        }
        val service = SpotifyDownloadService(
            metadata = SpotifyMetadataClients.anonymous(ExtractorHttp(transfer)),
            matcher = AudioMatcher(listOf(provider)),
            engine = engine,
            listStore = InMemorySpotifyListStore(),
            library = SpotifyLibraryClient(ExtractorHttp(transfer), tokenStore),
        )

        val preview = service.preview("saved")
        assertEquals(1, preview.songs.size)
        val report = service.queue(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(com.anydownlod.core.domain.AudioContainer.MP3)),
        )
        assertEquals(1, report.jobs.size)
        assertEquals("https://music.youtube.com/watch?v=fixture", report.jobs.single().request.sourceUrl)
    }
}
