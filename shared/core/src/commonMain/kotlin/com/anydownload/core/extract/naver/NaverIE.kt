/*
 * Naver extractors — AnyDownload
 *
 * Kotlin translation of `naver.py` from `yt_dlp/extractor/naver.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `naver.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL families match and fail typed. Every Naver path starts at
 * the `now_web_api` `play-info` endpoint, which needs a `md` HMAC-SHA1
 * signature over a fixed key the upstream file embeds; the port does not add
 * a crypto helper that carries that key (the same rule as `zingmp3` and
 * `abc`), so `NaverIE` and `NaverLiveIE` fail typed Unavailable. The
 * `play.rmcnmv` `_extract_video_info` walk and the `process_subtitles`
 * helper (upstream notes it is used by WeverseIE, whose port has its own
 * path) are not translated. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.naver

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val SIGNING_WALL =
    "The Naver now_web API needs an HMAC-SHA1 md signature over a fixed key; " +
        "the port does not embed that key."

/** Upstream `NaverBaseIE`: the shared signed-API wall. */
abstract class NaverBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected fun signingWall(): Nothing = throw ExtractionError.Unavailable(SIGNING_WALL)
}

/** Upstream `NaverIE`: a tv.naver.com video or embed. */
class NaverIE(
    http: ExtractorHttp,
) : NaverBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_call_api('/clips/{id}/play-info')`: the request needs the
        // signed `md` parameter.
        signingWall()
    }

    companion object {
        const val IE_KEY: String = "Naver"

        val VALID_URL: Regex = Regex(
            "https?://(?:m\\.)?tv(?:cast)?\\.naver\\.com/(?:v|embed)/(?<id>\\d+)",
        )
    }
}

/** Upstream `NaverLiveIE`: a tv.naver.com live channel. */
class NaverLiveIE(
    http: ExtractorHttp,
) : NaverBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_call_api('/live-end/normal/{id}/play-info')`: the request
        // needs the signed `md` parameter.
        signingWall()
    }

    companion object {
        const val IE_KEY: String = "NaverLive"

        val VALID_URL: Regex = Regex(
            "https?://(?:m\\.)?tv(?:cast)?\\.naver\\.com/l/(?<id>\\d+)",
        )
    }
}
