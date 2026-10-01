/*
 * Youku extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `youku.py` from
 * `yt_dlp/extractor/youku.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `youku.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `ups.youku.com` JSON into m3u8 rows (the tail channel is
 * skipped), the geo/private/error typed failures, and the show listing's
 * JSONP module/episode pages. The port's HTTP seam does not expose response
 * headers, so the `cna` value from the `eg.js` etag is not read and `utid`
 * is sent empty; the `__ysuid`/`xreferrer` cookies are not set by the
 * extractor (the local jar attaches whatever it has). The `videopassword`
 * param, uploader_url, and tags are not carried. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.youku

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

private val FORMAT_NAMES = mapOf(
    "3gp" to "h6",
    "3gphd" to "h5",
    "flv" to "h4",
    "flvhd" to "h4",
    "mp4" to "h3",
    "mp4hd" to "h3",
    "mp4hd2" to "h4",
    "mp4hd3" to "h4",
    "hd2" to "h2",
    "hd3" to "h1",
)

/** Upstream `YoukuIE`: a Youku/Tudou video. */
class YoukuIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val clientTs = Clock.System.now().toEpochMilliseconds() / 1_000_000.0
        val response = http.downloadJson(
            "https://ups.youku.com/ups/get.json" +
                "?vid=${percentEncode(videoId)}&ccode=0564&client_ip=192.168.1.1" +
                "&utid=&client_ts=$clientTs",
            headers = mapOf("Referer" to url),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Youku API was not an object.")
        val data = response.obj("data")
            ?: throw ExtractionError.Malformed("The Youku API had no data.")

        data.obj("error")?.let { error ->
            val note = error.str("note")
            when {
                note != null && "因版权原因无法观看此视频" in note -> throw ExtractionError.GeoRestricted()
                note != null && "该视频被设为私密" in note ->
                    throw ExtractionError.Unavailable("This video is private.")

                else -> throw ExtractionError.Unavailable(
                    "Youku server reported error ${error.number("code")?.toInt()}" +
                        (note?.let { ": ${cleanHtml(it)}" } ?: ""),
                )
            }
        }

        val video = data.obj("video")
            ?: throw ExtractionError.Malformed("The Youku API had no video.")
        val formats = mutableListOf<MediaFormat>()
        for (element in data.array("stream").orEmpty()) {
            val stream = element as? JsonObject ?: continue
            if (stream.str("channel_type") == "tail") continue
            val streamUrl = stream.str("m3u8_url") ?: continue
            formats += MediaFormat(
                formatId = FORMAT_NAMES[stream.str("stream_type")],
                url = streamUrl,
                ext = "mp4",
                protocol = "m3u8_native",
                filesize = stream.number("size")?.toLong(),
                width = stream.number("width")?.toLong(),
                height = stream.number("height")?.toLong(),
            )
        }
        return InfoDict(
            id = videoId,
            title = video.str("title"),
            duration = video.number("seconds"),
            uploader = video.str("username"),
            channelId = video.primitive("userid"),
            thumbnails = listOfNotNull(
                video.str("logo")?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "youku",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Youku"

        val VALID_URL: Regex = Regex(
            "(?:" +
                "https?://(?:" +
                "(?:v|play(?:er)?)\\.(?:youku|tudou)\\.com/(?:v_show/id_|player\\.php/sid/)|" +
                "video\\.tudou\\.com/v/" +
                ")|" +
                "youku:" +
                ")(?<id>[A-Za-z0-9]+)(?:\\.html|/v\\.swf|)",
        )
    }
}

/** Upstream `YoukuShowIE`: a list.youku.com show listing. */
class YoukuShowIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val showId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val pageConfig = ExtractorUtils.searchRegex("var\\s+PageConfig\\s*=\\s*(\\{.+\\});", webpage)
            ?.let { ExtractorUtils.parseJson(ExtractorUtils.jsToJson(it)) as? JsonObject }
            ?: throw ExtractionError.Malformed("The Youku show page had no PageConfig.")
        val configShowId = pageConfig.str("showid")
            ?: throw ExtractionError.Malformed("The Youku PageConfig had no showid.")

        val entries = mutableListOf<InfoEntry>()
        val (firstPage, initialEntries) = extractEntries(
            "http://list.youku.com/show/module",
            showId,
            mapOf("id" to configShowId, "tab" to "showInfo"),
        )
        firstPage?.let { page ->
            val firstPageReloadId = ExtractorUtils.searchRegex("<div[^>]+id=\"(reload_\\d+)", page)
            val reloadIds = Regex("<li[^>]+data-id=\"([^\"]+)\">").findAll(page)
                .map { it.groupValues[1] }
                .toList()
            entries += initialEntries
            for (reloadId in reloadIds) {
                if (reloadId == firstPageReloadId) continue
                val (_, newEntries) = extractEntries(
                    "http://list.youku.com/show/episode",
                    showId,
                    mapOf("id" to configShowId, "stage" to reloadId),
                )
                entries += newEntries
            }
        }
        val desc = ExtractorUtils.htmlSearchMeta(webpage, "description")
        val detailLi = elementByClass(webpage, "p-intro")
        return InfoDict(
            id = showId,
            title = desc?.split(',')?.firstOrNull(),
            description = detailLi?.let { elementByClass(it, "intro-more") },
            entries = entries,
            webpageUrl = url,
            extractor = "youku:show",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun extractEntries(
        dataUrl: String,
        showId: String,
        query: Map<String, String>,
    ): Pair<String?, List<InfoEntry>> {
        val fullQuery = query + ("callback" to "cb")
        val body = try {
            http.downloadWebpage(
                dataUrl + "?" + fullQuery.entries.joinToString("&") { (key, value) ->
                    "${percentEncode(key)}=${percentEncode(value)}"
                },
            )
        } catch (error: ExtractionError) {
            return null to emptyList()
        }
        val playlistData = ExtractorUtils.parseJson(stripJsonp(body)) as? JsonObject
            ?: return null to emptyList()
        val html = playlistData.str("html") ?: return null to emptyList()
        val dramaList = elementByClass(html, "p-drama-grid") ?: elementByClass(html, "p-drama-half-row")
            ?: throw ExtractionError.Malformed("No episodes found.")
        val entries = Regex("<a[^>]+href=\"([^\"]+)\"").findAll(dramaList).map {
            InfoEntry(url = protoRelativeUrl(it.groupValues[1]))
        }.toList()
        return html to entries
    }

    companion object {
        const val IE_KEY: String = "YoukuShow"

        val VALID_URL: Regex = Regex(
            "https?://list\\.youku\\.com/show/id_(?<id>[0-9a-z]+)\\.html",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `strip_jsonp`. */
private fun stripJsonp(value: String): String {
    val start = value.indexOf('(')
    val end = value.lastIndexOf(')')
    return if (start in 0 until end) value.substring(start + 1, end) else value
}

/** Upstream `_proto_relative_url(video_url, 'http:')`. */
private fun protoRelativeUrl(value: String): String =
    if (value.startsWith("//")) "http:$value" else value

/** Upstream `get_element_by_class` for the show listing sections. */
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
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[code shr 4])
            append(HEX_DIGITS[code and 0x0F])
        }
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
