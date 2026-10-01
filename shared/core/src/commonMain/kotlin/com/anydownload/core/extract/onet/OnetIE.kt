/*
 * Onet extractors — AnyDownload
 *
 * Kotlin translation of `onet.py` from `yt_dlp/extractor/onet.py` at upstream
 * tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `onet.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `qi.ckm.onetapi.pl` `get_asset_detail` JSON (one MPD row, one
 * HLS row, and the direct rows with the resolution/bitrate fields and the
 * audio `vcodec: none`), the mvp id search, the onet.pl `data-mvp` search
 * with the pulsembed fallback, and the channel `currentClip` JSON with its
 * video links as child entries. Limitations: an `ism` format is skipped (the
 * port has no ISM/MSS helper); m3u8/mpd subtitles are not parsed (one row
 * per manifest); the upstream `_yes_playlist` prompt is not carried, so the
 * channel page always returns its playlist; `display_id` is not modeled;
 * `timestamp` folds into `uploadDate`. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.onet

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val URL_BASE_RE = "https?://(?:(?:www\\.)?onet\\.tv|onet100\\.vod\\.pl)/[a-z]/"

/** Upstream `OnetBaseIE`: the asset-detail API. */
abstract class OnetBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_search_mvp_id`. */
    protected fun searchMvpId(webpage: String): String? =
        ExtractorUtils.searchRegex("id=([\"'])mvp:(.+?)\\1", webpage, group = 2)

    /** Upstream `_extract_from_id`: the asset detail and the format rows. */
    protected suspend fun extractFromId(videoId: String, webpage: String? = null): InfoDict {
        val query = mapOf(
            "body[id]" to videoId,
            "body[jsonrpc]" to "2.0",
            "body[method]" to "get_asset_detail",
            "body[params][ID_Publikacji]" to videoId,
            "body[params][Service]" to "www.onet.pl",
            "content-type" to "application/jsonp",
            "x-onet-app" to "player.front.onetapi.pl",
        )
        val queryString = query.entries.joinToString("&") {
            "${percentEncode(it.key)}=${percentEncode(it.value)}"
        }
        val response = http.downloadJson("http://qi.ckm.onetapi.pl/?$queryString") as? JsonObject
            ?: throw ExtractionError.Malformed("The Onet API was not an object.")
        response.str("error")?.let { throw ExtractionError.Unavailable("onet said: $it") }

        val video = (response.obj("result")?.obj("0"))
            ?: throw ExtractionError.Malformed("The Onet API returned no result.")

        val formats = mutableListOf<MediaFormat>()
        for ((formatType, value) in video.obj("formats").orEmpty()) {
            val formatsDict = value as? JsonObject ?: continue
            for ((formatId, listValue) in formatsDict) {
                val formatList = listValue as? JsonArray ?: continue
                for (element in formatList) {
                    val f = element as? JsonObject ?: continue
                    val videoUrl = f.str("url") ?: continue
                    val ext = ExtractorUtils.determineExt(videoUrl)
                    when {
                        formatId.startsWith("ism") -> Unit // The port has no ISM/MSS helper.
                        ext == "mpd" -> formats += MediaFormat(
                            formatId = "dash",
                            url = videoUrl,
                            ext = "mp4",
                            protocol = "mpd",
                        )
                        formatId.startsWith("hls") -> formats += MediaFormat(
                            formatId = "hls",
                            url = videoUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        )
                        else -> formats += MediaFormat(
                            formatId = formatId,
                            url = videoUrl,
                            abr = f.number("audio_bitrate"),
                            vcodec = if (formatType == "audio") "none" else null,
                            height = f.number("vertical_resolution")?.toLong(),
                            width = f.number("horizontal_resolution")?.toLong(),
                            vbr = f.number("video_bitrate"),
                        )
                    }
                }
            }
        }

        val meta = video.obj("meta") ?: JsonObject(emptyMap())
        val title = (webpage?.let { ExtractorUtils.htmlSearchMeta(it, "og:title") }) ?: meta.str("title")
        val description = (webpage?.let { ExtractorUtils.htmlSearchMeta(it, "og:description") })
            ?: meta.str("description")
        val duration = meta.number("length") ?: meta.number("lenght")

        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            duration = duration,
            uploadDate = meta.str("addDate")?.let(ExtractorUtils::unifiedStrdate),
            formats = formats,
            extractor = "onet",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `OnetMVPIE`: a bare `onetmvp:` id. */
class OnetMVPIE(
    http: ExtractorHttp,
) : OnetBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractFromId(videoId)
    }

    companion object {
        const val IE_KEY: String = "OnetMVP"

        val VALID_URL: Regex = Regex("onetmvp:(?<id>\\d+\\.\\d+)")
    }
}

/** Upstream `OnetIE`: one onet.tv clip page. */
class OnetIE(
    http: ExtractorHttp,
) : OnetBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val mvpId = searchMvpId(webpage)
            ?: throw ExtractionError.Malformed("The Onet page carried no mvp id.")
        return extractFromId(mvpId, webpage).copy(id = videoId, webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "Onet"

        val VALID_URL: Regex = Regex(
            "$URL_BASE_RE[a-z]+/(?<displayid>[0-9a-z-]+)/(?<id>[0-9a-z]+)",
        )
    }
}

/** Upstream `OnetChannelIE`: a channel page. */
class OnetChannelIE(
    http: ExtractorHttp,
) : OnetBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        // Upstream `_yes_playlist` prompt is not carried: the playlist is returned.
        val entries = mutableListOf<InfoEntry>()
        for (match in VIDEO_LINK.findAll(webpage)) {
            entries += InfoEntry(url = match.groupValues[1])
        }
        val channelTitle = stripOrNone(elementByClass(webpage, "o_channelName"))
        val channelDescription = stripOrNone(elementByClass(webpage, "o_channelDesc"))

        return InfoDict(
            id = channelId,
            title = channelTitle,
            description = channelDescription,
            entries = entries,
            webpageUrl = url,
            extractor = "onet:channel",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "OnetChannel"

        private val VIDEO_LINK = Regex(
            "<a[^>]+href=[\"']($URL_BASE_RE[a-z]+/[0-9a-z-]+/[0-9a-z]+)",
        )

        val VALID_URL: Regex = Regex("$URL_BASE_RE(?<id>[a-z]+)(?:[?#]|\$)")
    }
}

/** Upstream `OnetPlIE`: an onet.pl / businessinsider.com.pl / plejada.pl page. */
class OnetPlIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        var webpage = http.downloadWebpage(url)
        var mvpId = searchMvpId(webpage)
        if (mvpId == null) {
            val pulsembedUrl = ExtractorUtils.searchRegex(
                "data-src=([\"'])((?:https?:)?//pulsembed\\.eu/.+?)\\1",
                webpage,
                group = 2,
            ) ?: throw ExtractionError.Malformed("The Onet page carried no mvp or pulsembed id.")
            val absolute = if (pulsembedUrl.startsWith("//")) "https:$pulsembedUrl" else pulsembedUrl
            webpage = http.downloadWebpage(absolute)
            mvpId = searchMvpId(webpage)
                ?: throw ExtractionError.Malformed("The pulsembed page carried no mvp id.")
        }
        // Upstream `url_result('onetmvp:{mvp_id}')`; the port dispatches directly.
        val info = OnetMVPIE(http).extract("onetmvp:$mvpId")
        return info.copy(id = videoId, webpageUrl = url)
    }

    /** Upstream `_search_mvp_id`. */
    private fun searchMvpId(webpage: String): String? =
        ExtractorUtils.searchRegex("data-(?:params-)?mvp=[\"'](\\d+\\.\\d+)", webpage)

    companion object {
        const val IE_KEY: String = "OnetPl"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?(?:onet|businessinsider\\.com|plejada)\\.pl/" +
                "(?:[^/]+/)+(?<id>[0-9a-z]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun elementByClass(webpage: String, className: String): String? {
    val open = Regex(
        "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
            Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>",
    ).find(webpage) ?: return null
    val tag = open.groupValues[1]
    val start = open.range.last + 1
    var depth = 1
    var index = start
    val tagRegex = Regex("</?$tag\\b[^>]*>", RegexOption.IGNORE_CASE)
    while (index < webpage.length) {
        val match = tagRegex.find(webpage, index) ?: break
        if (match.value.startsWith("</")) {
            depth--
            if (depth == 0) return webpage.substring(start, match.range.first)
        } else {
            depth++
        }
        index = match.range.last + 1
    }
    return null
}

private fun stripOrNone(value: String?): String? =
    value?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotEmpty() }

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

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
