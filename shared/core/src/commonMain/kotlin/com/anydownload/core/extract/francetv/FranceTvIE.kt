/*
 * France TV extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `francetv.py` from
 * `yt_dlp/extractor/francetv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `francetv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `k7.ftven.fr` video API (desktop and mobile passes), the public
 * token endpoint that returns a signed manifest URL, m3u8/mpd/rtmp/plain
 * formats, and the site/info page id scans. f4m formats, manifest-embedded
 * subtitles, spritesheets, and the HEAD geo probe are not translated; the
 * DRM codes (2015/2017/2019) fail typed and code 2009 is a typed geo
 * failure. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.francetv

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

/** Upstream `FranceTVIE`: the video API. */
class FranceTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val rawId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = rawId.substringBefore('@')
        return extractVideo(videoId, hostname = "www.france.tv")
    }

    private suspend fun extractVideo(videoId: String, hostname: String?): InfoDict {
        var isLive: Boolean? = null
        var title: String? = null
        var subtitle: String? = null
        var image: String? = null
        var duration: Double? = null
        var uploadDate: String? = null
        var drmFormats = false
        val videos = mutableListOf<JsonObject>()
        for ((deviceType, browser) in listOf("desktop" to "chrome", "mobile" to "safari")) {
            val query = "device_type=$deviceType&browser=$browser" + (hostname?.let { "&domain=$it" } ?: "")
            val dinfo = try {
                http.downloadJson("https://k7.ftven.fr/videos/$videoId?$query") as? JsonObject
            } catch (error: ExtractionError) {
                null
            } catch (error: IllegalStateException) {
                null
            } ?: continue
            val video = dinfo.obj("video")
            if (video != null) {
                videos += video
                if (duration == null) duration = video.number("duration")
                if (isLive == null) isLive = video.bool("is_live")
            } else if (dinfo.number("code") != null) {
                when (dinfo.number("code")?.toInt()) {
                    2009 -> throw ExtractionError.GeoRestricted()
                    2015, 2017, 2019 -> {
                        drmFormats = true
                        continue
                    }
                }
            }
            val meta = dinfo.obj("meta")
            if (meta != null) {
                if (title == null) title = meta.str("title")
                if (subtitle == null) subtitle = meta.str("additional_title")
                if (image == null) image = meta.str("image_url")
                if (uploadDate == null) {
                    uploadDate = dateFromIso(meta.str("broadcasted_at"))
                }
            }
        }
        if (videos.isEmpty() && drmFormats) {
            throw ExtractionError.Unavailable("This France TV video is DRM protected.")
        }
        val formats = mutableListOf<MediaFormat>()
        for (video in videos) {
            var videoUrl = video.str("url") ?: continue
            val formatId = video.str("format")
            val tokenUrl = tokenUrl(video)
            if (tokenUrl != null) {
                val signed = try {
                    http.downloadJson("$tokenUrl?format=json&url=$videoUrl") as? JsonObject
                } catch (error: ExtractionError) {
                    null
                } catch (error: IllegalStateException) {
                    null
                }
                signed?.str("url")?.let { videoUrl = it }
            }
            val ext = ExtractorUtils.determineExt(videoUrl)
            when {
                ext == "m3u8" -> formats += MediaFormat(
                    formatId = formatId ?: "hls",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                ext == "mpd" -> formats += MediaFormat(
                    formatId = formatId ?: "dash",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "mpd",
                )

                videoUrl.startsWith("rtmp") -> formats += MediaFormat(
                    formatId = formatId?.let { "rtmp-$it" } ?: "rtmp",
                    url = videoUrl,
                    ext = "flv",
                )

                videoUrl.startsWith("http") -> formats += MediaFormat(
                    formatId = formatId,
                    url = videoUrl,
                    ext = ExtractorUtils.determineExt(videoUrl),
                )
                // f4m is skipped: the port has no f4m helper.
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The France TV API returned no playable format.")
        }
        val joinedTitle = listOfNotNull(title, subtitle).joinToString(" - ").trim()
        return InfoDict(
            id = videoId,
            title = joinedTitle.ifEmpty { null },
            duration = duration,
            uploadDate = uploadDate,
            isLive = isLive,
            thumbnails = listOfNotNull(image?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = "francetv:$videoId",
            extractor = "francetv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "FranceTV"

        val VALID_URL: Regex = Regex("francetv:(?<id>[^@#]+)")
    }
}

/** Upstream `FranceTVSiteIE`: the france.tv pages. */
class FranceTVSiteIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val videoId = ExtractorUtils.searchRegex(
            "\"options\"\\s*:\\s*\\{[^{}]*\"id\"\\s*:\\s*\"([^\"]+)\"",
            webpage,
            default = null,
        ) ?: UUID.find(webpage)?.groupValues?.get(1)
        ?: throw ExtractionError.Malformed("Unable to extract the France TV video id.")
        return InfoDict(
            id = displayId,
            redirectUrl = "francetv:${videoId.substringBefore('@')}",
            webpageUrl = url,
            extractor = "francetv:site",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "FranceTVSite"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.)?france\\.tv|mobile\\.france\\.tv)/(?:[^/]+/)*(?<id>[^/]+)\\.html",
        )

        private val UUID = Regex("([\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12})")
    }
}

/** Upstream `FranceTVInfoIE`: the franceinfo pages. */
class FranceTVInfoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val dailymotion = DAILYMOTION.findAll(webpage)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        if (dailymotion.isNotEmpty()) {
            return InfoDict(
                id = displayId,
                entries = dailymotion.map { InfoEntry(url = "https://www.dailymotion.com/video/$it") },
                webpageUrl = url,
                extractor = "franceinfo",
                extractorKey = IE_KEY,
            )
        }
        val videoId = VIDEO_ID_PATTERNS.firstNotNullOfOrNull {
            ExtractorUtils.searchRegex(it, webpage, default = null)
        } ?: throw ExtractionError.Malformed("Unable to extract the franceinfo video id.")
        return InfoDict(
            id = displayId,
            redirectUrl = "francetv:${videoId.substringBefore('@')}",
            webpageUrl = url,
            extractor = "franceinfo",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "FranceTVInfo"

        val VALID_URL: Regex = Regex(
            "https?://(?:www|mobile|france3-regions)\\.france(?:tv)?info\\.fr/(?:[^/?#]+/)*(?<id>[^/?#&.]+)",
        )

        private val DAILYMOTION = Regex(
            "(?:https?:)?//(?:www\\.)?dailymotion\\.com/(?:embed/video|video)/([A-Za-z0-9]+)",
        )
        private val VIDEO_ID_PATTERNS = listOf(
            "player\\.load[^;]+src:\\s*[\"']([^\"']+)",
            "id-video=([^@]+@[^\"]+)",
            "<a[^>]+href=\"(?:https?:)?//videos\\.francetv\\.fr/video/([^@]+@[^\"]+)",
            "(?:data-id|<figure[^<]+\\bid)=[\"']([\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12})",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun tokenUrl(video: JsonObject): String? {
    val token = video["token"] ?: return null
    return when (token) {
        is JsonPrimitive -> token.content.takeIf { it.isNotBlank() && it.startsWith("http") }
        is JsonObject -> token.str("akamai") ?: token.str("")
        else -> null
    }
}

private fun dateFromIso(value: String?): String? {
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(value ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
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
