/*
 * TED extractors — AnyDownload
 *
 * Kotlin translation of `ted.py` from `yt_dlp/extractor/ted.py` at upstream
 * tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ted.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `__NEXT_DATA__` page props (talk, series, playlist), the
 * `playerData` resources (one HLS row, the h264 rows with bitrate ids, the
 * audio row, the simplified http cross-fill), the subtitle-free m3u8 path,
 * the external-embed child entry, and the embed host rewrite. Limitations:
 * the port has no RTMP support, so the `rtmp` resource rows are skipped; the
 * m3u8 variants and their subtitles are not parsed (one row per manifest),
 * so the `http_url` cross-fill cannot exclude audio-only variants; the
 * `release_date` and `tags` fields are not modeled and are dropped; the
 * `#season_N` filter compares string forms. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.ted

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val VALID_URL_BASE = "https?://www\\.ted\\.com/(?:%s)(?:/lang/[^/#?]+)?/(?<id>[\\w-]+)"

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

/** Upstream `TedBaseIE`: the Next.js props and the playlist node walk. */
abstract class TedBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_search_nextjs_data`. */
    protected fun searchNextJsData(html: String): JsonObject? =
        NEXT_DATA.find(html)?.groupValues?.get(1)?.let { ExtractorUtils.parseJson(it) as? JsonObject }

    /** Upstream `_parse_playlist`: the canonical video URLs of one playlist. */
    protected fun parsePlaylist(playlist: JsonObject?): List<InfoEntry> {
        val nodes = playlist?.obj("videos")?.array("nodes") ?: return emptyList()
        return nodes.mapNotNull { element ->
            val node = element as? JsonObject ?: return@mapNotNull null
            if (node.str("__typename") != "Video") return@mapNotNull null
            val canonicalUrl = node.str("canonicalUrl") ?: return@mapNotNull null
            InfoEntry(id = canonicalUrl.trimEnd('/').substringAfterLast('/'), url = canonicalUrl)
        }
    }

    protected fun pageProps(webpage: String): JsonObject =
        searchNextJsData(webpage)?.obj("props")?.obj("pageProps")
            ?: throw ExtractionError.Malformed("The TED page carried no Next.js props.")
}

/** Upstream `TedTalkIE`: a talk page. */
class TedTalkIE(
    http: ExtractorHttp,
) : TedBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val talkInfo = pageProps(webpage).obj("videoData")
            ?: throw ExtractionError.Malformed("The TED talk page carried no video data.")
        val videoId = talkInfo.str("id")
            ?: throw ExtractionError.Malformed("The TED talk carried no id.")
        val playerData = ExtractorUtils.parseJson(talkInfo.str("playerData")) as? JsonObject
            ?: JsonObject(emptyMap())

        val formats = mutableListOf<MediaFormat>()
        var httpUrl: String? = null
        for ((formatId, value) in playerData.obj("resources").orEmpty()) {
            if (formatId == "hls") {
                val streamUrl = (value as? JsonObject)?.str("stream") ?: continue
                formats += MediaFormat(
                    formatId = formatId,
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                continue
            }
            val resources = value as? JsonArray ?: continue
            if (formatId == "h264") {
                for (element in resources) {
                    val resource = element as? JsonObject ?: continue
                    val h264Url = resource.str("file") ?: continue
                    val bitrate = resource.number("bitrate")?.toLong()
                    formats += MediaFormat(
                        url = h264Url,
                        formatId = "h264-${bitrate}k",
                        tbr = bitrate?.toDouble(),
                    )
                    if (Regex("\\d+k").containsMatchIn(h264Url)) httpUrl = h264Url
                }
            }
            // Upstream `rtmp` rows are skipped: the port has no RTMP support.
        }

        // Upstream `http_url` cross-fill; the port has one HLS row per manifest,
        // so audio-only variants cannot be excluded.
        if (httpUrl != null) {
            val hlsFormats = formats.filter { it.protocol == "m3u8_native" }
            for (hlsFormat in hlsFormats) {
                val bitrate = ExtractorUtils.searchRegex("(\\d+k)", hlsFormat.url ?: "")
                    ?: continue
                val bitrateUrl = Regex("\\d+k").replace(httpUrl!!, bitrate)
                formats += MediaFormat(
                    formatId = hlsFormat.formatId?.replace("hls", "http"),
                    url = bitrateUrl,
                    ext = hlsFormat.ext,
                    protocol = "http",
                )
            }
        }

        talkInfo.str("audioDownload")?.let { audioDownload ->
            formats += MediaFormat(formatId = "audio", url = audioDownload, vcodec = "none")
        }

        if (formats.isEmpty()) {
            val external = playerData.obj("external") ?: JsonObject(emptyMap())
            val service = external.str("service") ?: ""
            val target = (if (service.lowercase() == "youtube") external.str("code") else null)
                ?: external.str("uri")
                ?: throw ExtractionError.NoFormats("This TED talk has no downloadable formats.")
            // Upstream `url_result`: the port expands one child entry.
            return InfoDict(
                id = videoId,
                title = talkInfo.str("title") ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
                entries = listOf(InfoEntry(id = videoId, url = target)),
                webpageUrl = url,
                extractor = "ted:talk",
                extractorKey = ieKey,
            )
        }

        val thumbnail = (playerData.str("thumb") ?: ExtractorUtils.htmlSearchMeta(webpage, "og:image"))
            ?.substringBefore('?')

        return InfoDict(
            id = videoId,
            title = talkInfo.str("title") ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            uploader = talkInfo.str("presenterDisplayName"),
            thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            description = talkInfo.str("description")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            formats = formats,
            duration = talkInfo.number("duration")
                ?: ExtractorUtils.parseDuration(ExtractorUtils.htmlSearchMeta(webpage, "video:duration")),
            viewCount = strToInt(talkInfo["viewedCount"]),
            uploadDate = talkInfo.str("publishedAt")?.let(ExtractorUtils::unifiedStrdate),
            webpageUrl = url,
            extractor = "ted:talk",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TED"

        val VALID_URL: Regex = Regex(VALID_URL_BASE.replace("%s", "talks"))
    }
}

/** Upstream `TedSeriesIE`: a series page with optional `#season_N`. */
class TedSeriesIE(
    http: ExtractorHttp,
) : TedBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val season = match.groups["season"]?.value
        val webpage = http.downloadWebpage(url)
        val info = pageProps(webpage)

        val entries = mutableListOf<InfoEntry>()
        for (element in info.array("seasons").orEmpty()) {
            val seasonInfo = element as? JsonObject ?: continue
            if (season != null && seasonInfo.str("seasonNumber") != season) continue
            entries += parsePlaylist(seasonInfo)
        }

        val seriesId = info.obj("series")?.str("id")
        val seriesName = info.obj("series")?.str("name")
            ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")
        return InfoDict(
            id = if (season != null && seriesId != null) "${seriesId}_$season" else seriesId,
            title = if (season != null) "$seriesName Season $season" else seriesName,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            entries = entries,
            webpageUrl = url,
            extractor = "ted:series",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TEDSeries"

        val VALID_URL: Regex = Regex(
            VALID_URL_BASE.replace("%s", "series") + "(?:#season_(?<season>\\d+))?",
        )
    }
}

/** Upstream `TedPlaylistIE`: a curated playlist page. */
class TedPlaylistIE(
    http: ExtractorHttp,
) : TedBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playlist = pageProps(webpage).obj("playlist")
            ?: throw ExtractionError.Malformed("The TED playlist page carried no playlist.")
        val title = playlist.str("title")
            ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")?.replace(" | TED Talks", "")
        return InfoDict(
            id = playlist.str("id") ?: displayId,
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            entries = parsePlaylist(playlist),
            webpageUrl = url,
            extractor = "ted:playlist",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TEDPlaylist"

        val VALID_URL: Regex = Regex(VALID_URL_BASE.replace("%s", "playlists(?:/\\d+)?"))
    }
}

/** Upstream `TedEmbedIE`: the embed host rewrite to the talk page. */
class TedEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        // Upstream `url_result(re.sub(r'://embed(-ssl)?', '://www', url))`.
        val rewritten = Regex("://embed(-ssl)?").replaceFirst(url, "://www")
        return TedTalkIE(http).extract(rewritten).copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "TEDEmbed"

        val VALID_URL: Regex = Regex("https?://embed(?:-ssl)?\\.ted\\.com/")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `str_to_int` subset: commas and K/M/B suffixes. */
private fun strToInt(value: JsonElement?): Long? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    if (!primitive.isString) return primitive.content.toDoubleOrNull()?.toLong()
    val text = primitive.content.replace(",", "").trim()
    if (text.isEmpty()) return null
    val multiplier = when (text.last().uppercaseChar()) {
        'K' -> 1_000L
        'M' -> 1_000_000L
        'B' -> 1_000_000_000L
        else -> 1L
    }
    val number = if (multiplier == 1L) text.toDoubleOrNull() else text.dropLast(1).toDoubleOrNull()
    return number?.let { (it * multiplier).toLong() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
