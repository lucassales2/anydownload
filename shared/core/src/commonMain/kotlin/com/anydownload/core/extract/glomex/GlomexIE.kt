/*
 * Glomex extractors — AnyDownload
 *
 * Kotlin translation of `glomex.py` from `yt_dlp/extractor/glomex.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `glomex.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the integration-cloudfront API (one HLS row per m3u8 source, direct
 * rows, the language tag on every row), the 960x540 image thumbnails, the
 * video id type note, the geo-blocked error, and the player URL build. The
 * upstream `smuggle_url` origin is replaced by a direct `extractEmbed` call
 * between the two classes. Limitations: a multi-video playlist maps to the
 * port's selectable `media` items; m3u8 subtitles are not parsed (one row
 * per manifest); the `_extract_embed_urls` generic discovery is not carried
 * (GenericIE stays out). No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.glomex

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DEFAULT_ORIGIN_URL = "https://player.glomex.com/"
private const val API_URL = "https://integration-cloudfront-eu-west-1.mes.glomex.cloud/"
private const val BASE_PLAYER_URL = "https://player.glomex.com/integration/1/iframe-player.html"

/** Upstream `GlomexBaseIE`: the API call and the video mapping. */
abstract class GlomexBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {

    /** Upstream `_download_and_extract_api_data`. */
    protected suspend fun downloadAndExtract(
        playlistId: String,
        integration: String,
        currentUrl: String,
    ): InfoDict {
        val query = "integration_id=${percentEncode(integration)}" +
            "&playlist_id=${percentEncode(playlistId)}" +
            "&current_url=${percentEncode(currentUrl)}"
        val apiData = http.downloadJson("$API_URL?$query") as? JsonObject
            ?: throw ExtractionError.Malformed("The Glomex API was not an object.")
        val videos = apiData.array("videos")
            ?: throw ExtractionError.Malformed("The Glomex API returned no videos.")
        if (videos.isEmpty()) {
            throw ExtractionError.Unavailable("no videos found for $playlistId")
        }
        val extracted = videos.mapNotNull { it as? JsonObject }.map { extractApiData(it, playlistId) }
        if (extracted.size == 1) return extracted[0]
        return InfoDict(
            id = playlistId,
            media = extracted.map { info ->
                InfoMedia(
                    mediaId = info.id ?: playlistId,
                    title = info.title,
                    duration = info.duration,
                    thumbnails = info.thumbnails,
                    formats = info.formats,
                )
            },
            extractor = "glomex",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_extract_api_data`. */
    private fun extractApiData(video: JsonObject, videoId: String): InfoDict {
        if (video.str("error_code") == "contentGeoblocked") {
            val countries = video.array("geo_locations").orEmpty().mapNotNull {
                (it as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() }
            }
            throw ExtractionError.GeoRestricted(countries = countries)
        }

        val language = video.str("language")
        val formats = mutableListOf<MediaFormat>()
        for ((formatId, value) in video.obj("source").orEmpty()) {
            val formatUrl = (value as? JsonPrimitive)?.content ?: continue
            if (ExtractorUtils.determineExt(formatUrl) == "m3u8") {
                formats += MediaFormat(
                    formatId = formatId,
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    language = language,
                )
            } else {
                formats += MediaFormat(url = formatUrl, formatId = formatId, language = language)
            }
        }

        val thumbnails = mutableListOf<Thumbnail>()
        val seen = mutableSetOf<String>()
        val images = (video.array("images") ?: JsonArray(emptyList())) + listOfNotNull(video["image"])
        for (element in images) {
            val image = element as? JsonObject ?: continue
            val imageUrl = image.str("url") ?: continue
            val thumbnailUrl = "$imageUrl/profile:player-960x540"
            if (!seen.add(thumbnailUrl)) continue
            thumbnails += Thumbnail(url = thumbnailUrl, id = image.str("id"), width = 960, height = 540)
        }

        return InfoDict(
            id = video.str("clip_id") ?: videoId,
            title = video.str("title"),
            description = video.str("description"),
            thumbnails = thumbnails,
            duration = video.number("clip_duration"),
            uploadDate = video.number("created_at")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            formats = formats,
            extractor = "glomex",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `GlomexIE`: a video.glomex.com page. */
class GlomexIE(
    http: ExtractorHttp,
) : GlomexBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `url_result(build_player_url(...), GlomexEmbedIE, video_id)`.
        val embed = GlomexEmbedIE(http)
        val info = embed.extractEmbed(videoId, INTEGRATION_ID, url)
        return info.copy(id = videoId, webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "Glomex"

        private const val INTEGRATION_ID = "19syy24xjn1oqlpc"

        val VALID_URL: Regex = Regex("https?://video\\.glomex\\.com/[^/]+/(?<id>v-[^-]+)")
    }
}

/** Upstream `GlomexEmbedIE`: an iframe-player URL. */
class GlomexEmbedIE(
    http: ExtractorHttp,
) : GlomexBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val integration = queryValue(url, "integrationId")
            ?: throw ExtractionError.Unavailable("No integrationId in URL.")
        return extractEmbed(playlistId, integration, DEFAULT_ORIGIN_URL).copy(webpageUrl = url)
    }

    /** Upstream `build_player_url` + the smuggled origin, as one direct call. */
    internal suspend fun extractEmbed(
        playlistId: String,
        integration: String,
        originUrl: String,
    ): InfoDict = downloadAndExtract(playlistId, integration, originUrl)

    companion object {
        const val IE_KEY: String = "GlomexEmbed"

        /** Upstream `build_player_url`; the origin is passed as a call argument. */
        fun buildPlayerUrl(playlistId: String, integration: String): String =
            "$BASE_PLAYER_URL?playlistId=${percentEncode(playlistId)}" +
                "&integrationId=${percentEncode(integration)}"

        val VALID_URL: Regex = Regex(
            "https?://player\\.glomex\\.com/integration/[^/]/iframe-player\\.html" +
                "\\?([^#]+&)?playlistId=(?<id>[^#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun queryValue(url: String, name: String): String? {
    for (pair in url.substringAfter('?', "").split('&')) {
        if (pair.substringBefore('=') == name) return pair.substringAfter('=', "")
    }
    return null
}

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

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
