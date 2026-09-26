/*
 * Spotify metadata sessions — AnyDownload
 *
 * The unauthenticated client is the default. It lifts the anonymous session
 * the public Spotify embed page already carries and uses it only for public
 * Web API reads. The official client uses a stored client id and secret with
 * the client-credentials grant. Neither path is a user login: user OAuth is
 * T-092. Tokens and secrets are never logged, stored in fixtures, or written
 * into records.
 *
 * Reimplements the runtime selection in spotDL v4.5.2 (commit cd4a4203)
 * `utils/spotify.py` without copying its Python.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.platform.HttpMethods
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64

/** One metadata access token and, when Spotify gave one, its expiry. */
data class SpotifyAccessToken(
    val value: String,
    val expiresAtEpochMs: Long? = null,
)

/**
 * Supplies an access token for one query. Implementations cache internally;
 * [invalidate] drops the cache after a 401 so the client can retry once.
 */
fun interface SpotifyTokenSource {
    suspend fun token(query: SpotifyQuery): SpotifyAccessToken

    fun invalidate() = Unit
}

/**
 * The unauthenticated source. It reads the anonymous session that the public
 * embed page carries in its `__NEXT_DATA__` document. The page is fetched for
 * the query's own entity when there is one; a text search uses a public
 * Spotify editorial playlist only as the bootstrap page. No credentials are
 * involved and nothing about the session is persisted.
 */
class SpotifyEmbedTokenSource(
    private val http: ExtractorHttp,
    /** Overrides the bootstrap page; tests use a fixture URL. */
    private val bootstrapOverride: String? = null,
) : SpotifyTokenSource {

    private var cached: SpotifyAccessToken? = null

    override suspend fun token(query: SpotifyQuery): SpotifyAccessToken {
        cached?.let { return it }
        val bootstrap = bootstrapOverride ?: query.embedUrl() ?: DEFAULT_BOOTSTRAP_URL
        val page = try {
            http.downloadWebpage(bootstrap, headers = BROWSER_HEADERS)
        } catch (error: ExtractionError) {
            throw error.toSpotifyError()
        }
        val token = parseEmbedSession(page)
            ?: throw SpotifyMetadataError.NotAuthorized("The public Spotify page did not return a metadata session.")
        cached = token
        return token
    }

    override fun invalidate() {
        cached = null
    }

    companion object {
        /**
         * A public Spotify editorial playlist, used only as the token
         * bootstrap for text searches (an entity query bootstraps from its
         * own embed page). It is a public page, not a secret.
         */
        const val DEFAULT_BOOTSTRAP_URL: String =
            "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M"

        private val BROWSER_HEADERS = mapOf(
            "accept" to "text/html",
            "accept-language" to "en",
            "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        )

        private val nextData = Regex(
            """<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** Reads `settings.session` out of a public embed page, or null. */
        internal fun parseEmbedSession(html: String): SpotifyAccessToken? {
            val match = nextData.find(html) ?: return null
            val root = runCatching { Json.parseToJsonElement(match.groupValues[1]) }.getOrNull()
                as? JsonObject ?: return null
            val session = root.obj("props")
                ?.obj("pageProps")
                ?.obj("state")
                ?.obj("settings")
                ?.obj("session") ?: return null
            val value = session.str("accessToken") ?: return null
            return SpotifyAccessToken(value, session.num("accessTokenExpirationTimestampMs"))
        }
    }
}

/**
 * The official Web API source. Sends the stored client id and secret to the
 * Spotify token endpoint with the client-credentials grant. The secret is
 * never logged; the token stays in memory for this client only. User OAuth
 * (a Spotify login) is T-092 and is not implemented here.
 */
class SpotifyClientCredentialsTokenSource(
    private val http: ExtractorHttp,
    private val clientId: String,
    private val clientSecret: String,
    private val tokenUrl: String = "https://accounts.spotify.com/api/token",
) : SpotifyTokenSource {

    private var cached: SpotifyAccessToken? = null

    override suspend fun token(query: SpotifyQuery): SpotifyAccessToken {
        cached?.let { return it }
        val credentials = Base64.Default.encode("$clientId:$clientSecret".encodeToByteArray())
        val json = try {
            http.downloadJson(
                url = tokenUrl,
                method = HttpMethods.POST,
                headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                body = "grant_type=client_credentials".encodeToByteArray(),
                authorization = "Basic $credentials",
            ) as? JsonObject
        } catch (error: ExtractionError) {
            throw error.toSpotifyError()
        } ?: throw SpotifyMetadataError.Malformed()
        val value = json.str("access_token")
            ?: throw SpotifyMetadataError.NotAuthorized("Spotify did not accept the stored client credentials.")
        val expiresIn = json.num("expires_in")
        val token = SpotifyAccessToken(
            value = value,
            expiresAtEpochMs = expiresIn?.let { it * 1000L },
        )
        cached = token
        return token
    }

    override fun invalidate() {
        cached = null
    }
}

/** Maps the shared extractor failures to the Spotify metadata taxonomy. */
internal fun ExtractionError.toSpotifyError(): SpotifyMetadataError = when (this) {
    is ExtractionError.LoginRequired -> SpotifyMetadataError.NotAuthorized()
    is ExtractionError.Malformed -> SpotifyMetadataError.Malformed(message ?: "Spotify returned an unexpected response.")
    is ExtractionError.UnsupportedUrl -> SpotifyMetadataError.Unavailable(message ?: "Spotify metadata could not be loaded.")
    else -> SpotifyMetadataError.Unavailable(message ?: "Spotify metadata could not be loaded.")
}
