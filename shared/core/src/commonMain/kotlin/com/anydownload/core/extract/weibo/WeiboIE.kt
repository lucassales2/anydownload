/*
 * Weibo extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `weibo.py` from
 * `yt_dlp/extractor/weibo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `weibo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every URL form matches and fails typed. The Weibo API needs the
 * `passport.weibo.com` first-visit guest-cookie flow and visitor tokens
 * before any status/user JSON is readable; the port does not translate the
 * guest-token flow. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.weibo

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The Weibo API needs the passport.weibo.com first-visit guest-cookie flow and visitor tokens; " +
        "the port does not translate the guest-token flow."

private fun wall(): Nothing = throw ExtractionError.LoginRequired(WALL)

/** Upstream `WeiboIE`: a status page. */
class WeiboIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "Weibo"

        val VALID_URL: Regex = Regex(
            "https?://(?:m\\.weibo\\.cn/(?:status|detail)|(?:www\\.)?weibo\\.com/\\d+)/(?<id>[a-zA-Z0-9]+)",
        )
    }
}

/** Upstream `WeiboVideoIE`: a direct video page. */
class WeiboVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "WeiboVideo"

        private const val VIDEO_ID_RE = "\\d+:(?:[\\da-f]{32}|\\d{16,})"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?weibo\\.com/tv/show/(?<id>$VIDEO_ID_RE)|" +
                "https?://video\\.weibo\\.com/show/?\\?(?:[^#]+&)?fid=(?<id2>$VIDEO_ID_RE)",
        )
    }
}

/** Upstream `WeiboUserIE`: a user page. */
class WeiboUserIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "WeiboUser"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?weibo\\.com/u/(?<id>\\d+)")
    }
}
