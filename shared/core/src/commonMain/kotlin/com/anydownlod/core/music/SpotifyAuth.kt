/*
 * Spotify user auth — AnyDownload
 *
 * The on-device access token for the four library queries. This is not an
 * AnyDownload account: the token belongs to the user's own Spotify login and
 * lives only in the host's secret store. It never enters a log, a record, a
 * fixture, or the vault. Public metadata queries never need it.
 *
 * spotDL v4.5.2 (commit cd4a4203) uses `--user-auth` for the same four
 * queries; no Python is copied or vendored.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.platform.HttpMethods
import kotlinx.serialization.json.JsonObject

/** On-device access-token storage. The value is never logged or returned in a record. */
interface SpotifyTokenStore {
    fun load(): String?

    fun save(token: String)

    fun clear()

    companion object {
        val Unavailable: SpotifyTokenStore = object : SpotifyTokenStore {
            override fun load(): String? = null

            override fun save(token: String) = Unit

            override fun clear() = Unit
        }
    }
}

/** Test and fake-host store; the desktop host uses a file under its state dir. */
class InMemorySpotifyTokenStore : SpotifyTokenStore {
    private var token: String? = null

    override fun load(): String? = token

    override fun save(token: String) {
        this.token = token.takeIf { it.isNotBlank() }
    }

    override fun clear() {
        token = null
    }
}

/** The stored user token as a [SpotifyTokenSource] for the official API. */
class SpotifyStoredTokenSource(private val store: SpotifyTokenStore) : SpotifyTokenSource {
    override suspend fun token(query: SpotifyQuery): SpotifyAccessToken =
        SpotifyAccessToken(
            store.load()?.takeIf { it.isNotBlank() }
                ?: throw SpotifyMetadataError.NotAuthorized("Log in to Spotify to use this query."),
        )

    override fun invalidate() = Unit
}

/**
 * Login state and the PKCE token exchange. [login] stores an already-obtained
 * access token (the host's browser flow and tests both use it); [logout]
 * deletes it. [exchangeCode] posts the authorization code to Spotify's token
 * endpoint with the PKCE verifier and stores the returned access token.
 */
class SpotifyAuthService(
    private val tokenStore: SpotifyTokenStore,
    private val http: ExtractorHttp? = null,
    private val tokenUrl: String = "https://accounts.spotify.com/api/token",
) {
    fun isLoggedIn(): Boolean = !tokenStore.load().isNullOrBlank()

    fun login(accessToken: String) {
        require(accessToken.isNotBlank()) { "The Spotify token is empty." }
        tokenStore.save(accessToken)
    }

    fun logout() {
        tokenStore.clear()
    }

    /** The authorize page the host opens; the challenge is built on the host. */
    fun authorizeUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        scopes: List<String> = DEFAULT_SCOPES,
    ): String = buildString {
        append("https://accounts.spotify.com/authorize")
        append("?response_type=code")
        append("&client_id=").append(encodeQueryComponent(clientId))
        append("&redirect_uri=").append(encodeQueryComponent(redirectUri))
        append("&code_challenge_method=S256")
        append("&code_challenge=").append(encodeQueryComponent(codeChallenge))
        append("&scope=").append(encodeQueryComponent(scopes.joinToString(" ")))
    }

    /** Exchanges one authorization code and stores the access token. */
    suspend fun exchangeCode(
        clientId: String,
        code: String,
        verifier: String,
        redirectUri: String,
    ): String {
        val client = http
            ?: throw SpotifyMetadataError.Unavailable("Spotify login is not available on this host.")
        val body = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(encodeQueryComponent(code))
            append("&redirect_uri=").append(encodeQueryComponent(redirectUri))
            append("&client_id=").append(encodeQueryComponent(clientId))
            append("&code_verifier=").append(encodeQueryComponent(verifier))
        }
        val json = try {
            client.downloadJson(
                url = tokenUrl,
                method = HttpMethods.POST,
                headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                body = body.encodeToByteArray(),
            ) as? JsonObject
        } catch (error: ExtractionError) {
            throw error.toSpotifyError()
        } ?: throw SpotifyMetadataError.Malformed()
        val token = json.str("access_token")
            ?: throw SpotifyMetadataError.NotAuthorized("Spotify did not return an access token.")
        tokenStore.save(token)
        return token
    }

    companion object {
        /** spotDL's `--user-auth` scopes. */
        val DEFAULT_SCOPES: List<String> = listOf(
            "user-library-read",
            "user-follow-read",
            "playlist-read-private",
        )
    }
}
