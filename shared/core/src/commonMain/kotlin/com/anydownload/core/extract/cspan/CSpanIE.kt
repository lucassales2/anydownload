/*
 * C-SPAN extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `cspan.py` from
 * `yt_dlp/extractor/cspan.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `cspan.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `jwsetup` JWPlayer config (sources into rows, tracks into
 * caption tracks) plus the page metadata (title class/og title, description,
 * thumbnail, upload date, seclength duration, views), and the Ustream,
 * Brightcove and SenateISVP embeds as typed redirects. The obsolete
 * clip/prog ajax/fxml path and the JSON-LD merge are not translated; an
 * m3u8 source becomes one HLS row; the Ustream redirect needs the D19
 * `ustream.py` extractor. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.cspan

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val BRIGHTCOVE_URL_TEMPLATE =
    "http://players.brightcove.net/%s/%s_%s/index.html?videoId=%s"
private const val REFERER = "Referer"

/** One parsed JWPlayer config. */
private data class JwPlayer(
    val formats: List<MediaFormat>,
    val subtitles: List<SubtitleTrack>,
)

/** Upstream `CSpanIE`: a c-span.org video page. */
class CSpanIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        extractUstreamUrl(webpage)?.let { return redirect(it, url) }

        if (!url.contains("&vod")) {
            val brightcoveTag = Regex("(<[^>]+id='brightcove-player-embed'[^>]+>)").find(webpage)?.groupValues?.get(1)
            if (brightcoveTag != null) {
                val attributes = tagAttributes(brightcoveTag)
                val bcId = attributes["data-bcid"]
                if (bcId != null) {
                    val bcUrl = templateFill(
                        BRIGHTCOVE_URL_TEMPLATE,
                        attributes["data-bcaccountid"] ?: "3162030207001",
                        attributes["data-noprebcplayerid"] ?: "SyGGpuJy3g",
                        attributes["data-newbcplayerid"] ?: "default",
                        bcId,
                    )
                    return redirect(bcUrl, url)
                }
            }
        }

        val jwsetup = parseJwsetup(webpage)
        if (jwsetup != null) {
            val data = parseJwplayer(jwsetup, url, videoId)
            val title = elementByClass(webpage, "video-page-title")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            val description = elementContentByAttribute(webpage, "itemprop", "description")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "og:description")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "description")
            return InfoDict(
                id = videoId,
                title = title,
                description = description,
                duration = ExtractorUtils.searchRegex("jwsetup\\.seclength\\s*=\\s*(\\d+);", webpage)
                    ?.toDoubleOrNull(),
                uploadDate = elementContentByAttribute(webpage, "itemprop", "uploadDate")
                    ?.let(ExtractorUtils::unifiedStrdate),
                viewCount = ExtractorUtils.searchRegex(
                    "<span[^>]+class=['\"]views['\"][^>]*>([\\d,]+)\\s+Views</span>",
                    webpage,
                )?.replace(",", "")?.toLongOrNull(),
                thumbnails = listOfNotNull(
                    elementContentByAttribute(webpage, "itemprop", "thumbnailUrl")
                        ?.let { Thumbnail(url = it) },
                ),
                formats = data.formats,
                subtitles = data.subtitles,
                webpageUrl = url,
                extractor = "c-span",
                extractorKey = IE_KEY,
            )
        }

        extractSenateIsvpUrl(webpage)?.let { return redirect(it, url) }

        val errorMessage = elementByClass(webpage, "VLplayer-error-message")
        if (errorMessage != null) throw ExtractionError.Unavailable(errorMessage)
        throw ExtractionError.Malformed("Unable to find video id and type.")
    }

    private fun redirect(target: String, url: String): InfoDict = InfoDict(
        redirectUrl = target,
        webpageUrl = url,
        extractor = "c-span",
        extractorKey = IE_KEY,
    )

    companion object {
        const val IE_KEY: String = "CSpan"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?c-span\\.org/video/\\?(?<id>[0-9a-f]+)",
        )
    }
}

/** Upstream `CSpanCongressIE`: the congressional chronicle page. */
class CSpanCongressIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val query = queryParameters(url)
        val videoDate = query["date"]?.firstOrNull()
        var videoId = joinNonempty(query["chamber"]?.firstOrNull() ?: "senate", videoDate, "_")
        val webpage = http.downloadWebpage(url)
        if (videoDate == null) {
            ExtractorUtils.searchRegex(
                "jwsetup\\.clipprogdate = '(\\d{4}-\\d{2}-\\d{2})';",
                webpage,
            )?.let { videoId = "${videoId}_$it" }
        }
        val jwsetup = parseJwsetup(webpage)
            ?: throw ExtractionError.Malformed("The C-SPAN congress page had no jwsetup.")
        val data = parseJwplayer(jwsetup, url, videoId)
        val title = listOfNotNull(
            ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            ExtractorUtils.searchRegex(
                "(?s)<title>([^<]*?)</title>",
                webpage,
                setOf(RegexOption.DOT_MATCHES_ALL),
            ),
        ).firstOrNull()?.split('|')?.firstOrNull()?.replace(Regex("\\s+"), " ")?.trim()
        return InfoDict(
            id = videoId,
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "description"),
            formats = data.formats,
            subtitles = data.subtitles,
            webpageUrl = url,
            extractor = "c-span:congress",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CSpanCongress"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?c-span\\.org/congress/",
        )
    }
}

// ------------------------------------------------------------------ helpers

/**
 * Upstream `_parse_jwplayer_data` / `_parse_jwplayer_formats` subset: the
 * `sources` become rows (hls keeps the native protocol) and the
 * caption/subtitle `tracks` become subtitle tracks. The page referer rides
 * on every row like upstream `add_referer`.
 */
private fun parseJwplayer(jwsetup: JsonObject, baseUrl: String, videoId: String): JwPlayer {
    val formats = mutableListOf<MediaFormat>()
    for (element in jwsetup.array("sources").orEmpty()) {
        val source = element as? JsonObject ?: continue
        val file = source.str("file") ?: continue
        val sourceUrl = urlJoin(baseUrl, file)
        val type = source.str("type")
        val label = source.str("label")
        val height = source.number("height")?.toLong()
        val width = source.number("width")?.toLong()
        val tbr = source.number("bitrate") ?: source.number("tbr")
        if (type == "hls" || ExtractorUtils.determineExt(sourceUrl) == "m3u8") {
            formats += MediaFormat(
                formatId = label ?: "hls",
                url = sourceUrl,
                ext = "mp4",
                protocol = "m3u8_native",
                height = height,
                width = width,
                tbr = tbr,
                httpHeaders = mapOf(REFERER to baseUrl),
            )
        } else {
            formats += MediaFormat(
                formatId = label ?: height?.let { "${it}p" },
                url = sourceUrl,
                ext = ExtractorUtils.determineExt(sourceUrl),
                height = height,
                width = width,
                tbr = tbr,
                httpHeaders = mapOf(REFERER to baseUrl),
            )
        }
    }
    val subtitles = mutableListOf<SubtitleTrack>()
    for (element in jwsetup.array("tracks").orEmpty()) {
        val track = element as? JsonObject ?: continue
        val kind = track.str("kind") ?: continue
        if (kind != "captions" && kind != "subtitles") continue
        val file = track.str("file") ?: continue
        var ext = ExtractorUtils.determineExt(file, "vtt")
        if (ext == "php") ext = "vtt"
        subtitles += SubtitleTrack(
            language = track.str("label") ?: track.str("language") ?: "en",
            formats = listOf(SubtitleFormat(ext = ext, url = urlJoin(baseUrl, file))),
        )
    }
    if (formats.isEmpty()) throw ExtractionError.NoFormats("The C-SPAN player declared no source.")
    return JwPlayer(formats = formats, subtitles = subtitles)
}

/** `jwsetup\s*=\s*({.+?})\s*;` with a balanced-brace read of the object. */
private fun parseJwsetup(webpage: String): JsonObject? {
    val marker = Regex("jwsetup\\s*=\\s*").find(webpage) ?: return null
    val range = balancedObject(webpage, marker.range.last + 1) ?: return null
    val json = ExtractorUtils.jsToJson(webpage.substring(range.first, range.last + 1))
    return ExtractorUtils.parseJson(json) as? JsonObject
}

private fun balancedObject(value: String, start: Int): IntRange? {
    val open = value.indexOf('{', start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var index = open
    while (index < value.length) {
        val character = value[index]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' || character == '\'' -> inString = false
            }
        } else {
            when (character) {
                '"', '\'' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return open..index
                }
            }
        }
        index++
    }
    return null
}

/** Upstream `UstreamIE._EMBED_REGEX`: an iframe embed of ustream.tv. */
private fun extractUstreamUrl(webpage: String): String? = Regex(
    "<iframe[^>]+?src=([\"'])(https?://(?:www\\.)?(?:ustream\\.tv|video\\.ibm\\.com)/embed/.+?)\\1",
).find(webpage)?.groupValues?.get(2)

/** Upstream `SenateISVPIE._EMBED_REGEX`: an iframe embed of senate.gov. */
private fun extractSenateIsvpUrl(webpage: String): String? = Regex(
    "<iframe[^>]+src=['\"](https?://www\\.senate\\.gov/isvp/?\\?[^'\"]+)['\"]",
).find(webpage)?.groupValues?.get(1)

/** Upstream `get_element_by_class`: the text of the first matching element. */
private fun elementByClass(webpage: String, className: String): String? {
    val match = Regex(
        "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
            Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>(.*?)</\\1>",
    ).find(webpage) ?: return null
    return cleanHtml(match.groupValues[2])
}

/** Upstream `get_element_by_attribute` for the content of one element. */
private fun elementContentByAttribute(webpage: String, attribute: String, value: String): String? {
    val match = Regex(
        "(?s)<(\\w+)\\b(?=[^>]*\\b" + Regex.escape(attribute) + "\\s*=\\s*[\"']" +
            Regex.escape(value) + "[\"'])[^>]*>(.*?)</\\1>",
    ).find(webpage) ?: return null
    return cleanHtml(match.groupValues[2])
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private val ATTRIBUTE = Regex(
    "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
)

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        val value = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }
        out[match.groupValues[1].lowercase()] = value
    }
    return out
}

private fun queryParameters(url: String): Map<String, List<String>> {
    val query = url.substringAfter('?', "")
    val out = linkedMapOf<String, MutableList<String>>()
    for (pair in query.split('&')) {
        if (pair.isEmpty() || !pair.contains('=')) continue
        val key = pair.substringBefore('=')
        val value = pair.substringAfter('=')
        if (key.isEmpty() || value.isEmpty()) continue
        out.getOrPut(key) { mutableListOf() } += value
    }
    return out
}

private fun joinNonempty(first: String?, second: String?, delimiter: String): String =
    listOfNotNull(first, second).filter { it.isNotEmpty() }.joinToString(delimiter)

/** Multiplatform `%s` substitution for the Brightcove URL template. */
private fun templateFill(template: String, vararg values: String): String {
    var out = template
    for (value in values) out = out.replaceFirst("%s", value)
    return out
}

private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("//") -> "https:$value"
    value.startsWith("/") -> Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) + value
    else -> base.substringBefore('?').trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
