/*
 * TXXX network extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `txxx.py` from
 * `yt_dlp/extractor/txxx.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `txxx.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the TXXX network video-file and video-info APIs (with the custom
 * base64 alphabet used for the media paths) and the PornTop page player
 * JSON with its JSON-LD metadata. Like/dislike counters the port does not
 * carry are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.txxx

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

private const val DOMAINS =
    "hclips\\.com|hdzog\\.com|hdzog\\.tube|hotmovs\\.com|hotmovs\\.tube|inporn\\.com|" +
        "privatehomeclips\\.com|tubepornclassic\\.com|txxx\\.com|txxx\\.tube|upornia\\.com|" +
        "upornia\\.tube|vjav\\.com|vjav\\.tube|vxxx\\.com|voyeurhit\\.com|voyeurhit\\.tube"

/** Upstream `TxxxIE`: the TXXX network video pages. */
class TxxxIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val headers = mapOf("Referer" to url, "X-Requested-With" to "XMLHttpRequest")
        val videoFile = callApi(
            http,
            "https://$host/api/videofile.php?video_id=$videoId&lifetime=8640000",
            videoId,
            headers,
        ) as? JsonArray ?: throw ExtractionError.Malformed("The videofile API was not a list.")
        val numericId = videoId.toLongOrNull() ?: 0
        val slug = "${1_000_000L * (numericId / 1_000_000L)}/${1000 * (numericId / 1000)}"
        val videoInfo = callApi(
            http,
            "https://$host/api/json/video/86400/$slug/$videoId.json",
            videoId,
            headers,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The video info API was not an object.")
        val video = videoInfo.obj("video")
        val formats = getFormats(host, videoFile)
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The videofile API returned no playable source.")
        }
        return InfoDict(
            id = videoId,
            title = video?.str("title"),
            duration = ExtractorUtils.parseDuration(video?.str("duration"))?.toDouble(),
            uploader = video?.obj("user")?.str("username"),
            viewCount = video?.obj("statistics")?.number("viewed")?.toLong(),
            ageLimit = 18,
            thumbnails = listOfNotNull(video?.str("thumbsrc")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "txxx",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Txxx"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<host>$DOMAINS)/" +
                "(?:videos?[/-]|embed/)(?<id>\\d+)(?:/(?<displayId>[^/?#]+))?",
        )
    }
}

/** Upstream `PornTopIE`: the PornTop video pages. */
class PornTopIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val schemaRaw = balancedAfter(webpage, Regex("\\bschemaJson\\s*="))
            ?: throw ExtractionError.Malformed("The page had no schemaJson.")
        val schema = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(schemaRaw)) as? JsonObject
            ?: throw ExtractionError.Malformed("The schemaJson was not an object.")
        val playerRaw = ExtractorUtils.searchRegex(
            "window\\.initPlayer\\(.*}}},\\s*'([^']+)'",
            webpage,
            flags = setOf(RegexOption.DOT_MATCHES_ALL),
            default = null,
        ) ?: throw ExtractionError.Malformed("The page had no player JSON.")
        val videoFile = ExtractorUtils.parseJson(decodeBase64(playerRaw)) as? JsonArray
            ?: throw ExtractionError.Malformed("The player JSON was not a list.")
        val formats = getFormats(host, videoFile)
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The player JSON returned no playable source.")
        }
        return InfoDict(
            id = videoId,
            title = schema.str("name"),
            description = schema.str("description"),
            duration = schema.obj("duration")?.str("value")?.toDoubleOrNull()
                ?: ExtractorUtils.parseDuration(schema.str("duration"))?.toDouble(),
            uploader = schema.obj("author")?.str("name"),
            uploadDate = schema.str("uploadDate")?.take(10)?.replace("-", ""),
            ageLimit = 18,
            thumbnails = listOfNotNull(schema.str("thumbnailUrl")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "porntop",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PornTop"

        val VALID_URL: Regex = Regex(
            "https?://(?<host>(?:www\\.)?porntop\\.com)/video/(?<id>\\d+)(?:/(?<displayId>[^/?]+))?",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun callApi(
    http: ExtractorHttp,
    url: String,
    videoId: String,
    headers: Map<String, String>,
): kotlinx.serialization.json.JsonElement {
    val response = try {
        http.downloadJson(url, headers = headers)
    } catch (error: ExtractionError) {
        throw error
    }
    if (response is JsonObject) {
        response.str("error")?.let {
            throw ExtractionError.Unavailable("Txxx said: $it")
        }
    }
    return response
}

private fun getFormats(
    host: String,
    videoFile: JsonArray,
): List<MediaFormat> = videoFile.mapIndexedNotNull { index, element ->
    val video = element as? JsonObject ?: return@mapIndexedNotNull null
    val encoded = video.str("video_url") ?: return@mapIndexedNotNull null
    val decoded = decodeBase64(encoded)
    val url = if (decoded.startsWith("http")) decoded else "https://$host/$decoded"
    val format = video.str("format")?.substringBefore(',')?.trimStart('_')
    MediaFormat(
        formatId = format,
        url = url,
        preference = index,
    )
}

private val BASE64_TRANSLATION = mapOf(
    '\u0405' to 'S', '\u0406' to 'I', '\u0408' to 'J', '\u0410' to 'A', '\u0412' to 'B',
    '\u0415' to 'E', '\u041a' to 'K', '\u041c' to 'M', '\u041d' to 'H', '\u041e' to 'O',
    '\u0420' to 'P', '\u0421' to 'C', '\u0425' to 'X', ',' to '/', '.' to '+', '~' to '=',
)

private fun decodeBase64(text: String): String {
    val translated = buildString(text.length) {
        for (character in text) append(BASE64_TRANSLATION[character] ?: character)
    }
    return runCatching {
        kotlin.io.encoding.Base64.Default.decode(translated).decodeToString()
    }.getOrDefault(translated)
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

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
