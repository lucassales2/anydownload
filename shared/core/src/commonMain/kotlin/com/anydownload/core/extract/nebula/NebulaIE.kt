/*
 * Nebula extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `nebula.py` from
 * `yt_dlp/extractor/nebula.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nebula.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every `nebula.tv`/`nebula.app`/`watchnebula.com` URL form matches
 * and fails typed. The content API needs a guest/account API token minted by
 * `users.api.nebula.app` and the m3u8 carries that token as a query token, so
 * the port does not translate the authorization flow, the token-bearing
 * manifest, the metadata walk, or the watch-progress PATCH. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.nebula

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val BASE_URL_RE =
    "https?://(?:www\\.|beta\\.)?(?:watchnebula\\.com|nebula\\.app|nebula\\.tv)"

private const val WALL =
    "The Nebula content API needs a guest/account API token and its m3u8 carries the token as a " +
        "query parameter; the port does not translate the token flow."

/** Upstream `NebulaIE`: a single video. */
class NebulaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "Nebula"

        val VALID_URL: Regex = Regex("$BASE_URL_RE/videos/(?<id>[\\w-]+)")
    }
}

/** Upstream `NebulaClassIE`: a class episode pair. */
class NebulaClassIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NebulaClass"

        val VALID_URL: Regex = Regex(
            "$BASE_URL_RE/(?!(?:myshows|library|videos)/)(?<id>[\\w-]+)/(?<ep>[\\w-]+)/?(?:$|[?#])",
        )
    }
}

/** Upstream `NebulaSubscriptionsIE`: the subscription feeds. */
class NebulaSubscriptionsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NebulaSubscriptions"

        val VALID_URL: Regex = Regex("$BASE_URL_RE/(?<id>myshows|library/latest-videos)/?(?:$|[?#])")
    }
}

/** Upstream `NebulaChannelIE`: a channel page. */
class NebulaChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NebulaChannel"

        val VALID_URL: Regex = Regex(
            "$BASE_URL_RE/(?!myshows|library|videos)(?<id>[\\w-]+)/?(?:$|[?#])",
        )
    }
}

/** Upstream `NebulaSeasonIE`: a season listing. */
class NebulaSeasonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NebulaSeason"

        val VALID_URL: Regex = Regex(
            "$BASE_URL_RE/(?<series>[\\w-]+)/season/(?<seasonNumber>[\\w-]+)",
        )
    }
}
