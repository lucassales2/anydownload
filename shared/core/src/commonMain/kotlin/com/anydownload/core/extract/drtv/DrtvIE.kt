/*
 * DR TV extractors — AnyDownload
 *
 * Kotlin translation of the public metadata subset of `drtv.py` from
 * `yt_dlp/extractor/drtv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `drtv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public mu-online channel API (live HLS), the public
 * production-cdn page API (season and series listings), and the live
 * formats. The video path needs the anonymous SSO device token, so
 * `DRTVIE` matches and fails typed; the port does not carry display_id,
 * series, season, or episode fields, so they are dropped. No device id,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.drtv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val SERIES_API =
    "https://production-cdn.dr-massive.com/api/page?device=web_browser&item_detail_expand=all&" +
        "lang=da&max_list_prefetch=3&path="

/** Upstream `DRTVIE`: a dr.dk/dr-massive video page (typed wall). */
class DRTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The DR TV stream data needs the anonymous SSO device token, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "DRTV"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.)?dr\\.dk/tv/se(?:/ondemand)?/(?:[^/?#]+/)*|" +
                "(?:www\\.)?(?:dr\\.dk|dr-massive\\.com)/drtv/(?:se|episode|program)/)" +
                "(?<id>[\\da-z_-]+)",
        )
    }
}

/** Upstream `DRTVLiveIE`: a live channel. */
class DRTVLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val channelData = http.downloadJson(
            "https://www.dr.dk/mu-online/api/1.0/channel/$channelId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The DR channel API returned no object.")
        val formats = mutableListOf<MediaFormat>()
        for (serverElement in channelData.array("StreamingServers").orEmpty()) {
            val serverElementObject = serverElement as? JsonObject ?: continue
            val server = serverElementObject.str("Server") ?: continue
            val linkType = serverElementObject.str("LinkType")
            for (qualityElement in serverElementObject.array("Qualities").orEmpty()) {
                val quality = qualityElement as? JsonObject ?: continue
                for (streamElement in quality.array("Streams").orEmpty()) {
                    val stream = streamElement as? JsonObject ?: continue
                    val streamPath = stream.str("Stream") ?: continue
                    if (linkType != "HLS") continue
                    formats += MediaFormat(
                        formatId = linkType,
                        url = "$server/$streamPath?b=",
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                }
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The DR channel API returned no HLS stream.")
        }
        return InfoDict(
            id = channelId,
            title = channelData.str("Title"),
            isLive = true,
            thumbnails = listOfNotNull(
                channelData.str("PrimaryImageUri")?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "drtv:live",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "DRTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?dr\\.dk/(?:tv|TV)/live/(?<id>[\\da-z-]+)")
    }
}

/** Upstream `DRTVSeasonIE`: a season listing. */
class DRTVSeasonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["display"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val data = downloadPage(http, "/saeson/${displayId}_$seasonId")
        val item = firstItem(data)
        val entries = mutableListOf<InfoEntry>()
        for (episodeElement in item?.obj("episodes")?.array("items").orEmpty()) {
            val episode = episodeElement as? JsonObject ?: continue
            val path = episode.str("path") ?: continue
            entries += InfoEntry(
                id = episode.str("id"),
                title = episode.str("title") ?: episode.str("contextualTitle"),
                url = "https://www.dr.dk/drtv$path",
            )
        }
        return InfoDict(
            id = seasonId,
            title = item?.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "drtv:season",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "DRTVSeason"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:dr\\.dk|dr-massive\\.com)/drtv/saeson/" +
                "(?<display>[\\w-]+)_(?<id>\\d+)",
        )
    }
}

/** Upstream `DRTVSeriesIE`: a series listing. */
class DRTVSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["display"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seriesId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val data = downloadPage(http, "/serie/${displayId}_$seriesId")
        val item = firstItem(data)
        val entries = mutableListOf<InfoEntry>()
        for (seasonElement in item?.obj("show")?.obj("seasons")?.array("items").orEmpty()) {
            val season = seasonElement as? JsonObject ?: continue
            val path = season.str("path") ?: continue
            entries += InfoEntry(
                id = season.str("id"),
                title = season.str("title") ?: season.str("contextualTitle"),
                url = "https://www.dr.dk/drtv$path",
            )
        }
        return InfoDict(
            id = seriesId,
            title = item?.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "drtv:series",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "DRTVSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:dr\\.dk|dr-massive\\.com)/drtv/serie/" +
                "(?<display>[\\w-]+)_(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun downloadPage(http: ExtractorHttp, path: String): JsonObject =
    http.downloadJson(SERIES_API + path) as? JsonObject
        ?: throw ExtractionError.Malformed("The DR page API returned no object.")

/** Upstream `entries[0].item`. */
private fun firstItem(data: JsonObject): JsonObject? {
    val entries = data.array("entries") ?: return null
    return ((entries.firstOrNull() as? JsonObject)?.obj("item"))
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
