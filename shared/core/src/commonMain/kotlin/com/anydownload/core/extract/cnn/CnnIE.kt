/*
 * CNN extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `cnn.py` from
 * `yt_dlp/extractor/cnn.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `cnn.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the video-player page scan, the public fave API direct files and
 * closed captions, and the public medium API HLS row (the app id comes from
 * the page's `window.env`, never stored). The port does not carry
 * display ids, modified dates, or tags, so they are dropped; manifest
 * parsing is not translated, so an m3u8 URL becomes one HLS row. No cookie,
 * token, or private URL is stored here.
 */
package com.anydownload.core.extract.cnn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `CNNIE`: a CNN article or video page. */
class CNNIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = VALID_URL.find(url)?.groups?.get("display")?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val appId = windowEnv(webpage)?.str("TOP_AUTH_SERVICE_APP_ID")
        val entries = mutableListOf<InfoDict>()
        for (tag in Regex("(?s)<div[^>]+data-component-name=\"video-player\"[^>]*>").findAll(webpage)) {
            val attributes = tagAttributes(tag.value)
            val mediaId = attributes["data-media-id"] ?: continue
            val formats = mutableListOf<MediaFormat>()
            val subtitles = mutableListOf<SubtitleTrack>()
            val videoData = attributes["data-video-resource-parent-uri"]?.let { parentUri ->
                try {
                    http.downloadJson(
                        "https://fave.api.cnn.io/v1/video?id=$mediaId&stellarUri=$parentUri",
                    ) as? JsonObject
                } catch (error: ExtractionError) {
                    null
                }
            }
            if (videoData != null) {
                for (element in videoData.array("files").orEmpty()) {
                    val file = element as? JsonObject ?: continue
                    val fileUri = file.str("fileUri") ?: continue
                    val match = Regex("-(\\d+x\\d+)_(\\d+)k\\.mp4").find(fileUri)
                    val resolution = match?.groupValues?.get(1)
                    formats += MediaFormat(
                        formatId = "direct",
                        url = fileUri,
                        preference = 1,
                        tbr = match?.groupValues?.get(2)?.toDoubleOrNull(),
                        width = resolution?.substringBefore('x')?.toLongOrNull(),
                        height = resolution?.substringAfter('x')?.toLongOrNull(),
                    )
                }
                for (element in videoData.obj("closedCaptions")?.array("types").orEmpty()) {
                    val type = element as? JsonObject ?: continue
                    val track = type.obj("track") ?: continue
                    val trackUrl = track.str("url") ?: continue
                    subtitles += SubtitleTrack(
                        language = track.str("lang") ?: "en",
                        formats = listOf(SubtitleFormat(ext = "vtt", url = trackUrl)),
                    )
                }
            }
            if (appId != null) {
                val mediaData = try {
                    http.downloadJson("https://medium.ngtv.io/v2/media/$mediaId/desktop?appId=$appId")
                        as? JsonObject
                } catch (error: ExtractionError) {
                    null
                }
                val m3u8Url = mediaData?.obj("media")?.obj("desktop")?.obj("unprotected")
                    ?.obj("unencrypted")?.str("url")
                if (m3u8Url != null) {
                    formats += MediaFormat(
                        formatId = "hls",
                        url = m3u8Url,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                }
            }
            if (formats.isEmpty()) continue
            val poster = attributes["data-poster-image-override"]?.let { value ->
                (ExtractorUtils.parseJson(value) as? JsonObject)?.obj("big")?.str("uri")
            }
            val thumbnail = attributes["data-poster-image-override"]?.let { value ->
                (ExtractorUtils.parseJson(value) as? JsonObject)?.obj("big")?.str("uri")
            }
            entries += InfoDict(
                id = mediaId,
                title = cleanHtml(attributes["data-headline"])
                    ?: videoData?.str("headline"),
                description = cleanHtml(attributes["data-description"])
                    ?: cleanHtml(videoData?.str("description")),
                duration = attributes["data-duration"]?.let { parseDuration(it) }
                    ?: videoData?.number("trt"),
                uploadDate = ExtractorUtils.unifiedStrdate(
                    attributes["data-publish-date"]
                        ?: videoData?.obj("dateCreated")?.number("uts")?.let { (it * 1000).toLong().toString() },
                ),
                thumbnails = listOfNotNull(
                    (poster?.replace("?", "?c=original&") ?: thumbnail)?.let { Thumbnail(url = it) },
                ),
                formats = formats,
                subtitles = subtitles,
                extractor = "cnn",
                extractorKey = IE_KEY,
            )
        }
        if (entries.isEmpty()) {
            throw ExtractionError.NoFormats("The CNN page had no playable video player.")
        }
        if (entries.size == 1) {
            return entries.single().copy(webpageUrl = url)
        }
        return InfoDict(
            id = displayId,
            entries = entries.map {
                InfoEntry(id = it.id, title = it.title, url = "https://www.cnn.com/videos/${it.id}")
            },
            webpageUrl = url,
            extractor = "cnn",
            extractorKey = IE_KEY,
        )
    }

    private fun windowEnv(webpage: String): JsonObject? {
        val marker = Regex("window\\.env\\s*=").find(webpage) ?: return null
        val start = webpage.indexOf('{', marker.range.last + 1)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < webpage.length) {
            val c = webpage[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return ExtractorUtils.parseJson(webpage.substring(start, i + 1)) as? JsonObject
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "CNN"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:edition|www|money|cnnespanol)\\.)?cnn\\.com/(?!audio/)" +
                "(?<display>[^?#]+?)(?:[?#]|$|/index\\.html)",
        )
    }
}

/** Upstream `CNNIndonesiaIE`: a CNN Indonesia article. */
class CNNIndonesiaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val uploadDate = match.groups["uploaddate"]?.value
        val displayId = match.groups["display"]?.value ?: videoId
        val webpage = http.downloadWebpage(url)
        for (element in Regex(
            "(?s)<script[^>]+type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        ).findAll(webpage)) {
            val parsed = ExtractorUtils.parseJson(element.groupValues[1])
            val objects = when (parsed) {
                is JsonArray -> parsed.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(parsed)
                else -> emptyList()
            }
            for (obj in objects) {
                if (obj.str("@type") != "VideoObject") continue
                val embedUrl = obj.str("embedUrl") ?: continue
                return InfoDict(
                    id = videoId,
                    uploadDate = uploadDate,
                    redirectUrl = embedUrl,
                    webpageUrl = url,
                    extractor = "cnn:indonesia",
                    extractorKey = IE_KEY,
                )
            }
        }
        throw ExtractionError.Malformed("The CNN Indonesia page had no VideoObject embed URL.")
    }

    companion object {
        const val IE_KEY: String = "CNNIndonesia"

        val VALID_URL: Regex = Regex(
            "https?://www\\.cnnindonesia\\.com/[\\w-]+/(?<uploaddate>\\d{8})\\d+-\\d+-(?<id>\\d+)/" +
                "(?<display>[\\w-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun parseDuration(value: String): Double? {
    val parts = value.trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    var seconds = 0.0
    for (part in parts) {
        val number = part.toDoubleOrNull() ?: return null
        seconds = seconds * 60 + number
    }
    return seconds
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
