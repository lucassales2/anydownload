/*
 * TU Graz tube extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `tubetugraz.py` from
 * `yt_dlp/extractor/tubetugraz.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tubetugraz.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `search/episode.json` metadata and media tracks (https rows with
 * bitrates/resolution, HLS and DASH rows, the presentation/presenter format
 * note and -2 preference, and the Wowza SMIL fallbacks probed non-fatally),
 * plus the series listing and its series.json title. The Shibboleth/TFA login
 * flow is not translated. A series entry points at the episode's watch URL
 * instead of inlining its formats; the episode/series label fields the port
 * does not model are dropped (series fills `channel`, series id fills
 * `channelId`, creator fills `uploader`). No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.tubetugraz

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val EPISODE_API = "https://tube.tugraz.at/search/episode.json"
private const val WOWZA_BASE = "https://wowza.tugraz.at/matterhorn_engage"
private val FORMAT_TYPES = listOf("presentation", "presenter")

/** Upstream `TubeTuGrazBaseIE`: the shared episode and format walk. */
abstract class TubeTuGrazBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun extractEpisode(episodeInfo: JsonObject): InfoDict {
        val videoId = episodeInfo.primitive("id")
            ?: throw ExtractionError.Malformed("The TU Graz episode had no id.")
        val mediaPackage = episodeInfo.obj("mediapackage")
        val formats = extractFormats(mediaPackage?.obj("media")?.get("track"), videoId)
        return InfoDict(
            id = videoId,
            title = mediaPackage?.str("title") ?: episodeInfo.str("dcTitle"),
            uploader = creators(episodeInfo),
            duration = mediaPackage?.number("duration") ?: episodeInfo.number("dcExtent"),
            channel = mediaPackage?.str("seriestitle"),
            channelId = mediaPackage?.str("series") ?: episodeInfo.str("dcIsPartOf"),
            formats = formats,
            webpageUrl = "https://tube.tugraz.at/paella/ui/watch.html?id=$videoId",
            extractorKey = ieKey,
        )
    }

    private suspend fun extractFormats(trackValue: JsonElement?, videoId: String): List<MediaFormat> {
        val tracks = if (trackValue is JsonArray) trackValue else listOfNotNull(trackValue)
        var hasHls = false
        var hasDash = false
        val formats = mutableListOf<MediaFormat>()
        for (element in tracks) {
            val track = element as? JsonObject ?: continue
            val url = track.obj("tags")?.str("url") ?: track.str("url") ?: continue
            val formatType = track.str("type") ?: "unknown"
            when ((track.str("transport") ?: "https").lowercase()) {
                "https" -> {
                    val resolution = parseResolution(track.obj("video")?.str("resolution"))
                    formats += withType(
                        MediaFormat(
                            url = url,
                            abr = track.obj("audio")?.number("bitrate")?.div(1000),
                            vbr = track.obj("video")?.number("bitrate")?.div(1000),
                            fps = track.obj("video")?.number("framerate"),
                            width = resolution?.first,
                            height = resolution?.second,
                        ),
                        formatType,
                    )
                }

                "hls" -> {
                    hasHls = true
                    formats += withType(
                        MediaFormat(
                            formatId = "hls",
                            url = url,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        ),
                        formatType,
                    )
                }

                "dash" -> {
                    hasDash = true
                    formats += withType(
                        MediaFormat(formatId = "dash", url = url, ext = "mp4", protocol = "mpd"),
                        formatType,
                    )
                }

                else -> Unit
            }
        }
        for (formatType in FORMAT_TYPES) {
            if (!hasHls) {
                val url = "$WOWZA_BASE/smil:engage-player_${videoId}_$formatType.smil/playlist.m3u8"
                if (probe(url)) {
                    formats += withType(
                        MediaFormat(formatId = "hls", url = url, ext = "mp4", protocol = "m3u8_native"),
                        formatType,
                    )
                }
            }
            if (!hasDash) {
                val url = "$WOWZA_BASE/smil:engage-player_${videoId}_$formatType.smil/manifest_mpm4sav_mvlist.mpd"
                if (probe(url)) {
                    formats += withType(
                        MediaFormat(formatId = "dash", url = url, ext = "mp4", protocol = "mpd"),
                        formatType,
                    )
                }
            }
        }
        return formats
    }

    /** Upstream `_set_format_type`. */
    private fun withType(format: MediaFormat, formatType: String): MediaFormat = format.copy(
        formatNote = formatType,
        preference = if (!formatType.startsWith(FORMAT_TYPES[0])) -2 else format.preference,
    )

    private suspend fun probe(url: String): Boolean = try {
        http.downloadWebpage(url)
        true
    } catch (error: Exception) {
        false
    }

    private fun creators(episodeInfo: JsonObject): String? {
        val value: JsonElement? = episodeInfo.obj("mediapackage")?.obj("creators")?.get("creator")
            ?: episodeInfo["dcCreator"]
        val names = when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(value.content)
            else -> emptyList()
        }
        return names.filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotEmpty() }
    }
}

/** Upstream `TubeTuGrazIE`: one episode. */
class TubeTuGrazIE(
    http: ExtractorHttp,
) : TubeTuGrazBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson("$EPISODE_API?id=$videoId&limit=1") as? JsonObject
            ?: throw ExtractionError.Malformed("The TU Graz episode API was not an object.")
        val result = data.obj("search-results")?.get("result")
        val episodeInfo = result as? JsonObject ?: JsonObject(mapOf("id" to JsonPrimitive(videoId)))
        return extractEpisode(episodeInfo)
    }

    companion object {
        const val IE_KEY: String = "TubeTuGraz"

        val VALID_URL: Regex = Regex(
            "https?://tube\\.tugraz\\.at/(?:" +
                "paella/ui/watch\\.html\\?(?:[^#]*&)?id=|" +
                "portal/watch/" +
                ")(?<id>[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})",
        )
    }
}

/** Upstream `TubeTuGrazSeriesIE`: a series listing. */
class TubeTuGrazSeriesIE(
    http: ExtractorHttp,
) : TubeTuGrazBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val episodesData = http.downloadJson("$EPISODE_API?sid=$playlistId") as? JsonObject
            ?: throw ExtractionError.Malformed("The TU Graz episode API was not an object.")
        val entries = episodesData.obj("search-results")?.array("result").orEmpty().mapNotNull { element ->
            val episode = element as? JsonObject ?: return@mapNotNull null
            val episodeId = episode.primitive("id") ?: return@mapNotNull null
            InfoEntry(
                id = episodeId,
                title = episode.obj("mediapackage")?.str("title"),
                url = "https://tube.tugraz.at/paella/ui/watch.html?id=$episodeId",
            )
        }
        val seriesData = try {
            http.downloadJson(
                "https://tube.tugraz.at/series/series.json?seriesId=$playlistId&count=1&sort=TITLE",
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val title = ((seriesData?.array("catalogs")?.firstOrNull() as? JsonObject)
            ?.obj("http://purl.org/dc/terms/")?.array("title")?.firstOrNull() as? JsonObject)
            ?.str("value")
        return InfoDict(
            id = playlistId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TubeTuGrazSeries"

        val VALID_URL: Regex = Regex(
            "https?://tube\\.tugraz\\.at/paella/ui/browse\\.html\\?series=" +
                "(?<id>[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `parse_resolution`. */
private fun parseResolution(value: String?): Pair<Long?, Long?>? {
    val match = Regex("^(\\d+)x(\\d+)$").find(value?.trim() ?: return null) ?: return null
    return match.groupValues[1].toLongOrNull() to match.groupValues[2].toLongOrNull()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
