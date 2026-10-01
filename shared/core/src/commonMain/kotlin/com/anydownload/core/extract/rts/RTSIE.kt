/*
 * RTS extractor — AnyDownload
 *
 * Kotlin translation of `rts.py` from `yt_dlp/extractor/rts.py` at upstream
 * tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rts.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `rts.ch/a/{id}.html?f=json/article` JSON for the video and audio
 * forms — the streams rows (one HLS row with the SRG SSR akahd token, the
 * hds_sd/hls_sd skips, the direct bitrate rows), the `media` rows joined to
 * the rtsww download base, the SRG SSR block-reason check, and the
 * article/redirect dispatches as child entries. Limitations: f4m streams are
 * skipped (the port has no f4m helper); `_check_formats` HEAD probes are not
 * carried; m3u8 subtitles are not parsed; `display_id` is not modeled;
 * `timestamp` folds into `uploadDate`. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.rts

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.srgssr.SRGSSRIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `RTSIE`: an rts.ch page or `rts:` id. */
class RTSIE(
    http: ExtractorHttp,
) : SRGSSRIE(http, IE_KEY, VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var mediaId = match.groups["rtsid"]?.value ?: match.groups["id"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayid"]?.value ?: mediaId

        var allInfo = downloadJson(mediaId)
        if (allInfo.obj("video") == null && allInfo.obj("audio") == null) {
            val entries = mutableListOf<InfoEntry>()
            for (element in allInfo.array("items").orEmpty()) {
                val itemUrl = (element as? JsonObject)?.str("url") ?: continue
                entries += InfoEntry(url = itemUrl)
            }
            if (entries.isEmpty()) {
                val finalUrl = http.followRedirects(url)
                val page = http.downloadWebpage(finalUrl)
                val finalId = VALID_URL.find(finalUrl)?.groups?.get("id")?.value
                if (finalId != null && finalId != mediaId) {
                    return InfoDict(
                        id = finalId,
                        entries = listOf(InfoEntry(url = finalUrl)),
                        webpageUrl = url,
                        extractor = "rts",
                        extractorKey = ieKey,
                    )
                }
                var videos = ARTICLE_VIDEO.findAll(page).map { it.groupValues[1] }.toList()
                if (videos.isEmpty()) {
                    videos = IFRAME_VIDEO.findAll(page).map { it.groupValues[1] }.toList()
                }
                if (videos.isNotEmpty()) {
                    return InfoDict(
                        id = mediaId,
                        title = allInfo.str("title"),
                        entries = videos.map { InfoEntry(url = "srgssr:$it") },
                        webpageUrl = url,
                        extractor = "rts",
                        extractorKey = ieKey,
                    )
                }
                val internalId = ExtractorUtils.searchRegex(
                    "<(?:video|audio) data-id=\"([0-9]+)\"",
                    page,
                ) ?: throw ExtractionError.Malformed("The RTS page carried no internal video id.")
                mediaId = internalId
                allInfo = downloadJson(internalId)
            } else {
                return InfoDict(
                    id = mediaId,
                    title = allInfo.str("title"),
                    entries = entries,
                    webpageUrl = url,
                    extractor = "rts",
                    extractorKey = ieKey,
                )
            }
        }

        val mediaType = if (allInfo.obj("video") != null) "video" else "audio"
        // Upstream `_get_media_data('rts', ...)`: the SRG SSR block-reason check.
        getMediaData("rts", mediaType, mediaId)

        val info = (if (mediaType == "video") allInfo.obj("video")?.obj("JSONinfo") else allInfo.obj("audio"))
            ?: throw ExtractionError.Malformed("The RTS API returned no media info.")

        val formats = mutableListOf<MediaFormat>()
        val streams = info.obj("streams").orEmpty()
        for ((formatId, value) in streams) {
            val streamUrl = (value as? JsonPrimitive)?.content ?: continue
            if (formatId == "hds_sd" && "hds" in streams) continue
            if (formatId == "hls_sd" && "hls" in streams) continue
            val ext = ExtractorUtils.determineExt(streamUrl)
            when (ext) {
                "f4m" -> Unit // The port has no f4m helper.
                "m3u8" -> formats += MediaFormat(
                    formatId = formatId,
                    url = tokenize(streamUrl),
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                else -> formats += MediaFormat(
                    formatId = formatId,
                    url = streamUrl,
                    tbr = extractBitrate(streamUrl),
                )
            }
        }

        val downloadBase = if (mediaType == "audio") "http://rtsww-a-d.rts.ch/" else "http://rtsww-d.rts.ch/"
        for (element in info.array("media").orEmpty()) {
            val media = element as? JsonObject ?: continue
            val mediaUrl = media.str("url") ?: continue
            if (Regex("https?://").containsMatchIn(mediaUrl)) continue
            val rate = media.number("rate")
            val ext = media.str("ext") ?: ExtractorUtils.determineExt(mediaUrl, "mp4")
            val formatId = ext + (rate?.let { "-${it.toLong()}k" } ?: "")
            formats += MediaFormat(
                formatId = formatId,
                url = downloadBase.trimEnd('/') + "/" + mediaUrl.trimStart('/'),
                tbr = rate ?: extractBitrate(mediaUrl)?.toDouble(),
            )
        }

        var duration = info.number("duration") ?: info.number("cutout") ?: info.number("cutduration")
        if (duration == null) {
            duration = ExtractorUtils.parseDuration(info.str("duration") ?: info.str("cutout"))
        }

        return InfoDict(
            id = mediaId,
            title = info.str("title"),
            description = info.str("intro"),
            duration = duration,
            viewCount = info.number("plays")?.toLong(),
            uploader = info.str("programName"),
            uploadDate = info.str("broadcast_date")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = info.str("preview_image_url")
                ?.let { ExtractorUtils.unescapeHtml(it) }
                ?.let { listOf(Thumbnail(url = it)) }
                .orEmpty(),
            formats = formats,
            webpageUrl = url,
            extractor = "rts",
            extractorKey = ieKey,
        )
    }

    /** Upstream `download_json`: `rts.ch/a/{id}.html?f=json/article`. */
    private suspend fun downloadJson(internalId: String): JsonObject {
        val response = http.downloadJson("http://www.rts.ch/a/$internalId.html?f=json/article")
            as? JsonObject ?: throw ExtractionError.Malformed("The RTS article API was not an object.")
        return response
    }

    /** Upstream `extract_bitrate`: the `-{n}k.` pattern in the URL. */
    private fun extractBitrate(url: String): Double? =
        ExtractorUtils.searchRegex("-([0-9]+)k\\.", url)?.toDoubleOrNull()

    companion object {
        const val IE_KEY: String = "RTS"

        private val ARTICLE_VIDEO = Regex(
            "<article[^>]+class=\"content-item\"[^>]*>\\s*<a[^>]+data-video-urn=\"urn:([^\"]+)\"",
        )
        private val IFRAME_VIDEO = Regex(
            "(?s)<iframe[^>]+class=\"srg-player\"[^>]+src=\"[^\"]+urn:([^\"]+)\"",
        )

        val VALID_URL: Regex = Regex(
            "rts:(?<rtsid>\\d+)|https?://(?:.+?\\.)?rts\\.ch/(?:[^/]+/){2,}" +
                "(?<id>[0-9]+)-(?<displayid>.+?)\\.html",
        )
    }
}

// ------------------------------------------------------------------ helpers

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
