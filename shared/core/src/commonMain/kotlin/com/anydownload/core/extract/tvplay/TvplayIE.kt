/*
 * TVPlay extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `tvplay.py` from
 * `yt_dlp/extractor/tvplay.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tvplay.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the MTG `playapi.mtgx.tv` video/stream JSON (HLS rows, direct rows
 * with the upstream quality order, SAMI subtitles, and the geo-blocked typed
 * failure) and the `play.tv3.*` product/playlist JSON (HLS row, thumbnails,
 * and the resolved season/episode title). The port has no geo-bypass or f4m
 * helper, so an f4m stream is skipped and an RTMP stream is skipped (no RTMP
 * downloader); series/season/episode/release-year fields the port does not
 * model are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.tvplay

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

private const val MTG_API = "http://playapi.mtgx.tv/v3/videos"

/** Upstream `TVPlayIE`: the MTG (TVPlay/TV3Play) video API. */
class TVPlayIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val video = http.downloadJson("$MTG_API/$videoId") as? JsonObject
            ?: throw ExtractionError.Malformed("The MTG video API was not an object.")
        val streams = http.downloadJson("$MTG_API/stream/$videoId") as? JsonObject
            ?: throw ExtractionError.Malformed("The MTG streams API was not an object.")

        val formats = mutableListOf<MediaFormat>()
        for ((formatId, value) in streams.obj("streams").orEmpty()) {
            val videoUrl = (value as? JsonPrimitive)?.content
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            val ext = ExtractorUtils.determineExt(videoUrl)
            when {
                ext == "f4m" -> Unit // No f4m helper in the port.
                ext == "m3u8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                videoUrl.startsWith("rtmp") -> Unit // No RTMP downloader in the port.
                else -> formats += MediaFormat(
                    formatId = formatId,
                    url = videoUrl,
                    ext = ext,
                    preference = QUALITIES[formatId],
                )
            }
        }
        if (formats.isEmpty() && video.bool("is_geo_blocked") == true) {
            throw ExtractionError.GeoRestricted()
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        video.str("sami_path")?.let { samiPath ->
            val language = ExtractorUtils.searchRegex("_([a-z]{2})\\.xml", samiPath)
                ?: Regex("^https?://[^/]+").find(url)?.value?.substringAfterLast('.')
                ?: "en"
            subtitles += SubtitleTrack(
                language = language,
                formats = listOf(
                    SubtitleFormat(ext = ExtractorUtils.determineExt(samiPath, "sami"), url = samiPath),
                ),
            )
        }
        return InfoDict(
            id = videoId,
            title = video.str("title"),
            description = video.str("description"),
            channel = video.str("format_title"),
            duration = video.number("duration"),
            uploadDate = video.str("created_at")?.let(ExtractorUtils::unifiedStrdate),
            viewCount = video.obj("views")?.number("total")?.toLong(),
            ageLimit = video.number("age_limit")?.toInt(),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "mtg",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TVPlay"

        val VALID_URL: Regex = Regex(
            "(?:mtg:|https?://(?:www\\.)?(?:" +
                "tvplay(?:\\.skaties)?\\.lv(?:/parraides)?|" +
                "(?:tv3play|play\\.tv3)\\.lt(?:/programos)?|" +
                "tv3play(?:\\.tv3)?\\.ee/sisu" +
                ")/(?:[^/]+/)+)(?<id>\\d+)",
        )

        private val QUALITIES = mapOf("hls" to 0, "medium" to 1, "high" to 2)
    }
}

/** Upstream `TVPlayHomeIE`: the `play.tv3.*` product pages. */
class TVPlayHomeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val country = match.groups["country"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val isLive = match.groups["live"]?.value != null
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val origin = Regex("^(https?://[^/]+)").find(url)?.groupValues?.get(1)
            ?: throw ExtractionError.UnsupportedUrl()

        val apiPath = if (isLive) "lives/programmes" else "vods"
        val data = http.downloadJson(
            "$origin/api/products/$apiPath/$videoId?platform=BROWSER&lang=${country.uppercase()}",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The TVPlay product API was not an object.")
        val videoType = if (isLive) "CATCHUP" else "MOVIE"
        val streamId = if (isLive) {
            data.str("programRecordingId")
                ?: throw ExtractionError.Malformed("The TVPlay live product had no recording id.")
        } else {
            videoId
        }
        val stream = http.downloadJson(
            "$origin/api/products/$streamId/videos/playlist?videoType=$videoType&platform=BROWSER",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The TVPlay playlist API was not an object.")
        val source = ((stream.obj("sources")?.array("HLS")?.firstOrNull() as? JsonObject)?.str("src"))
            ?: throw ExtractionError.NoFormats("The TVPlay playlist had no HLS source.")

        val thumbnails = linkedSetOf<String>()
        for (element in data.obj("galary")?.obj("images")?.array("artworks").orEmpty()) {
            val artwork = element as? JsonObject ?: continue
            artwork.str("miniUrl")?.let { thumbnails += it }
            artwork.str("mainUrl")?.let { thumbnails += it }
        }
        return InfoDict(
            id = videoId,
            title = resolveTitle(data),
            description = data.str("description") ?: data.str("lead"),
            channel = data.obj("season")?.obj("serial")?.str("title"),
            duration = data.number("duration"),
            thumbnails = thumbnails.map { Thumbnail(url = it) },
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = source,
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            webpageUrl = url,
            extractor = "tvplay",
            extractorKey = IE_KEY,
        )
    }

    private fun resolveTitle(data: JsonObject): String? {
        val season = data.obj("season")
        val serial = season?.obj("serial")
        val title = data.str("title")
        val number = season?.number("number")?.toInt()
        val episode = data.number("episode")?.toInt()
        if (serial?.str("title") != null && serial.number("year") != null && number != null && episode != null) {
            val year = serial.number("year")!!.toInt()
            return "${serial.str("title")} ($year) | S${number.toString().padStart(2, '0')}" +
                "E${episode.toString().padStart(2, '0')}: $title"
        }
        return title
    }

    companion object {
        const val IE_KEY: String = "TVPlayHome"

        val VALID_URL: Regex = Regex(
            "https?://(?:tv3?)?play\\.(?:tv3|skaties)\\.(?<country>lv|lt|ee)/" +
                "(?<live>lives/)?[^?#&]+(?:episode|programme|clip)-(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
