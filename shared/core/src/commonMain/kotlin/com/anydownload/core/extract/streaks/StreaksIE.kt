/*
 * STREAKS extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `streaks.py` from
 * `yt_dlp/extractor/streaks.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `streaks.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `playback.api.streaks.jp` media JSON (HLS rows, DRM skip,
 * caption/subtitle tracks, metadata), the players.streaks.jp and playback URL
 * forms, and the public SSAI session query merge for a live source. The
 * upstream `X-Streaks-Api-Key` header and `api_key` extractor arg are not
 * carried (the header allowlist refuses it and no credential is stored), so
 * an API-key-gated media surfaces as typed Unavailable; the 403/404 error
 * body inspection and the `live_from_start` ffmpeg option are not
 * translated; an m3u8 URL becomes one HLS row. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.streaks

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_TEMPLATE = "https://%s.api.streaks.jp/v1/projects/%s/medias/%s%s"

/** Upstream `StreaksBaseIE`: the shared playback API extraction. */
abstract class StreaksBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_from_streaks_api`. */
    protected suspend fun extractFromStreaksApi(
        projectId: String,
        mediaId: String,
        apiKey: String? = null,
    ): InfoDict {
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "Origin" to "https://players.streaks.jp",
        )
        if (apiKey != null) headers["X-Streaks-Api-Key"] = apiKey
        val response = http.downloadJson(
            templateFill(API_TEMPLATE, "playback", projectId, mediaId, ""),
            headers = headers,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The STREAKS playback API was not an object.")

        val streaksId = response.str("id")
            ?: throw ExtractionError.Malformed("The STREAKS playback API had no id.")
        val type = response.str("type")
        val isLive = type == "linear" || type == "live"
        val sources = response.array("sources").orEmpty()
        val ssai = sources.firstNotNullOfOrNull { (it as? JsonObject)?.obj("ssai") }

        val formats = mutableListOf<MediaFormat>()
        var drmFormats = false
        for (element in sources) {
            val source = element as? JsonObject ?: continue
            val srcUrl = source.str("src")
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            if (source.obj("key_systems") != null) {
                drmFormats = true
                continue
            }
            val ext = ExtractorUtils.mimetype2ext(source.str("type"))
            if (ext != "m3u8") continue
            var finalUrl = srcUrl
            if (isLive && ssai != null && source.str("id") != null) {
                finalUrl = applySsaiSession(projectId, streaksId, source.str("id")!!, finalUrl)
            }
            formats += MediaFormat(
                formatId = "hls",
                url = finalUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }
        if (formats.isEmpty() && drmFormats) {
            throw ExtractionError.Unavailable("The video is DRM protected.")
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in response.array("tracks").orEmpty()) {
            val track = element as? JsonObject ?: continue
            val kind = track.str("kind") ?: continue
            if (kind != "captions" && kind != "subtitles") continue
            val src = track.str("src") ?: continue
            subtitles += SubtitleTrack(
                language = track.str("srclang")?.lowercase() ?: "ja",
                formats = listOf(
                    SubtitleFormat(ext = ExtractorUtils.determineExt(src, "vtt"), url = src),
                ),
            )
        }

        val thumbnail = response.obj("thumbnail")?.str("src") ?: response.obj("poster")?.str("src")
        return InfoDict(
            id = streaksId,
            title = cleanHtml(response.str("name")),
            description = cleanHtml(response.str("description")),
            duration = response.number("duration"),
            uploadDate = response.str("created_at")?.let(ExtractorUtils::unifiedStrdate),
            channelId = projectId,
            isLive = isLive,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            subtitles = subtitles,
            extractor = "streaks",
            extractorKey = ieKey,
        )
    }

    /** Upstream SSAI session: merge the returned query into the stream URL. */
    private suspend fun applySsaiSession(
        projectId: String,
        streaksId: String,
        sourceId: String,
        srcUrl: String,
    ): String {
        val session = try {
            http.downloadJson(
                templateFill(API_TEMPLATE, "ssai", projectId, streaksId, "/ssai/session"),
                method = "POST",
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                body = "{\"id\":\"$sourceId\"}".encodeToByteArray(),
            ) as? JsonArray
        } catch (error: ExtractionError) {
            null
        } ?: return srcUrl
        val query = (session.firstOrNull() as? JsonObject)?.obj("query") ?: return srcUrl
        val parts = mutableListOf<String>()
        for ((key, value) in query) {
            val text = when (value) {
                is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.content
                is JsonPrimitive -> value.content
                else -> null
            } ?: continue
            parts += "$key=$text"
        }
        if (parts.isEmpty()) return srcUrl
        val separator = if (srcUrl.contains('?')) '&' else '?'
        return srcUrl + separator + parts.joinToString("&")
    }
}

/** Upstream `StreaksIE`: a players.streaks.jp or playback API URL. */
class StreaksIE(
    http: ExtractorHttp,
) : StreaksBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val projectId = match.groups["project1"]?.value ?: match.groups["project2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val mediaId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val apiKey = match.groups["apikey"]?.value
        return extractFromStreaksApi(projectId, mediaId, apiKey)
    }

    companion object {
        const val IE_KEY: String = "Streaks"

        val VALID_URL: Regex = Regex(
            "https?://(?:" +
                "players\\.streaks\\.jp/(?<project1>[\\w-]+)/(?<apikey>[\\da-f]+)/index\\.html\\?" +
                "(?:[^#]+&)?m=|" +
                "playback\\.api\\.streaks\\.jp/v1/projects/(?<project2>[\\w-]+)/medias/" +
                ")(?<id>(?:ref:)?[\\w-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Multiplatform `%s` substitution for the API URL template. */
private fun templateFill(template: String, vararg values: String): String {
    var out = template
    for (value in values) out = out.replaceFirst("%s", value)
    return out
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
