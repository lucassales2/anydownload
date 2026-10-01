/*
 * Google Drive extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `googledrive.py` from
 * `yt_dlp/extractor/googledrive.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `googledrive.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public workspace-video playback API (adaptive and progressive
 * transcodes), the media metadata, and the usercontent download URL. The
 * public browser API key upstream carries is stored (it is an app key, not
 * a user credential); the timed-text subtitle XML is not parsed and the
 * folder batch API is not translated, so the folder URL form fails typed.
 * No cookie, user token, or private URL is stored here.
 */
package com.anydownload.core.extract.googledrive

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The public browser app key upstream carries; not a user credential. */
private const val PLAYBACK_KEY = "AIzaSyDVQw45DwoYh632gvsP5vPDqEKvb-Ywnb8"

/** Upstream `GoogleDriveIE`: a shared file. */
class GoogleDriveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoInfo = http.downloadJson(
            "https://content-workspacevideo-pa.googleapis.com/v1/drive/media/$videoId/playback" +
                "?key=$PLAYBACK_KEY",
            headers = mapOf("Referer" to "https://drive.google.com/"),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Google Drive playback API returned no object.")
        val formats = mutableListOf<MediaFormat>()
        val streamingData = videoInfo.obj("mediaStreamingData")?.obj("formatStreamingData")
        for (key in listOf("adaptiveTranscodes", "progressiveTranscodes")) {
            for (element in streamingData?.array(key).orEmpty()) {
                val fmt = element as? JsonObject ?: continue
                val formatUrl = fmt.str("url") ?: continue
                val metadata = fmt.obj("transcodeMetadata") ?: JsonObject(emptyMap())
                formats += MediaFormat(
                    formatId = fmt.primitive("itag"),
                    url = formatUrl,
                    ext = ExtractorUtils.mimetype2ext(metadata.str("mimeType")),
                    width = metadata.number("width")?.toLong(),
                    height = metadata.number("height")?.toLong(),
                    fps = metadata.number("videoFps"),
                    filesize = metadata.number("contentLength")?.toLong(),
                    vcodec = metadata.str("videoCodecString") ?: MediaFormat.CODEC_NONE,
                    acodec = metadata.str("audioCodecString") ?: MediaFormat.CODEC_NONE,
                )
            }
        }
        val mediaMetadata = videoInfo.obj("mediaMetadata")
        val title = mediaMetadata?.str("title")
        val sourceUrl = "https://drive.usercontent.google.com/download?id=$videoId&export=download&confirm=t"
        formats += MediaFormat(
            formatId = "source",
            url = sourceUrl,
            ext = ExtractorUtils.determineExt(title, "mp4").lowercase(),
            preference = 1,
        )
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in videoInfo.array("thumbnails").orEmpty()) {
            val thumb = element as? JsonObject ?: continue
            val thumbUrl = thumb.str("url") ?: continue
            thumbnails += Thumbnail(
                url = thumbUrl,
                width = thumb.number("width")?.toLong(),
                height = thumb.number("height")?.toLong(),
            )
        }
        return InfoDict(
            id = videoId,
            title = title,
            duration = mediaMetadata?.str("duration")?.let { parseDuration(it) },
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "google-drive",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "GoogleDrive"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:docs|drive|drive\\.usercontent)\\.google\\.com/" +
                "(?:(?:uc|open|download)\\?.*?id=|file/d/)|" +
                "video\\.google\\.com/get_player\\?.*?docid=)" +
                "(?<id>[a-zA-Z0-9_-]{28,})",
        )
    }
}

/** Upstream `GoogleDriveFolderIE`: a folder listing (typed wall). */
class GoogleDriveFolderIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.Unavailable(
            "The Google Drive folder batch API is not translated; use the file links directly.",
        )
    }

    companion object {
        const val IE_KEY: String = "GoogleDriveFolder"

        val VALID_URL: Regex = Regex(
            "https?://(?:docs|drive)\\.google\\.com/drive/folders/(?<id>[\\w-]{28,})",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun parseDuration(value: String): Double? {
    val text = value.trim().removeSuffix("s")
    return text.toDoubleOrNull()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
