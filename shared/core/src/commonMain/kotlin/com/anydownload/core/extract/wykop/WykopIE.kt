/*
 * Wykop extractors — AnyDownload
 *
 * Kotlin translation of the URL shapes of `wykop.py` from
 * `yt_dlp/extractor/wykop.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `wykop.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: all four URL families match and fail typed. Every API path starts at
 * `wykop.pl/api/v3/auth`, which mints an anonymous bearer token from a
 * frontend key/secret the upstream file embeds; the port does not carry that
 * credential (the same rule as `naver`, `zingmp3`, and `abc`), so the four
 * classes fail typed Unavailable. The `_common_data_extract` metadata walk
 * and the transparent-URL child resolution are not translated. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.wykop

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val TOKEN_WALL =
    "The Wykop API needs an anonymous bearer token minted from a frontend " +
        "key/secret; the port does not embed that credential."

/** Upstream `WykopBaseIE`: the shared token wall. */
abstract class WykopBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected fun tokenWall(): Nothing = throw ExtractionError.Unavailable(TOKEN_WALL)
}

/** Upstream `WykopDigIE`: a link (dig) page. */
class WykopDigIE(
    http: ExtractorHttp,
) : WykopBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !WykopDigCommentIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_call_api('links/{id}')`: the request needs the bearer token.
        tokenWall()
    }

    companion object {
        const val IE_KEY: String = "WykopDig"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?wykop\\.pl/link/(?<id>\\d+)")
    }
}

/** Upstream `WykopDigCommentIE`: one comment of a link page. */
class WykopDigCommentIE(
    http: ExtractorHttp,
) : WykopBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_call_api('links/{dig_id}/comments/{id}')`: the request needs the bearer token.
        tokenWall()
    }

    companion object {
        const val IE_KEY: String = "WykopDigComment"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?wykop\\.pl/link/(?<digid>\\d+)/[^/]+/komentarz/(?<id>\\d+)",
        )
    }
}

/** Upstream `WykopPostIE`: an entry (wpis) page. */
class WykopPostIE(
    http: ExtractorHttp,
) : WykopBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !WykopPostCommentIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_call_api('entries/{id}')`: the request needs the bearer token.
        tokenWall()
    }

    companion object {
        const val IE_KEY: String = "WykopPost"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?wykop\\.pl/wpis/(?<id>\\d+)")
    }
}

/** Upstream `WykopPostCommentIE`: one comment of an entry page. */
class WykopPostCommentIE(
    http: ExtractorHttp,
) : WykopBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_call_api('entries/{post_id}/comments/{id}')`: the request needs the bearer token.
        tokenWall()
    }

    companion object {
        const val IE_KEY: String = "WykopPostComment"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?wykop\\.pl/wpis/(?<postid>\\d+)/[^/#]+#(?<id>\\d+)",
        )
    }
}
