/*
 * Rutube extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `rutube.py` from
 * `yt_dlp/extractor/rutube.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rutube.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public video/options APIs (the private-video query token is
 * forwarded, never stored), the embed path, the m3u8/plain balancer and
 * live HLS formats, caption tracks, and the tags/movie/person/playlist/
 * channel listings (five pages eagerly). f4m formats are skipped; the
 * channel `u/<slug>` redux scan is translated, the `playlists` section is
 * unsupported. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.rutube

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `RutubeIE`: a video page. */
class RutubeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val query = parseQuery(url)
        val info = downloadAndExtractInfo(http, videoId, query)
        val options = downloadApiOptions(http, videoId, query)
        val (formats, subtitles) = extractFormats(options)
        return info.copy(formats = formats, subtitles = subtitles)
    }

    companion object {
        const val IE_KEY: String = "Rutube"

        val VALID_URL: Regex = Regex(
            "https?://rutube\\.ru/(?:(?:live/)?video(?:/private)?|(?:play/)?embed)/(?<id>[\\da-z]{32})",
        )
    }
}

/** Upstream `RutubeEmbedIE`: a numeric embed id. */
class RutubeEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val embedId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val query = parseQuery(url)
        val options = downloadApiOptions(http, embedId, query)
        val videoId = options.str("effective_video")
            ?: throw ExtractionError.Malformed("The embed options had no effective video.")
        val (formats, subtitles) = extractFormats(options)
        return downloadAndExtractInfo(http, videoId, query).copy(
            formats = formats,
            subtitles = subtitles,
        )
    }

    companion object {
        const val IE_KEY: String = "RutubeEmbed"

        val VALID_URL: Regex = Regex("https?://rutube\\.ru/(?:video|play)/embed/(?<id>[0-9]+)(?:[?#/]|$)")
    }
}

/** Upstream `RutubeTagsIE`: tag listings. */
class RutubeTagsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractPlaylist(http, url, "https://rutube.ru/api/tags/video/%s/?page=%s&format=json", IE_KEY)

    companion object {
        const val IE_KEY: String = "RutubeTags"

        val VALID_URL: Regex = Regex("https?://rutube\\.ru/tags/video/(?<id>\\d+)")
    }
}

/** Upstream `RutubeMovieIE`: movie listings. */
class RutubeMovieIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val movieId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val movie = try {
            http.downloadJson("https://rutube.ru/api/metainfo/tv/$movieId/?format=json") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val playlist = extractPlaylist(
            http,
            url,
            "https://rutube.ru/api/metainfo/tv/%s/video?page=%s&format=json",
            IE_KEY,
        )
        return playlist.copy(title = movie?.str("name"))
    }

    companion object {
        const val IE_KEY: String = "RutubeMovie"

        val VALID_URL: Regex = Regex("https?://rutube\\.ru/metainfo/tv/(?<id>\\d+)")
    }
}

/** Upstream `RutubePersonIE`: person video listings. */
class RutubePersonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractPlaylist(http, url, "https://rutube.ru/api/video/person/%s/?page=%s&format=json", IE_KEY)

    companion object {
        const val IE_KEY: String = "RutubePerson"

        val VALID_URL: Regex = Regex("https?://rutube\\.ru/video/person/(?<id>\\d+)")
    }
}

/** Upstream `RutubePlaylistIE`: custom playlists. */
class RutubePlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractPlaylist(http, url, "https://rutube.ru/api/playlist/custom/%s/videos?page=%s&format=json", IE_KEY)

    companion object {
        const val IE_KEY: String = "RutubePlaylist"

        val VALID_URL: Regex = Regex("https?://rutube\\.ru/plst/(?<id>\\d+)")
    }
}

/** Upstream `RutubeChannelIE`: channel listings. */
class RutubeChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var playlistId = match.groups["id"]?.value
        val slug = match.groups["slug"]?.value
        val section = match.groups["section"]?.value
        if (section == "playlists") throw ExtractionError.UnsupportedUrl()
        if (playlistId == null && slug != null) {
            val webpage = http.downloadWebpage(url)
            val raw = jsonAfterKey(webpage, "window.reduxState")?.toString() ?: webpage
            playlistId = Regex("\"channel_id\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)
                ?: throw ExtractionError.Malformed("The channel page had no channel id.")
        }
        val id = playlistId ?: throw ExtractionError.UnsupportedUrl()
        val originType = when (section) {
            "videos" -> "rtb,rst,ifrm,rspa"
            "shorts" -> "rshorts"
            else -> ""
        }
        val template = "https://rutube.ru/api/video/person/%s/?page=%s&format=json&origin__type=$originType"
        val playlist = extractPlaylist(http, url, template, IE_KEY, idOverride = id)
        return if (section != null) {
            playlist.copy(id = "${id}_$section")
        } else {
            playlist.copy(id = id)
        }
    }

    companion object {
        const val IE_KEY: String = "RutubeChannel"

        val VALID_URL: Regex = Regex(
            "https?://rutube\\.ru/(?:channel/(?<id>\\d+)|u/(?<slug>\\w+))(?:/(?<section>videos|shorts|playlists))?",
        )
    }
}

// ------------------------------------------------------------------ helpers

private const val MAX_PAGES = 5

private suspend fun downloadApiInfo(
    http: ExtractorHttp,
    videoId: String,
    query: Map<String, String>,
): JsonObject {
    val suffix = querySuffix(query)
    return http.downloadJson("https://rutube.ru/api/video/$videoId/?format=json$suffix") as? JsonObject
        ?: throw ExtractionError.Malformed("The video API was not an object.")
}

private suspend fun downloadApiOptions(
    http: ExtractorHttp,
    videoId: String,
    query: Map<String, String>,
): JsonObject {
    val suffix = querySuffix(query)
    return http.downloadJson("https://rutube.ru/api/play/options/$videoId/?format=json$suffix") as? JsonObject
        ?: throw ExtractionError.Malformed("The options API was not an object.")
}

private fun querySuffix(query: Map<String, String>): String =
    query.entries.joinToString("") { "&${it.key}=${it.value}" }

private suspend fun downloadAndExtractInfo(
    http: ExtractorHttp,
    videoId: String,
    query: Map<String, String>,
): InfoDict {
    val video = downloadApiInfo(http, videoId, query)
    val ageLimit = video.bool("is_adult")?.let { if (it) 18 else 0 }
    return InfoDict(
        id = video.str("id") ?: videoId,
        title = video.str("title"),
        description = video.str("description"),
        duration = video.number("duration"),
        uploadDate = ExtractorUtils.unifiedStrdate(video.str("created_ts")),
        uploader = video.obj("author")?.str("name"),
        viewCount = video.number("hits")?.toLong(),
        ageLimit = ageLimit,
        isLive = video.bool("is_livestream"),
        thumbnails = listOfNotNull(video.str("thumbnail_url")?.let { Thumbnail(url = it) }),
        webpageUrl = "https://rutube.ru/video/$videoId/",
        extractor = "rutube",
        extractorKey = "Rutube",
    )
}

private fun extractFormats(options: JsonObject): Pair<List<MediaFormat>, List<SubtitleTrack>> {
    val formats = mutableListOf<MediaFormat>()
    for ((formatId, value) in options.obj("video_balancer").orEmpty()) {
        val formatUrl = (value as? JsonPrimitive)?.content ?: continue
        when (ExtractorUtils.determineExt(formatUrl)) {
            "m3u8" -> formats += MediaFormat(
                formatId = formatId,
                url = formatUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )

            "f4m" -> Unit // f4m is skipped: the port has no f4m helper.
            else -> formats += MediaFormat(
                formatId = formatId,
                url = formatUrl,
                ext = ExtractorUtils.determineExt(formatUrl),
            )
        }
    }
    for (element in options.obj("live_streams")?.array("hls").orEmpty()) {
        val hlsUrl = (element as? JsonObject)?.str("url") ?: continue
        formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
    }
    if (formats.isEmpty()) {
        throw ExtractionError.NoFormats("The options API returned no playable format.")
    }
    val subtitles = mutableListOf<SubtitleTrack>()
    for (element in options.array("captions").orEmpty()) {
        val caption = element as? JsonObject ?: continue
        val file = caption.str("file") ?: continue
        subtitles += SubtitleTrack(
            language = caption.str("code") ?: "ru",
            name = caption.str("langTitle"),
            formats = listOf(SubtitleFormat(ext = ExtractorUtils.determineExt(file), url = file)),
        )
    }
    return Pair(formats, subtitles)
}

private suspend fun extractPlaylist(
    http: ExtractorHttp,
    url: String,
    template: String,
    ieKey: String,
    idOverride: String? = null,
): InfoDict {
    val playlistId = idOverride
        ?: Regex("(\\d+)").findAll(url).lastOrNull()?.groupValues?.get(1)
        ?: throw ExtractionError.UnsupportedUrl()
    val entries = mutableListOf<InfoEntry>()
    var page = 1
    while (page <= MAX_PAGES) {
        val pageUrl = templateFill(template, playlistId, page.toString())
        val response = try {
            http.downloadJson(pageUrl) as? JsonObject
        } catch (error: ExtractionError) {
            null
        } ?: break
        val results = response.array("results").orEmpty()
        if (results.isEmpty()) break
        for (element in results) {
            val result = element as? JsonObject ?: continue
            val videoUrl = result.str("video_url") ?: continue
            entries += InfoEntry(
                id = result.str("id"),
                title = result.str("title"),
                url = videoUrl,
            )
        }
        if (response.bool("has_next") != true || response.str("next") == null) break
        page++
    }
    return InfoDict(
        id = playlistId,
        entries = entries,
        webpageUrl = url,
        extractor = "rutube:playlist",
        extractorKey = ieKey,
    )
}

private fun templateFill(template: String, vararg values: String): String {
    var out = template
    for (value in values) out = out.replaceFirst("%s", value)
    return out
}

private fun parseQuery(url: String): Map<String, String> {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=')
        if (key.isEmpty()) null else key to pair.substringAfter('=', "")
    }.toMap()
}

private fun jsonAfterKey(html: String, key: String): JsonObject? {
    val marker = Regex(Regex.escape(key) + "\\s*=").find(html) ?: return null
    val start = html.indexOf('{', marker.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return ExtractorUtils.parseJson(html.substring(start, i + 1)) as? JsonObject
                    }
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

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let {
        when (it.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
