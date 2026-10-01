/*
 * NBA extractors — AnyDownload
 *
 * Kotlin translation of the public metadata subset of `nba.py` from
 * `yt_dlp/extractor/nba.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nba.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public solr program search, the watch.nba.com publishpoint
 * HLS path, the public collection content API, and the page id scans.
 * Everything behind the Turner CV/AdobePass auth flow or the internal
 * accessToken account API matches and fails typed. No cookie, token, or
 * signed media URL is stored here. Upstream marks these extractors
 * `_WORKING = False`; the port carries the public parts only.
 */
package com.anydownload.core.extract.nba

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

private const val WATCH_BASE = "https?://(?:(?:www\\.)?nba\\.com(?:/watch)?|watch\\.nba\\.com)/"
private const val TEAM = "blazers|bucks|bulls|cavaliers|celtics|clippers|grizzlies|hawks|heat|hornets|" +
    "jazz|kings|knicks|lakers|magic|mavericks|nets|nuggets|pacers|pelicans|pistons|raptors|rockets|" +
    "sixers|spurs|suns|thunder|timberwolves|warriors|wizards"
private const val CHANNEL_PATH = "video/channel|series"

private const val MAX_PAGES = 5

/** Upstream `NBAWatchEmbedIE`: a watch embed id. */
class NBAWatchEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractWatchVideo(http, "pid", videoId)
    }

    companion object {
        const val IE_KEY: String = "NBAWatchEmbed"

        val VALID_URL: Regex = Regex(WATCH_BASE + "embed\\?.*?\\bid=(?<id>\\d+)")
    }
}

/** Upstream `NBAWatchIE`: a watch video page. */
class NBAWatchIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val collectionId = queryParam(url, "collection")
        if (collectionId != null && collectionId != displayId) {
            return InfoDict(
                id = collectionId,
                redirectUrl = "https://www.nba.com/watch/list/collection/$collectionId",
                webpageUrl = url,
                extractor = "nba:watch",
                extractorKey = IE_KEY,
            )
        }
        return extractWatchVideo(http, "seoName", displayId)
    }

    companion object {
        const val IE_KEY: String = "NBAWatch"

        val VALID_URL: Regex = Regex(WATCH_BASE + "(?:nba/)?video/(?<id>.+?(?=/index\\.html)|(?:[^/]+/)*[^/?#&]+)")
    }
}

/** Upstream `NBAWatchCollectionIE`: a watch collection. */
class NBAWatchCollectionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val collectionId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = try {
                http.downloadJson(
                    "https://content-api-prod.nba.com/public/1/endeavor/video-list/collection/" +
                        "$collectionId?count=100&page=$page",
                ) as? JsonObject
            } catch (error: Exception) {
                break
            } ?: break
            val videos = response.obj("results")?.array("videos").orEmpty()
            if (videos.isEmpty()) break
            for (element in videos) {
                val video = element as? JsonObject ?: continue
                val program = video.obj("program") ?: JsonObject(emptyMap())
                val seoName = program.str("seoName") ?: program.str("slug") ?: continue
                entries += InfoEntry(
                    id = program.str("id") ?: video.str("id"),
                    title = program.str("title") ?: video.str("title"),
                    url = "https://www.nba.com/watch/video/$seoName",
                )
            }
            page++
        }
        return InfoDict(
            id = collectionId,
            entries = entries,
            webpageUrl = url,
            extractor = "nba:watch:collection",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NBAWatchCollection"

        val VALID_URL: Regex = Regex(WATCH_BASE + "list/collection/(?<id>[^/?#&]+)")
    }
}

/** Upstream `NBAEmbedIE`: the secure iframe. */
class NBAEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val contentId = queryParam(url, "contentId") ?: throw ExtractionError.UnsupportedUrl()
        val team = queryParam(url, "team")
        if (team.isNullOrBlank()) {
            return InfoDict(
                id = contentId,
                redirectUrl = "https://watch.nba.com/video/$contentId",
                webpageUrl = url,
                extractor = "nba:embed",
                extractorKey = IE_KEY,
            )
        }
        throw ExtractionError.LoginRequired(
            "The NBA embed player needs the internal accessToken account API, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "NBAEmbed"

        val VALID_URL: Regex = Regex(
            "https?://secure\\.nba\\.com/assets/amp/include/video/(?:topI|i)frame\\.html\\?.*?\\bcontentId=(?<id>[^?#&]+)",
        )
    }
}

/** Upstream `NBAIE`: a team video page. */
class NBAIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val team = match.groups["team"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val rawId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val contentId = if (url.contains("/play#/")) {
            decodeUrlComponent(rawId)
        } else {
            val webpage = http.downloadWebpage(url)
            Regex("videoID\\s*:\\s*\"([^\"]+)\"").find(webpage)?.groupValues?.get(1)
                ?: throw ExtractionError.Malformed("The NBA page had no video id.")
        }
        return InfoDict(
            id = contentId,
            redirectUrl = "https://secure.nba.com/assets/amp/include/video/iframe.html" +
                "?contentId=${encodeUrlComponent(contentId)}&team=$team",
            webpageUrl = url,
            extractor = "nba",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NBA"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?nba\\.com/(?<team>$TEAM)(?:/play\\#)?/" +
                "(?!(?:$CHANNEL_PATH))video/(?<id>(?:[^/]+/)*[^/?#&]+)",
        )
    }
}

/** Upstream `NBAChannelIE`: a team channel listing. */
class NBAChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The NBA channel listing needs the internal accessToken account API, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "NBAChannel"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?nba\\.com/(?<team>$TEAM)(?:/play\\#)?/(?:$CHANNEL_PATH)/(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractWatchVideo(
    http: ExtractorHttp,
    filterKey: String,
    filterValue: String,
): InfoDict {
    val search = try {
        http.downloadJson(
            "https://neulionscnbav2-a.akamaihd.net/solr/nbad_program/usersearch" +
                "?fl=description,image,name,pid,releaseDate,runtime,tags,seoName&q=$filterKey:$filterValue&wt=json",
        ) as? JsonObject
    } catch (error: ExtractionError) {
        null
    }
    val video = search?.obj("response")?.array("docs")?.firstOrNull() as? JsonObject
        ?: throw ExtractionError.Malformed("The NBA program search returned no video.")
    val videoId = video.primitive("pid")?.let { value ->
        runCatching { value.toDouble().toLong().toString() }.getOrNull() ?: value
    }
    val publishpoint = try {
        http.downloadJson(
            "https://watch.nba.com/service/publishpoint?type=video&format=json&id=$videoId",
        ) as? JsonObject
    } catch (error: ExtractionError) {
        null
    }
    val path = publishpoint?.str("path")?.replace(Regex("_(?:pc|iphone)\\."), ".")
    val formats = mutableListOf<MediaFormat>()
    if (path != null) {
        formats += MediaFormat(
            formatId = "hls",
            url = path,
            ext = "mp4",
            protocol = "m3u8_native",
        )
        formats += MediaFormat(
            formatId = "http",
            url = path.replace(".m3u8", ""),
            ext = "mp4",
            protocol = "http",
        )
    }
    if (formats.isEmpty()) {
        throw ExtractionError.Unavailable(
            "The NBA watch player returned no publishpoint path; the CVP auth flow is not translated.",
        )
    }
    val thumbnail = video.str("image")
        ?.let { "https://nbadsdmt.akamaized.net/media/nba/nba/thumbs/$it" }
    return InfoDict(
        id = videoId,
        title = video.str("name"),
        description = video.str("description"),
        duration = video.number("runtime"),
        uploadDate = ExtractorUtils.unifiedStrdate(video.str("releaseDate")),
        thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
        formats = formats,
        webpageUrl = null,
        extractor = "nba:watch",
        extractorKey = "NBAWatch",
    )
}

private fun queryParam(url: String, name: String): String? =
    Regex("[?&]$name=([^&#]+)").find(url)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

private fun encodeUrlComponent(value: String): String = value
    .replace("%", "%25").replace("&", "%26").replace("?", "%3F").replace("#", "%23")
    .replace(" ", "%20").replace("/", "%2F").replace(":", "%3A")

private fun decodeUrlComponent(value: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                out.append(hex.toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
