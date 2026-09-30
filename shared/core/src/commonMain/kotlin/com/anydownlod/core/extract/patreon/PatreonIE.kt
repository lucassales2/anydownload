/*
 * Patreon extractor — AnyDownload
 *
 * Kotlin translation of the public post subset of `PatreonIE` from
 * `yt_dlp/extractor/patreon.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `patreon.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `patreon.com/posts/<id>` and `creation?hid=<id>`. The public
 * `api/posts/<id>` JSON:API response maps the post title, cleaned content,
 * thumbnail, publish date, user include, campaign include, the `post_file`
 * download, and the `media` includes with a `download_url` + `size_bytes`
 * (mimetype extension). One attachment becomes `formats`; several become
 * bounded [InfoDict.media] items. An embed-only post re-dispatches its Vimeo
 * player URL or the raw embed URL. `current_user_can_view = false` fails
 * typed as a patron wall.
 *
 * Not translated: the campaign and search classes, comments, the media API
 * for inlined `data-media-id` content, and the Vids.io/YouTube embed
 * classification. A login wall or DRM wall is Partial. No cookie, bearer
 * token, or signed media URL is stored or committed; fixture hosts are
 * `*.example`.
 */
package com.anydownlod.core.extract.patreon

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.InfoMedia
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Upstream `PatreonIE`: one public post from the JSON:API. */
class PatreonIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Patreon"

    override suspend fun extract(url: String): InfoDict {
        val postId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(API_BASE + "posts/$postId?" + QUERY) as? JsonObject
            ?: throw ExtractionError.Malformed("The Patreon post response was empty.")
        val attributes = response.obj("data")?.obj("attributes")
            ?: throw ExtractionError.Malformed("The Patreon post had no attributes.")
        if ((attributes["current_user_can_view"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw ExtractionError.LoginRequired("This Patreon post is for patrons only.")
        }

        var uploader: String? = null
        var channel: String? = null
        var channelId: String? = null
        val media = mutableListOf<InfoMedia>()

        for (element in response.array("included").orEmpty()) {
            val include = element as? JsonObject ?: continue
            val includeAttributes = include.obj("attributes")
            when (include.str("type")) {
                "media" -> {
                    val downloadUrl = includeAttributes?.str("download_url") ?: continue
                    val sizeBytes = includeAttributes?.number("size_bytes") ?: continue
                    val ext = ExtractorUtils.mimetype2ext(includeAttributes.str("mimetype")) ?: "mp4"
                    media += InfoMedia(
                        mediaId = include.str("id") ?: downloadUrl,
                        title = includeAttributes.str("file_name"),
                        formats = listOf(
                            MediaFormat(
                                formatId = ext,
                                url = downloadUrl,
                                ext = ext,
                                filesize = sizeBytes.toLong(),
                                httpHeaders = REFERER_HEADERS,
                            ),
                        ),
                    )
                }

                "user" -> uploader = includeAttributes?.str("full_name")
                "campaign" -> {
                    channel = includeAttributes?.str("title")
                    channelId = include.str("id")
                }
            }
        }

        attributes.obj("post_file")?.let { postFile ->
            val fileUrl = postFile.str("url") ?: return@let
            val name = postFile.str("name")
            if (name == "video" || fileUrl.substringBefore('?').endsWith(".m3u8")) {
                media += InfoMedia(
                    mediaId = postId,
                    formats = listOf(
                        MediaFormat(
                            formatId = "hls",
                            url = fileUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                            httpHeaders = REFERER_HEADERS,
                        ),
                    ),
                )
            } else {
                val ext = ExtractorUtils.determineExt(fileUrl).takeIf { it != "unknown_video" }
                if (ext != null) {
                    media += InfoMedia(
                        mediaId = postId,
                        formats = listOf(
                            MediaFormat(formatId = ext, url = fileUrl, ext = ext, httpHeaders = REFERER_HEADERS),
                        ),
                    )
                }
            }
        }

        val image = attributes.obj("image")
        val thumbnail = image?.str("large_url") ?: image?.str("url")
        val base = InfoDict(
            id = postId,
            title = attributes.str("title"),
            description = attributes.str("content")?.let(::stripTags),
            uploadDate = attributes.str("published_at")?.let(ExtractorUtils::unifiedStrdate),
            uploader = uploader,
            channel = channel,
            channelId = channelId,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            webpageUrl = url,
            extractor = "patreon",
            extractorKey = IE_KEY,
        )

        if (media.isEmpty()) {
            val embedUrl = attributes.obj("embed")?.str("url")
                ?: throw ExtractionError.NoFormats("No supported media found in this Patreon post.")
            return base.copy(redirectUrl = vimeoPlayerUrl(embedUrl) ?: embedUrl)
        }
        return if (media.size == 1) {
            base.copy(formats = media[0].formats, title = base.title ?: media[0].title)
        } else {
            base.copy(media = media)
        }
    }

    companion object {
        const val IE_KEY: String = "Patreon"

        private const val API_BASE = "https://www.patreon.com/api/"

        /** Upstream `PatreonIE._VALID_URL`. */
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?patreon\\.com/" +
                "(?:creation\\?hid=|(?:[^/?#]+/)?posts/(?:[\\w-]+-)?)(?<id>\\d+)",
        )

        /** Upstream post query fields and includes, percent-encoded brackets. */
        private const val QUERY =
            "json-api-version=1.0&json-api-use-default-includes=false" +
                "&fields%5Bmedia%5D=download_url,mimetype,size_bytes,file_name" +
                "&fields%5Bpost%5D=comment_count,content,content_teaser_text,cleaned_teaser_text,embed,image," +
                "like_count,post_file,published_at,title,current_user_can_view" +
                "&fields%5Buser%5D=full_name,url" +
                "&fields%5Bcampaign%5D=url,name,patron_count" +
                "&include=audio,user,user_defined_tags,campaign,attachments_media"
    }
}

private val REFERER_HEADERS = mapOf("referer" to "https://www.patreon.com/")

/** Upstream `//vimeo.com/<id>[/<hash>]` to a player URL. */
private fun vimeoPlayerUrl(embedUrl: String): String? {
    val match = Regex("//vimeo\\.com/(\\d+)(?:/([\\da-f]+))?").find(embedUrl) ?: return null
    val id = match.groupValues[1]
    val hash = match.groupValues[2]
    return "https://player.vimeo.com/video/$id" + if (hash.isNotEmpty()) "?h=$hash" else ""
}

private fun stripTags(value: String): String {
    val withoutTags = Regex("<[^>]*>").replace(value, " ")
    return (ExtractorUtils.unescapeHtml(withoutTags) ?: withoutTags)
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
