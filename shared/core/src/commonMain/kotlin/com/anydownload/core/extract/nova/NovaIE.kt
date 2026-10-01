/*
 * TV Nova extractors — AnyDownload
 *
 * Kotlin translation of the public page/player subset of `nova.py` from
 * `yt_dlp/extractor/nova.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nova.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `player:` JSON of media.cms.nova.cz embeds (`lib.source.sources`
 * and `sourceInfo.duration`), the DRM source skip and typed DRM failure, and
 * the nova.cz article pages (embed delegation with description/upload date,
 * the videojs config JSON, and direct mediafile URLs). The pre-August-2023
 * `replacePlaceholders`/`Player.init` player path is not translated, an
 * m3u8/mpd source becomes one row, and an RTMP mediafile fails typed NoFormats
 * because the port has no RTMP downloader. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.nova

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `NovaEmbedIE`: a media.cms.nova.cz player embed. */
class NovaEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val formats = mutableListOf<MediaFormat>()
        var hasDrm = false

        fun processFormatList(formatList: JsonElement?, formatId: String = "") {
            val list = if (formatList is JsonArray) formatList else listOfNotNull(formatList)
            for (element in list) {
                val format = element as? JsonObject ?: continue
                if (format.obj("drm")?.get("keySystem") != null) {
                    hasDrm = true
                    continue
                }
                val formatUrl = format.str("src")?.takeIf {
                    it.startsWith("http://") || it.startsWith("https://")
                } ?: continue
                val formatType = format.str("type")
                val ext = ExtractorUtils.determineExt(formatUrl)
                if (formatType == "application/x-mpegURL" || formatId == "HLS" || ext == "m3u8") {
                    formats += MediaFormat(
                        formatId = "hls",
                        url = formatUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                } else if (formatType == "application/dash+xml" || formatId == "DASH" || ext == "mpd") {
                    formats += MediaFormat(
                        formatId = "dash",
                        url = formatUrl,
                        ext = "mp4",
                        protocol = "mpd",
                    )
                } else {
                    formats += MediaFormat(url = formatUrl, ext = ext)
                }
            }
        }

        var duration: Long? = null
        val player = parseObjectAfterMarker(webpage, "player:")
        if (player != null) {
            val sources = player.obj("lib")?.obj("source")?.get("sources")
            val sourceList = if (sources is JsonArray) sources else listOfNotNull(sources)
            for (source in sourceList) processFormatList(source)
            duration = player.obj("sourceInfo")?.number("duration")?.toLong()
        }

        if (formats.isEmpty() && hasDrm) {
            throw ExtractionError.Unavailable("The video is DRM protected.")
        }

        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?: ExtractorUtils.searchRegex("<value>([^<]+)", webpage)
            ?: ExtractorUtils.searchRegex("videoTitle\\s*:\\s*[\"']([^\"']+)", webpage)
        val thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image")
            ?: ExtractorUtils.searchRegex("poster\\s*:\\s*[\"']([^\"']+)", webpage)
        duration = ExtractorUtils.searchRegex("videoDuration\\s*:\\s*(\\d+)", webpage)
            ?.toLongOrNull() ?: duration
        return InfoDict(
            id = videoId,
            title = title,
            duration = duration?.toDouble(),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "nova:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NovaEmbed"

        val VALID_URL: Regex = Regex(
            "https?://media(?:tn)?\\.cms\\.nova\\.cz/embed/(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `NovaIE`: a nova.cz family site article page. */
class NovaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val site = match.groups["site"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val description = cleanHtml(ExtractorUtils.htmlSearchMeta(webpage, "og:description"))
        val uploadDate = when (site) {
            "novaplus" -> ExtractorUtils.searchRegex("(\\d{1,2}-\\d{1,2}-\\d{4})$", displayId)
                ?.let(ExtractorUtils::unifiedStrdate)

            "fanda" -> ExtractorUtils.searchRegex(
                "<span class=\"date_time\">(\\d{1,2}\\.\\d{1,2}\\.\\d{4})",
                webpage,
            )?.let(ExtractorUtils::unifiedStrdate)

            else -> null
        }

        val embedId = ExtractorUtils.searchRegex(
            "<iframe[^>]+\\bsrc=[\"'](?:https?:)?//media(?:tn)?\\.cms\\.nova\\.cz/embed/([^/?#&\"']+)",
            webpage,
        )
        if (embedId != null) {
            // Upstream `url_transparent` with the outer description/upload date.
            val embedded = NovaEmbedIE(http).extract("https://media.cms.nova.cz/embed/$embedId")
            return embedded.copy(
                description = description ?: embedded.description,
                uploadDate = uploadDate ?: embedded.uploadDate,
                webpageUrl = url,
            )
        }

        val videoId = ExtractorUtils.searchRegex(
            "(?:media|video_id)\\s*:\\s*'(\\d+)'",
            webpage,
        ) ?: ExtractorUtils.searchRegex("media=(\\d+)", webpage)
            ?: ExtractorUtils.searchRegex("id=\"article_video_(\\d+)\"", webpage)
            ?: ExtractorUtils.searchRegex("id=\"player_(\\d+)\"", webpage)
            ?: throw ExtractionError.Malformed("Unable to find the Nova video id.")

        var configUrl = ExtractorUtils.searchRegex(
            "src=\"(https?://(?:tn|api)\\.nova\\.cz/bin/player/videojs/config\\.php\\?[^\"]+)\"",
            webpage,
        )
        if (configUrl == null) {
            val siteId = when (site) {
                "fanda", "tn", "doma" -> "30"
                else -> "23000"
            }
            configUrl = "https://api.nova.cz/bin/player/videojs/config.php" +
                "?site=$siteId&media=$videoId&quality=3&version=1"
        }

        val configText = http.downloadWebpage(configUrl)
        val start = configText.indexOf('{')
        val end = configText.lastIndexOf('}')
        val config = if (start in 0 until end) {
            ExtractorUtils.parseJson(configText.substring(start, end + 1)) as? JsonObject
        } else {
            null
        } ?: throw ExtractionError.Malformed("The Nova player config was not JSON.")
        val mediafile = config.obj("mediafile")
            ?: throw ExtractionError.Malformed("The Nova player config had no mediafile.")
        val videoUrl = mediafile.str("src")
            ?: throw ExtractionError.Malformed("The Nova mediafile had no source.")
        if (Regex("^rtmpe?://").containsMatchIn(videoUrl)) {
            throw ExtractionError.NoFormats("The Nova stream uses RTMP, which the port does not support.")
        }
        val ext = ExtractorUtils.determineExt(videoUrl)
        val formats = listOf(
            MediaFormat(
                url = videoUrl,
                ext = ext,
                protocol = if (ext == "m3u8") "m3u8_native" else null,
            ),
        )
        return InfoDict(
            id = videoId,
            title = mediafile.obj("meta")?.str("title")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            description = description,
            uploadDate = uploadDate,
            thumbnails = listOfNotNull(config.str("poster")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "nova",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Nova"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^.]+\\.)?(?<site>tv(?:noviny)?|tn|novaplus|vymena|fanda|krasna|doma|prask)" +
                "\\.nova\\.cz/(?:[^/]+/)+(?<id>[^/]+?)(?:\\.html|/|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** The first `{...}` object after [marker], JS-cleaned and parsed. */
private fun parseObjectAfterMarker(webpage: String, marker: String): JsonObject? {
    val found = webpage.indexOf(marker)
    if (found < 0) return null
    val start = webpage.indexOf('{', found + marker.length)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var index = start
    while (index < webpage.length) {
        val character = webpage[index]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' || character == '\'' -> inString = false
            }
        } else {
            when (character) {
                '"', '\'' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val body = webpage.substring(start, index + 1)
                        val json = ExtractorUtils.jsToJson(body)
                        return ExtractorUtils.parseJson(json) as? JsonObject
                    }
                }
            }
        }
        index++
    }
    return null
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
