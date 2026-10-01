/*
 * Viu extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `viu.py` from
 * `yt_dlp/extractor/viu.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `viu.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the desktop clip API (`clip/load`: title, description, duration,
 * the m3u8 URL, and `subtitle_<lang>_<ext>` tracks) and the container API
 * (`container/load`: playlist entries as `viu:` children). `ViuOTTIE` and
 * `ViuOTTIndonesiaIE` match and fail typed: their playback APIs need bearer
 * tokens minted by the Viu auth gateway / user identity API, and the
 * Indonesia path is DRM-gated. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.viu

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ViuIE`: the desktop clip API. */
class ViuIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi(
            http,
            "clip/load",
            videoId,
            "appid=viu_desktop&fmt=json&id=$videoId",
        )
        val videoData = response.array("item")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The clip API returned no item.")
        val title = videoData.str("title")
        val urlPath = videoData.str("urlpathd") ?: videoData.str("urlpath")
        val tdirForWhole = videoData.str("tdirforwhole")
        val hlsFile = videoData.str("jwhlsfile")
        val m3u8Url = if (urlPath != null && tdirForWhole != null && hlsFile != null) {
            "$urlPath/$tdirForWhole/$hlsFile"
        } else {
            videoData.str("href")
        } ?: throw ExtractionError.NoFormats("The clip API returned no stream URL.")
        val subtitles = mutableListOf<SubtitleTrack>()
        for ((key, value) in videoData) {
            val match = SUBTITLE_KEY.find(key) ?: continue
            val lang = match.groupValues[1]
            val ext = match.groupValues[2]
            val subUrl = (value as? JsonPrimitive)?.content ?: continue
            subtitles += SubtitleTrack(
                language = lang,
                formats = listOf(SubtitleFormat(ext = ext, url = subUrl)),
            )
        }
        return InfoDict(
            id = videoId,
            title = title,
            description = videoData.str("description"),
            duration = videoData.number("duration"),
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = m3u8Url,
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "viu",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Viu"

        val VALID_URL: Regex = Regex("(?:viu:|https?://[^/]+\\.viu\\.com/[a-z]{2}/media/)(?<id>\\d+)")

        private val SUBTITLE_KEY = Regex("^subtitle_([^_]+)_(vtt|srt)$")
    }
}

/** Upstream `ViuPlaylistIE`: the playlist container API. */
class ViuPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi(
            http,
            "container/load",
            playlistId,
            "appid=viu_desktop&fmt=json&id=playlist-$playlistId",
        )
        val container = response.obj("container")
            ?: throw ExtractionError.Malformed("The container API returned no container.")
        val entries = container.array("item").orEmpty().mapNotNull { element ->
            val itemId = (element as? JsonObject)?.primitiveText("id") ?: return@mapNotNull null
            InfoEntry(id = itemId, url = "viu:$itemId")
        }
        return InfoDict(
            id = playlistId,
            title = container.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "viu:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ViuPlaylist"

        val VALID_URL: Regex = Regex("https?://www\\.viu\\.com/[^/]+/listing/playlist-(?<id>\\d+)")
    }
}

/** Upstream `ViuOTTIE`: the OTT player (bearer-token wall). */
class ViuOTTIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The Viu OTT playback API needs a bearer token minted by the Viu auth gateway and the " +
            "stream URLs are token-gated; the port does not translate the token flow.",
    )

    companion object {
        const val IE_KEY: String = "ViuOTT"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?viu\\.com/ott/(?<countryCode>[a-z]{2})/(?<langCode>[a-z]{2}-[a-z]{2})/vod/(?<id>\\d+)",
        )
    }
}

/** Upstream `ViuOTTIndonesiaIE`: the Indonesia OTT player (token/DRM wall). */
class ViuOTTIndonesiaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The Viu Indonesia OTT API needs a token from the user identity API and its content API " +
            "is DRM-gated; the port does not translate the token flow.",
    )

    companion object {
        const val IE_KEY: String = "ViuOTTIndonesia"

        val VALID_URL: Regex = Regex(
            "https?://www\\.viu\\.com/ott/\\w+/\\w+/all/video-[\\w-]+-(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun callApi(
    http: ExtractorHttp,
    path: String,
    displayId: String,
    query: String,
): JsonObject {
    val body = http.downloadJson("https://www.viu.com/api/$path?$query") as? JsonObject
        ?: throw ExtractionError.Malformed("The Viu API was not an object.")
    val response = body.obj("response")
        ?: throw ExtractionError.Malformed("The Viu API had no response.")
    if (response.str("status") != "success") {
        throw ExtractionError.Unavailable("Viu said: ${response.str("message") ?: "unknown error"}")
    }
    return response
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
