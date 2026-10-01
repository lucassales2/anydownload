/*
 * Sohu extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `sohu.py` from
 * `yt_dlp/extractor/sohu.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `sohu.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `vrs_flash`/`videonew` JSON, the six format ids, the
 * clipsURL/mp4PlayUrl CDN resolution loop, the mytv publish date, and the
 * SohuV base64 redirect. A geo-restricted or status-12 video fails typed; a
 * multipart video becomes selectable media. The 8-hour publish-time
 * adjustment, alt_title, and tags are not carried; the CDN URL is requested
 * per extraction and never stored. No cookie or token is stored here.
 */
package com.anydownload.core.extract.sohu

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

private val FORMAT_IDS = listOf("nor", "high", "super", "ori", "h2644k", "h2654k")
private const val CDN_SENTINEL = "newflv.sohu.ccgslb.net"

/** Upstream `SohuIE`: a tv.sohu.com or my.tv.sohu.com video. */
class SohuIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val mytv = match.groups["id"] != null
        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?.replace(Regex("( - 高清正版在线观看)? - 搜狐视频$"), "")
        val vid = ExtractorUtils.searchRegex("var vid ?= ?[\"'](\\d+)[\"']", webpage)
            ?: throw ExtractionError.Malformed("The Sohu page had no vid.")

        val vidData = fetchData(vid, mytv, videoId)
        if (vidData.number("play")?.toInt() != 1) {
            if (vidData.number("status")?.toInt() == 12) {
                throw ExtractionError.Unavailable("There's something wrong in the video.")
            }
            throw ExtractionError.GeoRestricted()
        }
        val data = vidData.obj("data")
            ?: throw ExtractionError.Malformed("The Sohu video data was empty.")

        val formatsJson = linkedMapOf<String, JsonObject>()
        for (formatId in FORMAT_IDS) {
            val formatVid = data.primitive("${formatId}Vid") ?: continue
            formatsJson[formatId] = if (formatVid == vid) vidData else fetchData(formatVid, mytv, videoId)
        }
        val partCount = data.number("totalBlocks")?.toInt()
            ?: throw ExtractionError.Malformed("The Sohu video had no block count.")

        val media = mutableListOf<InfoMedia>()
        val singleFormats = mutableListOf<MediaFormat>()
        for (part in 0 until partCount) {
            val formats = mutableListOf<MediaFormat>()
            for ((formatId, formatData) in formatsJson) {
                val partData = formatData.obj("data") ?: continue
                val clipUrl = partData.array("clipsURL")?.getOrNull(part)?.let { (it as? JsonPrimitive)?.content }
                    ?: partData.array("mp4PlayUrl")?.getOrNull(part)?.let { (it as? JsonPrimitive)?.content }
                    ?: throw ExtractionError.Malformed("Unable to extract the URL for clip $part.")
                val su = partData.array("su")?.getOrNull(part)?.let { (it as? JsonPrimitive)?.content }
                    ?: throw ExtractionError.Malformed("Unable to extract the CDN key for clip $part.")
                val videoUrl = resolveCdn(
                    allot = formatData.str("allot")
                        ?: throw ExtractionError.Malformed("The Sohu format had no CDN host."),
                    clipUrl = clipUrl,
                    su = su,
                    videoId = videoId,
                    note = "Downloading $formatId video URL part ${part + 1} of $partCount",
                )
                formats += MediaFormat(
                    formatId = formatId,
                    url = videoUrl,
                    filesize = partData.array("clipsBytes")?.getOrNull(part)
                        ?.let { (it as? JsonPrimitive)?.content }?.toLongOrNull(),
                    width = partData.number("width")?.toLong(),
                    height = partData.number("height")?.toLong(),
                    fps = partData.number("fps"),
                )
            }
            if (partCount == 1) {
                singleFormats += formats
            } else {
                media += InfoMedia(
                    mediaId = "${videoId}_part${part + 1}",
                    title = title,
                    duration = data.array("clipsDuration")?.getOrNull(part)
                        ?.let { (it as? JsonPrimitive)?.content }?.toDoubleOrNull(),
                    formats = formats,
                )
            }
        }

        val publishTime = if (mytv) {
            ExtractorUtils.searchRegex(
                "publishTime:\\s*[\"'](\\d+-\\d+-\\d+ \\d+:\\d+)[\"']",
                webpage,
            )
        } else {
            vidData.primitive("tv_application_time")
        }
        return InfoDict(
            id = videoId,
            title = title,
            uploader = vidData.obj("wm_data")?.str("wm_username"),
            uploadDate = publishTime?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = listOfNotNull(
                data.str("coverImg")?.let { Thumbnail(url = it) },
            ),
            formats = singleFormats,
            media = media,
            webpageUrl = url,
            extractor = "sohu",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun fetchData(vid: String, mytv: Boolean, videoId: String): JsonObject {
        val base = if (mytv) {
            "http://my.tv.sohu.com/play/videonew.do?vid="
        } else {
            "http://hot.vrs.sohu.com/vrs_flash.action?vid="
        }
        return http.downloadJson(base + vid) as? JsonObject
            ?: throw ExtractionError.Malformed("The Sohu data API was not an object.")
    }

    /** Upstream CDN resolution loop: up to six hops off the sentinel host. */
    private suspend fun resolveCdn(
        allot: String,
        clipUrl: String,
        su: String,
        videoId: String,
        note: String,
    ): String {
        var videoUrl = CDN_SENTINEL
        var cdnId: String? = null
        var retries = 0
        while (CDN_SENTINEL in videoUrl) {
            val params = buildString {
                append("prot=9")
                append("&file=${percentEncode(clipUrl)}")
                append("&new=${percentEncode(su)}")
                append("&prod=h5n&rb=1")
                if (cdnId != null) append("&idc=${percentEncode(cdnId!!)}")
            }
            val partInfo = ExtractorUtils.parseJson(
                http.downloadWebpage("http://$allot/?$params"),
            ) as? JsonObject ?: throw ExtractionError.Malformed("The Sohu CDN response was not JSON.")
            videoUrl = partInfo.str("url")
                ?: throw ExtractionError.Malformed("The Sohu CDN response had no URL.")
            cdnId = partInfo.str("nid")
            retries++
            if (retries > 5) throw ExtractionError.Unavailable("Failed to get the Sohu video URL.")
        }
        return videoUrl
    }

    companion object {
        const val IE_KEY: String = "Sohu"

        val VALID_URL: Regex = Regex(
            "https?://my\\.tv\\.sohu\\.com/.+?/(?<id>\\d+)\\.shtml.*?|" +
                "https?://tv\\.sohu\\.com/.+?/n(?<id2>\\d+)\\.shtml.*?",
        )
    }
}

/** Upstream `SohuVIE`: the base64 `/v/` redirect form. */
class SohuVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val encodedId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val path = try {
            Base64.UrlSafe.decode(encodedId).decodeToString()
        } catch (error: IllegalArgumentException) {
            throw ExtractionError.Malformed("The Sohu V id was not base64.")
        }
        val subdomain = if (Regex("\\d+/n\\d+\\.shtml").containsMatchIn(path)) "tv" else "my.tv"
        return InfoDict(
            id = encodedId,
            redirectUrl = "http://$subdomain.sohu.com/$path",
            webpageUrl = url,
            extractor = "sohu:v",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SohuV"

        val VALID_URL: Regex = Regex(
            "https?://tv\\.sohu\\.com/v/(?<id>[\\w=-]+)\\.html(?:$|[#?])",
        )
    }
}

// ------------------------------------------------------------------ helpers

private const val HEX_DIGITS = "0123456789ABCDEF"

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
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
