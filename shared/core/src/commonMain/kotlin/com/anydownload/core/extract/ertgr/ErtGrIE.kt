/*
 * ERT extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `ertgr.py` from
 * `yt_dlp/extractor/ertgr.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ertgr.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public api.app.ertflix.gr Player/AcquireContent and
 * Tile/GetTiles/GetSeriesDetails calls (HLS and direct rows; MPD skipped),
 * the series episode groups, and the ert.gr webtv embed VOD path. The port
 * does not carry season/series/episode fields, so they are dropped. No
 * cookie, token, or private URL is stored here.
 */
package com.anydownload.core.extract.ertgr

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

private const val API_BASE = "https://api.app.ertflix.gr"
private const val HEADERS_PARAM = "{\"X-Api-Date-Format\":\"iso\",\"X-Api-Camel-Case\":false}"

/** Shared upstream `ERTFlixBaseIE` API helpers. */
abstract class ERTFlixBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_call_api`. */
    protected suspend fun callApi(
        videoId: String,
        method: String,
        apiVersion: Int = 1,
        data: JsonObject? = null,
        extraParams: Map<String, String> = emptyMap(),
    ): JsonObject? {
        val query = mutableListOf("\$headers=${encodeQueryValue(HEADERS_PARAM)}")
        if (data == null) query += "platformCodename=www"
        for ((key, value) in extraParams) query += "$key=${encodeQueryValue(value)}"
        val body = data?.let {
            ExtractorUtils.parseJson(
                "{\"platformCodename\":\"www\"," + it.toString().removePrefix("{").let { rest ->
                    if (rest.isBlank()) "}" else rest
                },
            )
        }
        val response = try {
            http.downloadJson(
                "$API_BASE/v$apiVersion/$method?" + query.joinToString("&"),
                method = if (body != null) "POST" else "GET",
                headers = if (body != null) {
                    mapOf("Content-Type" to "application/json;charset=utf-8")
                } else {
                    emptyMap()
                },
                body = body?.toString()?.encodeToByteArray(),
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        if (response?.obj("Result")?.boolean("Success") == true) return response
        return null
    }

    /** Upstream `_call_api_get_tiles`. */
    protected suspend fun callApiGetTiles(videoId: String): JsonObject? {
        val requested = "[{\"Id\":\"$videoId\"}]"
        val data = ExtractorUtils.parseJson("{\"RequestedTiles\":$requested}") as? JsonObject
            ?: return null
        val response = callApi(videoId, "Tile/GetTiles", apiVersion = 2, data = data) ?: return null
        return response.array("Tiles")?.firstOrNull { (it as? JsonObject)?.str("Id") == videoId }
            as? JsonObject
    }

    /** Upstream `_extract_formats_and_subs`. */
    protected fun extractFormats(mediaInfo: JsonObject, videoId: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (mediaElement in mediaInfo.array("MediaFiles").orEmpty()) {
            val media = mediaElement as? JsonObject ?: continue
            if (media.str("RoleCodename") != "main") continue
            for (formatElement in media.array("Formats").orEmpty()) {
                val format = formatElement as? JsonObject ?: continue
                val formatUrl = format.str("Url") ?: continue
                when (ExtractorUtils.determineExt(formatUrl)) {
                    "m3u8" -> formats += MediaFormat(
                        formatId = "hls",
                        url = formatUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )

                    "mpd" -> Unit // MPEG-DASH manifests are not translated.

                    else -> formats += MediaFormat(
                        formatId = media.primitive("Id"),
                        url = formatUrl,
                        ext = ExtractorUtils.determineExt(formatUrl),
                    )
                }
            }
        }
        return formats
    }
}

/** Upstream `ERTFlixCodenameIE`: an ERTFLIX codename. */
class ERTFlixCodenameIE(
    http: ExtractorHttp,
) : ERTFlixBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val mediaInfo = callApi(videoId, "Player/AcquireContent", extraParams = mapOf("codename" to videoId))
            ?: throw ExtractionError.Unavailable("The ERTFLIX codename returned no content.")
        val formats = extractFormats(mediaInfo, videoId)
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The ERTFLIX content returned no playable format.")
        }
        return InfoDict(
            id = videoId,
            title = videoId,
            formats = formats,
            webpageUrl = url,
            extractor = "ertflix:codename",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ERTFlixCodename"

        val VALID_URL: Regex = Regex("ertflix:(?<id>[\\w-]+)")
    }
}

/** Upstream `ERTFlixIE`: an ertflix.gr vod or series page. */
class ERTFlixIE(
    http: ExtractorHttp,
) : ERTFlixBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        if (videoId.startsWith("ser.")) {
            return extractSeries(videoId, url)
        }
        val tile = callApiGetTiles(videoId)
            ?: throw ExtractionError.Unavailable("The ERTFLIX tile was not found.")
        val codename = tile.str("Codename")
            ?: throw ExtractionError.Malformed("The ERTFLIX tile had no codename.")
        val episodeId = tile.str("Id")
        val title = tile.str("Title")
        val description = tile.str("ShortDescription") ?: tile.str("TinyDescription")
        val thumbnail = mainImageUrl(tile)
        return InfoDict(
            id = codename,
            title = title,
            description = description,
            duration = tile.number("DurationSeconds"),
            uploadDate = ExtractorUtils.unifiedStrdate(tile.str("PublishDate")),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            redirectUrl = "ertflix:$codename",
            webpageUrl = url,
            extractor = "ertflix",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun extractSeries(seriesId: String, url: String): InfoDict {
        val mediaInfo = callApi(
            seriesId,
            "Tile/GetSeriesDetails",
            extraParams = mapOf("id" to seriesId),
        ) ?: throw ExtractionError.Unavailable("The ERTFLIX series returned no details.")
        val series = mediaInfo.obj("Series") ?: JsonObject(emptyMap())
        val entries = mutableListOf<InfoEntry>()
        for (groupElement in mediaInfo.array("EpisodeGroups").orEmpty()) {
            val group = groupElement as? JsonObject ?: continue
            for (episodeElement in group.array("Episodes").orEmpty()) {
                val episode = episodeElement as? JsonObject ?: continue
                val codename = episode.str("Codename") ?: continue
                if (episode.boolean("HasPlayableStream") == false) continue
                entries += InfoEntry(
                    id = codename,
                    title = episode.str("Title"),
                    url = "ertflix:$codename",
                )
            }
        }
        return InfoDict(
            id = seriesId,
            title = series.str("Title"),
            description = series.str("ShortDescription") ?: series.str("TinyDescription"),
            entries = entries,
            webpageUrl = url,
            extractor = "ertflix",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ERTFlix"

        val VALID_URL: Regex = Regex(
            "https?://www\\.ertflix\\.gr/(?:[^/]+/)?(?:series|vod)/(?<id>[a-z]{3}\\.\\d+)",
        )
    }
}

/** Upstream `ERTWebtvEmbedIE`: an ert.gr webtv embed. */
class ERTWebtvEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url)?.let { decodeUrlComponent(it) }
            ?: throw ExtractionError.UnsupportedUrl()
        var thumbnail = queryParam(url, "bgimg")
        if (thumbnail != null && !thumbnail.startsWith("http")) {
            thumbnail = "https://program.ert.gr$thumbnail"
        }
        return InfoDict(
            id = videoId,
            title = "VOD - $videoId",
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = "https://mediastream.ert.gr/vodedge/_definst_/mp4:dvrorigin/$videoId/playlist.m3u8",
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            webpageUrl = url,
            extractor = "ertwebtv:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ERTWebtvEmbed"

        val VALID_URL: Regex = Regex(
            "https?://www\\.ert\\.gr/webtv/live-uni/vod/dt-uni-vod\\.php\\?([^#]+&)?f=(?<id>[^#\u0026]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun mainImageUrl(tile: JsonObject): String? {
    val images = tile.array("Images") ?: tile.array("Image") ?: return null
    for (element in images) {
        val image = element as? JsonObject ?: continue
        if (image.boolean("IsMain") == true) return image.str("Url")
    }
    return null
}

private fun encodeQueryValue(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    val hex = "0123456789ABCDEF"
    val out = StringBuilder()
    for (byte in value.encodeToByteArray()) {
        val character = byte.toInt().toChar()
        when {
            character in unreserved -> out.append(character)
            character == ' ' -> out.append('+')
            else -> out.append('%').append(hex[(byte.toInt() shr 4) and 0xF])
                .append(hex[byte.toInt() and 0xF])
        }
    }
    return out.toString()
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "")
    for (part in query.split('&')) {
        if (part.substringBefore('=', "") == name) {
            val value = part.substringAfter('=', "")
            if (value.isNotBlank()) return decodeUrlComponent(value)
        }
    }
    return null
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

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
