/*
 * Pinterest extractors — AnyDownload
 *
 * Kotlin translation of `pinterest.py` from `yt_dlp/extractor/pinterest.py`
 * at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `pinterest.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `PinResource` JSON (one HLS row or direct row per `video_list`
 * entry, thumbnails, title/description/uploader, uploadDate from
 * `created_at`), the `Board`/`BoardFeed` pagination as pin-URL entries, and
 * the country-domain list. Limitations: the platform allowlist refuses the
 * `X-Pinterest-PWS-Handler` header upstream sends (the streaks/`x-addr`
 * rule); an embed-src pin returns one child entry at the embed URL because
 * the port's transparent dispatch is the entry expansion, so the pin
 * metadata merge is not carried; the repost/comment/category/tag fields the
 * info dict does not model are dropped. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.pinterest

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val VALID_URL_BASE =
    "https?://(?:[^/]+\\.)?pinterest\\.(?:" +
        "com|fr|de|ch|jp|cl|ca|it|co\\.uk|nz|ru|com\\.au|at|pt|co\\.kr|es|com\\.mx|" +
        "dk|ph|th|com\\.uy|co|nl|info|kr|ie|vn|com\\.vn|ec|mx|in|pe|co\\.at|hu|" +
        "co\\.in|co\\.nz|id|com\\.ec|com\\.py|tw|be|uk|com\\.bo|com\\.pe)"

/** Upstream `PinterestBaseIE`: the resource API and the video info builder. */
abstract class PinterestBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {

    /** Upstream `_call_api`: the `resource_response` of one resource request. */
    protected suspend fun callApi(resource: String, options: JsonObject): JsonObject {
        val data = buildJsonObject { put("options", options) }
        val response = http.downloadJson(
            "https://www.pinterest.com/resource/${resource}Resource/get/?data=${percentEncode(data.toString())}",
            headers = mapOf("x-pinterest-pws-handler" to "www/[username].js"),
        ) as? JsonObject
            ?: throw ExtractionError.Malformed("The Pinterest API was not an object.")
        return response["resource_response"] as? JsonObject
            ?: throw ExtractionError.Malformed("The Pinterest API returned no resource response.")
    }

    /** Upstream `_extract_video`. */
    protected fun extractVideo(data: JsonObject, extractFormats: Boolean = true): InfoDict {
        val videoId = data.str("id")
            ?: throw ExtractionError.Malformed("The Pinterest pin carried no id.")

        val thumbnails = mutableListOf<Thumbnail>()
        for ((_, value) in data.obj("images").orEmpty()) {
            val image = value as? JsonObject ?: continue
            val imageUrl = image.str("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            thumbnails += Thumbnail(
                url = imageUrl,
                width = image.number("width")?.toLong(),
                height = image.number("height")?.toLong(),
            )
        }

        var title = data.str("title") ?: data.str("grid_title") ?: ""
        val description = data.str("seo_description") ?: data.str("description")
        val createdAt = data.str("created_at")?.let(ExtractorUtils::unifiedStrdate)
        val uploader = data.obj("closeup_attribution")?.str("full_name")

        val domain = data.str("domain") ?: ""
        val embedSrc = data.obj("embed")?.str("src")
        if (!domain.equals("uploaded by user", ignoreCase = true) && embedSrc != null) {
            // Upstream `url_transparent`: the port's transparent dispatch is a
            // single child entry at the embed URL, so the pin metadata is not merged.
            return InfoDict(
                id = videoId,
                title = title.ifEmpty { null },
                description = description,
                uploadDate = createdAt,
                thumbnails = thumbnails,
                uploader = uploader,
                entries = listOf(InfoEntry(id = videoId, url = embedSrc)),
                webpageUrl = "https://www.pinterest.com/pin/$videoId/",
                extractor = "pinterest",
                extractorKey = ieKey,
            )
        }

        val formats = mutableListOf<MediaFormat>()
        var duration: Double? = null
        if (extractFormats) {
            val videoList = data.obj("videos")?.obj("video_list") ?: data.videoListFromStoryPin()
            val seen = mutableSetOf<String>()
            for ((formatId, value) in videoList.orEmpty()) {
                val formatDict = value as? JsonObject ?: continue
                val formatUrl = formatDict.str("url")
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: continue
                if (!seen.add(formatUrl)) continue
                duration = formatDict.number("duration")?.let { it / 1000.0 }
                val ext = ExtractorUtils.determineExt(formatUrl)
                if ("hls" in formatId.lowercase() || ext == "m3u8") {
                    formats += MediaFormat(
                        formatId = formatId,
                        url = formatUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                } else {
                    formats += MediaFormat(
                        formatId = formatId,
                        url = formatUrl,
                        width = formatDict.number("width")?.toLong(),
                        height = formatDict.number("height")?.toLong(),
                    )
                }
            }
        }

        return InfoDict(
            id = videoId,
            title = title.ifEmpty { null },
            formats = formats,
            duration = duration,
            thumbnails = thumbnails,
            description = description,
            uploadDate = createdAt,
            uploader = uploader,
            webpageUrl = "https://www.pinterest.com/pin/$videoId/",
            extractor = "pinterest",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `PinterestIE`: one pin. */
class PinterestIE(
    http: ExtractorHttp,
) : PinterestBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi(
            "Pin",
            buildJsonObject {
                put("field_set_key", "unauth_react_main_pin")
                put("id", videoId)
            },
        )["data"] as? JsonObject
            ?: throw ExtractionError.Malformed("The Pinterest pin response carried no data.")
        return extractVideo(data)
    }

    companion object {
        const val IE_KEY: String = "Pinterest"

        val VALID_URL: Regex = Regex("$VALID_URL_BASE/pin/(?:[\\w-]+--)?(?<id>\\d+)")
    }
}

/** Upstream `PinterestCollectionIE`: a board page. */
class PinterestCollectionIE(
    http: ExtractorHttp,
) : PinterestBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        !PinterestIE.VALID_URL.containsMatchIn(url) && super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val username = match.groups["username"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val slug = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val board = callApi(
            "Board",
            buildJsonObject {
                put("slug", slug)
                put("username", username)
            },
        )["data"] as? JsonObject
            ?: throw ExtractionError.Malformed("The Pinterest board response carried no data.")
        val boardId = board.str("id")
            ?: throw ExtractionError.Malformed("The Pinterest board carried no id.")

        val entries = mutableListOf<InfoEntry>()
        var bookmark: String? = null
        var pages = 0
        while (pages < MAX_PAGES) {
            pages++
            val options = buildJsonObject {
                put("board_id", boardId)
                put("page_size", 250)
                bookmark?.let { put("bookmarks", JsonArray(listOf(JsonPrimitive(it)))) }
            }
            val boardFeed = callApi("BoardFeed", options)
            for (element in (boardFeed["data"] as? JsonArray).orEmpty()) {
                val item = element as? JsonObject ?: continue
                if (item.str("type") != "pin") continue
                val videoId = item.str("id") ?: continue
                entries += InfoEntry(id = videoId, url = "https://www.pinterest.com/pin/$videoId/")
            }
            bookmark = boardFeed.str("bookmark")
            if (bookmark == null) break
        }

        return InfoDict(
            id = boardId,
            title = board.str("name"),
            entries = entries,
            webpageUrl = url,
            extractor = "pinterest:collection",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "PinterestCollection"

        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex("$VALID_URL_BASE/(?<username>[^/]+)/(?<id>[^/?#&]+)")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `traverse_obj(data, ('story_pin_data', 'pages', ..., 'blocks', ..., 'video', 'video_list'))`. */
private fun JsonObject.videoListFromStoryPin(): JsonObject? {
    val pages = obj("story_pin_data")?.array("pages") ?: return null
    for (page in pages) {
        val blocks = (page as? JsonObject)?.array("blocks") ?: continue
        for (block in blocks) {
            (block as? JsonObject)?.obj("video")?.obj("video_list")?.let { return it }
        }
    }
    return null
}

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
