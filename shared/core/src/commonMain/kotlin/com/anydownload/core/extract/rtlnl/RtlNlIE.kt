/*
 * RTL Nederland / RTL Lëtzebuerg extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `rtlnl.py` from
 * `yt_dlp/extractor/rtlnl.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rtlnl.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the rtl.nl adaptive JSON (title, synopsis, m3u8 path, timestamp,
 * duration, poster/thumb bases) and the rtl.lu `<rtl-player>` /
 * `<rtl-audioplayer>` pages (HLS video row, mp3 audio row, poster or og
 * image, og title/description, live flag). An m3u8 URL becomes one HLS row,
 * so manifest subtitles are not parsed. The upstream `_EMBED_REGEX` list and
 * the quoted `"thumb_base_url"` key quirk are kept. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.rtlnl

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val RTL_NL_API = "http://www.rtl.nl/system/s4m/vfd/version=2/uuid="

/** Upstream `RtlNlIE`: the adaptive JSON of rtl.nl and rtlxl.nl. */
class RtlNlIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val uuid = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val info = http.downloadJson(RTL_NL_API + uuid + "/fmt=adaptive/") as? JsonObject
            ?: throw ExtractionError.Malformed("The rtl.nl adaptive response was not an object.")
        val material = info.array("material")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The rtl.nl response had no material.")
        val abstract = info.array("abstracts")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The rtl.nl response had no abstract.")
        val title = abstract.str("name")
            ?: throw ExtractionError.Malformed("The rtl.nl abstract had no name.")
        val subtitle = material.str("title")
        val description = material.str("synopsis")
        val videopath = material.str("videopath")
            ?: throw ExtractionError.Malformed("The rtl.nl material had no video path.")
        val meta = info.obj("meta") ?: JsonObject(emptyMap())
        val m3u8Url = (meta.str("videohost") ?: "http://manifest.us.rtl.nl") + videopath

        // Upstream `('poster_base_url', '"thumb_base_url"')`: the second key
        // really carries its quotes.
        val thumbnails = mutableListOf<Thumbnail>()
        for (key in listOf("poster_base_url", "\"thumb_base_url\"")) {
            val base = meta.str(key) ?: continue
            thumbnails += Thumbnail(
                url = protoRelativeUrl(base + uuid),
                width = ExtractorUtils.searchRegex("/sz=([0-9]+)", base)?.toLongOrNull(),
                height = ExtractorUtils.searchRegex("/sz=[0-9]+x([0-9]+)", base)?.toLongOrNull(),
            )
        }
        return InfoDict(
            id = uuid,
            title = if (subtitle != null) "$title - $subtitle" else title,
            description = description,
            duration = ExtractorUtils.parseDuration(material.str("duration")),
            uploadDate = material.number("original_date")?.toLong()
                ?.let(ExtractorUtils::epochSecondsToDate),
            thumbnails = thumbnails,
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = m3u8Url,
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            webpageUrl = url,
            extractor = "rtl.nl",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RtlNl"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|static)\\.)?" +
                "(?:" +
                "rtlxl\\.nl/(?:[^#]*#!|programma)/[^/]+/|" +
                "rtl\\.nl/(?:(?:system/videoplayer/(?:[^/]+/)+(?:video_)?embed\\.html|embed)\\b.+?\\buuid=|video/)|" +
                "embed\\.rtl\\.nl/#uuid=" +
                ")(?<id>[0-9a-f-]+)",
        )
    }
}

/**
 * Upstream `RTLLuBaseIE`: the shared `<rtl-player>` / `<rtl-audioplayer>`
 * extraction for the four rtl.lu classes.
 */
abstract class RTLLuBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    private val extractorLabel: String,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val isLive = videoId in listOf("live", "live-2", "lauschteren")
        val webpage = http.downloadWebpage(url)
        val videoUrl = mediaUrl(webpage, "video")
        val audioUrl = mediaUrl(webpage, "audio")
        val formats = mutableListOf<MediaFormat>()
        if (videoUrl != null) {
            formats += MediaFormat(
                formatId = "hls",
                url = videoUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }
        if (audioUrl != null) {
            formats += MediaFormat(
                formatId = "audio",
                url = audioUrl,
                ext = "mp3",
                vcodec = MediaFormat.CODEC_NONE,
            )
        }
        val thumbnail = mediaUrl(webpage, "thumbnail")
            ?: ExtractorUtils.htmlSearchMeta(webpage, "og:image")
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            isLive = isLive,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = extractorLabel,
            extractorKey = ieKey,
        )
    }

    private fun mediaUrl(webpage: String, mediaType: String): String? = when (mediaType) {
        "video" -> ExtractorUtils.searchRegex("<rtl-player\\s[^>]*\\bhls\\s*=\\s*\"([^\"]+)", webpage)
        "audio" -> ExtractorUtils.searchRegex("<rtl-audioplayer\\s[^>]*\\bsrc\\s*=\\s*\"([^\"]+)", webpage)
        "thumbnail" -> ExtractorUtils.searchRegex("<rtl-player\\s[^>]*\\bposter\\s*=\\s*\"([^\"]+)", webpage)
        else -> null
    }
}

/** Upstream `RTLLuTeleVODIE`: an rtl.lu tele VOD video. */
class RTLLuTeleVODIE(
    http: ExtractorHttp,
) : RTLLuBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    extractorLabel = "rtl.lu:tele-vod",
) {
    companion object {
        const val IE_KEY: String = "RTLLuTeleVOD"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtl\\.lu/(tele/(?<slug>[\\w-]+)/v/|video/)(?<id>\\d+)(\\.html)?",
        )
    }
}

/** Upstream `RTLLuArticleIE`: an rtl.lu article video or audio. */
class RTLLuArticleIE(
    http: ExtractorHttp,
) : RTLLuBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    extractorLabel = "rtl.lu:article",
) {
    companion object {
        const val IE_KEY: String = "RTLLuArticle"

        val VALID_URL: Regex = Regex(
            "https?://(?:(www|5minutes|today)\\.)rtl\\.lu/(?:[\\w-]+)/(?:[\\w-]+)/a/(?<id>\\d+)\\.html",
        )
    }
}

/** Upstream `RTLLuLiveIE`: the rtl.lu tele/radio live pages. */
class RTLLuLiveIE(
    http: ExtractorHttp,
) : RTLLuBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    extractorLabel = "rtl.lu:live",
) {
    companion object {
        const val IE_KEY: String = "RTLLuLive"

        val VALID_URL: Regex = Regex(
            "https?://www\\.rtl\\.lu/(?:tele|radio)/(?<id>live(?:-\\d+)?|lauschteren)",
        )
    }
}

/** Upstream `RTLLuRadioIE`: an rtl.lu radio segment. */
class RTLLuRadioIE(
    http: ExtractorHttp,
) : RTLLuBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    extractorLabel = "rtl.lu:radio",
) {
    companion object {
        const val IE_KEY: String = "RTLLuRadio"

        val VALID_URL: Regex = Regex(
            "https?://www\\.rtl\\.lu/radio/(?:[\\w-]+)/s/(?<id>\\d+)(\\.html)?",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_proto_relative_url`: `//host/...` becomes `https://host/...`. */
private fun protoRelativeUrl(value: String): String =
    if (value.startsWith("//")) "https:$value" else value

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
