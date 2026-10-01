/*
 * NFB/ONF extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `nfb.py` from
 * `yt_dlp/extractor/nfb.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nfb.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `window.PLAYER_OPTIONS` page data (HLS source plus the
 * described-video dvSource), the film page metadata scan, the `episodesData`
 * episode metadata, and the series listing. Manifest parsing is not
 * translated, so an m3u8 URL becomes one HLS row; the port does not carry
 * season/series fields or json-ld merges. No cookie, token, or private URL
 * is stored here.
 */
package com.anydownload.core.extract.nfb

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

/** Shared upstream `NFBBaseIE` episode data helpers. */
abstract class NFBBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_ep_data`. */
    protected fun extractEpData(webpage: String): List<JsonObject> {
        val marker = Regex("episodesData\\s*:").find(webpage) ?: return emptyList()
        val range = balancedRange(webpage, marker.range.last + 1)
        val value = if (range != null) {
            ExtractorUtils.parseJson(webpage.substring(range.first, range.last + 1))
        } else {
            // episodesData may be an array rather than an object.
            val arrayStart = webpage.indexOf('[', marker.range.last + 1)
            val arrayRange = if (arrayStart >= 0) balancedRange(webpage, arrayStart, '[', ']') else null
            if (arrayRange == null) {
                null
            } else {
                ExtractorUtils.parseJson(webpage.substring(arrayRange.first, arrayRange.last + 1))
            }
        }
        return when (value) {
            is JsonArray -> value.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(value)
            else -> emptyList()
        }
    }

    /** Upstream `_extract_ep_info` for the fields the port carries. */
    protected fun extractEpInfo(data: List<JsonObject>, videoId: String, slug: String? = null): InfoDict {
        val episode = data.firstOrNull { it.str("embed_url")?.contains(videoId) == true }
            ?: JsonObject(emptyMap())
        val dataLayer = episode.obj("data_layer") ?: JsonObject(emptyMap())
        val series = dataLayer.str("seriesTitle")
        val episodeTitle = dataLayer.str("episodeTitle")
        val title = listOfNotNull(series, episodeTitle).takeIf { it.isNotEmpty() }?.joinToString(" - ")
        val episodeNumber = Regex("[/-]e(?:pisode)?-?(\\d+)(?:[/-]|$)")
            .find(slug ?: videoId)?.groupValues?.get(1)?.toLongOrNull()
        return InfoDict(
            id = videoId,
            title = title,
            description = episode.str("description"),
            channel = series,
            duration = null,
            uploadDate = dataLayer.number("episodeYear")?.toLong()?.toString(),
            thumbnails = listOfNotNull(episode.str("thumbnail_url")?.let { Thumbnail(url = it) }),
            extractor = "nfb",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `NFBIE`: an NFB/ONF film or episode. */
class NFBIE(
    http: ExtractorHttp,
) : NFBBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val site = match.groups["site"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var type = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var slug = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = try {
            http.followRedirects("https://www.$site.ca/$type/$slug/")
        } catch (error: ExtractionError) {
            "https://www.$site.ca/$type/$slug/"
        }
        val webpage = http.downloadWebpage(finalUrl)
        VALID_URL.find(finalUrl)?.let { redirectMatch ->
            type = redirectMatch.groups["type"]?.value ?: type
            slug = redirectMatch.groups["id"]?.value ?: slug
        }
        val playerMarker = Regex("window\\.PLAYER_OPTIONS\\[[^\\]]+\\]\\s*=").find(webpage)
            ?: throw ExtractionError.Malformed("The NFB page had no player options.")
        val playerData = balancedRange(webpage, playerMarker.range.last + 1)
            ?.let { ExtractorUtils.parseJson(webpage.substring(it.first, it.last + 1)) as? JsonObject }
            ?: throw ExtractionError.Malformed("The NFB player options were not JSON.")
        val overlayUrl = playerData.obj("overlay")?.str("url")
            ?: throw ExtractionError.Malformed("The NFB player had no overlay URL.")
        val videoId = overlayUrl.trimEnd('/').removeSuffix("/overlay").substringAfterLast('/')
        val source = playerData.str("source")
            ?: throw ExtractionError.NoFormats("The NFB player had no source.")
        val formats = mutableListOf(
            MediaFormat(formatId = "hls", url = source, ext = "mp4", protocol = "m3u8_native"),
        )
        playerData.str("dvSource")?.let {
            formats += MediaFormat(
                formatId = "dv",
                url = it,
                ext = "mp4",
                protocol = "m3u8_native",
                preference = -2,
                formatNote = "described video",
            )
        }
        val info = if (type == "film") {
            InfoDict(
                id = videoId,
                title = Regex("[\"']nfb_version_title[\"']\\s*:\\s*[\"']([^\"']+)").find(webpage)
                    ?.groupValues?.get(1),
                description = Regex(
                    "<[^>]+\\bid=[\"']tabSynopsis[\"'][^>]*>\\s*<p[^>]*>\\s*([^<]+)",
                ).find(webpage)?.groupValues?.get(1),
                uploadDate = Regex("[\"']nfb_version_year[\"']\\s*:\\s*[\"']([^\"']+)").find(webpage)
                    ?.groupValues?.get(1),
                thumbnails = listOfNotNull(
                    playerData.str("poster")?.let { Thumbnail(url = it) },
                ),
                formats = formats,
                extractor = "nfb",
                extractorKey = IE_KEY,
            )
        } else {
            extractEpInfo(extractEpData(webpage), videoId, slug).copy(
                formats = formats,
                extractorKey = IE_KEY,
            )
        }
        val uploader = Regex("<[^>]+\\bitemprop=[\"']director[\"'][^>]*>([^<]+)").find(webpage)
            ?.groupValues?.get(1)
        return info.copy(uploader = uploader ?: info.uploader, webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "NFB"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<site>nfb|onf)\\.ca/(?<type>film)/(?<id>[^/?#\u0026]+)|" +
                "https?://(?:www\\.)?(?<site2>nfb|onf)\\.ca/(?<type2>series?)/" +
                "(?<id2>[^/?#\u0026]+/s(?:ea|ai)son\\d+/episode\\d+)",
        )
    }
}

/** Upstream `NFBSeriesIE`: an NFB/ONF series. */
class NFBSeriesIE(
    http: ExtractorHttp,
) : NFBBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val site = match.groups["site"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val type = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seriesId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonPath = if (type == "serie") "saison" else "season"
        val webpage = http.downloadWebpage("https://www.$site.ca/$type/$seriesId/$seasonPath" + "1/episode1")
        val episodes = extractEpData(webpage)
        val entries = mutableListOf<InfoEntry>()
        for (episode in episodes) {
            val embedUrl = episode.str("embed_url") ?: continue
            if (!NFBIE.VALID_URL.containsMatchIn(embedUrl)) continue
            entries += InfoEntry(url = embedUrl)
        }
        return InfoDict(
            id = seriesId,
            entries = entries,
            webpageUrl = url,
            extractor = "nfb:series",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NFBSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<site>nfb|onf)\\.ca/(?<type>series?)/(?<id>[^/?#\u0026]+)/?(?:[?#]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun balancedRange(value: String, start: Int, openChar: Char = '{', closeChar: Char = '}'): IntRange? {
    val open = value.indexOf(openChar, start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = open
    while (i < value.length) {
        val c = value[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) return open..i
                }
            }
        }
        i++
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
