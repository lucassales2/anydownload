/*
 * FC2 extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `fc2.py` from
 * `yt_dlp/extractor/fc2.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `fc2.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the FC2 content page and `videoplaylist` JSON (title/thumbnail/
 * description and the single play row, native for an HLS URL), the
 * `flv2.swf` embed delegation with the computed thumbnail, and the
 * live.fc2.com URL forms. The upstream netrc login and session-cookie clear
 * are not translated (a 401 is the typed login wall), and the live class
 * matches and fails typed because the HLS playlist arrives over the FC2
 * WebSocket control channel, which the port does not implement. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.fc2

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `FC2IE`: an FC2 video content page. */
class FC2IE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        var title: String? = null
        var thumbnail: String? = null
        var description: String? = null
        if (!url.startsWith("fc2:")) {
            val webpage = http.downloadWebpage(url)
            title = ExtractorUtils.searchRegex(
                "<h2\\s+class=\"videoCnt_title\">([^<]+?)</h2>",
                webpage,
            ) ?: ExtractorUtils.searchRegex(
                "\\s+href=\"[^\"]+\"\\s*title=\"([^\"]+?)\"\\s*rel=\"nofollow\">\\s*<img",
                webpage,
            )
            thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image")
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
        }
        val vidPlaylist = http.downloadJson("https://video.fc2.com/api/v3/videoplaylist/$videoId?sh=1&fs=0")
            as? JsonObject ?: throw ExtractionError.Malformed("The FC2 playlist API was not an object.")
        val rawUrl = vidPlaylist.obj("playlist")?.str("nq")
            ?: throw ExtractionError.Unavailable("Unable to extract the FC2 video URL.")
        val vidUrl = urlJoin("https://video.fc2.com/", rawUrl)
        val isHls = vidPlaylist.number("type")?.toInt() == 2
        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = listOf(
                MediaFormat(
                    url = vidUrl,
                    ext = "mp4",
                    protocol = if (isHls) "m3u8_native" else null,
                ),
            ),
            webpageUrl = url,
            extractor = "fc2",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "FC2"

        val VALID_URL: Regex = Regex(
            "(?:https?://video\\.fc2\\.com/(?:[^/]+/)*content/|fc2:)(?<id>[^/]+)",
        )
    }
}

/** Upstream `FC2EmbedIE`: the legacy `flv2.swf` embed. */
class FC2EmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val query = parseQuery(url)
        val videoId = query["i"]?.lastOrNull()
            ?: throw ExtractionError.UnsupportedUrl()
        val title = query["tl"]?.firstOrNull() ?: "FC2 video $videoId"
        val sj = query["sj"]?.firstOrNull()
        val thumbnail = sj?.let {
            val parts = listOf(videoId.take(6), videoId.substring(6, 8), videoId.takeLast(2).take(1), videoId.takeLast(1))
            "http://video$it-thumbnail.fc2.com/up/pic/" + parts.joinToString("/") + "/$videoId.jpg"
        }
        // Upstream `url_transparent` to `fc2:{video_id}` with the embed title.
        val info = FC2IE(http).extract("fc2:$videoId")
        return info.copy(
            title = title,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            webpageUrl = url,
        )
    }

    companion object {
        const val IE_KEY: String = "FC2Embed"

        val VALID_URL: Regex = Regex(
            "https?://video\\.fc2\\.com/flv2\\.swf\\?(?<query>.+)",
        )
    }
}

/** Upstream `FC2LiveIE`: a live.fc2.com stream (WebSocket control channel). */
class FC2LiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        http.downloadWebpage("https://live.fc2.com/$videoId/")
        // Upstream then talks to getControlServer.php and fetches the HLS
        // playlist over a WebSocket. The port has no WebSocket client, so the
        // stream fails typed instead of silently returning no formats.
        throw ExtractionError.Unavailable(
            "The FC2 live stream needs the WebSocket control channel, which the port does not implement.",
        )
    }

    companion object {
        const val IE_KEY: String = "FC2Live"

        val VALID_URL: Regex = Regex(
            "https?://live\\.fc2\\.com/(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `urllib.parse.parse_qs` with `unquote_plus` values. */
private fun parseQuery(url: String): Map<String, List<String>> {
    val out = linkedMapOf<String, MutableList<String>>()
    for (pair in url.substringAfter('?', "").split('&')) {
        if (pair.isEmpty() || !pair.contains('=')) continue
        val key = formDecode(pair.substringBefore('='))
        val value = formDecode(pair.substringAfter('='))
        if (key.isEmpty() || value.isEmpty()) continue
        out.getOrPut(key) { mutableListOf() } += value
    }
    return out
}

private fun formDecode(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                bytes += code.toByte()
                index += 3
                continue
            }
        }
        if (character == '+') {
            bytes += ' '.code.toByte()
        } else {
            bytes += character.toString().encodeToByteArray().toList()
        }
        index++
    }
    return bytes.toByteArray().decodeToString()
}

/** Upstream `urllib.parse.urljoin` for an absolute base and a path. */
private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("//") -> "https:$value"
    value.startsWith("/") -> Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) + value
    else -> base.trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
