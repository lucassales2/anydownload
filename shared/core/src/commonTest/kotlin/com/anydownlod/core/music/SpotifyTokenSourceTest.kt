package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Token acquisition for both backends. The only token-shaped value here is
 * the repository's `REDACTED` placeholder; no real session or secret exists
 * in this file or its fixtures.
 */
class SpotifyTokenSourceTest {

    private val embedPage = """
        <!doctype html><html><body>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"state":{"settings":{"session":{"accessToken":"REDACTED","accessTokenExpirationTimestampMs":9999999999999,"isAnonymous":true}}}}}}
        </script>
        </body></html>
    """.trimIndent()

    private val trackJson = """
        {"id":"track1","name":"Fixture Song","type":"track","duration_ms":1000,
        "artists":[{"name":"Fixture Artist"}],"album":{"id":"album1","name":"Fixture Album"}}
    """.trimIndent()

    @Test
    fun theEmbedSessionParsesTokenAndExpiry() {
        val token = SpotifyEmbedTokenSource.parseEmbedSession(embedPage)
        assertNotNull(token)
        assertEquals("REDACTED", token.value)
        assertEquals(9_999_999_999_999L, token.expiresAtEpochMs)
    }

    @Test
    fun aPageWithoutANextDataSessionHasNoToken() {
        assertNull(SpotifyEmbedTokenSource.parseEmbedSession("<html><body>no session</body></html>"))
        assertNull(
            SpotifyEmbedTokenSource.parseEmbedSession(
                """<script id="__NEXT_DATA__" type="application/json">{"props":{}}</script>""",
            ),
        )
    }

    @Test
    fun anEntityQueryBootstrapsFromItsOwnEmbedPageAndCaches() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://open.spotify.com/embed/track/*",
                    contentType = "text/html",
                    body = embedPage,
                ),
            ),
        )
        val source = SpotifyEmbedTokenSource(ExtractorHttp(transfer))
        val query = SpotifyQueryParser.parse("https://open.spotify.com/track/track1")!!

        assertEquals("REDACTED", source.token(query).value)
        assertEquals("REDACTED", source.token(query).value)
        assertEquals(1, transfer.requests.size, "the session is cached for the client's lifetime")
        assertTrue(transfer.requests.single().url.startsWith("https://open.spotify.com/embed/track/"))

        source.invalidate()
        source.token(query)
        assertEquals(2, transfer.requests.size)
    }

    @Test
    fun aTextSearchBootstrapsFromThePublicPlaylistPage() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://open.spotify.com/embed/playlist/*",
                    contentType = "text/html",
                    body = embedPage,
                ),
            ),
        )
        val source = SpotifyEmbedTokenSource(ExtractorHttp(transfer))
        val query = SpotifyQueryParser.parse("Fixture Artist - Fixture Song")!!
        assertEquals("REDACTED", source.token(query).value)
        assertEquals(SpotifyEmbedTokenSource.DEFAULT_BOOTSTRAP_URL, transfer.requests.single().url)
    }

    @Test
    fun aPageWithoutASessionFailsTyped() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://open.spotify.com/embed/*",
                    contentType = "text/html",
                    body = "<html><body>no session</body></html>",
                ),
            ),
        )
        val source = SpotifyEmbedTokenSource(ExtractorHttp(transfer))
        assertFailsWith<SpotifyMetadataError.NotAuthorized> {
            source.token(SpotifyQueryParser.parse("https://open.spotify.com/track/track1")!!)
        }
    }

    @Test
    fun clientCredentialsPostTheBasicHeaderAndParseTheResponse() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://accounts.spotify.com/api/token",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"access_token":"REDACTED","token_type":"Bearer","expires_in":3600}""",
                ),
            ),
        )
        val source = SpotifyClientCredentialsTokenSource(
            http = ExtractorHttp(transfer),
            clientId = "fixture-client",
            clientSecret = "fixture-secret",
        )

        val token = source.token(SpotifyQueryParser.parse("track:fixture")!!)
        assertEquals("REDACTED", token.value)
        assertEquals(3_600_000L, token.expiresAtEpochMs)

        val request = transfer.requests.single()
        val expected = Base64.Default.encode("fixture-client:fixture-secret".encodeToByteArray())
        assertEquals("Basic $expected", request.authorization)
        assertTrue(request.headers["content-type"]?.startsWith("application/x-www-form-urlencoded") == true)
    }

    @Test
    fun aRejectedCredentialRequestFailsTyped() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://accounts.spotify.com/api/token",
                    method = "POST",
                    statusCode = 401,
                    contentType = "application/json",
                    body = """{"error":"invalid_client"}""",
                ),
            ),
        )
        val source = SpotifyClientCredentialsTokenSource(
            http = ExtractorHttp(transfer),
            clientId = "fixture-client",
            clientSecret = "fixture-secret",
        )
        assertFailsWith<SpotifyMetadataError.NotAuthorized> {
            source.token(SpotifyQueryParser.parse("track:fixture")!!)
        }
    }

    @Test
    fun theMetadataClientUsesTheAnonymousSessionWhenNoCredentialsExist() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://open.spotify.com/embed/track/*",
                    contentType = "text/html",
                    body = embedPage,
                ),
                FixtureRoute(
                    urlPattern = "https://api.spotify.com/v1/tracks/*",
                    contentType = "application/json",
                    body = trackJson,
                ),
            ),
        )
        val client = SpotifyMetadataClients.default(ExtractorHttp(transfer))
        val result = client.resolve("https://open.spotify.com/track/track1")
        assertEquals("Fixture Song", result.songs.single().title)
        val apiRequest = transfer.requests.last()
        assertEquals("Bearer REDACTED", apiRequest.authorization)
    }
}
