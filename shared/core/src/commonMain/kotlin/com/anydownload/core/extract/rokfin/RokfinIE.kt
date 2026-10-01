/*
 * Rokfin extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `rokfin.py` from
 * `yt_dlp/extractor/rokfin.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rokfin.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public post/stream API (metadata, m3u8/plain formats, the
 * storyboard fallback), the stack and channel listings. The OAuth login
 * (premium content), the viewer-comment walk, and the Meilisearch search key
 * (`rkfnsearch:`) are not translated; premium posts without a public URL
 * fail typed with a login requirement. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.rokfin

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

private const val API_BASE_URL = "https://prod-api-v2.production.rokfin.com/api/v2/public/"

/** Upstream `RokfinIE`: a post or stream. */
class RokfinIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoType = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val metadata = http.downloadJson("$API_BASE_URL$videoId") as? JsonObject
            ?: throw ExtractionError.Malformed("The Rokfin API was not an object.")
        val scheduled = ExtractorUtils.unifiedStrdate(metadata.str("scheduledAt"))
        val isPremium = metadata["premiumPlan"] != null || metadata["premium"] != null
        var videoUrl = metadata.str("url")
            ?: (metadata.array("content")?.firstOrNull() as? JsonObject)?.str("contentUrl")
        if (videoUrl == null || videoUrl == "fake.m3u8") {
            val storyboard = metadata.str("timelineUrl")
                ?: (metadata.array("content")?.firstOrNull() as? JsonObject)?.str("timelineUrl")
            val capture = storyboard?.let {
                Regex("https?://[^/]+/([^/]+)/storyboard\\.vtt").find(it)?.groupValues?.get(1)
            }
            videoUrl = capture?.let { "https://stream.v.rokfin.com/$it.m3u8" }
        }
        val formats = mutableListOf<MediaFormat>()
        if (videoUrl != null) {
            formats += MediaFormat(
                url = videoUrl,
                ext = if (ExtractorUtils.determineExt(videoUrl) == "m3u8") "mp4" else null,
                protocol = if (ExtractorUtils.determineExt(videoUrl) == "m3u8") "m3u8_native" else null,
            )
        }
        if (formats.isEmpty()) {
            if (isPremium) {
                throw ExtractionError.LoginRequired("This video is only available to premium users.")
            }
            if (scheduled != null) {
                throw ExtractionError.NotYetAvailable("The stream is offline; scheduled for $scheduled.")
            }
            throw ExtractionError.NoFormats("The Rokfin post carried no playable format.")
        }
        val content = metadata.array("content")?.firstOrNull() as? JsonObject
        val uploader = metadata.obj("createdBy")?.str("username") ?: metadata.obj("creator")?.str("username")
        return InfoDict(
            id = videoId,
            title = metadata.str("title") ?: content?.str("contentTitle"),
            description = metadata.str("description") ?: content?.str("contentDescription"),
            duration = content?.number("duration"),
            thumbnails = listOfNotNull(
                (metadata.str("thumbnail") ?: content?.str("thumbnailUrl1"))?.let { Thumbnail(url = it) },
            ),
            uploader = uploader,
            channel = metadata.obj("createdBy")?.str("name") ?: metadata.obj("creator")?.str("name"),
            channelId = metadata.obj("createdBy")?.primitiveText("id") ?: metadata.obj("creator")?.primitiveText("id"),
            uploadDate = metadata.number("postedAtMilli")?.let {
                ExtractorUtils.epochSecondsToDate((it / 1000).toLong())
            } ?: dateFromIso(metadata.str("scheduledAt") ?: metadata.str("creationDateTime")),
            isLive = metadata.str("stoppedAt") == null && scheduled == null && videoType == "stream",
            availability = if (isPremium) "premium_only" else "public",
            formats = formats,
            webpageUrl = url,
            extractor = "rokfin",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Rokfin"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rokfin\\.com/(?<id>(?<type>post|stream)/\\d+)",
        )
    }
}

/** Upstream `RokfinStackIE`: a stack listing. */
class RokfinStackIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val listId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadata = http.downloadJson("${API_BASE_URL}stack/$listId") as? JsonObject
            ?: throw ExtractionError.Malformed("The Rokfin stack API was not an object.")
        return InfoDict(
            id = listId,
            title = metadata.str("title"),
            entries = videoEntries(metadata),
            webpageUrl = url,
            extractor = "rokfin:stack",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RokfinStack"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?rokfin\\.com/stack/(?<id>[^/]+)")
    }
}

/** Upstream `RokfinChannelIE`: a channel listing. */
class RokfinChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelName = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val channelInfo = http.downloadJson("${API_BASE_URL}user/$channelName") as? JsonObject
            ?: throw ExtractionError.Malformed("The Rokfin user API was not an object.")
        val channelId = channelInfo.primitiveText("id")
            ?: throw ExtractionError.Malformed("The Rokfin user had no id.")
        val entries = mutableListOf<InfoEntry>()
        var page = 0
        while (page < MAX_PAGES) {
            val dataUrl = "${API_BASE_URL}user/$channelName/posts?page=$page&size=50"
            val metadata = try {
                http.downloadJson(dataUrl) as? JsonObject
            } catch (error: ExtractionError) {
                break
            } ?: break
            entries += videoEntries(metadata)
            if (metadata["last"] == JsonPrimitive(true)) break
            page++
        }
        return InfoDict(
            id = "$channelId-new",
            title = "$channelName - New",
            description = channelInfo.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "rokfin:channel",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RokfinChannel"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rokfin\\.com/(?!((feed/?)|(discover/?)|(channels/?))$)(?<id>[^/]+)/?$",
        )

        private const val MAX_PAGES = 5
    }
}

// ------------------------------------------------------------------ helpers

private fun dateFromIso(value: String?): String? {
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(value ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
}

private val MEDIA_TYPES = mapOf(
    "video" to "post",
    "audio" to "post",
    "stream" to "stream",
    "dead_stream" to "stream",
    "stack" to "stack",
)

private fun videoEntries(metadata: JsonObject): List<InfoEntry> =
    metadata.array("content").orEmpty().mapNotNull { element ->
        val content = element as? JsonObject ?: return@mapNotNull null
        val mediaType = MEDIA_TYPES[content.str("mediaType")] ?: return@mapNotNull null
        val videoId = if (mediaType == "post") {
            content.primitiveText("id")
        } else {
            content.primitiveText("mediaId")
        } ?: return@mapNotNull null
        InfoEntry(
            id = "$mediaType/$videoId",
            title = content.obj("content")?.str("contentTitle"),
            url = "https://rokfin.com/$mediaType/$videoId",
        )
    }

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
