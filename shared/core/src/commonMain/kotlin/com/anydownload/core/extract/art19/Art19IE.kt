/*
 * ART19 extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `art19.py` from
 * `yt_dlp/extractor/art19.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `art19.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public player metadata endpoint (`Accept:
 * application/vnd.art19.v0+json`), the public RSS episode JSON (mp3 and
 * media rows; waveform_bin is skipped), and the series endpoint listing.
 * The port does not carry episode/season ids or display ids, so they are
 * dropped. No cookie, token, or private URL is stored here.
 */
package com.anydownload.core.extract.art19

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

private const val UUID = "[\\da-f]{8}-?[\\da-f]{4}-?[\\da-f]{4}-?[\\da-f]{4}-?[\\da-f]{12}"

/** Upstream `Art19IE`: an episode. */
class Art19IE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val episodeId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val playerMetadata = try {
            http.downloadJson(
                "https://art19.com/episodes/$episodeId",
                headers = mapOf("Accept" to "application/vnd.art19.v0+json"),
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val rssMetadata = try {
            http.downloadJson("https://rss.art19.com/episodes/$episodeId.json") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val formats = mutableListOf<MediaFormat>(
            MediaFormat(
                formatId = "direct",
                url = "https://rss.art19.com/episodes/$episodeId.mp3",
                vcodec = MediaFormat.CODEC_NONE,
                acodec = "mp3",
            ),
        )
        for ((formatId, value) in rssMetadata?.obj("content")?.obj("media").orEmpty()) {
            if (formatId == "waveform_bin") continue
            val format = value as? JsonObject ?: continue
            val formatUrl = format.str("url") ?: continue
            formats += MediaFormat(
                formatId = formatId,
                url = formatUrl,
                vcodec = MediaFormat.CODEC_NONE,
                acodec = formatId,
                preference = if (formatId == "ogg") -2 else -1,
            )
        }
        val episode = playerMetadata?.obj("episode")
        val content = rssMetadata?.obj("content")
        val thumbnail = content?.str("cover_image")
        return InfoDict(
            id = episodeId,
            title = episode?.str("title") ?: content?.str("episode_title"),
            description = episode?.str("description_plain")
                ?: content?.str("episode_description_plain"),
            duration = content?.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(episode?.str("created_at")),
            channel = content?.str("series_title"),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "art19",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Art19"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?art19\\.com/shows/[^/#?]+/episodes/(?<id>$UUID)|" +
                "https?://rss\\.art19\\.com/episodes/(?<id2>$UUID)\\.mp3",
        )
    }
}

/** Upstream `Art19ShowIE`: a show listing. */
class Art19ShowIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val seriesId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val seriesMetadata = http.downloadJson(
            "https://art19.com/series/$seriesId",
            headers = mapOf("Accept" to "application/vnd.art19.v0+json"),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ART19 series API returned no object.")
        val series = seriesMetadata.obj("series")
        val entries = mutableListOf<InfoEntry>()
        for (element in series?.array("episode_ids").orEmpty()) {
            val episodeId = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            entries += InfoEntry(id = episodeId, url = "https://rss.art19.com/episodes/$episodeId.mp3")
        }
        return InfoDict(
            id = series?.primitive("id") ?: seriesId,
            title = series?.str("title"),
            description = series?.str("description_plain"),
            uploadDate = ExtractorUtils.unifiedStrdate(series?.str("created_at")),
            entries = entries,
            webpageUrl = url,
            extractor = "art19:show",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Art19Show"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?art19\\.com/shows/(?<id>[\\w-]+)(?:/embed)?/?(?:$|[#?])|" +
                "https?://rss\\.art19\\.com/(?<id2>[\\w-]+)/?(?:$|[#?])",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
