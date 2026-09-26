/*
 * Spotify metadata client — AnyDownload
 *
 * The seam T-085 (match), T-086 (download), and T-092 (user library) build
 * on. The default client is unauthenticated; a stored client id and secret
 * select the official Web API. Neither is an AnyDownload account, and no
 * client downloads audio.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp

/** Which session backs a client. The UI may show this, never a token. */
enum class SpotifyBackend {
    UNAUTHENTICATED,
    OFFICIAL_WEB_API,
}

/**
 * A stored client id and secret. [isComplete] is the only thing that selects
 * the official Web API; a partial pair stays on the unauthenticated client.
 */
data class SpotifyCredentials(
    val clientId: String = "",
    val clientSecret: String = "",
) {
    val isComplete: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    /** Never render a secret: only presence is safe to show. */
    override fun toString(): String =
        "SpotifyCredentials(clientId=${if (clientId.isBlank()) "absent" else "present"}, " +
            "clientSecret=${if (clientSecret.isBlank()) "absent" else "present"})"
}

/**
 * Resolves a Spotify URL or search into song records. Implementations must
 * not download media, call YouTube, log a URL or token, or write a file.
 */
interface SpotifyMetadataClient {
    val backend: SpotifyBackend

    /** Resolves one parsed query; per-entry failures stay in the result. */
    suspend fun resolve(query: SpotifyQuery): SongListResult

    /** Parses [raw] and resolves it; blank input fails typed. */
    suspend fun resolve(raw: String): SongListResult {
        val query = SpotifyQueryParser.parse(raw)
            ?: throw SpotifyMetadataError.BadQuery()
        return resolve(query)
    }
}

/** Builds the default (unauthenticated) or official client. */
object SpotifyMetadataClients {

    /** Anonymous unless both stored credential fields are present. */
    fun default(http: ExtractorHttp, credentials: SpotifyCredentials? = null): SpotifyMetadataClient =
        if (credentials?.isComplete == true) {
            official(http, credentials)
        } else {
            anonymous(http)
        }

    /** The default client: the public page's anonymous session, no account. */
    fun anonymous(http: ExtractorHttp): SpotifyMetadataClient = SpotifyWebApiClient(
        http = http,
        tokens = SpotifyEmbedTokenSource(http),
        backend = SpotifyBackend.UNAUTHENTICATED,
    )

    /** The official Web API client, selected only for complete credentials. */
    fun official(http: ExtractorHttp, credentials: SpotifyCredentials): SpotifyMetadataClient =
        SpotifyWebApiClient(
            http = http,
            tokens = SpotifyClientCredentialsTokenSource(
                http = http,
                clientId = credentials.clientId,
                clientSecret = credentials.clientSecret,
            ),
            backend = SpotifyBackend.OFFICIAL_WEB_API,
        )
}
