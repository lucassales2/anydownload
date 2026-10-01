/*
 * RCTI+ extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `rcti.py` from
 * `yt_dlp/extractor/rcti.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rcti.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every URL form matches and fails typed. The API needs a visitor
 * access token minted by the `api.rctiplus.com/api/v1/visitor` endpoint and
 * sent as the `Authorization` header on every call; the port does not
 * translate the guest-token flow. No token or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.rcti

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The RCTI+ API needs a visitor access token minted by the visitor endpoint and sent as the " +
        "Authorization header; the port does not translate the guest-token flow."

private fun wall(): Nothing = throw ExtractionError.LoginRequired(WALL)

/** Upstream `RCTIPlusIE`: episodes, clips, extras, and events. */
class RCTIPlusIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "RCTIPlus"

        val VALID_URL: Regex = Regex(
            "https?://www\\.rctiplus\\.com/(?:programs/\\d+?/.*?/)?" +
                "(?<type>episode|clip|extra|live-event|missed-event)/(?<id>\\d+)/(?<displayId>[^/?#&]+)",
        )
    }
}

/** Upstream `RCTIPlusSeriesIE`: series pages. */
class RCTIPlusSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "RCTIPlusSeries"

        val VALID_URL: Regex = Regex(
            "https?://www\\.rctiplus\\.com/programs/(?<id>\\d+)/(?<displayId>[^/?#&]+)" +
                "(?:/(?<type>episodes|extras|clips))?",
        )
    }
}

/** Upstream `RCTIPlusTVIE`: the TV and event live pages. */
class RCTIPlusTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "RCTIPlusTV"

        val VALID_URL: Regex = Regex(
            "https?://www\\.rctiplus\\.com/((tv/(?<tvName>\\w+))|(?<eventName>live-event|missed-event))",
        )
    }
}
