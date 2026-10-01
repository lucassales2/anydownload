/*
 * Life.ru extractors — AnyDownload
 *
 * Kotlin translation of `lifenews.py` from
 * `yt_dlp/extractor/lifenews.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `lifenews.py` is not vendored; see
 * shared/core/src/NOTICE.md and port/manifest.json.
 *
 * Scope: the Life.ru page's video/source and iframe link discovery (single
 * video, single iframe, or multi-item), the og title/description, the
 * hits-count and time metadata, and the embed page's `options.playlist`
 * (one HLS row, the original row, the old `"file"` fallback). Limitations:
 * a multi-item page with direct videos maps to selectable `media` items
 * (the Vidyard/TapTap model) while a page whose items are iframes maps to
 * child entries — a mixed page keeps only the iframe entries; `timestamp`
 * folds into `uploadDate`; m3u8 subtitles are not parsed (one row per
 * manifest). No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.lifenews

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `LifeNewsIE`: a life.ru article page. */
class LifeNewsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        val videoUrls = VIDEO_SOURCE.findAll(webpage).map { it.groupValues[1] }.toList()
        val iframeLinks = IFRAME.findAll(webpage).map { it.groupValues[1] }.toList()
        if (videoUrls.isEmpty() && iframeLinks.isEmpty()) {
            throw ExtractionError.Unavailable("No media links available for $videoId")
        }

        val title = removeEnd(ExtractorUtils.htmlSearchMeta(webpage, "og:title"), " - Life.ru")
        val description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
        val viewCount = ExtractorUtils.searchRegex(
            "<div[^>]+class=([\"']).*?\\bhits-count\\b.*?\\1[^>]*>\\s*(\\d+)\\s*</div>",
            webpage,
            group = 2,
        )?.toLongOrNull()
        val uploadDate = ExtractorUtils.searchRegex(
            "<time[^>]+datetime=([\"'])(.+?)\\1",
            webpage,
            group = 2,
        )?.let(ExtractorUtils::unifiedStrdate)

        if (videoUrls.size == 1 && iframeLinks.isEmpty()) {
            return InfoDict(
                id = videoId,
                title = title,
                description = description,
                viewCount = viewCount,
                uploadDate = uploadDate,
                formats = listOf(MediaFormat(url = urlJoin(url, videoUrls[0]))),
                webpageUrl = url,
                extractor = "life",
                extractorKey = ieKey,
            )
        }
        if (iframeLinks.size == 1 && videoUrls.isEmpty()) {
            val iframeUrl = protoRelative(iframeLinks[0], "http:")
            val info = LifeEmbedIE(http).extract(iframeUrl)
            return info.copy(
                title = title ?: info.title,
                description = description,
                viewCount = viewCount,
                uploadDate = uploadDate,
                webpageUrl = url,
            )
        }

        if (iframeLinks.isNotEmpty()) {
            // A mixed page keeps the dispatchable iframe entries; see the header note.
            return InfoDict(
                id = videoId,
                title = title,
                description = description,
                viewCount = viewCount,
                uploadDate = uploadDate,
                entries = iframeLinks.map { InfoEntry(url = protoRelative(it, "http:")) },
                webpageUrl = url,
                extractor = "life",
                extractorKey = ieKey,
            )
        }

        val media = videoUrls.mapIndexed { index, videoUrl ->
            InfoMedia(
                mediaId = "$videoId-video${index + 1}",
                title = if (title != null) "$title (Видео ${index + 1})" else null,
                formats = listOf(MediaFormat(url = urlJoin(url, videoUrl))),
            )
        }
        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            viewCount = viewCount,
            uploadDate = uploadDate,
            media = media,
            webpageUrl = url,
            extractor = "life",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "LifeNews"

        private val VIDEO_SOURCE = Regex("<video[^>]+><source[^>]+src=[\"'](.+?)[\"']")
        private val IFRAME = Regex(
            "<iframe[^>]+src=[\"']((?:https?:)?//embed\\.life\\.ru/(?:embed|video)/.+?)[\"']",
        )

        val VALID_URL: Regex = Regex("https?://life\\.ru/t/[^/]+/(?<id>\\d+)")
    }
}

/** Upstream `LifeEmbedIE`: an embed.life.ru player page. */
class LifeEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        val formats = mutableListOf<MediaFormat>()
        var thumbnail: String? = null

        fun extractM3u8(manifestUrl: String) {
            formats += MediaFormat(
                formatId = "m3u8",
                url = manifestUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }

        fun extractOriginal(originalUrl: String) {
            formats += MediaFormat(
                url = originalUrl,
                formatId = ExtractorUtils.determineExt(originalUrl),
                quality = "1",
            )
        }

        val playlist = (extractBalancedJson(webpage, Regex("options\\s*=")) as? JsonObject)
            ?.obj("playlist")
        if (playlist != null) {
            playlist.str("master")?.let { master ->
                if (ExtractorUtils.determineExt(master) == "m3u8") {
                    extractM3u8(urlJoin(url, master))
                }
            }
            playlist.str("original")?.let(::extractOriginal)
            thumbnail = playlist.str("image")
        }

        if (formats.isEmpty()) {
            for (match in FILE_URL.findAll(webpage)) {
                val fileUrl = urlJoin(url, match.groupValues[1])
                if (ExtractorUtils.determineExt(fileUrl) == "m3u8") {
                    extractM3u8(fileUrl)
                } else {
                    extractOriginal(fileUrl)
                }
            }
        }

        val resolvedThumbnail = thumbnail ?: ExtractorUtils.searchRegex(
            "\"image\"\\s*:\\s*\"([^\"]+)",
            webpage,
        )

        return InfoDict(
            id = videoId,
            title = videoId,
            thumbnails = resolvedThumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            webpageUrl = url,
            extractor = "life:embed",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "LifeEmbed"

        private val FILE_URL = Regex("\"file\"\\s*:\\s*\"([^\"]+)")

        val VALID_URL: Regex = Regex("https?://embed\\.life\\.ru/(?:embed|video)/(?<id>[\\da-f]{32})")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_search_json`: the balanced object after [marker]. */
private fun extractBalancedJson(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var index = start
    while (index < html.length) {
        val char = html[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return ExtractorUtils.parseJson(html.substring(start, index + 1))
                }
            }
        }
        index++
    }
    return null
}

/** Upstream `urljoin`: an absolute path replaces the base path. */
private fun urlJoin(base: String, path: String): String {
    if (path.startsWith("http")) return path
    val scheme = base.substringBefore("://")
    val host = base.substringAfter("://").substringBefore('/')
    return if (path.startsWith("/")) {
        "$scheme://$host$path"
    } else {
        base.substringBeforeLast('/', "") + "/" + path
    }
}

/** Upstream `_proto_relative_url(url, 'http:')`. */
private fun protoRelative(url: String, scheme: String): String =
    if (url.startsWith("//")) "$scheme$url" else url

/** Upstream `remove_end`. */
private fun removeEnd(value: String?, end: String): String? {
    val text = value ?: return null
    return if (text.endsWith(end)) text.dropLast(end.length).trim() else text
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}
