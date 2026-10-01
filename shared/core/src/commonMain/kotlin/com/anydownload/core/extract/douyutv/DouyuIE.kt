/*
 * Douyu extractors — AnyDownload
 *
 * Kotlin translation of `douyutv.py` from `yt_dlp/extractor/douyutv.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `douyutv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL families match and the room page's live checks are
 * translated (`$ROOM.room_id`, `videoLoop`, `show_status`). The stream
 * signature needs the page's `ub98484234` JS function executed with the
 * crypto-js/md5 dependency (upstream `_calc_sign` runs it through
 * PhantomJS), which is jsinterp and stays out of the port, so a live room
 * fails typed. The room API metadata path and the `$DATA` video metadata are
 * not translated. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.douyutv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val JS_SIGN_WALL =
    "The Douyu stream signature needs the page JS signing function executed in a JS runtime, " +
        "which the port excludes."

/** Upstream `DouyuBaseIE`: the shared typed JS-signing wall. */
abstract class DouyuBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected fun jsSigningWall(): Nothing = throw ExtractionError.Unavailable(JS_SIGN_WALL)
}

/** Upstream `DouyuTVIE`: a douyu.com live room. */
class DouyuTVIE(
    http: ExtractorHttp,
) : DouyuBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val roomId = ExtractorUtils.searchRegex("\\\$ROOM\\.room_id\\s*=\\s*(\\d+)", webpage)
            ?: throw ExtractionError.Malformed("The Douyu room page had no room id.")
        if (ExtractorUtils.searchRegex("\"videoLoop\"\\s*:\\s*(\\d+)", webpage) == "1") {
            throw ExtractionError.Unavailable("The channel is auto-playing VODs.")
        }
        if (
            ExtractorUtils.searchRegex("\\\$ROOM\\.show_status\\s*=\\s*(\\d+)", webpage) == "2"
        ) {
            throw ExtractionError.Unavailable("This Douyu channel is not live.")
        }
        // Upstream `_get_sign_func` + `_calc_sign`: the stream formats need
        // the page JS sign function, which the port excludes.
        jsSigningWall()
    }

    companion object {
        const val IE_KEY: String = "DouyuTV"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?douyu(?:tv)?\\.com/" +
                "(?:topic/\\w+\\?rid=|(?:[^/]+/))*(?<id>[A-Za-z0-9]+)",
        )
    }
}

/** Upstream `DouyuShowIE`: a v.douyu.com VOD show. */
class DouyuShowIE(
    http: ExtractorHttp,
) : DouyuBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream parses `window.$DATA` for the metadata and then signs the
        // getStreamUrl request with the page JS function; the port stops at
        // the wall.
        http.downloadWebpage(url)
        jsSigningWall()
    }

    companion object {
        const val IE_KEY: String = "DouyuShow"

        val VALID_URL: Regex = Regex(
            "https?://v(?:mobile)?\\.douyu\\.com/show/(?<id>[0-9a-zA-Z]+)",
        )
    }
}
