/*
 * Digital Concert Hall extractor — AnyDownload
 *
 * Kotlin translation of the URL surface of `digitalconcerthall.py` from
 * `yt_dlp/extractor/digitalconcerthall.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `digitalconcerthall.py` is not
 * vendored; see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the film/concert/work URL surface. The API needs an OAuth access
 * token from the browser local storage (upstream reads it from
 * `--username token --password ACCESS_TOKEN`), so every URL form matches and
 * fails typed. The OAuth client secret the upstream file carries is not
 * stored here.
 */
package com.anydownload.core.extract.digitalconcerthall

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `DigitalConcertHallIE`: a film, concert, or work page. */
class DigitalConcertHallIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The Digital Concert Hall API needs an OAuth access token from the browser local " +
                "storage, and the OAuth client secret is not stored by the port.",
        )
    }

    companion object {
        const val IE_KEY: String = "DigitalConcertHall"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?digitalconcerthall\\.com/(?<language>[a-z]+)/" +
                "(?<type>film|concert|work)/(?<id>[0-9]+)-?(?<part>[0-9]+)?",
        )
    }
}
