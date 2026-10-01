/*
 * Floatplane extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `floatplane.py` from
 * `yt_dlp/extractor/floatplane.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `floatplane.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL forms match and fails typed. Floatplane is a subscription
 * platform whose GraphQL API needs a `sails.sid` login session cookie
 * (upstream raises login-required without it); the port has no Floatplane
 * login. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.floatplane

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "Floatplane is a subscription platform whose GraphQL API needs a sails.sid login session " +
        "cookie; the port has no Floatplane login."

/** Upstream `FloatplaneIE`: a post. */
class FloatplaneIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "Floatplane"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|beta)\\.)?floatplane\\.com/post/(?<id>\\w+)",
        )
    }
}

/** Upstream `FloatplaneChannelIE`: a channel listing. */
class FloatplaneChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "FloatplaneChannel"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|beta)\\.)?floatplane\\.com/channel/(?<id>[\\w-]+)/home(?:/(?<channel>[\\w-]+))?",
        )
    }
}
