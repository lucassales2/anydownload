/*
 * MediaStream / Win Sports extractors — AnyDownload
 *
 * Kotlin translation of `mediastream.py` from
 * `yt_dlp/extractor/mediastream.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mediastream.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the embed page's `window.MDSTRM.OPTIONS` JSON (one HLS row with the
 * `at`/`access_token`/`uid`/`sid`/`pid`/`av` query params, one MPD row, and
 * direct rows), the four geo-restriction messages, the og metadata, the
 * WinSports drupal-settings JSON and its `mediastream_formatter` lookup, and
 * the JSON-LD/player-script/iframe embed discovery. Limitations: the
 * MediaStreamVideoPlayer div pattern of `_extract_mediastream_urls` is not
 * carried (the JSON-LD, `playerMdStream.mdstreamVideo`, and iframe forms
 * are); m3u8/mpd subtitles are not parsed (one row per manifest);
 * `_extract_from_webpage` is not carried (GenericIE stays out); the
 * transparent WinSports dispatch instantiates `MediaStreamIE` directly. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.mediastream

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.JsonLd
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val EMBED_BASE_URL = "https://mdstrm.com/embed"
private const val BASE_URL_RE = "https?://mdstrm\\.com/(?:embed|live-stream)"

private val GEO_MESSAGES = listOf(
    "Debido a tu ubicación no puedes ver el contenido",
    "You are not allowed to watch this video: Geo Fencing Restriction",
    "Este contenido no está disponible en tu zona geográfica.",
    "El contenido sólo está disponible dentro de",
)

/** Upstream `MediaStreamBaseIE`: the embed-URL discovery. */
abstract class MediaStreamBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_mediastream_urls` subset. */
    protected fun extractMediastreamUrls(webpage: String): List<String> {
        val urls = mutableListOf<String>()
        for (obj in JsonLd.objects(webpage)) {
            if (obj.str("@type") != "VideoObject") continue
            for (name in listOf("embedUrl", "contentUrl")) {
                val value = obj.str(name) ?: continue
                if (Regex("$BASE_URL_RE/\\w+").containsMatchIn(value)) urls += value
            }
        }
        for (match in PLAYER_SCRIPT.findAll(webpage)) {
            urls += "$EMBED_BASE_URL/${match.groupValues[1]}"
        }
        for (match in IFRAME.findAll(webpage)) {
            urls += match.groupValues[1]
        }
        return urls.distinct()
    }

    companion object {
        private val PLAYER_SCRIPT = Regex(
            "<script[^>]+>[^>]*playerMdStream\\.mdstreamVideo\\(\\s*['\"](\\w+)",
        )
        private val IFRAME = Regex("<iframe[^>]+\\bsrc=\"($BASE_URL_RE/\\w+)")
    }
}

/** Upstream `MediaStreamIE`: one embed/live-stream page. */
class MediaStreamIE(
    http: ExtractorHttp,
) : MediaStreamBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        if (GEO_MESSAGES.any { it in webpage }) throw ExtractionError.GeoRestricted()

        val config = extractBalancedJson(webpage, Regex("window\\.MDSTRM\\.OPTIONS\\s*="))
            as? JsonObject
            ?: throw ExtractionError.Malformed("The MediaStream page carried no player options.")

        val formats = mutableListOf<MediaFormat>()
        for ((videoFormat, value) in config.obj("src").orEmpty()) {
            val src = (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: continue
            when (videoFormat) {
                "hls" -> {
                    val params = linkedMapOf<String, String?>(
                        "at" to "web-app",
                        "access_token" to queryValue(url, "access_token"),
                        "uid" to searchWindow(webpage, "MDSTRMUID"),
                        "sid" to searchWindow(webpage, "MDSTRMSID"),
                        "pid" to searchWindow(webpage, "MDSTRMPID"),
                        "av" to searchWindow(webpage, "VERSION"),
                    )
                    formats += MediaFormat(
                        formatId = "hls",
                        url = updateUrlQuery(src, params.filterValues { it != null }),
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                }
                "mpd" -> formats += MediaFormat(
                    formatId = "dash",
                    url = src,
                    ext = "mp4",
                    protocol = "mpd",
                )
                else -> formats += MediaFormat(url = src)
            }
        }

        return InfoDict(
            id = videoId,
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title") ?: config.str("title"),
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            thumbnails = ExtractorUtils.htmlSearchMeta(webpage, "og:image", "thumbnail")
                ?.let { listOf(Thumbnail(url = it)) }
                .orEmpty(),
            formats = formats,
            isLive = config.str("type") == "live",
            webpageUrl = url,
            extractor = "mediastream",
            extractorKey = ieKey,
        )
    }

    private fun searchWindow(webpage: String, name: String): String? =
        ExtractorUtils.searchRegex("window\\.$name\\s*=\\s*[\"']([^\"']+)[\"'];", webpage)

    companion object {
        const val IE_KEY: String = "MediaStream"

        val VALID_URL: Regex = Regex("$BASE_URL_RE/(?<id>\\w+)")
    }
}

/** Upstream `WinSportsVideoIE`: a Win Sports video page. */
class WinSportsVideoIE(
    http: ExtractorHttp,
) : MediaStreamBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val data = extractBalancedJson(
            webpage,
            Regex("<script\\s*[^>]+data-drupal-selector=\"drupal-settings-json\">"),
        ) as? JsonObject

        val fromSettings = data?.obj("settings")?.obj("mediastream_formatter")
            ?.values?.firstNotNullOfOrNull { formatter ->
                (formatter as? JsonObject)?.obj("mediastream_id")?.str("url")
            }
        val mediastreamUrl = fromSettings
            ?.let { urlJoin("$EMBED_BASE_URL/", it) }
            ?: extractMediastreamUrls(webpage).firstOrNull()
            ?: throw ExtractionError.NoFormats("No MediaStream embed found in webpage.")

        val jsonLdTitle = JsonLd.entries(webpage)
            .firstOrNull { it.str("@type") == "VideoObject" }?.str("title")
        val title = cleanHtml(removeEnd(
            jsonLdTitle ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            "| Win Sports",
        ))

        // Upstream `url_result(..., url_transparent=True)`; the port dispatches directly.
        val info = MediaStreamIE(http).extract(mediastreamUrl)
        return info.copy(
            id = info.id ?: displayId,
            title = title ?: info.title,
            webpageUrl = url,
        )
    }

    companion object {
        const val IE_KEY: String = "WinSportsVideo"

        val VALID_URL: Regex = Regex("https?://www\\.winsports\\.co/videos/(?<id>[\\w-]+)")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_search_json`: the balanced object after [marker]. */
private fun extractBalancedJson(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var index = start
    while (index < html.length) {
        val char = html[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return ExtractorUtils.parseJson(html.substring(start, index + 1))
                }
            }
        }
        index++
    }
    return null
}

/** Upstream `update_url_query`: merge the params, replacing existing keys. */
private fun updateUrlQuery(url: String, params: Map<String, String?>): String {
    val base = url.substringBefore('?')
    val merged = linkedMapOf<String, String>()
    for (pair in url.substringAfter('?', "").split('&')) {
        if (pair.isEmpty()) continue
        merged[pair.substringBefore('=')] = pair
    }
    for ((name, value) in params) {
        if (value == null) continue
        merged[name] = "${percentEncode(name)}=${percentEncode(value)}"
    }
    return if (merged.isEmpty()) base else base + "?" + merged.values.joinToString("&")
}

/** Upstream `parse_qs(url)['name'][0]`. */
private fun queryValue(url: String, name: String): String? {
    for (pair in url.substringAfter('?', "").split('&')) {
        if (pair.substringBefore('=') == name) return pair.substringAfter('=', "")
    }
    return null
}

/** Upstream `urljoin(base, path)` for the two forms the page carries. */
private fun urlJoin(base: String, path: String): String =
    if (path.startsWith("http")) path else base.trimEnd('/') + "/" + path.trimStart('/')

/** Upstream `remove_end`. */
private fun removeEnd(value: String?, end: String): String? {
    val text = value ?: return null
    return if (text.endsWith(end)) text.dropLast(end.length).trim() else text
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
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

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}
