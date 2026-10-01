/*
 * Yahoo extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `yahoo.py` from
 * `yt_dlp/extractor/yahoo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `yahoo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public caas content article API, the video-api.yql stream API
 * (webm/mp4/hls rows and closed captions), the story/iframe playlist scan,
 * and the Yahoo Japan News preloaded-state + feapi path (public app id and
 * a space-id MD5, not a user credential). The `yvsearch:` search key is not
 * routed by the engine, so `YahooSearchIE` is planned. The port does not
 * carry series/display_id fields, so they are dropped. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.yahoo

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
import com.anydownload.core.extract.md5Hex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val STREAMS_API = "https://video-api.yql.yahoo.com/v1/video/sapi/streams/"

/** Upstream `YahooIE`: a Yahoo video or story page. */
class YahooIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val country = match.groups["country"]?.value?.split('-')?.first() ?: "us"
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val articleUrl = if (country == "malaysia") "my" else country
        val item = try {
            http.downloadJson(
                "https://$articleUrl.yahoo.com/caas/content/article?url=${encodeUrl(url)}",
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }?.array("items")?.firstOrNull()?.let { (it as? JsonObject)?.obj("data")?.obj("partnerData") }
            ?: throw ExtractionError.Malformed("The Yahoo content API returned no item.")

        if (item.str("type") != "video") {
            val entries = mutableListOf<InfoEntry>()
            val cover = item.obj("cover")
            if (cover?.str("type") == "yvideo") {
                cover.str("url")?.let { entries += InfoEntry(id = cover.str("uuid"), url = it) }
            }
            for (element in item.array("body").orEmpty()) {
                val body = element as? JsonObject ?: continue
                if (body.str("type") == "videoIframe") {
                    body.str("url")?.let { entries += InfoEntry(url = it) }
                }
            }
            if (item.str("type") == "storywithleadvideo") {
                item.obj("meta")?.obj("player")?.str("url")?.let { entries += InfoEntry(url = it) }
            }
            return InfoDict(
                id = item.str("uuid") ?: displayId,
                title = item.str("title"),
                description = item.str("summary"),
                entries = entries,
                webpageUrl = url,
                extractor = "yahoo",
                extractorKey = IE_KEY,
            )
        }
        val videoId = item.str("uuid") ?: displayId
        return extractYahooVideo(videoId, country).copy(webpageUrl = url, extractorKey = IE_KEY)
    }

    private suspend fun extractYahooVideo(videoId: String, country: String): InfoDict {
        val meta = try {
            http.downloadJson("$STREAMS_API$videoId")
        } catch (error: ExtractionError) {
            null
        }?.obj("query")?.obj("results")?.array("mediaObj")?.firstOrNull()
            ?.let { (it as? JsonObject)?.obj("meta") }
            ?: throw ExtractionError.Malformed("The Yahoo stream API returned no media object.")
        val isLive = meta.boolean("uplynk_live") == true
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        val seenCaptionUrls = mutableSetOf<String>()
        var statusMessage: String? = null
        for (format in if (isLive) listOf("m3u8") else listOf("webm", "mp4")) {
            val mediaObj = try {
                http.downloadJson(
                    "$STREAMS_API$videoId?format=$format&region=${country.uppercase()}",
                )
            } catch (error: ExtractionError) {
                continue
            }.obj("query")?.obj("results")?.array("mediaObj")?.firstOrNull() as? JsonObject ?: continue
            statusMessage = mediaObj.obj("status")?.str("msg") ?: statusMessage
            for (element in mediaObj.array("streams").orEmpty()) {
                val stream = element as? JsonObject ?: continue
                val host = stream.str("host") ?: continue
                val path = stream.str("path") ?: continue
                val streamUrl = host + path
                if (stream.str("format") == "m3u8") {
                    formats += MediaFormat(
                        formatId = "hls",
                        url = streamUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                    continue
                }
                val tbr = stream.number("bitrate")
                formats += MediaFormat(
                    formatId = listOfNotNull(format, tbr?.toLong()?.toString()).joinToString("-"),
                    url = streamUrl,
                    width = stream.number("width")?.toLong(),
                    height = stream.number("height")?.toLong(),
                    tbr = tbr,
                    fps = stream.number("framerate"),
                )
            }
            for (element in mediaObj.array("closedcaptions").orEmpty()) {
                val cc = element as? JsonObject ?: continue
                val ccUrl = cc.str("url") ?: continue
                if (!seenCaptionUrls.add(ccUrl)) continue
                subtitles += SubtitleTrack(
                    language = cc.str("lang") ?: "en-US",
                    formats = listOf(
                        SubtitleFormat(
                            ext = ExtractorUtils.mimetype2ext(cc.str("content_type")) ?: "vtt",
                            url = ccUrl,
                        ),
                    ),
                )
            }
        }
        if (formats.isEmpty() && statusMessage == "geo restricted") {
            throw ExtractionError.GeoRestricted(listOf("us"))
        }
        return InfoDict(
            id = videoId,
            title = cleanHtml(meta.str("title")),
            description = cleanHtml(meta.str("description")),
            duration = meta.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(meta.str("publish_time")),
            viewCount = meta.number("view_count")?.toLong(),
            isLive = isLive,
            thumbnails = listOfNotNull(
                meta.str("thumbnail")?.replace("^http://".toRegex(), "https://")
                    ?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            subtitles = subtitles,
            extractor = "yahoo",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Yahoo"

        val VALID_URL: Regex = Regex(
            "(?<url>https?://(?:(?<country>[a-zA-Z]{2}(?:-[a-zA-Z]{2})?|malaysia)\\.)?" +
                "(?:[\\da-zA-Z_-]+\\.)?yahoo\\.com/(?:[^/]+/)*(?<id>[^?\u0026#]*-[0-9]+(?:-[a-z]+)?)\\.html)",
        )
    }
}

/** Upstream `YahooJapanNewsIE`: a Yahoo Japan News article. */
class YahooJapanNewsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val marker = Regex("__PRELOADED_STATE__\\s*=").find(webpage)
            ?: throw ExtractionError.Malformed("The Yahoo Japan page had no preloaded state.")
        val state = balancedAfter(webpage, marker.range.last + 1)
            ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
            ?: throw ExtractionError.Malformed("The Yahoo Japan preloaded state was not JSON.")
        var contentId: Long? = null
        for (paragraphElement in state.obj("articleDetail")?.array("paragraphs").orEmpty()) {
            val paragraph = paragraphElement as? JsonObject ?: continue
            for (objectElement in paragraph.array("objectItems").orEmpty()) {
                val objectItem = objectElement as? JsonObject ?: continue
                val vid = objectItem.obj("video")?.number("vid")?.toLong()
                if (vid != null) {
                    contentId = vid
                    break
                }
            }
            if (contentId != null) break
        }
        val id = contentId
            ?: throw ExtractionError.Unavailable("This article does not contain a video")
        val host = "news.yahoo.co.jp"
        val spaceId = state.obj("pageData")?.str("spaceId")
        val ak = if (spaceId != null) {
            md5Hex("${spaceId}_$host".encodeToByteArray())
        } else {
            ""
        }
        val jsonData = http.downloadJson(
            "https://feapi-yvpub.yahooapis.jp/v1/content/$id?appid=" +
                "dj0zaiZpPVZMTVFJR0FwZWpiMyZzPWNvbnN1bWVyc2VjcmV0Jng9YjU-" +
                "&output=json&domain=$host&ak=$ak&device_type=1100",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Yahoo Japan content API returned no object.")
        val formats = mutableListOf<MediaFormat>()
        for (resultElement in jsonData.obj("ResultSet")?.array("Result").orEmpty()) {
            val result = resultElement as? JsonObject ?: continue
            for (videoElement in result.obj("VideoUrlSet")?.array("VideoUrl").orEmpty()) {
                val video = videoElement as? JsonObject ?: continue
                val delivery = video.str("delivery") ?: continue
                val videoUrl = video.str("Url") ?: continue
                if (delivery == "hls") {
                    formats += MediaFormat(
                        formatId = "hls",
                        url = videoUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                } else {
                    val bitrate = video.number("bitrate")
                    formats += MediaFormat(
                        formatId = listOfNotNull("http", bitrate?.toLong()?.toString())
                            .joinToString("-"),
                        url = videoUrl,
                        height = video.number("height")?.toLong(),
                        width = video.number("width")?.toLong(),
                        tbr = bitrate,
                    )
                }
            }
        }
        val title = state.obj("articleDetail")?.str("headline")
            ?: state.obj("pageData")?.obj("pageParam")?.str("title")
            ?: metaContent(webpage, "og:title")
            ?: metaContent(webpage, "twitter:title")
        val description = state.obj("pageData")?.str("description")
            ?: metaContent(webpage, "og:description")
            ?: metaContent(webpage, "description")
            ?: metaContent(webpage, "twitter:description")
        val thumbnail = state.obj("pageData")?.str("ogpImage")
            ?: metaContent(webpage, "og:image")
            ?: metaContent(webpage, "twitter:image")
        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats.distinctBy { it.url },
            webpageUrl = url,
            extractor = "yahoo:japannews",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "YahooJapanNews"

        val VALID_URL: Regex = Regex("https?://news\\.yahoo\\.co\\.jp/(?:articles|feature)/(?<id>[a-zA-Z0-9]+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun encodeUrl(value: String): String = value
    .replace("%", "%25").replace("&", "%26").replace("?", "%3F").replace("#", "%23")
    .replace(" ", "%20").replace(":", "%3A").replace("/", "%2F")

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

private fun balancedAfter(html: String, start: Int): String? {
    val open = html.indexOf('{', start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = open
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
                    if (depth == 0) return html.substring(open, i + 1)
                }
            }
        }
        i++
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun kotlinx.serialization.json.JsonElement.obj(name: String): JsonObject? =
    (this as? JsonObject)?.get(name) as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
