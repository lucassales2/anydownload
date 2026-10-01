/*
 * RTP extractor — AnyDownload
 *
 * Kotlin translation of the URL surface of `rtp.py` from
 * `yt_dlp/extractor/rtp.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rtp.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the RTP Play URL surface. The API path needs the guest auth token
 * from `rtpplayapi.rtp.pt/play/api/2/token-manager` (the upstream file
 * carries the mobile auth hash headers), and the HTML fallback's obfuscated
 * player data is not translated, so every URL form matches and fails typed.
 * No auth hash, token, or media URL is stored here.
 */
package com.anydownload.core.extract.rtp

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `RTPIE`: an RTP Play episode. */
class RTPIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The RTP Play API needs a guest auth token, and the obfuscated HTML player data is not translated.",
        )
    }

    companion object {
        const val IE_KEY: String = "RTP"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtp\\.pt/play/(?:[^/#?]+/)?" +
                "(?<program>p\\d+)/(?<episode>e\\d+)(?:/[^/#?]+/(?<asset>\\d+))?",
        )
    }
}
