/*
 * TVer extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `tver.py` from
 * `yt_dlp/extractor/tver.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tver.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL forms match and fail typed. The platform API needs a
 * browser session (platform_uid/platform_token) and the streams come from
 * the Streaks backend via `StreaksBaseIE`, which the port does not
 * translate. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.tver

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The TVer platform API needs a browser session (platform_uid/platform_token) and the streams " +
        "come from the Streaks backend; the port does not translate the guest-session/Streaks flow."

/** Upstream `TVerIE`: the episode, series, and corner pages. */
class TVerIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "TVer"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?tver\\.jp/(?:(?<type>lp|corner|series|episodes?|feature)/)+(?<id>[a-zA-Z0-9]+)",
        )
    }
}

/** Upstream `TVerOlympicIE`: the Olympic pages. */
class TVerOlympicIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "TVerOlympic"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?tver\\.jp/olympic/milanocortina2026/(?<type>live|video)/play/(?<id>\\w+)",
        )
    }
}
