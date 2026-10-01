/*
 * NekoHacker extractor — AnyDownload
 *
 * Kotlin translation of `nekohacker.py` from
 * `yt_dlp/extractor/nekohacker.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nekohacker.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `playlist` element's `li[data-audiopath]` tracks as selectable
 * `media` items (the port's multi-track model) with the track id/title/
 * duration, the `srp_player_params` artwork thumbnail, the release date, and
 * the no-playlist iframe handling. Limitations: the upstream playlist result
 * maps to `media` items, so `album`/`track`/`artists`/`track_number` are
 * dropped; the `url_result(url, 'Generic')` fallback becomes one child entry
 * at the iframe src (GenericIE stays out); a Spotify embed fails typed. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.nekohacker

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `NekoHackerIE`: one release page. */
class NekoHackerIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playlist = elementByClass(webpage, "playlist")

        if (playlist == null) {
            val iframeSrc = iframeSource(webpage)
                ?: throw ExtractionError.Unavailable("No playlist or embed found in webpage.")
            if (Regex("https?://(?:\\w+\\.)?spotify\\.com/").containsMatchIn(iframeSrc)) {
                throw ExtractionError.Unavailable("Spotify embeds are not supported.")
            }
            // Upstream `url_result(url, 'Generic')`: the port expands one child entry.
            return InfoDict(
                id = playlistId,
                entries = listOf(InfoEntry(url = iframeSrc)),
                webpageUrl = url,
                extractor = "nekohacker",
                extractorKey = ieKey,
            )
        }

        val playerParams = extractBalancedJson(webpage, Regex("var srp_player_params_[\\da-f]+\\s*="))
            as? JsonObject
        val artwork = (playerParams?.get("artwork") as? JsonPrimitive)?.content
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        val media = mutableListOf<InfoMedia>()
        for ((index, match) in TRACK_TAG.findAll(playlist).withIndex()) {
            val attributes = extractAttributes(match.value)
            val trackUrl = attributes["data-audiopath"]
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: continue
            val ext = ExtractorUtils.determineExt(trackUrl)
            val trackId = attributes["data-trackid"] ?: "track${index + 1}"
            media += InfoMedia(
                mediaId = trackId,
                title = attributes["data-tracktitle"],
                duration = ExtractorUtils.parseDuration(attributes["data-tracktime"]),
                thumbnails = artwork?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
                formats = listOf(
                    MediaFormat(
                        url = trackUrl,
                        ext = ext,
                        vcodec = "none",
                        acodec = if (ext == "mp3") "mp3" else null,
                    ),
                ),
            )
        }

        val albumTitle = TRACK_TAG.find(playlist)
            ?.let { extractAttributes(it.value)["data-albumtitle"] }
        return InfoDict(
            id = playlistId,
            title = albumTitle,
            media = media,
            webpageUrl = url,
            extractor = "nekohacker",
            extractorKey = ieKey,
        )
    }

    /** Upstream `find_element(tag='iframe')` + `extract_attributes` src. */
    private fun iframeSource(webpage: String): String? {
        val tag = Regex("(?s)<iframe\\b[^>]*>").find(webpage)?.value ?: return null
        return extractAttributes(tag)["src"]
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    companion object {
        const val IE_KEY: String = "NekoHacker"

        private val TRACK_TAG = Regex("<li[^>]+data-audiopath[^>]+>")

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?nekohacker\\.com/(?<id>(?!free-dl)[\\w-]+)")
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

/** Upstream `_search_json`: the balanced object after [marker]. */
private fun extractBalancedJson(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var index = start
    while (index < html.length) {
        val char = html[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return ExtractorUtils.parseJson(html.substring(start, index + 1))
                }
            }
        }
        index++
    }
    return null
}

/** Upstream `extract_attributes` for one tag. */
private fun extractAttributes(tag: String): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        val value = match.groupValues[2]
            .ifEmpty { match.groupValues[3] }
            .ifEmpty { match.groupValues[4] }
        attributes[match.groupValues[1].lowercase()] = value
    }
    return attributes
}

private val ATTRIBUTE = Regex(
    """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""",
)
