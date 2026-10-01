/*
 * Z-aN extractor — AnyDownload
 *
 * Kotlin translation of the URL surface of `zan.py` from
 * `yt_dlp/extractor/zan.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `zan.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the play page is fetched, the geo error element maps to a typed
 * GeoRestricted(JP), and every other page fails typed LoginRequired. The
 * play flow needs the `csrf-token`/`vod-pct`/`live-player-token` meta values
 * (absent for an anonymous visitor, so upstream itself raises a login
 * requirement) and sends them to `/api/live/{id}/getLiveStatus` with an
 * `X-Csrf-Token` header the platform allowlist refuses; the port carries
 * neither (the streaks/sonyliv header rule). The m3u8 DISPLAY-NAME resolution
 * fixup, the multi-angle crop formats (ffmpeg args), and the detail-page
 * metadata walk are not translated. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.zan

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The Z-aN play API needs the session csrf-token/vod-pct/live-player-token meta values " +
        "and the X-Csrf-Token header the platform allowlist refuses, which the port does not carry."

/** Upstream `ZanIE`: a zan-live.com play page. */
class ZanIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        // Upstream: `find_element(cls='p-common_message__headline--error')` -> geo restricted.
        val geoMessage = elementByClass(webpage, "p-common_message__headline--error")
            ?.let { cleanHtml(it) }
        if (!geoMessage.isNullOrEmpty()) {
            throw ExtractionError.GeoRestricted(countries = listOf("JP"))
        }

        throw ExtractionError.LoginRequired(WALL)
    }

    companion object {
        const val IE_KEY: String = "Zan"

        val VALID_URL: Regex = Regex(
            "https?://(www\\.)?zan-live\\.com/[^/?#]+/live/play/\\d+/(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `get_element_by_class` with tag-balanced slicing. */
private fun elementByClass(webpage: String, className: String): String? {
    val open = Regex(
        "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
            Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>",
    ).find(webpage) ?: return null
    val tag = open.groupValues[1]
    val start = open.range.last + 1
    var depth = 1
    var index = start
    val tagRegex = Regex("</?$tag\\b[^>]*>", RegexOption.IGNORE_CASE)
    while (index < webpage.length) {
        val match = tagRegex.find(webpage, index) ?: break
        if (match.value.startsWith("</")) {
            depth--
            if (depth == 0) return webpage.substring(start, match.range.first)
        } else {
            depth++
        }
        index = match.range.last + 1
    }
    return null
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}
