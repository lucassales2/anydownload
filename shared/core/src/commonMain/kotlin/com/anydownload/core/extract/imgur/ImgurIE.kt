/*
 * Imgur extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `imgur.py` from
 * `yt_dlp/extractor/imgur.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `imgur.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the anonymous `api.imgur.com/post/v1` media/albums API (the public
 * client id upstream carries, not a user credential), the gifv page source
 * scan, the `videoItem` GIF JSON, the twitter:player meta format, and the
 * album/gallery entries. The port does not carry like/comment counters or
 * uploader URLs, so they are dropped. No cookie, user token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.imgur

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

/** The anonymous public client id upstream uses; not a user credential. */
private const val CLIENT_ID = "546c25a59c58ad7"

/** Upstream `ImgurIE`: one media page. */
class ImgurIE(
    http: ExtractorHttp,
) : ImgurBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi("media", videoId)
        val media = data.array("media")?.firstOrNull() as? JsonObject
        val isVideo = media?.str("type") == "video" ||
            media?.obj("metadata")?.boolean("is_animated") == true
        if (!isVideo) {
            throw ExtractionError.Unavailable("$videoId is not a video or animated image")
        }
        val webpage = try {
            http.downloadWebpage("https://i.imgur.com/$videoId.gifv")
        } catch (error: ExtractionError) {
            ""
        }
        val formats = mutableListOf<MediaFormat>()
        if (media != null) {
            val mediaUrl = media.str("url")
            if (mediaUrl != null) {
                val isImage = media.str("type") == "image"
                formats += MediaFormat(
                    url = mediaUrl,
                    ext = media.str("ext") ?: ExtractorUtils.mimetype2ext(media.str("mime_type"))
                        ?: ExtractorUtils.determineExt(mediaUrl),
                    width = media.number("width")?.toLong(),
                    height = media.number("height")?.toLong(),
                    filesize = media.number("size")?.toLong(),
                    acodec = if (isImage || media.obj("metadata")?.boolean("has_sound") == false) {
                        MediaFormat.CODEC_NONE
                    } else {
                        null
                    },
                    preference = if (isImage) -10 else null,
                )
            }
        }
        val videoElements = Regex("(?s)<div class=\"video-elements\">(.*?)</div>").find(webpage)
            ?.groupValues?.get(1)
        if (videoElements != null) {
            val width = ogNumber(webpage, "video:width") ?: ogNumber(webpage, "image:width")
            val height = ogNumber(webpage, "video:height") ?: ogNumber(webpage, "image:height")
            for (match in Regex("<source\\s+src=\"([^\"]+)\"\\s+type=\"([^\"]+)\"").findAll(videoElements)) {
                val mimeType = match.groupValues[2]
                formats += MediaFormat(
                    formatId = mimeType.substringAfter('/', ""),
                    url = protoRelativeUrl(match.groupValues[1]),
                    ext = ExtractorUtils.mimetype2ext(mimeType),
                    width = width,
                    height = height,
                )
            }
            val gifJson = Regex("var\\s+videoItem\\s*=").find(webpage)?.let { marker ->
                balancedAfter(webpage, marker.range.last + 1)
            }?.let { ExtractorUtils.parseJson(it) as? JsonObject }
            val gifUrl = gifJson?.str("gifUrl")?.let { protoRelativeUrl(it) }
            if (gifUrl != null) {
                formats += MediaFormat(
                    formatId = "gif",
                    url = gifUrl,
                    ext = "gif",
                    width = width,
                    height = height,
                    filesize = gifJson.number("size")?.toLong(),
                    acodec = MediaFormat.CODEC_NONE,
                    vcodec = "gif",
                    container = "gif",
                    preference = -10,
                )
            }
        }
        metaContent(webpage, "twitter:player:stream")?.let { stream ->
            formats += MediaFormat(
                formatId = "twitter",
                url = stream,
                ext = ExtractorUtils.mimetype2ext(
                    metaContent(webpage, "twitter:player:stream:content_type"),
                ),
                width = metaContent(webpage, "twitter:width")?.toLongOrNull(),
                height = metaContent(webpage, "twitter:height")?.toLongOrNull(),
            )
        }
        val deduped = formats.distinctBy { it.url }
        if (deduped.isEmpty()) {
            throw ExtractionError.NoFormats("No sources found for video $videoId. Maybe a plain image?")
        }
        val metadata = media?.obj("metadata")
        val account = data.obj("account")
        val thumbnailUrl = metaContent(webpage, "thumbnailUrl")
            ?: metaContent(webpage, "twitter:image")
            ?: metaContent(webpage, "og:image")
        return InfoDict(
            id = videoId,
            title = metadata?.str("title") ?: metaContent(webpage, "og:title"),
            description = getDescription(
                metadata?.str("description") ?: metaContent(webpage, "og:description").orEmpty(),
            ),
            duration = metadata?.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(
                metadata?.str("created_at") ?: data.str("created_at"),
            ),
            channel = account?.str("username"),
            thumbnails = listOfNotNull(thumbnailUrl?.let { Thumbnail(url = it) }),
            formats = deduped,
            webpageUrl = url,
            extractor = "imgur",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Imgur"

        val VALID_URL: Regex = Regex(
            "https?://(?:i\\.)?imgur\\.com/(?!(?:a|gallery|t|topic|r)/)(?:[^/?#]+-)?(?<id>[a-zA-Z0-9]+)",
        )
    }
}

/** Upstream `ImgurGalleryIE`: a gallery. */
class ImgurGalleryIE(
    http: ExtractorHttp,
) : ImgurGalleryBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL, gallery = true) {
    companion object {
        const val IE_KEY: String = "ImgurGallery"

        val VALID_URL: Regex = Regex(
            "https?://(?:i\\.)?imgur\\.com/(?:gallery|(?:t(?:opic)?|r)/[^/?#]+)/" +
                "(?:[^/?#]+-)?(?<id>[a-zA-Z0-9]+)",
        )
    }
}

/** Upstream `ImgurAlbumIE`: an album. */
class ImgurAlbumIE(
    http: ExtractorHttp,
) : ImgurGalleryBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL, gallery = false) {
    companion object {
        const val IE_KEY: String = "ImgurAlbum"

        val VALID_URL: Regex = Regex("https?://(?:i\\.)?imgur\\.com/a/(?:[^/?#]+-)?(?<id>[a-zA-Z0-9]+)")
    }
}

/** Shared upstream `ImgurGalleryBaseIE` behaviour. */
abstract class ImgurGalleryBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    private val gallery: Boolean,
) : ImgurBaseIE(ieKey = ieKey, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        var galleryId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = try {
            callApi("albums", galleryId)
        } catch (error: ExtractionError) {
            null
        }
        val title = data?.str("title")?.trim()
        val description = data?.let { getDescription(it.str("description").orEmpty()) }
        if (data?.boolean("is_album") == true) {
            val items = data.array("media").orEmpty().mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val animated = item.obj("metadata")?.boolean("is_animated") == true
                if (item.str("type") != "video" && !animated) return@mapNotNull null
                item.primitive("id")
            }
            val mediaId = if (gallery && items.size == 1) items.single() else null
            if (mediaId == null) {
                return InfoDict(
                    id = galleryId,
                    title = title,
                    description = description,
                    entries = items.map { InfoEntry(url = "https://imgur.com/$it") },
                    webpageUrl = url,
                    extractor = "imgur:gallery",
                    extractorKey = ieKey,
                )
            }
            galleryId = mediaId
        }
        return InfoDict(
            id = galleryId,
            title = title,
            description = description,
            redirectUrl = "https://imgur.com/$galleryId",
            webpageUrl = url,
            extractor = "imgur:gallery",
            extractorKey = ieKey,
        )
    }
}

/** Shared upstream `ImgurBaseIE` behaviour. */
abstract class ImgurBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_call_api`: the anonymous post API. */
    protected suspend fun callApi(endpoint: String, videoId: String): JsonObject = http.downloadJson(
        "https://api.imgur.com/post/v1/$endpoint/$videoId?client_id=$CLIENT_ID&include=media,account",
    ) as? JsonObject ?: throw ExtractionError.Malformed("The Imgur API returned no object.")

    /** Upstream `get_description`. */
    protected fun getDescription(value: String?): String? {
        if (value == null || value.contains("Discover the magic of the internet at Imgur")) return null
        return value.takeIf { it.isNotBlank() }
    }
}

// ------------------------------------------------------------------ helpers

private fun protoRelativeUrl(value: String): String =
    if (value.startsWith("//")) "https:$value" else value

private fun ogNumber(webpage: String, property: String): Long? =
    metaContent(webpage, property)?.toLongOrNull()

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

private fun balancedAfter(html: String, start: Int): String? {
    val open = html.indexOf('{', start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = open
    while (i < html.length) {
        val c = html[i]
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
                    if (depth == 0) return html.substring(open, i + 1)
                }
            }
        }
        i++
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
