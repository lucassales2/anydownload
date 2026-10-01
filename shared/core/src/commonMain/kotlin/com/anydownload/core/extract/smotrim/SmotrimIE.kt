/*
 * Smotrim extractors — AnyDownload
 *
 * Kotlin translation of the public player API subset of `smotrim.py` from
 * `yt_dlp/extractor/smotrim.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `smotrim.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public player iframe API (video m3u8 rows, audio files, live
 * mp3/m3u8), the channel page player scan, and the brand/podcast listings.
 * A locked item fails typed LoginRequired and an API error fails typed
 * GeoRestricted (RU). Manifest parsing is not translated, so each m3u8 URL
 * becomes one HLS row; the port does not carry series/season fields, so
 * they are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.smotrim

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

private const val BASE_URL = "https://smotrim.ru"
private const val PAGE_SIZE = 15
private const val MAX_PAGES = 5

/** Shared upstream `SmotrimBaseIE` behaviour. */
abstract class SmotrimBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_from_smotrim_api` for the fields the port carries. */
    protected suspend fun extractFromApi(type: String, itemId: String): InfoDict {
        val path = "data${type.replace("-", "")}/${if (type == "live") "uid" else "id"}"
        val data = http.downloadJson(
            "https://player.smotrim.ru/iframe/$path/$itemId/sid/smotrim",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Smotrim player API returned no object.")
        val media = data.obj("data")?.obj("playlist")?.array("medialist")?.lastOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Smotrim player API had no media item.")
        if (media.boolean("locked") == true) {
            throw ExtractionError.LoginRequired()
        }
        media.str("errors")?.let {
            throw ExtractionError.GeoRestricted(listOf("RU"))
        }
        val webpageUrl = data.obj("data")?.obj("template")?.str("share_url")
        val webpage = if (webpageUrl != null) {
            try {
                http.downloadWebpage(webpageUrl)
            } catch (error: ExtractionError) {
                ""
            }
        } else {
            ""
        }
        val thumbnail = metaContent(webpage, "og:image") ?: metaContent(webpage, "twitter:image")
        val common = InfoDict(
            id = media.primitive("id"),
            title = cleanHtml(media.str("episodeTitle") ?: media.str("title")),
            description = cleanHtml(media.str("anons")),
            channelId = media.primitive("channelId"),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            extractor = "smotrim",
            extractorKey = ieKey,
        )
        val metadata = when (type) {
            "audio" -> {
                val audioUrl = media.str("audio_url")
                val bookmark = findBookmark(webpage)
                common.copy(
                    title = cleanHtml(bookmark?.str("subtitle")) ?: common.title,
                    duration = media.number("duration"),
                    uploadDate = ExtractorUtils.unifiedStrdate(bookmark?.str("published")),
                    formats = listOfNotNull(
                        audioUrl?.let {
                            MediaFormat(
                                url = it,
                                ext = ExtractorUtils.determineExt(it, "mp3"),
                                vcodec = MediaFormat.CODEC_NONE,
                            )
                        },
                    ),
                )
            }

            "audio-live" -> common.copy(
                formats = listOfNotNull(
                    media.obj("source")?.str("auto")?.let {
                        MediaFormat(url = it, ext = "mp3", vcodec = MediaFormat.CODEC_NONE)
                    },
                ),
            )

            else -> {
                val formats = mutableListOf<MediaFormat>()
                for (element in media.obj("sources")?.obj("m3u8")?.values.orEmpty()) {
                    val m3u8Url = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                    formats += MediaFormat(
                        formatId = "hls",
                        url = m3u8Url,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                }
                common.copy(formats = formats)
            }
        }
        return metadata.copy(
            isLive = type == "audio-live" || type == "live",
            webpageUrl = webpageUrl,
        )
    }

    /** Upstream `class="bookmark"` JSON, HTML-unescaped. */
    private fun findBookmark(webpage: String): JsonObject? {
        val marker = Regex("class=\"bookmark\"[^>]+value\\s*=\\s*\"").find(webpage) ?: return null
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
                            val raw = webpage.substring(start, i + 1)
                            return ExtractorUtils.unescapeHtml(raw)?.let {
                                ExtractorUtils.parseJson(it) as? JsonObject
                            }
                        }
                    }
                }
            }
            i++
        }
        return null
    }
}

/** Upstream `SmotrimIE`: a video. */
class SmotrimIE(
    http: ExtractorHttp,
) : SmotrimBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractFromApi("video", videoId).copy(webpageUrl = url, extractorKey = IE_KEY)
    }

    companion object {
        const val IE_KEY: String = "Smotrim"

        val VALID_URL: Regex = Regex(
            "(?:https?:)?//(?:(?:player|www)\\.)?smotrim\\.ru(?:/iframe)?/video(?:/id)?/(?<id>\\d+)",
        )
    }
}

/** Upstream `SmotrimAudioIE`: an audio item. */
class SmotrimAudioIE(
    http: ExtractorHttp,
) : SmotrimBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val audioId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractFromApi("audio", audioId).copy(webpageUrl = url, extractorKey = IE_KEY)
    }

    companion object {
        const val IE_KEY: String = "SmotrimAudio"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:player|www)\\.)?smotrim\\.ru(?:/iframe)?/audio(?:/id)?/(?<id>\\d+)",
        )
    }
}

/** Upstream `SmotrimLiveIE`: a channel or live stream. */
class SmotrimLiveIE(
    http: ExtractorHttp,
) : SmotrimBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var type = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        if (type == "live" && displayId.all { it.isDigit() }) {
            val finalUrl = try {
                http.followRedirects(url)
            } catch (error: ExtractionError) {
                url
            }
            val redirectMatch = VALID_URL.find(finalUrl)
            type = redirectMatch?.groups?.get("type")?.value ?: type
            displayId = redirectMatch?.groups?.get("id")?.value ?: displayId
        }
        val videoId: String
        if (type == "channel") {
            val webpage = http.downloadWebpage(url)
            val frameSrc = elementAttributes(webpage, "main-player__frame")["src"]
                ?: elementAttributes(webpage, "audio-play-button")["value"]
                    ?.let { unquote(it) }?.let { ExtractorUtils.parseJson(it) as? JsonObject }
                    ?.str("source")
                ?: throw ExtractionError.Malformed("The Smotrim channel page had no player source.")
            val srcMatch = VALID_URL.find(frameSrc)
                ?: throw ExtractionError.Malformed("The Smotrim player source was not a Smotrim URL.")
            type = srcMatch.groups["type"]?.value ?: type
            videoId = srcMatch.groups["id"]?.value
                ?: throw ExtractionError.Malformed("The Smotrim player source had no id.")
        } else {
            videoId = displayId
        }
        return extractFromApi(type, videoId).copy(webpageUrl = url, extractorKey = IE_KEY)
    }

    companion object {
        const val IE_KEY: String = "SmotrimLive"

        val VALID_URL: Regex = Regex(
            "(?:https?:)?//(?:(?:(?:test)?player|www)\\.)?(?:smotrim\\.ru|vgtrk\\.com)(?:/iframe)?/" +
                "(?<type>channel|(?:audio-)?live)(?:/u?id)?/(?<id>[\\da-f-]+)",
        )
    }
}

/** Upstream `SmotrimPlaylistIE`: a brand or podcast listing. */
class SmotrimPlaylistIE(
    http: ExtractorHttp,
) : SmotrimBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistType = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val season = match.groups["season"]?.value
        val webpage = http.downloadWebpage(url)
        val title = metaContent(webpage, "og:title") ?: metaContent(webpage, "twitter:title")
        val entries = mutableListOf<InfoEntry>()
        if (season != null) {
            for (link in Regex("href=\"(/video/\\d+)\"").findAll(webpage)) {
                entries += InfoEntry(url = "$BASE_URL${link.groupValues[1]}")
            }
            return InfoDict(
                id = playlistId,
                title = title,
                entries = entries,
                webpageUrl = url,
                extractor = "smotrim:playlist",
                extractorKey = IE_KEY,
            )
        }
        val endpoint = if (elementHtml(webpage, "brand-main-item__videos") != null) "videos" else "audios"
        val key = if (playlistType == "podcast") "rubricId" else "brandId"
        var page = 1
        while (page <= MAX_PAGES) {
            val items = try {
                http.downloadJson(
                    "$BASE_URL/api/$endpoint?$key=$playlistId&limit=$PAGE_SIZE&page=$page",
                ) as? JsonObject
            } catch (error: Exception) {
                break
            } ?: break
            val list = items.array("contents")?.lastOrNull()?.let { it as? JsonObject }
                ?.array("list").orEmpty()
            if (list.isEmpty()) break
            for (element in list) {
                val link = (element as? JsonObject)?.str("link") ?: continue
                entries += InfoEntry(url = urlJoin(BASE_URL, link))
            }
            page++
        }
        return InfoDict(
            id = playlistId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "smotrim:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SmotrimPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://smotrim\\.ru/(?<type>brand|podcast)/(?<id>\\d+)/?(?<season>[\\w-]+)?",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun elementAttributes(webpage: String, className: String): Map<String, String> {
    val match = Regex(
        "<[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" + Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>",
    ).find(webpage) ?: return emptyMap()
    return tagAttributes(match.value)
}

private fun elementHtml(webpage: String, className: String): String? = Regex(
    "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" + Regex.escape(className) +
        "(?:\\s+[\\w-]+)*[\"'][^>]*>(.*?)</\\1>",
).find(webpage)?.groupValues?.get(2)

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun unquote(value: String): String =
    if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        value.substring(1, value.length - 1)
    } else {
        value
    }

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1].ifBlank { null } }
    }
    return null
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    return if (href.startsWith("/")) base + href else "$base/$href"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
