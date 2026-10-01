/*
 * Condé Nast extractor — AnyDownload
 *
 * Kotlin translation of `condenast.py` from
 * `yt_dlp/extractor/condenast.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `condenast.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the player params (the `var params = {...}` JSON and the
 * `data-js="video-player"` attributes), the `embed-api.json` /
 * `player/video.js` / `player/loader.js` / `inline/video/{id}.js` fallback
 * chain, the source rows (one HLS row for m3u8, direct rows with the
 * numeric quality), the vtt/srt/tml captions, the `__PRELOADED_STATE__`
 * description, and the series thumb-title entries. Limitations: the
 * `_EMBED_REGEX` generic discovery is not carried (GenericIE stays out);
 * the JSON-LD merge on the params path is not carried (the port's JsonLd
 * has no VideoObject mapping); the `tags`, `series`, `season`, and
 * `categories` fields are not modeled and are dropped; the series playlist
 * takes the URL slug as its id (upstream leaves it unset). No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.condenast

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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val SITES = "allure|architecturaldigest|arstechnica|bonappetit|brides|cnevids|" +
    "cntraveler|details|epicurious|glamour|golfdigest|gq|newyorker|self|teenvogue|" +
    "vanityfair|vogue|wired|wmagazine"

private const val VALID_URL_BASE = "https?://(?:video|www|player(?:-backend)?)\\.(?:$SITES)\\.com/"

/** Upstream `CondeNastIE`: the shared custom HTML5 player. */
class CondeNastIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value
        if (videoId != null) {
            return extractVideo(
                mapOf(
                    "videoId" to videoId,
                    "playerId" to match.groups["playerid"]?.value,
                    "target" to match.groups["target"]?.value,
                ),
            ).copy(webpageUrl = url)
        }

        val displayId = match.groups["displayid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val urlType = match.groups["type"]?.value
        val webpage = http.downloadWebpage(url)

        if (urlType == "series") return extractSeries(url, displayId, webpage)

        val preloaded = ExtractorUtils.searchRegex(
            "(?s)__PRELOADED_STATE__\\s*=\\s*(\\{.+?\\});",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
        )?.let { ExtractorUtils.parseJson(ExtractorUtils.jsToJson(it)) as? JsonObject }
        val preloadedVideo = preloaded?.obj("transformed")?.obj("video")
        val params = if (preloadedVideo != null) {
            mapOf("videoId" to preloadedVideo.str("id"))
        } else {
            extractVideoParams(webpage)
        }
        val info = extractVideo(params)
        return info.copy(
            description = preloadedVideo?.str("description")?.trim() ?: info.description,
            webpageUrl = url,
        )
    }

    /** Upstream `_extract_series`: the thumb-title paths as child entries. */
    private fun extractSeries(url: String, displayId: String, webpage: String): InfoDict {
        val title = ExtractorUtils.searchRegex(
            "(?s)<div class=\"cne-series-info\">.*?<h1>(.+?)</h1>",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
        ) ?: throw ExtractionError.Malformed("The Condé Nast series page carried no title.")
        val scheme = url.substringBefore("://")
        val host = url.substringAfter("://").substringBefore('/')
        val baseUrl = "$scheme://$host"
        val paths = mutableListOf<String>()
        for (pathMatch in SERIES_PATH.findAll(webpage)) {
            val path = pathMatch.groupValues[1]
            if (path !in paths) paths += path
        }
        return InfoDict(
            id = displayId,
            title = title,
            entries = paths.map { InfoEntry(url = "$baseUrl$it") },
            webpageUrl = url,
            extractor = "condenast:series",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_extract_video_params`: the JSON or the player attributes. */
    private fun extractVideoParams(webpage: String): Map<String, String?> {
        val paramsText = ExtractorUtils.searchRegex(
            "(?s)var\\s+params\\s*=\\s*(\\{.+?\\})[;,]",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
            default = "{}",
        )
        val query = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(paramsText ?: "{}")) as? JsonObject
        if (query != null && query.isNotEmpty()) {
            val videoId = ExtractorUtils.searchRegex(
                "(?:data-video-id=|currentVideoId\\s*=\\s*)[\"']([\\da-f]+)",
                webpage,
            )
            return mapOf("videoId" to videoId)
        }
        val tag = ExtractorUtils.searchRegex(
            "(<[^>]+data-js=\"video-player\"[^>]+>)",
            webpage,
        ) ?: throw ExtractionError.Malformed("The Condé Nast page carried no player element.")
        val attributes = extractAttributes(tag)
        return mapOf(
            "videoId" to attributes["data-video"],
            "playerId" to attributes["data-player"],
            "target" to attributes["id"],
        )
    }

    /** Upstream `_extract_video`: the fallback chain and the player info. */
    private suspend fun extractVideo(params: Map<String, String?>): InfoDict {
        val videoId = params["videoId"]
            ?: throw ExtractionError.Malformed("The Condé Nast player carried no video id.")

        var videoInfo: JsonObject? = null
        runCatching {
            val query = queryString(params.filterValues { it != null } + ("embedType" to "inline"))
            http.downloadJson("http://player.cnevids.com/embed-api.json?$query")
        }.getOrNull()?.let { videoInfo = (it as? JsonObject)?.obj("video") }

        if (videoInfo == null && params["playerId"] != null) {
            runCatching {
                http.downloadJson("http://player.cnevids.com/player/video.js?${queryString(params)}")
            }.getOrNull()?.let { videoInfo = (it as? JsonObject)?.obj("video") }
        }

        if (videoInfo == null) {
            runCatching {
                http.downloadWebpage("http://player.cnevids.com/player/loader.js?${queryString(params)}")
            }.getOrNull()?.let { videoInfo = configVideo(it) }
        }
        if (videoInfo == null) {
            val target = params["target"] ?: "embedplayer"
            runCatching {
                http.downloadWebpage("https://player.cnevids.com/inline/video/$videoId.js?target=$target")
            }.getOrNull()?.let { videoInfo = configVideo(it) }
        }

        val info = videoInfo
            ?: throw ExtractionError.Malformed("Unable to find the Condé Nast video info.")

        val formats = mutableListOf<MediaFormat>()
        for (element in info.array("sources").orEmpty()) {
            val source = element as? JsonObject ?: continue
            val src = source.str("src") ?: continue
            val ext = ExtractorUtils.mimetype2ext(source.str("type")) ?: ExtractorUtils.determineExt(src)
            if (ext == "m3u8") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = src,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                continue
            }
            val quality = source.str("quality")
            formats += MediaFormat(
                formatId = ext + (quality?.let { "-$it" } ?: ""),
                url = src,
                ext = ext,
                quality = if (quality == "high") "1" else "0",
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for ((type, value) in info.obj("captions").orEmpty()) {
            if (type !in setOf("vtt", "srt", "tml")) continue
            val captionUrl = (value as? JsonObject)?.str("src") ?: continue
            subtitles += SubtitleTrack(
                language = "en",
                formats = listOf(SubtitleFormat(ext = type, url = captionUrl)),
            )
        }

        return InfoDict(
            id = videoId,
            title = info.str("title"),
            formats = formats,
            thumbnails = info.str("poster_frame")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            uploader = info.str("brand"),
            duration = info.number("duration"),
            uploadDate = info.str("premiere_date")?.let(ExtractorUtils::unifiedStrdate),
            subtitles = subtitles,
            extractor = "condenast",
            extractorKey = ieKey,
        )
    }

    /** Upstream `var config = {...}` fallback. */
    private fun configVideo(webpage: String): JsonObject? {
        val config = ExtractorUtils.searchRegex(
            "(?s)var\\s+config\\s*=\\s*(\\{.+?\\});",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
        ) ?: return null
        return (ExtractorUtils.parseJson(ExtractorUtils.jsToJson(config)) as? JsonObject)?.obj("video")
    }

    companion object {
        const val IE_KEY: String = "CondeNast"

        private val SERIES_PATH = Regex(
            "(?s)<p class=\"cne-thumb-title\">.*?<a href=\"(/watch/.+?)[\"?]",
        )

        val VALID_URL: Regex = Regex(
            VALID_URL_BASE +
                "(?:(?:(?:embed(?:js)?|(?:script|inline)/video)/" +
                "(?<id>[0-9a-f]{24})(?:/(?<playerid>[0-9a-f]{24}))?" +
                "(?:.+?\\btarget=(?<target>[^&]+))?)|" +
                "(?<type>watch|series|video)/(?<displayid>[^/?#]+))",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `extract_attributes` for one tag. */
private fun extractAttributes(tag: String): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        val value = match.groupValues[2]
            .ifEmpty { match.groupValues[3] }
            .ifEmpty { match.groupValues[4] }
        attributes[match.groupValues[1].lowercase()] = value
    }
    return attributes
}

private val ATTRIBUTE = Regex(
    """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""",
)

private fun queryString(params: Map<String, String?>): String =
    params.entries.joinToString("&") { (name, value) ->
        "${percentEncode(name)}=${percentEncode(value.orEmpty())}"
    }

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

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
