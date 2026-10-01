/*
 * Udemy extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `udemy.py` from
 * `yt_dlp/extractor/udemy.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `udemy.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL forms match and fail typed. The lecture API needs an
 * authenticated Udemy session (login, enrollment, and the `_download_lecture`
 * endpoint), and the curriculum listing needs the same subscriber session;
 * the port has no Udemy login. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.udemy

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The Udemy API needs an authenticated session (login and enrollment) for lecture media and " +
        "subscriber curriculum items; the port has no Udemy login."

/** Upstream `UdemyIE`: a single lecture. */
class UdemyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "Udemy"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?udemy\\.com/" +
                "(?:[^#]+\\#/lecture/|lecture/view/?\\?lectureId=|[^/]+/learn/v4/t/lecture/)" +
                "(?<id>\\d+)",
        )
    }
}

/** Upstream `UdemyCourseIE`: a course curriculum. */
class UdemyCourseIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !UdemyIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "UdemyCourse"

        val VALID_URL: Regex = Regex("https?://(?:[^/]+\\.)?udemy\\.com/(?<id>[^/?#&]+)")
    }
}
