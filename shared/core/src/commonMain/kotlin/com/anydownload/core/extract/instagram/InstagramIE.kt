/*
 * Instagram extractors — AnyDownload
 *
 * Kotlin translation of the logged-out post subset of `InstagramIE` and the
 * `InstagramIOSIE` redirect from `yt_dlp/extractor/instagram.py` at upstream
 * tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `instagram.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `/p/`, `/tv/`, `/reel/`, and `/reels/` post URLs (optionally behind
 * one profile segment). The page's `data-sjs` RelayPrefetchedStreamCache data
 * is searched for `xig_polaris_media.if_not_gated_logged_out` (or the older
 * `xdt_api__v1__media__shortcode__web_info.items`) and maps video versions,
 * the DASH manifest, image candidates, caption, user, date, and view count.
 * Carousels become [InfoDict.media] items. A page without that data falls
 * back to the `og:video` meta pair; if neither is present the post fails typed
 * as a sign-in wall. The API `media/<id>/info/` path, comments, stories,
 * users, tags, and the logged-in product path are not translated. A login
 * wall or DRM wall is Partial. No cookie or signed media URL is stored or
 * committed; fixture hosts are `*.example`.
 */
package com.anydownload.core.extract.instagram

import com.anydownload.core.download.Mpd
import com.anydownload.core.download.MpdResult
import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Upstream `InstagramIE`: one public post from the page's embedded data. */
class InstagramIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Instagram"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val shortcode = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        productInfo(webpage)?.let { product ->
            val id = product.str("pk") ?: product.number("pk")?.toLong()?.toString() ?: shortcode
            val carousel = product.array("carousel_media")?.filterIsInstance<JsonObject>().orEmpty()
            if (carousel.isNotEmpty()) {
                return carouselInfo(product, carousel, id, shortcode, url)
            }
            return productInfoDict(product, id, shortcode, url)
        }

        val openGraphVideo = ExtractorUtils.htmlSearchMeta(
            webpage,
            "og:video",
            "og:video:secure_url",
            "og:video:url",
            "twitter:player:stream",
        )
        if (openGraphVideo != null) {
            val thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image", "twitter:image")
            return InfoDict(
                id = shortcode,
                title = ExtractorUtils.htmlSearchMeta(webpage, "og:title", "twitter:title"),
                description = ExtractorUtils.htmlSearchMeta(webpage, "og:description", "twitter:description"),
                formats = listOf(
                    MediaFormat(
                        formatId = "og",
                        url = openGraphVideo,
                        ext = "mp4",
                        protocol = "https",
                        httpHeaders = REFERER_HEADERS,
                    ),
                ),
                thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
                webpageUrl = url,
                extractor = "instagram",
                extractorKey = IE_KEY,
            )
        }

        throw ExtractionError.LoginRequired(
            "Instagram did not expose this post; the logged-out data was missing and a sign-in may be required.",
        )
    }

    /** One post with a video, mapped from the logged-out product dict. */
    private fun productInfoDict(product: JsonObject, id: String, shortcode: String, url: String): InfoDict {
        val formats = videoVersions(product)
        product.str("video_dash_manifest")?.let { manifest ->
            if ("<MPD" in manifest) {
                when (val result = Mpd.parse(url, manifest)) {
                    is MpdResult.Formats -> formats += result.formats.map { it.copy(httpHeaders = REFERER_HEADERS) }
                    is MpdResult.Failed -> Unit
                }
            }
        }
        val user = product.obj("user")
        val channel = user?.str("username")
        val title = product.str("title") ?: channel?.let { "Video by $it" }
        return InfoDict(
            id = pkToId(id) ?: shortcode,
            title = title,
            description = captionText(product),
            formats = formats,
            thumbnails = imageCandidates(product),
            duration = product.number("video_duration"),
            uploader = user?.str("full_name"),
            channel = channel,
            channelId = user?.str("pk") ?: user?.number("pk")?.toLong()?.toString(),
            uploadDate = product.number("taken_at")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = product.number("view_count")?.toLong(),
            webpageUrl = url,
            extractor = "instagram",
            extractorKey = IE_KEY,
        )
    }

    /** One carousel post; each child media is a bounded [InfoMedia] item. */
    private fun carouselInfo(
        product: JsonObject,
        carousel: List<JsonObject>,
        id: String,
        shortcode: String,
        url: String,
    ): InfoDict {
        val user = product.obj("user")
        val channel = user?.str("username")
        val media = carousel.mapIndexed { index, node ->
            val mediaId = node.str("pk") ?: node.number("pk")?.toLong()?.toString()
            InfoMedia(
                mediaId = mediaId?.let { pkToId(it) } ?: "$shortcode-$index",
                duration = node.number("video_duration"),
                thumbnails = imageCandidates(node),
                formats = videoVersions(node),
            )
        }
        return InfoDict(
            id = pkToId(id) ?: shortcode,
            title = channel?.let { "Post by $it" },
            description = captionText(product),
            media = media,
            uploader = user?.str("full_name"),
            channel = channel,
            channelId = user?.str("pk") ?: user?.number("pk")?.toLong()?.toString(),
            uploadDate = product.number("taken_at")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = product.number("view_count")?.toLong(),
            webpageUrl = url,
            extractor = "instagram",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_product_media` video versions plus codec fields. */
    private fun videoVersions(product: JsonObject): MutableList<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in product.array("video_versions").orEmpty()) {
            val version = element as? JsonObject ?: continue
            val videoUrl = version.str("url") ?: continue
            formats += MediaFormat(
                formatId = version.str("id") ?: version.number("type")?.toLong()?.toString(),
                url = videoUrl,
                ext = "mp4",
                width = version.number("width")?.toLong(),
                height = version.number("height")?.toLong(),
                vcodec = product.str("video_codec"),
                acodec = if ((product["has_audio"] as? JsonPrimitive)?.booleanOrNull == false) {
                    MediaFormat.CODEC_NONE
                } else {
                    null
                },
                httpHeaders = REFERER_HEADERS,
            )
        }
        return formats
    }

    private fun imageCandidates(product: JsonObject): List<Thumbnail> =
        product.obj("image_versions2")?.array("candidates").orEmpty()
            .filterIsInstance<JsonObject>()
            .mapNotNull { candidate ->
                val thumbnailUrl = candidate.str("url") ?: return@mapNotNull null
                Thumbnail(
                    url = thumbnailUrl,
                    width = candidate.number("width")?.toLong(),
                    height = candidate.number("height")?.toLong(),
                )
            }
            .reversed()

    private fun captionText(product: JsonObject): String? = when (val caption = product["caption"]) {
        is JsonObject -> caption.str("text")
        is JsonPrimitive -> caption.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        else -> null
    }

    companion object {
        const val IE_KEY: String = "Instagram"

        /** Upstream `_VALID_URL` for the post forms. */
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?instagram\\.com(?:/(?!share/)[^/?#]+)?/" +
                "(?:p|tv|reels?(?!/audio/))/(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `InstagramIOSIE`: the app scheme's media pk. */
class InstagramIOSIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Instagram iOS"

    override suspend fun extract(url: String): InfoDict {
        val mediaId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val shortcode = pkToId(mediaId) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = shortcode,
            webpageUrl = url,
            extractor = "instagram",
            extractorKey = IE_KEY,
            redirectUrl = "https://instagram.com/p/$shortcode",
        )
    }

    companion object {
        const val IE_KEY: String = "InstagramIOS"

        val VALID_URL: Regex = Regex("instagram://media\\?id=(?<id>[\\d_]+)")
    }
}

private val REFERER_HEADERS = mapOf("referer" to "https://www.instagram.com/")

private val SJS = Regex(
    "<script[^>]*\\bdata-sjs[^>]*>\\s*(\\{.*\\})\\s*</script>",
    RegexOption.DOT_MATCHES_ALL,
)

private const val ENCODING_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Upstream `_pk_to_id`: the numeric media pk as a shortcode. */
private fun pkToId(mediaId: String): String? {
    var value = mediaId.substringBefore('_').toLongOrNull() ?: return null
    if (value == 0L) return ENCODING_CHARS[0].toString()
    val result = StringBuilder()
    while (value > 0) {
        result.append(ENCODING_CHARS[(value % 64).toInt()])
        value /= 64
    }
    return result.reverse().toString()
}

/**
 * Upstream `RelayPrefetchedStreamCache` walk, generalized: the first nested
 * object carrying [xig_polaris_media] wins, then the older shortcode web info.
 */
private fun productInfo(webpage: String): JsonObject? {
    for (match in SJS.findAll(webpage)) {
        val root = ExtractorUtils.parseJson(match.groupValues[1]) ?: continue
        findKey(root, "xig_polaris_media")?.obj("if_not_gated_logged_out")?.let { return it }
        findKey(root, "xdt_api__v1__media__shortcode__web_info")
            ?.array("items")
            ?.firstOrNull()
            ?.let { if (it is JsonObject) return it }
    }
    return null
}

private fun findKey(element: JsonElement?, key: String): JsonObject? {
    when (element) {
        is JsonObject -> {
            (element[key] as? JsonObject)?.let { return it }
            for ((_, value) in element) findKey(value, key)?.let { return it }
        }

        is JsonArray -> for (value in element) findKey(value, key)?.let { return it }
        else -> Unit
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
