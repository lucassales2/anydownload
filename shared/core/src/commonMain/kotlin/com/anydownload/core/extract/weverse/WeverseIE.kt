/*
 * Weverse extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `weverse.py` from
 * `yt_dlp/extractor/weverse.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `weverse.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the artist live/media/moment post URLs and the artist tab URLs
 * match and fail typed as an account/login wall. The Weverse API needs the
 * `we2_access_token`/`we2_refresh_token` cookies (or an OAuth refresh
 * token), a device id, and HMAC-SHA1-signed query parameters; the port has
 * no extractor login flow or cookie read, so the token exchange, the signed
 * API calls, and the guest `/preview` path are not translated. No cookie,
 * bearer token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.weverse

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `WeverseBaseIE`: the shared account-wall failure. */
abstract class WeverseBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Every Weverse API path needs the account tokens the port does not hold. */
    protected fun accountWall(): Nothing = throw ExtractionError.LoginRequired(ACCOUNT_WALL_MESSAGE)

    companion object {
        /** The one-sentence reason on every Weverse URL form. */
        const val ACCOUNT_WALL_MESSAGE: String =
            "The Weverse API needs a Weverse account token cookie (we2_access_token/" +
                "we2_refresh_token) and HMAC-signed requests; this account API is not translated."
    }
}

/** Upstream `WeverseIE`: the artist live post pages. */
class WeverseIE(
    http: ExtractorHttp,
) : WeverseBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = accountWall()

    companion object {
        const val IE_KEY: String = "Weverse"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?weverse\\.io/(?<artist>[^/?#]+)/live/(?<id>[\\d-]+)",
        )
    }
}

/** Upstream `WeverseMediaIE`: the artist media post pages. */
class WeverseMediaIE(
    http: ExtractorHttp,
) : WeverseBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = accountWall()

    companion object {
        const val IE_KEY: String = "WeverseMedia"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?weverse\\.io/(?<artist>[^/?#]+)/media/(?<id>[\\d-]+)",
        )
    }
}

/** Upstream `WeverseMomentIE`: the artist moment post pages. */
class WeverseMomentIE(
    http: ExtractorHttp,
) : WeverseBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = accountWall()

    companion object {
        const val IE_KEY: String = "WeverseMoment"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?weverse\\.io/(?<artist>[^/?#]+)/moment/" +
                "(?<uid>[\\da-f]+)/post/(?<id>[\\d-]+)",
        )
    }
}

/** Upstream `WeverseTabBaseIE`: the artist tab listings. */
abstract class WeverseTabBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : WeverseBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `WeverseLiveTabIE`: the artist live tab. */
class WeverseLiveTabIE(
    http: ExtractorHttp,
) : WeverseTabBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = accountWall()

    companion object {
        const val IE_KEY: String = "WeverseLiveTab"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?weverse\\.io/(?<id>[^/?#]+)/live/?(?:[?#]|$)",
        )
    }
}

/** Upstream `WeverseMediaTabIE`: the artist media tab. */
class WeverseMediaTabIE(
    http: ExtractorHttp,
) : WeverseTabBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = accountWall()

    companion object {
        const val IE_KEY: String = "WeverseMediaTab"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?weverse\\.io/(?<id>[^/?#]+)/media(?:/|/all|/new)?(?:[?#]|$)",
        )
    }
}

/** Upstream `WeverseLiveIE`: the artist root live page. */
class WeverseLiveIE(
    http: ExtractorHttp,
) : WeverseBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = accountWall()

    companion object {
        const val IE_KEY: String = "WeverseLive"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?weverse\\.io/(?<id>[^/?#]+)/?(?:[?#]|$)",
        )
    }
}
