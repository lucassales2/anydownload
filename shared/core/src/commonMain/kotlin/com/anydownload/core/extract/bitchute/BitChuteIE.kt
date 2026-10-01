/*
 * BitChute extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `bitchute.py` from
 * `yt_dlp/extractor/bitchute.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `bitchute.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `api.bitchute.com/api/beta` video/media/channel APIs
 * and the HLS or direct media row. The seed-host HEAD probe is skipped, the
 * channel/playlist listing matches and fails typed because it needs the
 * old-site CSRF cookie the port refuses, and the port does not carry
 * channel/uploader URLs or tags, so they are dropped. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.bitchute

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

/** Upstream `BitChuteIE`: one video. */
class BitChuteIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val mediaUrl = callApi("video/media", """{"video_id":"$videoId"}""", videoId)
            ?.str("media_url")
            ?: throw ExtractionError.NoFormats(
                "Video is unavailable. Please make sure this video is playable in the browser.",
            )
        val formats = if (ExtractorUtils.determineExt(mediaUrl) == "m3u8") {
            listOf(
                MediaFormat(formatId = "hls", url = mediaUrl, ext = "mp4", protocol = "m3u8_native"),
            )
        } else {
            listOf(MediaFormat(url = mediaUrl, ext = ExtractorUtils.determineExt(mediaUrl)))
        }
        val video = callApi("video", """{"video_id":"$videoId"}""", videoId)
        val channelId = video?.obj("channel")?.str("channel_id")
        val channel = if (channelId != null) {
            callApi("channel", """{"channel_id":"$channelId"}""", videoId)
        } else {
            null
        }
        val channelInfo = video?.obj("channel")
        return InfoDict(
            id = videoId,
            title = video?.str("video_name"),
            description = video?.str("description"),
            duration = video?.str("duration")?.let { parseDuration(it) },
            uploadDate = ExtractorUtils.unifiedStrdate(video?.str("date_published")),
            viewCount = video?.number("view_count")?.toLong(),
            isLive = video?.str("state_id") == "live",
            channel = channel?.str("channel_name") ?: channelInfo?.str("channel_name"),
            channelId = channel?.str("channel_id") ?: channelId,
            uploader = channel?.str("profile_name"),
            thumbnails = listOfNotNull(video?.str("thumbnail_url")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "bitchute",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun callApi(endpoint: String, body: String, displayId: String): JsonObject? = try {
        http.downloadJson(
            "https://api.bitchute.com/api/beta/$endpoint",
            method = "POST",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
            ),
            body = body.encodeToByteArray(),
        ) as? JsonObject
    } catch (error: ExtractionError) {
        null
    }

    companion object {
        const val IE_KEY: String = "BitChute"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|old)\\.)?bitchute\\.com/(?:video|embed|torrent/[^/?#]+)/(?<id>[^/?#\u0026]+)",
        )
    }
}

/** Upstream `BitChuteChannelIE`: a channel or playlist listing (typed wall). */
class BitChuteChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.Unavailable(
            "The BitChute channel/playlist listing needs the old-site CSRF cookie, which the port refuses.",
        )
    }

    companion object {
        const val IE_KEY: String = "BitChuteChannel"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|old)\\.)?bitchute\\.com/(?<type>channel|playlist)/(?<id>[^/?#\u0026]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `parse_duration` for the simple `HH:MM:SS`/`MM:SS` forms. */
private fun parseDuration(value: String): Double? {
    val parts = value.trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    var seconds = 0.0
    for (part in parts) {
        val number = part.toDoubleOrNull() ?: return null
        seconds = seconds * 60 + number
    }
    return seconds
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
