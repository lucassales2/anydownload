/*
 * PLAY (GoPlay) extractor — AnyDownload
 *
 * Kotlin translation of the URL surface of `goplay.py` from
 * `yt_dlp/extractor/goplay.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `goplay.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the URL form matches and fails typed. The PLAY long-form API needs
 * an AWS Cognito login bearer token (the upstream `AwsIdp` SRP flow), and
 * the streams are DRM/SSAI-gated; the port has no PLAY login. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.goplay

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `GoPlayIE`: the play.tv video pages. */
class GoPlayIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(
        "The PLAY long-form API needs an AWS Cognito login bearer token and the streams are " +
            "DRM/SSAI-gated; the port has no PLAY login.",
    )

    companion object {
        const val IE_KEY: String = "GoPlay"

        val VALID_URL: Regex = Regex("https?://(www\\.)?play\\.tv/video/([^/?#]+/[^/?#]+/|)(?<id>[^/#]+)")
    }
}
