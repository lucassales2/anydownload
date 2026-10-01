/*
 * TuneIn extractors — AnyDownload
 *
 * Kotlin translation of the public API/page subset of `tunein.py` from
 * `yt_dlp/extractor/tunein.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tunein.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the station radio page, the podcast program listing, the podcast
 * episode, the embed-player redirect, and the tun.in shortener. Streams come
 * from the public Tune.ashx JSON and metadata from the public profiles JSON;
 * an m3u8 stream becomes one HLS row, so its subtitle tracks are not parsed.
 * The port does not carry alt_title, channel_follower_count, location,
 * series/series_id (mapped to channel/channel_id), cast, display_id, or the
 * upstream timestamp field (mapped to upload_date). No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.tunein

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

private const val PROFILES_BASE = "https://api.tunein.com/profiles"
private const val TUNE_URL = "https://opml.radiotime.com/Tune.ashx"
private const val PODCAST_PAGE_SIZE = 20
private const val MAX_PAGES = 5

/**
 * Upstream `TuneInBaseIE`: the shared profiles API and Tune.ashx stream
 * helpers. The profiles call is non-fatal like upstream; the Tune.ashx call
 * is fatal, so a station with no stream response fails typed.
 */
abstract class TuneInBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun callApi(
        itemId: String,
        endpoint: String? = null,
        query: Map<String, String> = emptyMap(),
    ): JsonObject {
        val url = buildString {
            append(PROFILES_BASE).append('/').append(itemId)
            if (!endpoint.isNullOrBlank()) append('/').append(endpoint)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { (key, value) ->
                    "${percentEncode(key)}=${percentEncode(value)}"
                })
            }
        }
        return try {
            http.downloadJson(url) as? JsonObject ?: JsonObject(emptyMap())
        } catch (error: ExtractionError) {
            JsonObject(emptyMap())
        }
    }

    protected suspend fun extractFormatsAndSubtitles(contentId: String): List<MediaFormat> {
        val streams = http.downloadJson(
            TUNE_URL + "?formats=" + percentEncode("mp3,aac,ogg,flash,hls") +
                "&id=" + percentEncode(contentId) + "&render=json",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The TuneIn stream response was not an object.")
        val formats = mutableListOf<MediaFormat>()
        for (element in streams.array("body").orEmpty()) {
            val stream = element as? JsonObject ?: continue
            val streamUrl = stream.str("url") ?: continue
            val mediaType = stream.str("media_type")
            if (mediaType == "hls") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = protoRelativeUrl(streamUrl),
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            } else {
                formats += MediaFormat(
                    formatId = mediaType,
                    url = protoRelativeUrl(streamUrl),
                    ext = mediaType,
                    abr = stream.number("bitrate"),
                )
            }
        }
        return formats
    }
}

/** Upstream `TuneInStationIE`: a live radio station page. */
class TuneInStationIE(
    http: ExtractorHttp,
) : TuneInBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val stationId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val formats = extractFormatsAndSubtitles(stationId)
        val item = callApi(stationId).obj("Item")
        val play = item?.obj("Actions")?.obj("Play")
        return InfoDict(
            id = stationId,
            title = cleanHtml(item?.str("Title")),
            description = cleanHtml(item?.str("Description")),
            isLive = play?.bool("IsLive"),
            thumbnails = listOfNotNull(protoUrl(item?.str("Image"))?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "tunein:station",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TuneInStation"

        val VALID_URL: Regex = Regex(
            "https?://tunein\\.com/radio/[^/?#]+(?<id>s\\d+)",
        )
    }
}

/** Upstream `TuneInPodcastIE`: a paged program listing. */
class TuneInPodcastIE(
    http: ExtractorHttp,
) : TuneInBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        if (TuneInPodcastEpisodeIE.VALID_URL.containsMatchIn(url)) false else super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val podcastId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        // Upstream OnDemandPagedList starts at page 0 and stops on a short page.
        var page = 0
        while (page < MAX_PAGES) {
            val contents = callApi(
                podcastId,
                "contents",
                mapOf(
                    "filter" to "t:free",
                    "limit" to PODCAST_PAGE_SIZE.toString(),
                    "offset" to (page * PODCAST_PAGE_SIZE).toString(),
                ),
            )
            val guideIds = contents.array("Items").orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("GuideId") }
            if (guideIds.isEmpty()) break
            for (guideId in guideIds) {
                entries += InfoEntry(
                    url = updateUrlQuery(url, "topicId", guideId.substring(1)),
                )
            }
            if (guideIds.size < PODCAST_PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = podcastId,
            title = callApi(podcastId).obj("Item")?.str("Title"),
            entries = entries,
            webpageUrl = url,
            extractor = "tunein:podcast:program",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TuneInPodcast"

        val VALID_URL: Regex = Regex(
            "https?://tunein\\.com/podcasts(?:/[^/?#]+){1,2}(?<id>p\\d+)",
        )
    }
}

/** Upstream `TuneInPodcastEpisodeIE`: one podcast episode via its topic id. */
class TuneInPodcastEpisodeIE(
    http: ExtractorHttp,
) : TuneInBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val seriesId = match.groups["seriesid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val episodeId = "t$displayId"
        val formats = extractFormatsAndSubtitles(episodeId)
        val seriesTitle = cleanHtml(callApi(seriesId).obj("Item")?.str("Title"))
        val item = callApi(episodeId).obj("Item")
        val play = item?.obj("Actions")?.obj("Play")
        return InfoDict(
            id = episodeId,
            title = cleanHtml(item?.str("Title")),
            description = cleanHtml(item?.str("Description")),
            duration = play?.number("Duration"),
            channel = seriesTitle,
            channelId = seriesId,
            uploadDate = ExtractorUtils.unifiedStrdate(play?.str("PublishTime")),
            thumbnails = listOfNotNull(protoUrl(item?.str("Image"))?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "tunein:podcast",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TuneInPodcastEpisode"

        // Upstream `(?i:topicid)`: the whole pattern is case-insensitive here.
        val VALID_URL: Regex = Regex(
            "https?://tunein\\.com/podcasts(?:/[^/?#]+){1,2}(?<seriesid>p\\d+)" +
                "/?\\?(?:[^#]+&)?topicid=(?<id>\\d+)",
            RegexOption.IGNORE_CASE,
        )
    }
}

/** Upstream `TuneInEmbedIE`: the embed player re-dispatches to a page URL. */
class TuneInEmbedIE(
    http: ExtractorHttp,
) : TuneInBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val embedId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val kind = when (embedId.firstOrNull()) {
            'p' -> "program"
            's' -> "station"
            't' -> "topic"
            else -> throw ExtractionError.UnsupportedUrl()
        }
        return InfoDict(
            id = embedId,
            redirectUrl = "https://tunein.com/$kind/?${kind}id=${embedId.substring(1)}",
            webpageUrl = url,
            extractor = "tunein:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TuneInEmbed"

        val VALID_URL: Regex = Regex(
            "https?://tunein\\.com/embed/player/(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `TuneInShortenerIE`: tun.in redirects to a canonical page. */
class TuneInShortenerIE(
    http: ExtractorHttp,
) : TuneInBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val redirectId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // The server does not support HEAD requests, mirroring upstream.
        val resolved = stripPort(http.followRedirects(url))
        // Prevent an infinite loop when the redirect fails.
        if (suitable(resolved)) throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = redirectId,
            redirectUrl = resolved,
            webpageUrl = url,
            extractor = "tunein:shortener",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TuneInShortener"

        val VALID_URL: Regex = Regex(
            "https?://tun\\.in/(?<id>[^/?#]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `clean_html`: tags stripped, entities decoded, whitespace collapsed. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Upstream `_proto_relative_url`: `//host/...` becomes `https://host/...`. */
private fun protoRelativeUrl(value: String): String =
    if (value.startsWith("//")) "https:$value" else value

private fun protoUrl(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let(::protoRelativeUrl)

/**
 * Upstream `update_url_query` for one key: the existing query is kept and the
 * key is replaced in place or appended.
 */
private fun updateUrlQuery(url: String, name: String, value: String): String {
    val fragment = url.substringAfter('#', "")
    val withoutFragment = url.substringBefore('#')
    val path = withoutFragment.substringBefore('?')
    val query = withoutFragment.substringAfter('?', "")
    val params = if (query.isEmpty()) mutableListOf() else query.split('&').toMutableList()
    val encoded = "$name=${percentEncode(value)}"
    val index = params.indexOfFirst { it == name || it.startsWith("$name=") }
    if (index >= 0) params[index] = encoded else params.add(encoded)
    val rebuilt = if (params.isEmpty()) path else path + "?" + params.joinToString("&")
    return if (fragment.isEmpty()) rebuilt else "$rebuilt#$fragment"
}

/** Upstream `urllib.parse.urlparse(...)._replace(netloc=hostname)`: the port is dropped. */
private fun stripPort(url: String): String {
    val match = Regex("^(https?://)([^/?#]+)(.*)$").find(url) ?: return url
    val authority = match.groupValues[2]
    val host = authority.substringAfterLast('@').substringBefore(':')
    return match.groupValues[1] + host + match.groupValues[3]
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/** Percent-encoding with the RFC 3986 unreserved set, like Python `quote_plus`. */
private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[code shr 4])
            append(HEX_DIGITS[code and 0x0F])
        }
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
