/*
 * Rooster Teeth extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `roosterteeth.py` from
 * `yt_dlp/extractor/roosterteeth.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `roosterteeth.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public svod-be watch API (Client-Type: web), the HLS row from
 * the reported m3u8 URL, the episode metadata and thumbnails, and the
 * series season/bonus-feature listings. FIRST-only content fails typed;
 * the Brightcove fallback for a 403 m3u8 is not translated; the port does
 * not carry series/season/episode/tags fields, so they are dropped. No
 * login, cookie, or token is stored here.
 */
package com.anydownload.core.extract.roosterteeth

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

private const val API_BASE = "https://svod-be.roosterteeth.com"
private const val API_BASE_URL = "$API_BASE/api/v1"
private const val MAX_SEASONS = 50

/** Shared upstream `RoosterTeethBaseIE` behaviour. */
abstract class RoosterTeethBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_video_info` for the fields the port carries. */
    protected fun videoInfo(data: JsonObject): InfoDict {
        val attributes = data.obj("attributes") ?: JsonObject(emptyMap())
        val subOnly = attributes.boolean("is_sponsors_only") == true
        val episodeId = data.primitive("uuid")
        var videoId = data.primitive("id")
        if (videoId != null && attributes["parent_content_id"] != null) {
            videoId += "-bonus"
        } else if (videoId == null) {
            videoId = episodeId
        }
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in data.obj("included")?.array("images").orEmpty()) {
            val image = element as? JsonObject ?: continue
            if (image.str("type") !in listOf("episode_image", "bonus_feature_image")) continue
            for ((name, value) in image.obj("attributes").orEmpty()) {
                val url = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (!url.isNullOrBlank()) thumbnails += Thumbnail(url = url)
            }
        }
        return InfoDict(
            id = videoId,
            title = attributes.str("title") ?: attributes.str("display_title"),
            description = attributes.str("description") ?: attributes.str("caption"),
            duration = attributes.number("length"),
            uploadDate = ExtractorUtils.unifiedStrdate(attributes.str("original_air_date")),
            channelId = attributes.str("channel_id"),
            availability = if (subOnly) "needs_subscription" else "public",
            thumbnails = thumbnails,
            extractor = "roosterteeth",
            extractorKey = ieKey,
        )
    }

    protected suspend fun downloadApi(path: String, note: String = ""): JsonObject {
        val url = when {
            path.startsWith("http") -> path
            path.startsWith("/") -> "$API_BASE$path"
            else -> "$API_BASE_URL/$path"
        }
        return http.downloadJson(url, headers = mapOf("Client-Type" to "web")) as? JsonObject
            ?: throw ExtractionError.Malformed("The Rooster Teeth API returned no object.")
    }
}

/** Upstream `RoosterTeethIE`: an episode/watch page. */
class RoosterTeethIE(
    http: ExtractorHttp,
) : RoosterTeethBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val apiEpisodeUrl = "$API_BASE_URL/watch/$displayId"
        val videoData = try {
            (downloadApi("watch/$displayId/videos").array("data")?.firstOrNull() as? JsonObject)
                ?: throw ExtractionError.Malformed("The Rooster Teeth video API returned no video.")
        } catch (error: ExtractionError) {
            if (error is ExtractionError.LoginRequired) {
                throw ExtractionError.LoginRequired(
                    "$displayId is only available for FIRST members",
                )
            }
            throw error
        }
        val m3u8Url = videoData.obj("attributes")?.str("url")
            ?: throw ExtractionError.NoFormats("The Rooster Teeth video API returned no stream URL.")
        val formats = listOf(
            MediaFormat(formatId = "hls", url = m3u8Url, ext = "mp4", protocol = "m3u8_native"),
        )
        val episode = downloadApi("watch/$displayId").array("data")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Rooster Teeth episode API returned no episode.")
        return videoInfo(episode).copy(
            formats = formats,
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RoosterTeeth"

        val VALID_URL: Regex = Regex(
            "https?://(?:.+?\\.)?roosterteeth\\.com/(?:bonus-feature|episode|watch)/(?<id>[^/?#\u0026]+)",
        )
    }
}

/** Upstream `RoosterTeethSeriesIE`: a series listing. */
class RoosterTeethSeriesIE(
    http: ExtractorHttp,
) : RoosterTeethBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val seriesId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val seasonNumber = queryParam(url, "season")?.toIntOrNull()
        val entries = mutableListOf<InfoEntry>()
        val seasons = downloadApi("shows/$seriesId/seasons?order=asc&order_by")
            .array("data").orEmpty().take(MAX_SEASONS)
        for (element in seasons) {
            val season = element as? JsonObject ?: continue
            val number = season.obj("attributes")?.number("number")?.toInt()
            if (seasonNumber != null && number != seasonNumber) continue
            val episodesLink = season.obj("links")?.str("episodes") ?: continue
            val episodes = try {
                downloadApi(episodesLink + "?per_page=1000").array("data").orEmpty()
            } catch (error: ExtractionError) {
                emptyList()
            }
            for (episodeElement in episodes) {
                val episode = episodeElement as? JsonObject ?: continue
                val self = episode.obj("canonical_links")?.str("self") ?: continue
                entries += InfoEntry(
                    id = episode.primitive("id"),
                    title = episode.obj("attributes")?.str("title"),
                    url = "https://www.roosterteeth.com$self",
                )
            }
        }
        if (seasonNumber == null) {
            val bonus = try {
                downloadApi("shows/$seriesId/bonus_features?order=asc&order_by&per_page=1000")
                    .array("data").orEmpty()
            } catch (error: ExtractionError) {
                emptyList()
            }
            for (element in bonus) {
                val episode = element as? JsonObject ?: continue
                val self = episode.obj("canonical_links")?.str("self") ?: continue
                entries += InfoEntry(
                    id = episode.primitive("id"),
                    title = episode.obj("attributes")?.str("title"),
                    url = "https://www.roosterteeth.com$self",
                )
            }
        }
        val id = if (seasonNumber == null) seriesId else "$seriesId-$seasonNumber"
        return InfoDict(
            id = id,
            entries = entries,
            webpageUrl = url,
            extractor = "roosterteeth:series",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RoosterTeethSeries"

        val VALID_URL: Regex = Regex("https?://(?:.+?\\.)?roosterteeth\\.com/series/(?<id>[^/?#\u0026]+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "")
    for (part in query.split('&')) {
        if (part.substringBefore('=', "") == name) {
            return part.substringAfter('=', "").takeIf { it.isNotBlank() }
        }
    }
    return null
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
