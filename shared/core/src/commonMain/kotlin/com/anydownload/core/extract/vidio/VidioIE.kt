/*
 * Vidio extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `vidio.py` from
 * `yt_dlp/extractor/vidio.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vidio.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public videos/clips API, the premier playlist API, and the
 * livestreaming detail API with its HLS sources. The anonymous API key is
 * fetched at runtime from the site (never stored), premium sources without
 * a stream fail typed LoginRequired, and DRM livestreams fail typed. The
 * port does not carry like/dislike/comment counters or display ids, so they
 * are dropped; DASH sources are skipped and an m3u8 URL becomes one HLS row
 * because manifest parsing is not translated.
 */
package com.anydownload.core.extract.vidio

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

/** Upstream `VidioIE`: a watch/embed video. */
class VidioIE(
    http: ExtractorHttp,
) : VidioBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi("https://api.vidio.com/videos/$videoId", videoId)
        val video = data.array("videos")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Vidio video API returned no video.")
        val title = video.str("title")?.trim()
        val isPremium = video.boolean("is_premium") == true
        val formats = mutableListOf<MediaFormat>()
        if (isPremium) {
            val sources = try {
                http.downloadJson(
                    "https://www.vidio.com/interactions_stream.json?video_id=$videoId&type=videos",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
            val source = sources?.str("source")
            if (source == null && sources?.str("source_dash") == null) {
                throw ExtractionError.LoginRequired(
                    "This video is only available for registered users with the appropriate subscription",
                )
            }
            if (source != null) {
                formats += MediaFormat(formatId = "hls", url = source, ext = "mp4", protocol = "m3u8_native")
            }
        } else {
            val hlsUrl = data.array("clips")?.firstOrNull()?.let { (it as? JsonObject)?.str("hls_url") }
                ?: throw ExtractionError.NoFormats("The Vidio video API returned no clip URL.")
            formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
        }
        val channel = data.array("channels")?.firstOrNull() as? JsonObject
        val user = data.array("users")?.firstOrNull() as? JsonObject
        return InfoDict(
            id = videoId,
            title = title,
            description = video.str("description")?.trim(),
            duration = video.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(video.str("created_at")),
            channel = channel?.str("name"),
            channelId = channel?.primitive("id"),
            uploader = user?.str("name"),
            viewCount = video.number("total_view_count")?.toLong(),
            thumbnails = listOfNotNull(video.str("image_url_medium")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "vidio",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Vidio"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?vidio\\.com/(watch|embed)/(?<id>\\d+)-(?<display>[^/?#\u0026]+)",
        )
    }
}

/** Upstream `VidioPremierIE`: a premier playlist. */
class VidioPremierIE(
    http: ExtractorHttp,
) : VidioBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi("https://api.vidio.com/content_profiles/$playlistId/playlists", playlistId)
        val entries = mutableListOf<InfoEntry>()
        for (element in data.array("data").orEmpty()) {
            val playlist = element as? JsonObject ?: continue
            val watchpage = playlist.obj("links")?.str("watchpage") ?: continue
            entries += InfoEntry(
                id = playlist.primitive("id"),
                title = playlist.obj("attributes")?.str("name"),
                url = watchpage,
            )
        }
        return InfoDict(
            id = playlistId,
            entries = entries,
            webpageUrl = url,
            extractor = "vidio:premier",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "VidioPremier"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?vidio\\.com/premier/(?<id>\\d+)/(?<display>[^/?#\u0026]+)",
        )
    }
}

/** Upstream `VidioLiveIE`: a livestream. */
class VidioLiveIE(
    http: ExtractorHttp,
) : VidioBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val streamData = callApi("https://www.vidio.com/api/livestreamings/$videoId/detail", videoId)
        val streamMeta = streamData.array("livestreamings")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Vidio livestreaming API returned no stream.")
        if (streamMeta.boolean("is_drm") == true) {
            throw ExtractionError.Unavailable("This livestream is DRM protected.")
        }
        val formats = mutableListOf<MediaFormat>()
        if (streamMeta.boolean("is_premium") == true) {
            val sources = try {
                http.downloadJson(
                    "https://www.vidio.com/interactions_stream.json?video_id=$videoId&type=livestreamings",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
            val source = sources?.str("source")
            if (source == null && sources?.str("source_dash") == null) {
                throw ExtractionError.LoginRequired(
                    "This video is only available for registered users with the appropriate subscription",
                )
            }
            if (source != null) {
                val token = liveToken(videoId)
                formats += MediaFormat(
                    formatId = "hls",
                    url = source + "?" + (token ?: ""),
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            }
        } else {
            streamMeta.str("stream_token_url")?.let { streamUrl ->
                val token = liveToken(videoId)
                formats += MediaFormat(
                    formatId = "hls",
                    url = streamUrl + "?" + (token ?: ""),
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            }
            streamMeta.str("stream_url")?.let {
                formats += MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native")
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Vidio livestreaming API returned no playable source.")
        }
        val user = streamData.array("users")?.firstOrNull() as? JsonObject
        return InfoDict(
            id = videoId,
            title = streamMeta.str("title"),
            description = streamMeta.str("description")?.trim(),
            isLive = true,
            uploader = user?.str("name"),
            thumbnails = listOfNotNull(streamMeta.str("image")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "vidio:live",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun liveToken(videoId: String): String? {
        val tokens = try {
            http.downloadJson("https://www.vidio.com/live/$videoId/tokens", method = "POST", body = ByteArray(0))
        } catch (error: ExtractionError) {
            null
        }
        return tokens?.let { (it as? JsonObject)?.str("token") }
    }

    companion object {
        const val IE_KEY: String = "VidioLive"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?vidio\\.com/live/(?<id>\\d+)-(?<display>[^/?#\u0026]+)",
        )
    }
}

/** Shared upstream `VidioBaseIE` behaviour. */
abstract class VidioBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    private var apiKey: String? = null

    /** Upstream `_initialize_pre_login`: the anonymous API key is fetched, never stored. */
    protected suspend fun fetchApiKey(): String {
        apiKey?.let { return it }
        val response = http.downloadJson("https://www.vidio.com/auth", method = "POST", body = ByteArray(0))
        val key = (response as? JsonObject)?.str("api_key")
            ?: throw ExtractionError.Malformed("The Vidio auth endpoint returned no API key.")
        apiKey = key
        return key
    }

    /** Upstream `_call_api`. */
    protected suspend fun callApi(url: String, videoId: String): JsonObject = http.downloadJson(
        url,
        headers = mapOf(
            "Content-Type" to "application/vnd.api+json",
            "X-API-KEY" to fetchApiKey(),
        ),
    ) as? JsonObject ?: throw ExtractionError.Malformed("The Vidio API returned no object for $videoId.")
}

// ------------------------------------------------------------------ helpers

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
