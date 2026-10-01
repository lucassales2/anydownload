/*
 * Pluralsight extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `pluralsight.py` from
 * `yt_dlp/extractor/pluralsight.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `pluralsight.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL forms match and fail typed. The player GraphQL bootstrap
 * and the legacy player payload need an authenticated Pluralsight
 * subscription session (login and authorization), and the clip playback
 * URLs come from that session; the port has no Pluralsight login. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.pluralsight

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The Pluralsight player GraphQL/legacy payload needs an authenticated subscription session and " +
        "the clip playback URLs come from that session; the port has no Pluralsight login."

/** Upstream `PluralsightIE`: the player pages. */
class PluralsightIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "Pluralsight"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|app)\\.)?pluralsight\\.com/(?:training/)?player\\?",
        )
    }
}

/** Upstream `PluralsightCourseIE`: the course pages. */
class PluralsightCourseIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "PluralsightCourse"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|app)\\.)?pluralsight\\.com/(?:library/)?courses/(?<id>[^/]+)",
        )
    }
}
