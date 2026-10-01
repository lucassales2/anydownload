/*
 * 4tube / Fux / PornTube / PornerBros extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `fourtube.py` from
 * `yt_dlp/extractor/fourtube.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `fourtube.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the token API (`{host}/{mediaId}/desktop/{heights}`) into direct
 * rows, the page meta/anchor metadata, the player-JS initialization fallback,
 * and the PornTube `INITIALSTATE` base64 JSON. Categories, dislike counts,
 * and channel/uploader fields the port does not model are dropped (the
 * uploader id fills `channelId`); the token API is called with an empty POST
 * body and Origin/Referer headers. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.fourtube

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
import kotlin.io.encoding.Base64

/** Upstream `FourTubeBaseIE`: the shared token API and page extraction. */
abstract class FourTubeBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    protected val pattern: Regex,
    private val tknHost: String,
    private val urlTemplate: String,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = pattern) {
    /** Upstream `_extract_formats`: the token API's direct rows. */
    protected suspend fun extractFormats(
        url: String,
        videoId: String,
        mediaId: String,
        sources: List<String>,
    ): List<MediaFormat> {
        val origin = url.substringBefore("://") + "://" +
            url.substringAfter("://").substringBefore('/')
        val tokens = http.downloadJson(
            "https://$tknHost/$mediaId/desktop/${sources.joinToString("+")}",
            method = "POST",
            headers = mapOf("Origin" to origin, "Referer" to url),
            body = ByteArray(0),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The token API was not an object.")
        return sources.mapNotNull { source ->
            val token = tokens.obj(source)?.str("token") ?: return@mapNotNull null
            MediaFormat(
                formatId = "${source}p",
                url = token,
                height = source.toLongOrNull(),
                preference = source.toIntOrNull(),
            )
        }
    }

    override suspend fun extract(url: String): InfoDict {
        val match = pattern.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val kind = match.groups["kind"]?.value
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayid"]?.value
        val pageUrl = if (kind == "m" || displayId == null) {
            urlTemplate.replaceFirst("%s", videoId)
        } else {
            url
        }
        val webpage = http.downloadWebpage(pageUrl)
        val title = ExtractorUtils.htmlSearchMeta(webpage, "name")
        val uploaderId = ExtractorUtils.searchRegex(
            "<a class=\"item-to-subscribe\" href=\"[^\"]+/(?:channel|user)s?/([^/\"]+)\" " +
                "title=\"Go to [^\"]+ page\">",
            webpage,
        )
        val uploader = ExtractorUtils.searchRegex(
            "<a class=\"item-to-subscribe\" href=\"[^\"]+/(?:channel|user)s?/[^/\"]+\" " +
                "title=\"Go to ([^\"]+) page\">",
            webpage,
        )
        var mediaId = ExtractorUtils.searchRegex(
            "<button[^>]+data-id=([\"'])(\\d+)\\1[^>]+data-quality=",
            webpage,
            group = 2,
        )
        var sources = Regex("<button[^>]+data-quality=([\"'])(.+?)\\1").findAll(webpage)
            .map { it.groupValues[2] }
            .toList()
        if (mediaId == null || sources.isEmpty()) {
            val playerUrl = ExtractorUtils.searchRegex(
                "<script[^>]id=([\"'])playerembed\\1[^>]+src=([\"'])(?<url>.+?)\\2",
                webpage,
                group = 3,
            ) ?: throw ExtractionError.Malformed("The player script was not found.")
            val playerJs = http.downloadWebpage(playerUrl)
            val paramsJs = ExtractorUtils.searchRegex(
                "\\$\\.ajax\\(url,\\s*opts\\);\\s*\\}\\s*\\}\\)\\(([0-9,\\[\\] ]+)\\)",
                playerJs,
            ) ?: throw ExtractionError.Malformed("The player initialization parameters were not found.")
            val params = ExtractorUtils.parseJson("[$paramsJs]") as? JsonArray
                ?: throw ExtractionError.Malformed("The player initialization parameters were not JSON.")
            mediaId = (params.getOrNull(0) as? JsonPrimitive)?.content
            sources = (params.getOrNull(2) as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.content }
        }
        val finalMediaId = mediaId ?: throw ExtractionError.Malformed("The media id was not found.")
        return InfoDict(
            id = videoId,
            title = title,
            duration = ExtractorUtils.htmlSearchMeta(webpage, "duration")
                ?.let(ExtractorUtils::parseDuration),
            uploadDate = ExtractorUtils.htmlSearchMeta(webpage, "uploadDate")
                ?.let(ExtractorUtils::unifiedStrdate),
            uploader = uploader,
            channelId = uploaderId,
            viewCount = parseCount(
                ExtractorUtils.searchRegex(
                    "<meta[^>]+itemprop=\"interactionCount\"[^>]+content=\"UserPlays:([0-9,]+)\">",
                    webpage,
                ),
            ),
            ageLimit = 18,
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "thumbnailUrl")?.let { Thumbnail(url = it) },
            ),
            formats = extractFormats(url, videoId, finalMediaId, sources),
            webpageUrl = url,
            extractor = ieKey,
            extractorKey = ieKey,
        )
    }

    protected fun percentDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '%' && index + 2 < value.length) {
                val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (code != null) {
                    bytes += code.toByte()
                    index += 3
                    continue
                }
            }
            bytes += character.toString().encodeToByteArray().toList()
            index++
        }
        return bytes.toByteArray().decodeToString()
    }
}

/** Upstream `FourTubeIE`: a 4tube.com video. */
class FourTubeIE(
    http: ExtractorHttp,
) : FourTubeBaseIE(
    ieKey = IE_KEY,
    http = http,
    pattern = VALID_URL,
    tknHost = "token.4tube.com",
    urlTemplate = "https://www.4tube.com/videos/%s/video",
) {
    companion object {
        const val IE_KEY: String = "FourTube"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?<kind>www|m)\\.)?4tube\\.com/(?:videos|embed)/(?<id>\\d+)" +
                "(?:/(?<displayid>[^/?#&]+))?",
        )
    }
}

/** Upstream `FuxIE`: a fux.com video. */
class FuxIE(
    http: ExtractorHttp,
) : FourTubeBaseIE(
    ieKey = IE_KEY,
    http = http,
    pattern = VALID_URL,
    tknHost = "token.fux.com",
    urlTemplate = "https://www.fux.com/video/%s/video",
) {
    companion object {
        const val IE_KEY: String = "Fux"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?<kind>www|m)\\.)?fux\\.com/(?:video|embed)/(?<id>\\d+)" +
                "(?:/(?<displayid>[^/?#&]+))?",
        )
    }
}

/** Upstream `PornTubeIE`: a porntube.com video (INITIALSTATE JSON). */
class PornTubeIE(
    http: ExtractorHttp,
) : FourTubeBaseIE(
    ieKey = IE_KEY,
    http = http,
    pattern = VALID_URL,
    tknHost = "tkn.porntube.com",
    urlTemplate = "https://www.porntube.com/videos/video_%s",
) {
    override suspend fun extract(url: String): InfoDict {
        val match = pattern.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val encoded = ExtractorUtils.searchRegex(
            "INITIALSTATE\\s*=\\s*([\"'])((?:(?!\\1).)+)\\1",
            webpage,
            group = 2,
        ) ?: throw ExtractionError.Malformed("The PornTube INITIALSTATE was not found.")
        val decoded = percentDecode(Base64.Default.decode(encoded).decodeToString())
        val page = ExtractorUtils.parseJson(decoded) as? JsonObject
            ?: throw ExtractionError.Malformed("The PornTube INITIALSTATE was not JSON.")
        val video = page.obj("page")?.obj("video")
            ?: throw ExtractionError.Malformed("The PornTube INITIALSTATE had no video.")
        val title = video.str("title")
        val mediaId = video.primitive("mediaId")
            ?: throw ExtractionError.Malformed("The PornTube video had no media id.")
        val sources = video.array("encodings").orEmpty()
            .mapNotNull { (it as? JsonObject)?.primitive("height") }
        val channel = video.obj("channel")
        return InfoDict(
            id = videoId,
            title = title,
            duration = video.number("durationInSeconds"),
            uploadDate = video.str("publishedAt")?.let(ExtractorUtils::unifiedStrdate),
            uploader = video.obj("user")?.str("username") ?: channel?.str("name"),
            channel = channel?.str("name"),
            channelId = channel?.primitive("id") ?: video.obj("user")?.primitive("id"),
            viewCount = video.number("playsQty")?.toLong(),
            ageLimit = 18,
            thumbnails = listOfNotNull(video.str("masterThumb")?.let { Thumbnail(url = it) }),
            formats = extractFormats(url, videoId, mediaId, sources),
            webpageUrl = url,
            extractor = ieKey,
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "PornTube"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?<kind>www|m)\\.)?porntube\\.com/" +
                "(?:videos/(?<displayid>[^/]+)_|embed/)(?<id>\\d+)",
        )
    }
}

/** Upstream `PornerBrosIE`: a pornerbros.com video. */
class PornerBrosIE(
    http: ExtractorHttp,
) : FourTubeBaseIE(
    ieKey = IE_KEY,
    http = http,
    pattern = VALID_URL,
    tknHost = "token.pornerbros.com",
    urlTemplate = "https://www.pornerbros.com/videos/video_%s",
) {
    companion object {
        const val IE_KEY: String = "PornerBros"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?<kind>www|m)\\.)?pornerbros\\.com/" +
                "(?:videos/(?<displayid>[^/]+)_|embed/)(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `str_to_int` for the comma-grouped counters. */
private fun parseCount(value: String?): Long? =
    value?.replace(",", "")?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
