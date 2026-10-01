/*
 * MSN extractor — AnyDownload
 *
 * Kotlin translation of `msn.py` from `yt_dlp/extractor/msn.py` at upstream
 * tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `msn.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `assets.msn.com/content/view/v2/Detail` JSON for the video,
 * webcontent, and article page types — the externalVideoFiles rows (one HLS
 * row, one MPD row, or direct rows with format/size/height/width), the
 * closedCaptions ttml tracks, the common metadata, and the transparent
 * dispatches as child entries. Limitations: the third-party/webcontent
 * dispatches become one child entry at the source URL (the port's
 * transparent dispatch), so the page metadata is not merged; `timestamp`,
 * `release_timestamp`, and `modified_timestamp` fold into `uploadDate` (the
 * latter two are dropped); `uploader_id`, `display_id`, and `tags` are not
 * modeled and are dropped; m3u8/mpd subtitles are not parsed (one row per
 * manifest). No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.msn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `MSNIE`: one msn.com video, webcontent, or article page. */
class MSNIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val locale = match.groups["locale"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val pageId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val json = http.downloadJson("https://assets.msn.com/content/view/v2/Detail/$locale/$pageId")
            as? JsonObject ?: throw ExtractionError.Malformed("The MSN API was not an object.")

        val title = json.str("title")
        val description = json.str("abstract") ?: cleanHtml(json.str("body"))
        val uploadDate = json.str("createdDateTime")?.let(ExtractorUtils::unifiedStrdate)
        val thumbnail = json.obj("thumbnail")?.obj("image")?.str("url")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val duration = json.obj("videoMetadata")?.number("playTime")
        val uploader = json.obj("provider")?.str("name")
        val sourceUrl = json.str("sourceHref")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        return when (val pageType = json.str("type")) {
            "video" -> {
                if (json.obj("thirdPartyVideoPlayer")?.boolean("enabled") == true && sourceUrl != null) {
                    return childEntry(pageId, url, title, description, uploadDate, thumbnail, uploader, sourceUrl)
                }
                val formats = mutableListOf<MediaFormat>()
                val videoMetadata = json.obj("videoMetadata")
                for (element in videoMetadata?.array("externalVideoFiles").orEmpty()) {
                    val file = element as? JsonObject ?: continue
                    val fileUrl = file.str("url")
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: continue
                    when (ExtractorUtils.determineExt(fileUrl)) {
                        "m3u8" -> formats += MediaFormat(
                            formatId = "hls",
                            url = fileUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        )
                        "mpd" -> formats += MediaFormat(
                            formatId = "dash",
                            url = fileUrl,
                            ext = "mp4",
                            protocol = "mpd",
                        )
                        else -> formats += MediaFormat(
                            url = fileUrl,
                            formatId = file.str("format"),
                            filesize = file.number("fileSize")?.toLong(),
                            height = file.number("height")?.toLong(),
                            width = file.number("width")?.toLong(),
                        )
                    }
                }
                val subtitles = mutableListOf<SubtitleTrack>()
                for (element in videoMetadata?.array("closedCaptions").orEmpty()) {
                    val caption = element as? JsonObject ?: continue
                    val captionUrl = caption.str("href")
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: continue
                    subtitles += SubtitleTrack(
                        language = caption.str("locale") ?: "en-us",
                        formats = listOf(SubtitleFormat(ext = "ttml", url = captionUrl)),
                    )
                }
                InfoDict(
                    id = pageId,
                    title = title,
                    description = description,
                    uploadDate = uploadDate,
                    duration = duration,
                    thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
                    uploader = uploader,
                    formats = formats,
                    subtitles = subtitles,
                    webpageUrl = url,
                    extractor = "msn",
                    extractorKey = ieKey,
                )
            }
            "webcontent" -> {
                if (sourceUrl == null) {
                    throw ExtractionError.Unavailable("Could not find source URL.")
                }
                childEntry(pageId, url, title, description, uploadDate, thumbnail, uploader, sourceUrl)
            }
            "article" -> {
                val entries = mutableListOf<InfoEntry>()
                for (element in json.array("socialEmbeds").orEmpty()) {
                    val postUrl = (element as? JsonObject)?.str("postUrl")
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: continue
                    entries += InfoEntry(url = postUrl)
                }
                InfoDict(
                    id = pageId,
                    title = title,
                    description = description,
                    uploadDate = uploadDate,
                    thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
                    uploader = uploader,
                    entries = entries,
                    webpageUrl = url,
                    extractor = "msn",
                    extractorKey = ieKey,
                )
            }
            else -> throw ExtractionError.Unavailable("Unsupported page type: $pageType")
        }
    }

    /** Upstream `url_result(source_url)`: the port expands one child job. */
    private fun childEntry(
        pageId: String,
        pageUrl: String,
        title: String?,
        description: String?,
        uploadDate: String?,
        thumbnail: String?,
        uploader: String?,
        sourceUrl: String,
    ): InfoDict = InfoDict(
        id = pageId,
        title = title,
        description = description,
        uploadDate = uploadDate,
        thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
        uploader = uploader,
        entries = listOf(InfoEntry(url = sourceUrl)),
        webpageUrl = pageUrl,
        extractor = "msn",
        extractorKey = ieKey,
    )

    companion object {
        const val IE_KEY: String = "MSN"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|preview)\\.)?msn\\.com/(?<locale>[a-z]{2}-[a-z]{2})/" +
                "(?:[^/?#]+/)+(?<displayid>[^/?#]+)/[a-z]{2}-(?<id>[\\da-zA-Z]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
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

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
