/*
 * Yandex Video / Dzen extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `yandexvideo.py` from
 * `yt_dlp/extractor/yandexvideo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `yandexvideo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public frontend.vh.yandex.ru GraphQL/v23 player JSON, the
 * preview inline-params redirect, and the Dzen SSR data (video streams and
 * channel feeds, five pages eagerly). The DASH DRM streams are recorded as
 * manifests (the port does not fetch DRM licenses); like/dislike counters
 * the port does not carry are dropped. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.yandexvideo

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

/** Upstream `YandexVideoIE`: the portal/player pages. */
class YandexVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val player = try {
            val response = http.downloadJson(
                "https://frontend.vh.yandex.ru/graphql",
                method = "POST",
                body = """{"query":"{ player(content_id: \"$videoId\") { computed_title content_url description duration program_title release_date release_date_ut release_year restriction_age season start_time streams thumbnail title views_count } }"}""".encodeToByteArray(),
            ) as? JsonObject
            (response?.obj("data")?.obj("player"))?.obj("content")
        } catch (error: ExtractionError) {
            null
        } ?: run {
            val fallback = try {
                http.downloadJson(
                    "https://frontend.vh.yandex.ru/v23/player/$videoId.json?stream_options=hires&disable_trackings=1",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            } ?: throw ExtractionError.Malformed("The player API returned no data.")
            fallback.obj("content")
                ?: throw ExtractionError.Malformed("The player API had no content.")
        }
        val formats = mutableListOf<MediaFormat>()
        val streams = player.array("streams").orEmpty().toMutableList()
        player.str("content_url")?.let { streams += JsonPrimitive(it) }
        for (element in streams) {
            val streamUrl = when (element) {
                is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
                is JsonObject -> element.str("url")
                else -> null
            } ?: continue
            when (ExtractorUtils.determineExt(streamUrl)) {
                "ismc" -> Unit
                "m3u8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "mpd" -> formats += MediaFormat(
                    formatId = "dash",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "mpd",
                )

                else -> formats += MediaFormat(url = streamUrl, ext = ExtractorUtils.determineExt(streamUrl))
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The player API returned no playable format.")
        }
        val season = player.obj("season")
        return InfoDict(
            id = videoId,
            title = player.str("title") ?: player.str("computed_title"),
            description = player.str("description"),
            duration = player.number("duration"),
            uploadDate = listOf("release_date", "release_date_ut", "start_time")
                .firstNotNullOfOrNull { player.number(it) }
                ?.let { ExtractorUtils.epochSecondsToDate(it.toLong()) },
            ageLimit = player.number("restriction_age")?.toInt(),
            viewCount = player.number("views_count")?.toLong(),
            channel = player.str("program_title"),
            thumbnails = listOfNotNull(player.str("thumbnail")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "yandexvideo",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "YandexVideo"

        val VALID_URL: Regex = Regex(
            "https?://(?:" +
                "yandex\\.ru(?:/(?:portal/(?:video|efir)|efir))?/?\\?.*?stream_id=|" +
                "frontend\\.vh\\.yandex\\.ru/player/" +
                ")(?<id>(?:[\\da-f]{32}|[\\w-]{12}))",
        )
    }
}

/** Upstream `YandexVideoPreviewIE`: the preview pages. */
class YandexVideoPreviewIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val dataRaw = ExtractorUtils.searchRegex(
            "window\\.Ya\\.__inline_params__\\s*=\\s*JSON\\.parse\\('([^\"]+?\\\\u0022video\\\\u0022:[^\"]+?})'\\);",
            webpage,
            default = null,
        ) ?: throw ExtractionError.Malformed("The preview page had no inline params.")
        val dataJson = ExtractorUtils.parseJson(lowercaseEscape(dataRaw)) as? JsonObject
            ?: throw ExtractionError.Malformed("The preview inline params were not JSON.")
        val videoUrl = dataJson.obj("video")?.str("url")
            ?: throw ExtractionError.Malformed("The preview had no video URL.")
        return InfoDict(
            id = videoId,
            redirectUrl = videoUrl,
            webpageUrl = url,
            extractor = "yandexvideo:preview",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "YandexVideoPreview"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?yandex\\.\\w{2,3}(?:\\.(?:am|ge|il|tr))?/video/preview(?:/?\\?.*?filmId=|/)(?<id>\\d+)",
        )
    }
}

/** Upstream `ZenYandexIE`: a Dzen media/video page. */
class ZenYandexIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val initialId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (videoId, ssrData) = fetchSsrData(http, url, initialId)
        val videoData = ssrData.obj("videoMetaResponse")
            ?: throw ExtractionError.Malformed("The Dzen page had no video metadata.")
        val video = videoData.obj("video")
        val streamUrls = linkedSetOf<String>()
        video?.array("streams").orEmpty().forEach { element ->
            (element as? JsonObject)?.str("url")?.let { streamUrls += it }
        }
        video?.array("mp4Streams").orEmpty().forEach { element ->
            (element as? JsonObject)?.str("url")?.let { streamUrls += it }
        }
        video?.array("oneVideoStreams").orEmpty().forEach { element ->
            (element as? JsonObject)?.str("url")?.let { streamUrls += it }
        }
        val formats = mutableListOf<MediaFormat>()
        for (streamUrl in streamUrls) {
            val ext = ExtractorUtils.determineExt(streamUrl)
            val contentType = queryParam(streamUrl, "ct")
            when {
                ext == "mpd" || contentType == "6" -> formats += MediaFormat(
                    formatId = "dash",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "mpd",
                )

                ext == "m3u8" || contentType == "8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                contentType == "0" -> {
                    val formatType = queryParam(streamUrl, "type")
                    formats += MediaFormat(
                        formatId = formatType,
                        url = streamUrl,
                        ext = "mp4",
                        preference = QUALITY_ORDER.indexOf(formatType).takeIf { it >= 0 }?.plus(1),
                    )
                }

                else -> Unit
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Dzen page returned no playable stream.")
        }
        return InfoDict(
            id = videoId,
            title = videoData.str("title"),
            description = videoData.str("description"),
            duration = video?.number("duration"),
            viewCount = video?.number("views")?.toLong(),
            uploadDate = videoData.number("publicationDate")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            uploader = videoData.obj("source")?.str("title"),
            thumbnails = listOfNotNull(videoData.str("image")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "dzen.ru",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ZenYandex"

        val VALID_URL: Regex = Regex(
            "https?://(zen\\.yandex|dzen)\\.ru(?:/video)?/(media|watch)/" +
                "(?:(?:id/[^/]+/|[^/]+/)(?:[a-z0-9-]+)-)?(?<id>[a-z0-9-]+)",
        )

        private val QUALITY_ORDER = listOf("4", "0", "1", "2", "3", "5", "6", "7")
    }
}

/** Upstream `ZenYandexChannelIE`: a Dzen channel page. */
class ZenYandexChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val initialId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (channelId, ssrData) = fetchSsrData(http, url, initialId)
        val channelData = ssrData.obj("exportResponse")
            ?: throw ExtractionError.Malformed("The channel page had no export data.")
        val entries = mutableListOf<InfoEntry>()
        var feedData = channelData.obj("feedData")
        var page = 0
        while (feedData != null && page < MAX_PAGES) {
            page++
            val items = feedData.array("items").orEmpty()
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val nested = item.array("items")
                if (nested != null) {
                    for (inner in nested) {
                        val entry = inner as? JsonObject ?: continue
                        entry.str("link")?.let { entries += InfoEntry(id = entry.str("id"), title = entry.str("title"), url = it) }
                    }
                } else {
                    item.str("link")?.let { entries += InfoEntry(id = item.str("id"), title = item.str("title"), url = it) }
                }
            }
            val more = feedData.obj("more")?.str("link") ?: break
            feedData = try {
                http.downloadJson(more) as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
        }
        val channel = channelData.obj("channel")?.obj("source")
        return InfoDict(
            id = channelId,
            title = channel?.str("title"),
            description = channel?.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "dzen.ru:channel",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ZenYandexChannel"

        val VALID_URL: Regex = Regex("https?://(zen\\.yandex|dzen)\\.ru/(?!media|video)(?:id/)?(?<id>[a-z0-9-_]+)")
    }
}

// ------------------------------------------------------------------ helpers

private const val MAX_PAGES = 5

private suspend fun fetchSsrData(
    http: ExtractorHttp,
    url: String,
    initialId: String,
): Pair<String, JsonObject> {
    var videoId = initialId
    var webpage = http.downloadWebpage(url)
    val redirect = jsonAfterKey(webpage, "it", anyKey = true)?.str("retpath")
    if (redirect != null) {
        videoId = Regex("(?<id>[a-z0-9-]+)").findAll(redirect).lastOrNull()?.groupValues?.get(1) ?: videoId
        webpage = http.downloadWebpage(redirect)
    }
    val paramsRaw = balancedAfter(webpage, Regex("(?:var|let|const)\\s+_params\\s*=\\s*\\("))
        ?: throw ExtractionError.Malformed("The Dzen page had no params data.")
    val params = ExtractorUtils.parseJson(paramsRaw) as? JsonObject
        ?: throw ExtractionError.Malformed("The Dzen params were not JSON.")
    val ssrData = params.obj("ssrData")
        ?: throw ExtractionError.Malformed("The Dzen params had no ssrData.")
    return Pair(videoId, ssrData)
}

private fun jsonAfterKey(html: String, key: String, anyKey: Boolean = false): JsonObject? {
    val pattern = if (anyKey) {
        Regex("(?:var|let|const)\\s+it\\s*=")
    } else {
        Regex(Regex.escape(key) + "\\s*=")
    }
    val raw = balancedAfter(html, pattern) ?: return null
    return ExtractorUtils.parseJson(raw) as? JsonObject
}

private fun balancedAfter(html: String, marker: Regex): String? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
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
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        i++
    }
    return null
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    return query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")
}

private fun lowercaseEscape(value: String): String =
    value.replace("\\u0022", "\"").replace("\\u0026", "&").replace("\\u0027", "'")

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
