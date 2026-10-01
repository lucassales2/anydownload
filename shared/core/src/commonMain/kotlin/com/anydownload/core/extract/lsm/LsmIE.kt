/*
 * LSM extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `lsm.py` from
 * `yt_dlp/extractor/lsm.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `lsm.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Latvian Radio `LR.audio.Player` page scan (audio/video items,
 * m3u8 and direct sources), the LTV embed payload redirects (YouTube and the
 * CloudyCDN embed URL), and the Nuxt replay data (`Object.create(null, …)`
 * is normalized, `__REPLAY__` is found, hls or embed playback). The port
 * does not carry season/series fields, so they are dropped. No cookie,
 * token, or private URL is stored here.
 */
package com.anydownload.core.extract.lsm

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `LSMLREmbedIE`: a Latvian Radio embed. */
class LSMLREmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = queryParam(url, "show")?.takeIf { it != "0" }
            ?: queryParam(url, "id")
            ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val marker = Regex("LR\\.audio\\.Player\\s*\\([^{]*").find(webpage)
            ?: throw ExtractionError.Malformed("The LSM page had no player data.")
        val playerRange = balancedRange(webpage, marker.range.last + 1)
            ?: throw ExtractionError.Malformed("The LSM player JSON was not found.")
        val playerJson = ExtractorUtils.parseJson(webpage.substring(playerRange.first, playerRange.last + 1))
            as? JsonObject
            ?: throw ExtractionError.Malformed("The LSM player JSON was not an object.")
        val mediaRange = balancedRange(webpage, playerRange.last + 1)
            ?: throw ExtractionError.Malformed("The LSM media JSON was not found.")
        val mediaJson = ExtractorUtils.parseJson(webpage.substring(mediaRange.first, mediaRange.last + 1))
            as? JsonObject
            ?: throw ExtractionError.Malformed("The LSM media JSON was not an object.")
        val entries = mutableListOf<InfoDict>()
        for (kind in listOf("audio", "video")) {
            for (element in mediaJson.array(kind).orEmpty()) {
                val item = element as? JsonObject ?: continue
                val itemId = item.str("id") ?: continue
                val formats = mutableListOf<MediaFormat>()
                for (sourceElement in item.array("sources").orEmpty()) {
                    val source = sourceElement as? JsonObject ?: continue
                    val sourceUrl = source.str("file") ?: continue
                    if (ExtractorUtils.determineExt(sourceUrl) == "m3u8") {
                        formats += MediaFormat(
                            formatId = "hls",
                            url = sourceUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        )
                    } else {
                        formats += MediaFormat(
                            url = sourceUrl,
                            ext = ExtractorUtils.determineExt(sourceUrl),
                        )
                    }
                }
                var title = item.str("title")
                if (itemId.startsWith("v") && title == null) {
                    for (audioElement in mediaJson.array("audio").orEmpty()) {
                        val audio = audioElement as? JsonObject ?: continue
                        val audioId = audio.str("id") ?: continue
                        if (audioId.length > 1 && itemId.length > 1 && audioId.substring(1) == itemId.substring(1)) {
                            title = audio.str("title")?.let { "$it - Video Version" }
                            break
                        }
                    }
                }
                val poster = playerJson.str("poster")
                entries += InfoDict(
                    id = itemId,
                    title = title,
                    duration = item.number("duration"),
                    thumbnails = listOfNotNull(
                        poster?.let { Thumbnail(url = urlJoin(url, it)) },
                    ),
                    formats = formats,
                    extractor = "lsm:lr",
                    extractorKey = IE_KEY,
                )
            }
        }
        if (entries.isEmpty()) {
            throw ExtractionError.NoFormats("The LSM player had no media items.")
        }
        if (entries.size == 1) {
            return entries.single().copy(webpageUrl = url)
        }
        return InfoDict(
            id = videoId,
            entries = entries.map { InfoEntry(id = it.id, title = it.title, url = url) },
            webpageUrl = url,
            extractor = "lsm:lr",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "LSMLREmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:latvijasradio|lr1|lr2|klasika|lr4|naba|radioteatris)\\.lsm|pieci)\\.lv/" +
                "[^/?#]+/(?:pleijeris|embed)/?\\?(?:[^#]+&)?(?:show|id)=(?<id>\\d+)",
        )
    }
}

/** Upstream `LSMLTVEmbedIE`: an LTV embed payload. */
class LSMLTVEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url)?.let { decodeUrlComponent(it) }
            ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val marker = Regex("window\\.ltvEmbedPayload\\s*=").find(webpage)
            ?: throw ExtractionError.Malformed("The LTV page had no embed payload.")
        val data = balancedAfter(webpage, marker.range.last + 1)
            ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
            ?: throw ExtractionError.Malformed("The LTV embed payload was not JSON.")
        val source = data.obj("source")
        val embedType = source?.obj("name")?.str("name") ?: source?.str("name")
        val embedUrl = when (embedType) {
            "backscreen", "telia" -> source?.str("embed_url")
            "youtube" -> source?.str("id")?.let { "https://www.youtube.com/watch?v=$it" }
            else -> null
        }
        if (embedUrl == null) {
            throw ExtractionError.UnsupportedUrl("Unsupported embed type \"$embedType\".")
        }
        val poster = source?.str("poster")
        return InfoDict(
            id = videoId,
            title = data.obj("parentInfo")?.str("title"),
            duration = data.obj("parentInfo")?.number("duration"),
            thumbnails = listOfNotNull(poster?.let { Thumbnail(url = it) }),
            redirectUrl = embedUrl,
            webpageUrl = url,
            extractor = "lsm:ltv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "LSMLTVEmbed"

        val VALID_URL: Regex = Regex("https?://ltv\\.lsm\\.lv/embed\\?(?:[^#]+&)?c=(?<id>[^#\u0026]+)")
    }
}

/** Upstream `LSMReplayIE`: an LSM replay page. */
class LSMReplayIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val fixed = webpage.replace(
            Regex("Object\\.create\\(null(?:,(\\{.+\\}))?\\)", RegexOption.DOT_MATCHES_ALL),
            "$1",
        )
        val marker = Regex("window\\.__NUXT__\\s*=").find(fixed)
            ?: throw ExtractionError.Malformed("The LSM page had no Nuxt data.")
        val nuxt = balancedAfter(fixed, marker.range.last + 1)
            ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
            ?: throw ExtractionError.Malformed("The LSM Nuxt data was not JSON.")
        val data = findReplay(nuxt)
            ?: throw ExtractionError.Malformed("The LSM page had no replay data.")
        val playback = data.obj("playback")
            ?: throw ExtractionError.Malformed("The LSM replay had no playback data.")
        val playbackType = playback.str("type")
        val service = playback.obj("service")
        val mediaItem = data.obj("mediaItem")
        val base = InfoDict(
            id = videoId,
            title = mediaItem?.str("title"),
            description = mediaItem?.str("lead") ?: mediaItem?.str("body"),
            duration = mediaItem?.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(mediaItem?.str("aired_at")),
            thumbnails = listOfNotNull(mediaItem?.str("largeThumbnail")?.let { Thumbnail(url = it) }),
            webpageUrl = url,
            extractor = "lsm:replay",
            extractorKey = IE_KEY,
        )
        return when (playbackType) {
            "playable_audio_lr" -> {
                val hlsUrl = service?.str("hls_url")
                    ?: throw ExtractionError.NoFormats("The LSM replay had no HLS URL.")
                base.copy(
                    formats = listOf(
                        MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native"),
                    ),
                )
            }

            "embed" -> {
                val embedUrl = service?.str("url")
                    ?: throw ExtractionError.Malformed("The LSM replay had no embed URL.")
                base.copy(redirectUrl = embedUrl)
            }

            else -> throw ExtractionError.Unavailable("Unsupported playback type \"$playbackType\".")
        }
    }

    /** Finds the `__REPLAY__` object anywhere in the Nuxt data. */
    private fun findReplay(element: JsonElement?): JsonObject? = when (element) {
        is JsonObject -> {
            val direct = element["__REPLAY__"] as? JsonObject
            if (direct != null && direct.obj("playback") != null) {
                direct
            } else {
                element.values.firstNotNullOfOrNull { findReplay(it) }
            }
        }

        is JsonArray -> element.firstNotNullOfOrNull { findReplay(it) }
        else -> null
    }

    companion object {
        const val IE_KEY: String = "LSMReplay"

        val VALID_URL: Regex = Regex(
            "https?://replay\\.lsm\\.lv/[^/?#]+/(?:skaties/|klausies/)?(?:ieraksts|statja)/[^/?#]+/" +
                "(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun balancedAfter(value: String, start: Int): String? {
    val range = balancedRange(value, start) ?: return null
    return value.substring(range.first, range.last + 1)
}

private fun balancedRange(value: String, start: Int): IntRange? {
    val open = value.indexOf('{', start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = open
    while (i < value.length) {
        val c = value[i]
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
                    if (depth == 0) return open..i
                }
            }
        }
        i++
    }
    return null
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "")
    for (part in query.split('&')) {
        if (part.substringBefore('=', "") == name) {
            val value = part.substringAfter('=', "")
            if (value.isNotBlank()) return value
        }
    }
    return null
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun decodeUrlComponent(value: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                out.append(hex.toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
