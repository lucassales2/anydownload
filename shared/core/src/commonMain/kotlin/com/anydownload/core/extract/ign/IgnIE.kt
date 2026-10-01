/*
 * IGN extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `ign.py` from
 * `yt_dlp/extractor/ign.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ign.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `apis.ign.com` slug API, the video formats (m3u8,
 * assets, mezzanine), the content-feed-grid playlist scan, the embed
 * redirect, and the article media/videoplayer/nextjs entries. The f4m
 * (HDS) source is skipped and the `tags` field the port does not carry is
 * dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.ign

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

/** Shared upstream `IGNBaseIE` behaviour. */
abstract class IgnBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    private val pageType: String,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_call_api`. */
    protected suspend fun callApi(slug: String): JsonObject = http.downloadJson(
        "https://apis.ign.com/$pageType/v3/${pageType}s/slug/$slug",
    ) as? JsonObject ?: throw ExtractionError.Malformed("The IGN API returned no object.")

    /** Upstream `_checked_call_api`: a 404 becomes an expected failure. */
    protected suspend fun checkedCallApi(slug: String, allow503: Boolean = false): JsonObject? = try {
        callApi(slug)
    } catch (error: ExtractionError) {
        if (error is ExtractionError.Unavailable && allow503) {
            null
        } else {
            throw ExtractionError.Unavailable("Content not found: expired?")
        }
    }

    /** Upstream `_extract_video_info`. */
    protected fun videoInfo(video: JsonObject): InfoDict {
        val videoId = video.str("videoId")
            ?: throw ExtractionError.Malformed("The IGN video had no id.")
        val formats = mutableListOf<MediaFormat>()
        val refs = video.obj("refs")
        refs?.str("m3u8Url")?.let {
            formats += MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native")
        }
        for (element in video.array("assets").orEmpty()) {
            val asset = element as? JsonObject ?: continue
            val assetUrl = asset.str("url") ?: continue
            formats += MediaFormat(
                url = assetUrl,
                tbr = asset.number("bitrate")?.let { it / 1000.0 },
                fps = asset.number("frame_rate"),
                width = asset.number("width")?.toLong(),
                height = asset.number("height")?.toLong(),
            )
        }
        video.obj("system")?.str("mezzanineUrl")?.let {
            formats += MediaFormat(
                formatId = "mezzanine",
                url = it,
                ext = ExtractorUtils.determineExt(it, "mp4"),
                preference = 1,
            )
        }
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in video.array("thumbnails").orEmpty()) {
            val thumb = element as? JsonObject ?: continue
            thumb.str("url")?.let { thumbnails += Thumbnail(url = it) }
        }
        val metadata = video.obj("metadata") ?: JsonObject(emptyMap())
        return InfoDict(
            id = videoId,
            title = metadata.str("longTitle") ?: metadata.str("title") ?: metadata.str("name"),
            description = metadata.str("description"),
            uploadDate = ExtractorUtils.unifiedStrdate(metadata.str("publishDate")),
            duration = metadata.number("duration"),
            thumbnails = thumbnails,
            formats = formats,
            extractor = "ign",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `IGNIE`: an ign.com/pcmag.com video page or playlist. */
class IGNIE(
    http: ExtractorHttp,
) : IgnBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL, pageType = "video") {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value
        if (displayId != null) {
            return videoInfo(checkedCallApi(displayId)!!).copy(
                webpageUrl = url,
                extractorKey = IE_KEY,
            )
        }
        val webpage = http.downloadWebpage(url)
        val grid = Regex(
            "(?s)<section\\b[^>]+\\bclass\\s*=\\s*['\"](?:[\\w-]+\\s+)*?content-feed-grid(?!\\B|-)[^>]+>(.+?)</section[^>]*>",
        ).find(webpage)?.groupValues?.get(1).orEmpty()
        val entries = mutableListOf<InfoEntry>()
        for (link in Regex(
            "<a\\b[^>]+\\bhref\\s*=\\s*('|\")(/videos/(?:\\d{4}/\\d{2}/\\d{2}/)?[^'\"]+?)\\1",
        ).findAll(grid)) {
            entries += InfoEntry(url = urlJoin("https://www.ign.com", link.groupValues[2]))
        }
        return InfoDict(
            id = match.groups["filt"]?.value ?: "all",
            entries = entries,
            webpageUrl = url,
            extractor = "ign.com",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "IGN"

        val VALID_URL: Regex = Regex(
            "https?://(?:.+?\\.ign|www\\.pcmag)\\.com/videos" +
                "(?:/(?:\\d{4}/\\d{2}/\\d{2}/)?(?<id>.+?)(?:[/?\u0026#]|$)|(?:/?\\?(?<filt>[^\u0026#]+))?)",
        )
    }
}

/** Upstream `IGNVideoIE`: a numbered video page. */
class IGNVideoIE(
    http: ExtractorHttp,
) : IgnBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL, pageType = "video") {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val parsed = url.substringBefore('?').substringBefore('#')
        val embedUrl = parsed.substringBeforeLast('/') + "/embed"
        val newUrl = try {
            http.followRedirects(embedUrl)
        } catch (error: ExtractionError) {
            embedUrl
        }
        val webpage = http.downloadWebpage(newUrl)
        queryParam(newUrl, "url")?.let {
            return InfoDict(
                id = videoId,
                redirectUrl = it,
                webpageUrl = url,
                extractorKey = IE_KEY,
            )
        }
        val videoElement = Regex("(<div\\b[^>]+\\bdata-video-id\\s*=\\s*[^>]+>)").find(webpage)?.value
        if (videoElement == null) {
            if (newUrl == embedUrl) {
                throw ExtractionError.Malformed("Redirect loop: $url")
            }
            return InfoDict(
                id = videoId,
                redirectUrl = newUrl,
                webpageUrl = url,
                extractorKey = IE_KEY,
            )
        }
        val settings = tagAttributes(videoElement)["data-settings"]
            ?.let { ExtractorUtils.unescapeHtml(it) ?: it } ?: "{}"
        val video = (ExtractorUtils.parseJson(settings) as? JsonObject)?.obj("video")
            ?: throw ExtractionError.Malformed("The IGN embed had no video data.")
        return videoInfo(video).copy(webpageUrl = url, extractorKey = IE_KEY)
    }

    companion object {
        const val IE_KEY: String = "IGNVideo"

        val VALID_URL: Regex = Regex(
            "https?://.+?\\.ign\\.com/(?:[a-z]{2}/)?[^/]+/(?<id>\\d+)/(?:video|trailer)/",
        )
    }
}

/** Upstream `IGNArticleIE`: an article. */
class IGNArticleIE(
    http: ExtractorHttp,
) : IgnBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL, pageType = "article") {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val article = checkedCallApi(displayId, allow503 = true)
        if (article != null) {
            val entries = mutableListOf<InfoEntry>()
            val mediaUrl = article.array("mediaRelations")?.firstOrNull()
                ?.let { it as? JsonObject }?.obj("media")?.obj("metadata")?.str("url")
            if (mediaUrl != null) entries += InfoEntry(url = mediaUrl)
            for (element in article.array("content").orEmpty()) {
                val content = (element as? JsonPrimitive)?.content ?: continue
                for (match in Regex(
                    "(?:\\[(?:ignvideo\\s+url|youtube\\s+clip_id)|<iframe[^>]+src)=\"([^\"]+)\"",
                ).findAll(content)) {
                    entries += InfoEntry(url = match.groupValues[1])
                }
            }
            return InfoDict(
                id = article.str("articleId"),
                title = article.obj("metadata")?.str("headline"),
                entries = entries,
                webpageUrl = url,
                extractorKey = IE_KEY,
            )
        }

        val webpage = http.downloadWebpage(url)
        val dableId = Regex("<meta[^>]+name\\s*=\\s*['\"]dable:item_id['\"][^>]+content\\s*=\\s*['\"]([^'\"]+)['\"]")
            .find(webpage)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content\\s*=\\s*['\"]([^'\"]+)['\"][^>]+name\\s*=\\s*['\"]dable:item_id['\"]")
                .find(webpage)?.groupValues?.get(1)
        if (dableId != null) {
            val entries = mutableListOf<InfoEntry>()
            for (objectMatch in Regex(
                "(?s)<object\\b[^>]+\\bclass\\s*=\\s*(\"|')ign-videoplayer\\1[^>]*>(?<params>.+?)</object",
            ).findAll(webpage)) {
                val flashvarsTag = Regex("(<param\\b[^>]+\\bname\\s*=\\s*(\"|')flashvars\\2[^>]*>)")
                    .find(objectMatch.groups["params"]?.value.orEmpty())?.value
                val value = flashvarsTag?.let { tagAttributes(it)["value"] }
                val flashUrl = value?.let { queryParam(it, "url") }
                if (flashUrl != null) entries += InfoEntry(url = flashUrl)
            }
            return InfoDict(
                id = dableId,
                entries = entries,
                webpageUrl = url,
                extractorKey = IE_KEY,
            )
        }

        val postId = Regex("\\bdata-post-id\\s*=\\s*(\"|')(?<id>[\\da-f]+)\\1")
            .find(webpage)?.groups?.get("id")?.value
        val nextData = Regex("(?s)<script[^>]+id\\s*=\\s*['\"]__NEXT_DATA__['\"][^>]*>(.*?)</script>")
            .find(webpage)?.groupValues?.get(1)
            ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
        val entries = mutableListOf<InfoEntry>()
        val apolloState = nextData?.obj("props")?.obj("apolloState")
        if (apolloState != null) {
            for ((key, value) in apolloState) {
                if (!key.startsWith("videoPlayerProps(")) continue
                val ref = (value as? JsonObject)?.str("__ref") ?: continue
                val modernKey = ref.replace("PlayerProps", "ModernContent")
                if (apolloState[modernKey] is JsonObject) continue
                val video = apolloState[ref] as? JsonObject ?: continue
                val info = runCatching { videoInfo(video) }.getOrNull() ?: continue
                entries += InfoEntry(id = info.id, title = info.title, url = url)
            }
        }
        val title = Regex("<meta[^>]+property\\s*=\\s*['\"]og:title['\"][^>]+content\\s*=\\s*['\"]([^'\"]*)['\"]")
            .find(webpage)?.groupValues?.get(1)
            ?.replace(Regex("\\s+-\\s+IGN\\s*$"), "")
        return InfoDict(
            id = postId ?: displayId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "IGNArticle"

        val VALID_URL: Regex = Regex(
            "https?://.+?\\.ign\\.com/(?:articles(?:/\\d{4}/\\d{2}/\\d{2})?|" +
                "(?:[a-z]{2}/)?(?:[\\w-]+/)*?feature/\\d+)/(?<id>[^/?\u0026#]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "")
    for (part in query.split('&')) {
        val key = part.substringBefore('=', "")
        if (key == name) {
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

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonElement.obj(name: String): JsonObject? = (this as? JsonObject)?.get(name) as? JsonObject

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
