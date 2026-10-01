/*
 * Red Bee extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `redbee.py` from
 * `yt_dlp/extractor/redbee.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `redbee.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the URL surface of ParliamentLive UK and RTBF. The Exposure API
 * needs an anonymous device bearer token (RTBF also needs a gigya JWT), so
 * every URL form matches and fails typed. No device id, token, or media URL
 * is stored here.
 */
package com.anydownload.core.extract.redbee

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val LOGIN_WALL =
    "The Red Bee Exposure API needs an anonymous device bearer token, which the port does not carry."

/** Upstream `ParliamentLiveUKIE`: a UK parliament event. */
class ParliamentLiveUKIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "ParliamentLiveUK"

        val VALID_URL: Regex = Regex(
            "(?i)https?://(?:www\\.)?parliamentlive\\.tv/Event/Index/" +
                "(?<id>[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12})",
        )
    }
}

/** Upstream `RTBFIE`: an RTBF video. */
class RTBFIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "RTBF"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtbf\\.be/" +
                "(?:video/[^?]+\\?.*?\\bid=|ouftivi/(?:[^/]+/)*[^?]+\\?.*?\\bvideoId=|" +
                "auvio/[^/]+\\?.*?\\b(?:l)?id=)(?<id>\\d+)",
        )
    }
}
