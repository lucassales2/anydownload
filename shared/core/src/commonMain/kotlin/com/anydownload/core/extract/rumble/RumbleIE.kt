/*
 * Rumble extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `rumble.py` from
 * `yt_dlp/extractor/rumble.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rumble.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `embedJS/u3` player API (hls/timeline/audio/plain
 * formats, captions, thumbnails, live status, metadata), the page embed
 * scan, and the channel pages (five pages eagerly). The `tar` format type
 * is skipped and the page counters the port does not carry are dropped. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.rumble

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

/** Upstream `RumbleEmbedIE`: an embed id. */
class RumbleEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val video = http.downloadJson(
            "https://rumble.com/embedJS/u3/?request=video&ver=2&v=$videoId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Rumble player API was not an object.")
        val live = video.number("live")?.toInt()
        val hasDvr = video["livestream_has_dvr"] != null
        val liveStatus = when (live) {
            0 -> if (hasDvr) "was_live" else "not_live"
            1 -> if (hasDvr) "was_live" else "is_upcoming"
            2 -> "is_live"
            else -> null
        }
        val formats = mutableListOf<MediaFormat>()
        for ((formatType, value) in video.obj("ua").orEmpty()) {
            if (formatType == "tar") continue
            val entries = when (value) {
                is JsonObject -> value.entries.associate { (height, info) ->
                    val item = info as? JsonObject ?: JsonObject(emptyMap())
                    height to item
                }.values
                is JsonArray -> value.mapNotNull { it as? JsonObject }
                else -> emptyList()
            }
            for (videoInfo in entries) {
                val meta = videoInfo.obj("meta") ?: JsonObject(emptyMap())
                val streamUrl = videoInfo.str("url") ?: continue
                when (formatType) {
                    "hls" -> {
                        formats += MediaFormat(
                            formatId = "hls",
                            url = streamUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                            tbr = meta.number("bitrate")?.toDouble(),
                            width = meta.number("w")?.toLong(),
                            height = meta.number("h")?.toLong(),
                        )
                    }

                    else -> {
                        val isTimeline = formatType == "timeline"
                        val isAudio = formatType == "audio"
                        val height = meta.number("h")?.toLong()
                        formats += MediaFormat(
                            formatId = listOfNotNull(
                                formatType,
                                height?.let { "${it}p" },
                            ).joinToString("-"),
                            url = streamUrl,
                            acodec = if (isTimeline) MediaFormat.CODEC_NONE else null,
                            vcodec = if (isAudio) MediaFormat.CODEC_NONE else null,
                            formatNote = if (isTimeline) "Timeline" else null,
                            fps = if (isTimeline || isAudio) null else video.number("fps"),
                            tbr = meta.number("bitrate")?.toDouble(),
                            filesize = meta.number("size")?.toLong(),
                            width = meta.number("w")?.toLong(),
                            height = height,
                        )
                    }
                }
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Rumble player API returned no playable format.")
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        for ((language, value) in video.obj("cc").orEmpty()) {
            val info = value as? JsonObject ?: continue
            val path = info.str("path") ?: continue
            subtitles += SubtitleTrack(
                language = language,
                name = info.str("language"),
                formats = listOf(SubtitleFormat(ext = ExtractorUtils.determineExt(path), url = path)),
            )
        }
        val author = video.obj("author")
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in video.array("t").orEmpty()) {
            val thumb = element as? JsonObject ?: continue
            val thumbUrl = thumb.str("i") ?: continue
            thumbnails += Thumbnail(
                url = thumbUrl,
                width = thumb.number("w")?.toLong(),
                height = thumb.number("h")?.toLong(),
            )
        }
        if (thumbnails.isEmpty()) {
            video.str("i")?.let { thumbnails += Thumbnail(url = it) }
        }
        return InfoDict(
            id = videoId,
            title = video.str("title")?.let { ExtractorUtils.unescapeHtml(it) ?: it },
            duration = if (liveStatus == "is_live" || liveStatus == "post_live") {
                null
            } else {
                video.number("duration")
            },
            uploadDate = ExtractorUtils.unifiedStrdate(video.str("pubDate")),
            isLive = liveStatus == "is_live",
            channel = author?.str("name"),
            channelId = author?.str("url"),
            thumbnails = thumbnails,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = "https://rumble.com/embed/$videoId",
            extractor = "rumble",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RumbleEmbed"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?rumble\\.com/embed/(?:[0-9a-z]+\\.)?(?<id>[0-9a-z]+)")
    }
}

/** Upstream `RumbleIE`: a video page. */
class RumbleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val pageId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val embedId = Regex("https?://(?:www\\.)?rumble\\.com/embed/(?:[0-9a-z]+\\.)?([0-9a-z]+)")
            .find(webpage)?.groupValues?.get(1)
            ?: Regex(
                "<script>[^<]*\\bRumble\\(\\s*\"play\"\\s*,\\s*\\{[^}]*['\"]?video['\"]?\\s*:\\s*['\"]([0-9a-z]+)['\"]",
            ).find(webpage)?.groupValues?.get(1)
            ?: throw ExtractionError.UnsupportedUrl("The Rumble page had no embed URL.")
        return InfoDict(
            id = pageId,
            redirectUrl = "https://rumble.com/embed/$embedId",
            description = ExtractorUtils.searchRegex(
                "(?s)<div[^>]+class=\"[^\"]*media-description[^\"]*\"[^>]*>(.*?)</div>",
                webpage,
                default = null,
            )?.let(::cleanHtml),
            webpageUrl = url,
            extractor = "rumble",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Rumble"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?rumble\\.com/(?<id>v(?!ideos)[\\w.-]+)[^/]*$")
    }
}

/** Upstream `RumbleChannelIE`: a channel listing. */
class RumbleChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val channelUrl = match.groups["url"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val webpage = try {
                http.downloadWebpage("$channelUrl?page=$page")
            } catch (error: Exception) {
                break
            }
            var found = false
            for (tag in Regex("<a\\b[^>]*>").findAll(webpage)) {
                val attributes = tagAttributes(tag.value)
                val classes = attributes["class"] ?: continue
                if (!classes.split(' ').contains("videostream__link")) continue
                val href = attributes["href"] ?: continue
                entries += InfoEntry(url = urlJoin("https://rumble.com", href))
                found = true
            }
            if (!found) break
            page++
        }
        return InfoDict(
            id = playlistId,
            entries = entries,
            webpageUrl = url,
            extractor = "rumble:channel",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RumbleChannel"

        val VALID_URL: Regex = Regex(
            "(?<url>https?://(?:www\\.)?rumble\\.com/(?:c|user)/(?<id>[^&?#$/]+))",
        )

        private const val MAX_PAGES = 5
    }
}

// ------------------------------------------------------------------ helpers

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
