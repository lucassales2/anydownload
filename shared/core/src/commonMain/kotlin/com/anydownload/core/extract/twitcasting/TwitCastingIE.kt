/*
 * TwitCasting extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `twitcasting.py` from
 * `yt_dlp/extractor/twitcasting.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `twitcasting.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the movie page scan (data-movie-playlist JSON or reversed base64,
 * data-movie-url, m3u8 sources), the live streamserver HLS rows, the live
 * check, and the user history listing. Password-protected movies fail typed
 * because the port has no video-password option; websocket_frag sources are
 * skipped and an m3u8 URL becomes one HLS row. The port does not carry
 * uploader ids, so they are dropped. No cookie, token, or private URL is
 * stored here.
 */
package com.anydownload.core.extract.twitcasting

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_PAGES = 5
private val M3U8_HEADERS = mapOf("Origin" to "https://twitcasting.tv", "Referer" to "https://twitcasting.tv/")

/** Upstream `TwitCastingIE`: a recorded movie. */
class TwitCastingIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        if (Regex("<form\\s+method=\"POST\">\\s*<input\\s+[^>]+?name=\"password\"").containsMatchIn(webpage)) {
            throw ExtractionError.LoginRequired(
                "This video is protected by a password, and the port has no video-password option.",
            )
        }
        val videoJsData = moviePlaylist(webpage, videoId)?.array("2")
        val title = elementText(webpage, "movietitle")
            ?: metaContent(webpage, "og:title")
            ?: metaContent(webpage, "twitter:title")
        val thumbnail = videoJsData?.firstOrNull()?.let { (it as? JsonObject)?.str("thumbnailUrl") }
            ?: metaContent(webpage, "og:image")
        val description = elementText(webpage, "authorcomment")
            ?: metaContent(webpage, "description")
            ?: metaContent(webpage, "og:description")
            ?: metaContent(webpage, "twitter:description")
        val duration = videoJsData?.let { entries ->
            entries.mapNotNull { (it as? JsonObject)?.number("duration") }
                .takeIf { it.size == entries.size }?.sum()?.div(1000.0)
        } ?: elementText(webpage, "tw-player-duration-time")?.let { parseDuration(it) }
        val viewCount = Regex("Total\\s*:\\s*Views\\s*([\\d,]+)|総視聴者\\s*:\\s*([\\d,]+)\\s*</")
            .find(webpage)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            ?.replace(",", "")?.toLongOrNull()
        val timestamp = Regex("data-toggle=\"true\"[^>]+datetime=\"([^\"]+)\"").find(webpage)
            ?.groupValues?.get(1)
        val isLive = listOf("data-is-onlive=\"true\"", "data-live-type=\"live\"", "data-status=\"online\"")
            .any { it in webpage }
        val base = InfoDict(
            id = videoId,
            title = title,
            description = description,
            duration = duration,
            uploadDate = ExtractorUtils.unifiedStrdate(timestamp),
            viewCount = viewCount,
            isLive = isLive,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            extractor = "twitcasting",
            extractorKey = IE_KEY,
        )
        if (isLive) {
            val streamData = http.downloadJson(
                "https://twitcasting.tv/streamserver.php?target=${match.groups["uploader"]?.value}" +
                    "&mode=client&player=pc_web",
            ) as? JsonObject
            val formats = mutableListOf<MediaFormat>()
            for ((quality, element) in streamData?.obj("tc-hls")?.obj("streams").orEmpty()) {
                val streamUrl = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                formats += MediaFormat(
                    formatId = "hls-$quality",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    preference = QUALITIES[quality],
                    httpHeaders = M3U8_HEADERS,
                )
            }
            if (formats.isEmpty()) {
                throw ExtractionError.LoginRequired()
            }
            return base.copy(formats = formats, webpageUrl = url)
        }
        val m3u8Urls = mutableListOf<String>()
        Regex("data-movie-url=([\"'])((?:(?!\\1).)+)\\1").findAll(webpage)
            .forEach { m3u8Urls += it.groupValues[2] }
        if (m3u8Urls.isEmpty()) {
            for (element in videoJsData.orEmpty()) {
                val sourceUrl = (element as? JsonObject)?.obj("source")?.str("url") ?: continue
                m3u8Urls += sourceUrl
            }
        }
        if (m3u8Urls.isEmpty()) {
            throw ExtractionError.NoFormats("Failed to get m3u8 playlist.")
        }
        if (m3u8Urls.size == 1) {
            return base.copy(
                formats = listOf(
                    MediaFormat(
                        formatId = "hls",
                        url = m3u8Urls.single(),
                        ext = "mp4",
                        protocol = "m3u8_native",
                        httpHeaders = M3U8_HEADERS,
                    ),
                ),
                webpageUrl = url,
            )
        }
        val media = m3u8Urls.mapIndexed { index, m3u8Url ->
            InfoMedia(
                mediaId = "$videoId-$index",
                title = title,
                duration = duration,
                formats = listOf(
                    MediaFormat(
                        url = m3u8Url,
                        ext = "mp4",
                        protocol = "m3u8_native",
                        httpHeaders = M3U8_HEADERS,
                    ),
                ),
            )
        }
        return base.copy(media = media, webpageUrl = url)
    }

    /** Upstream `_parse_data_movie_playlist`: JSON, or reversed base64 JSON. */
    private fun moviePlaylist(webpage: String, videoId: String): JsonObject? {
        val raw = Regex("data-movie-playlist='([^']+?)'").find(webpage)?.groupValues?.get(1)
            ?: return null
        (ExtractorUtils.parseJson(raw) as? JsonObject)?.let { return it }
        val decoded = try {
            kotlin.io.encoding.Base64.Default.decode(raw.reversed()).decodeToString()
        } catch (error: IllegalArgumentException) {
            return null
        }
        return ExtractorUtils.parseJson(decoded) as? JsonObject
    }

    companion object {
        const val IE_KEY: String = "TwitCasting"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/?#]+\\.)?twitcasting\\.tv/(?<uploader>[^/?#]+)/(?:movie|twplayer)/(?<id>\\d+)",
        )

        private val QUALITIES = mapOf("low" to 1, "medium" to 2, "high" to 3)
    }
}

/** Upstream `TwitCastingLiveIE`: a user's current live. */
class TwitCastingLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val uploaderId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val status = try {
            http.downloadJson(
                "https://frontendapi.twitcasting.tv/watch/user/$uploaderId",
                method = "POST",
                body = ByteArray(0),
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        if (status?.boolean("is_live") == false) {
            throw ExtractionError.Unavailable("$uploaderId is not live.")
        }
        val webpage = http.downloadWebpage("https://twitcasting.tv/$uploaderId/show/")
        val isLive = status?.boolean("is_live") == true ||
            Regex("(?s)(<span\\s*class=\"tw-movie-thumbnail2-badge\"\\s*data-status=\"live\">\\s*LIVE)")
                .containsMatchIn(webpage)
        val currentLive = Regex(
            "(?s)<a\\s+class=\"tw-movie-thumbnail2\"\\s+href=\"/[^/\"]+/movie/(\\d+)\"",
        ).find(webpage)?.groupValues?.get(1)
        if (!isLive || currentLive == null) {
            throw ExtractionError.Unavailable("$uploaderId is not live.")
        }
        return InfoDict(
            id = currentLive,
            redirectUrl = "https://twitcasting.tv/$uploaderId/movie/$currentLive",
            webpageUrl = url,
            extractor = "twitcasting:live",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TwitCastingLive"

        val VALID_URL: Regex = Regex("https?://(?:[^/?#]+\\.)?twitcasting\\.tv/(?<id>[^/?#]+)/?(?:[#?]|$)")
    }
}

/** Upstream `TwitCastingUserIE`: a user's live history. */
class TwitCastingUserIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val uploaderId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val baseUrl = "https://twitcasting.tv/$uploaderId/show"
        val entries = mutableListOf<InfoEntry>()
        var nextUrl: String? = baseUrl
        var page = 1
        while (nextUrl != null && page <= MAX_PAGES) {
            val webpage = try {
                http.downloadWebpage("$nextUrl?filter=watchable")
            } catch (error: Exception) {
                break
            }
            for (match in Regex(
                "(?s)<a\\s+class=\"tw-movie-thumbnail2\"\\s+href=\"(/[^/\"]+/movie/\\d+)\"",
            ).findAll(webpage)) {
                entries += InfoEntry(url = urlJoin(baseUrl, match.groupValues[1]))
            }
            val nextPath = Regex("<a href=\"(/${Regex.escape(uploaderId)}/show/\\d+-\\d+)[?\\\"]")
                .find(webpage)?.groupValues?.get(1)
            nextUrl = nextPath?.let { urlJoin(baseUrl, it) }
            page++
        }
        return InfoDict(
            id = uploaderId,
            title = "$uploaderId - Live History",
            entries = entries,
            webpageUrl = url,
            extractor = "twitcasting:user",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TwitCastingUser"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/?#]+\\.)?twitcasting\\.tv/(?<id>[^/?#]+)/(?:show|archive)/?(?:[#?]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun elementText(webpage: String, id: String): String? {
    val match = Regex(
        "(?s)<[^>]+id=[\"']" + Regex.escape(id) + "[\"'][^>]*>(.*?)</",
    ).find(webpage) ?: return null
    val text = match.groupValues[1].replace(Regex("<[^>]*>"), "")
    return ExtractorUtils.unescapeHtml(text)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1].ifBlank { null } }
    }
    return null
}

private fun parseDuration(value: String): Double? {
    val parts = value.trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    var seconds = 0.0
    for (part in parts) {
        val number = part.toDoubleOrNull() ?: return null
        seconds = seconds * 60 + number
    }
    return seconds
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun JsonElement.obj(name: String): JsonObject? = (this as? JsonObject)?.get(name) as? JsonObject

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
