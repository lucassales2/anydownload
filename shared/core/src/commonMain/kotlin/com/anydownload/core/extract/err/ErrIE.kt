/*
 * ERR extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `err.py` from
 * `yt_dlp/extractor/err.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `err.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public Jupiter/Jupiter+/Lasteekraan `vodContent` JSON and the
 * arhiiv `content/video` JSON, with the HLS/DASH/direct rows and the
 * metadata fields the port models. A DRM-restricted Jupiter media is the
 * typed failure; alt_title, modified/release timestamps, release year,
 * season/episode numbers, and series/episode ids the port does not model are
 * dropped (series fills `channel`, series id fills `channelId`). An m3u8 or
 * mpd URL becomes one row, so manifest subtitles are not parsed. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.err

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ERRJupiterIE`: a Jupiter / Jupiter+ / Lasteekraan page. */
class ERRJupiterIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(
            "https://services.err.ee/api/v2/vodContent/getContentPageData?contentId=$videoId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ERR content API was not an object.")
        val data = response.obj("data")?.obj("mainContent")
            ?: throw ExtractionError.Malformed("The ERR content API had no main content.")
        val mediaData = data.array("medias").orEmpty()
            .firstOrNull { it is JsonObject } as? JsonObject
            ?: throw ExtractionError.Malformed("The ERR content had no media.")
        if (mediaData.obj("restrictions")?.bool("drm") == true) {
            throw ExtractionError.Unavailable("The video is DRM protected.")
        }

        val formats = mutableListOf<MediaFormat>()
        val src = mediaData.obj("src")
        for (key in listOf("hls", "hls2", "hlsNew")) {
            val formatUrl = src?.str(key) ?: continue
            formats += MediaFormat(
                formatId = "hls",
                url = formatUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }
        for (key in listOf("dash", "dashNew")) {
            val formatUrl = src?.str(key) ?: continue
            formats += MediaFormat(formatId = "dash", url = formatUrl, ext = "mp4", protocol = "mpd")
        }
        src?.str("file")?.let {
            formats += MediaFormat(formatId = "http", url = it, ext = ExtractorUtils.determineExt(it))
        }

        val isEpisode = data.str("type") == "episode"
        return InfoDict(
            id = videoId,
            title = data.str("heading"),
            description = cleanHtml(data.str("lead") ?: data.str("body")),
            uploadDate = data.number("created")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            channel = if (isEpisode) data.str("heading") else null,
            channelId = if (isEpisode) data.primitive("rootContentId") else null,
            formats = formats,
            webpageUrl = url,
            extractor = "err:jupiter",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ERRJupiter"

        val VALID_URL: Regex = Regex(
            "https?://(?:jupiter(?:pluss)?|lasteekraan)\\.err\\.ee/(?<id>\\d+)",
        )
    }
}

/** Upstream `ERRArhiivIE`: an arhiiv.err.ee video page. */
class ERRArhiivIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson("https://arhiiv.err.ee/api/v1/content/video/$videoId") as? JsonObject
            ?: throw ExtractionError.Malformed("The ERR archive API was not an object.")
        val src = data.obj("media")?.obj("src")
        val formats = mutableListOf<MediaFormat>()
        src?.str("hls")?.let {
            formats += MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native")
        }
        src?.str("dash")?.let {
            formats += MediaFormat(formatId = "dash", url = it, ext = "mp4", protocol = "mpd")
        }
        val info = data.obj("info")
        return InfoDict(
            id = videoId,
            title = info?.str("title"),
            description = info?.str("synopsis"),
            uploadDate = info?.str("uploadDate")?.let(ExtractorUtils::unifiedStrdate),
            channel = info?.str("seriesTitle"),
            channelId = info?.str("seriesId"),
            formats = formats,
            webpageUrl = url,
            extractor = "err:arhiiv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ERRArhiiv"

        val VALID_URL: Regex = Regex(
            "https://arhiiv\\.err\\.ee/video/(?:vaata/)?(?<id>[^/?#]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

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

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
