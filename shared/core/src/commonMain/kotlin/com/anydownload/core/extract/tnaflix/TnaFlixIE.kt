/*
 * TNAFlix network extractors — AnyDownload
 *
 * Kotlin translation of the public page/XML/JSON subset of `tnaflix.py`
 * from `yt_dlp/extractor/tnaflix.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tnaflix.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public page scan (hidden inputs, flashvars config), the
 * TNAFlix/MovieFap XML config formats and thumbnails, and the EMPFlix JSON
 * player `<source>` scan. The vkey/nkey query parameters come from the page
 * itself; fixtures use fake values. The port does not carry display ids,
 * comment counts, ratings, or categories, so they are dropped. No cookie,
 * token, or private URL is stored here.
 */
package com.anydownload.core.extract.tnaflix

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonObject

private const val MAX_THUMBS = 40

/** Shared upstream `TNAFlixNetworkBaseIE` behaviour. */
abstract class TnaFlixNetworkBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    private val titleRegex: String? = null,
    private val descriptionRegex: String? = null,
    private val uploaderRegex: String? = null,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    private val pattern: Regex = validUrl

    protected suspend fun extractNetwork(url: String): InfoDict {
        val match = pattern.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["display"]?.value?.takeIf { it.isNotBlank() }
            ?: match.groups["display2"]?.value?.takeIf { it.isNotBlank() }
            ?: videoId
        val webpage = http.downloadWebpage(url)
        val inputs = hiddenInputs(webpage)
        var cfgUrl = configUrl(webpage)
            ?: inputs["config"]?.let { protoRelative(it) }
        val query = mutableListOf<String>()
        if (cfgUrl == null && inputs["vkey"] != null && inputs["nkey"] != null) {
            cfgUrl = "http://cdn-fck.$host.com/$host/${inputs["vkey"]}.fid"
            query += "key=${inputs["nkey"]}"
            query += "VID=$videoId"
            query += "premium=1"
            query += "vip=1"
            query += "alpha="
        }
        val formats = mutableListOf<MediaFormat>()
        val thumbnails = mutableListOf<Thumbnail>()
        var jsonLdTitle: String? = null
        var jsonLdDescription: String? = null
        if (cfgUrl != null) {
            val suffix = if (query.isEmpty()) "" else "?" + query.joinToString("&")
            val cfgXml = http.downloadWebpage(cfgUrl + suffix, headers = mapOf("Referer" to url))
            regexText(cfgXml, "(?s)<videoLink>(.*?)</videoLink>")?.let { videoLink ->
                formats += MediaFormat(
                    url = protoRelative(ExtractorUtils.unescapeHtml(videoLink) ?: videoLink),
                    ext = regexText(cfgXml, "<videoConfig>\\s*<type>(.*?)</type>") ?: "flv",
                )
            }
            for (item in Regex("(?s)<quality>(.*?)</quality>").findAll(cfgXml)) {
                val block = item.groupValues[1]
                val videoLink = regexText(block, "(?s)<videoLink>(.*?)</videoLink>") ?: continue
                val res = regexText(block, "<res>(.*?)</res>")
                val height = res?.let { Regex("^(\\d+)[pP]").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                formats += MediaFormat(
                    url = protoRelative(ExtractorUtils.unescapeHtml(videoLink) ?: videoLink),
                    formatId = res,
                    height = height,
                )
            }
            thumbnails += timelineThumbnails(cfgXml)
            regexText(cfgXml, "(?s)<startThumb>(.*?)</startThumb>")?.let {
                thumbnails += Thumbnail(url = protoRelative(it.trim()))
            }
        } else {
            val playerJson = try {
                http.downloadJson(
                    "http://www.$host.com/ajax/video-player/$videoId",
                    headers = mapOf("Referer" to url),
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
            val player = playerJson?.str("html").orEmpty()
            for (sourceMatch in Regex("<source src=\"([^\"]+)\"").findAll(player)) {
                val videoUrl = sourceMatch.groupValues[1]
                val height = Regex("-(\\d+)p\\.").find(videoUrl)?.groupValues?.get(1)?.toLongOrNull()
                formats += MediaFormat(
                    url = protoRelative(videoUrl),
                    ext = videoUrl.substringAfterLast('.', "").takeIf { it.isNotBlank() },
                    height = height,
                    formatId = height?.let { "${it}p" },
                )
            }
            regexText(player, "data-poster=\"([^\"]+)\"")?.let {
                thumbnails += Thumbnail(url = protoRelative(it))
            }
            val jsonLd = ExtractorUtils.parseJson(
                Regex("(?s)<script[^>]+type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>")
                    .find(webpage)?.groupValues?.get(1) ?: "{}",
            ) as? JsonObject
            jsonLdTitle = jsonLd?.str("name")
            jsonLdDescription = jsonLd?.str("description")
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The TNAFlix network page returned no playable format.")
        }
        return InfoDict(
            id = videoId,
            title = extractField(webpage, titleRegex) ?: metaContent(webpage, "og:title") ?: jsonLdTitle,
            description = extractField(webpage, descriptionRegex)
                ?: metaContent(webpage, "og:description")
                ?: jsonLdDescription,
            duration = metaContent(webpage, "duration")?.let { parseDuration(it) },
            uploader = extractField(webpage, uploaderRegex),
            ageLimit = 18,
            thumbnails = thumbnails.distinctBy { it.url },
            formats = formats,
            webpageUrl = url,
            extractor = "tnaflix",
            extractorKey = ieKey,
        )
    }

    private fun configUrl(webpage: String): String? {
        val patterns = listOf(
            "flashvars\\.config\\s*=\\s*escape\\(\"([^\"]+)\"",
            "<input[^>]+name=\"config\\d?\" value=\"([^\"]+)\"",
            "config\\s*=\\s*([\"'])((?:https?:)?//(?:(?!\\1).)+)\\1",
        )
        for (pattern in patterns) {
            val match = Regex(pattern).find(webpage) ?: continue
            val value = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
            return protoRelative(value)
        }
        return null
    }

    private fun hiddenInputs(webpage: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (match in Regex("<input[^>]+name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"").findAll(webpage)) {
            out[match.groupValues[1]] = match.groupValues[2]
        }
        return out
    }

    private fun timelineThumbnails(cfgXml: String): List<Thumbnail> {
        val pattern = regexText(cfgXml, "(?s)<(?:imagePattern|pattern)>(.*?)</(?:imagePattern|pattern)>")
            ?: return emptyList()
        val first = regexText(cfgXml, "(?s)<(?:imageFirst|first)>(\\d+)</(?:imageFirst|first)>")
            ?.toIntOrNull() ?: return emptyList()
        val last = regexText(cfgXml, "(?s)<(?:imageLast|last)>(\\d+)</(?:imageLast|last)>")
            ?.toIntOrNull() ?: return emptyList()
        if (first > last) return emptyList()
        val width = regexText(cfgXml, "(?s)<imageWidth>(\\d+)</imageWidth>")?.toLongOrNull()
        val height = regexText(cfgXml, "(?s)<imageHeight>(\\d+)</imageHeight>")?.toLongOrNull()
        val out = mutableListOf<Thumbnail>()
        for (index in first..last) {
            out += Thumbnail(
                url = protoRelative(pattern.replace("#", index.toString())),
                width = width,
                height = height,
            )
            if (out.size >= MAX_THUMBS) break
        }
        return out
    }

    private fun extractField(webpage: String, pattern: String?): String? =
        pattern?.let { regexText(webpage, it) }
}

/** Upstream `TNAFlixNetworkEmbedIE`: a player embed (redirect). */
class TNAFlixNetworkEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = videoId,
            redirectUrl = "http://www.$host.com/category/$videoId/video$videoId",
            webpageUrl = url,
            extractor = "tnaflix:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TNAFlixNetworkEmbed"

        val VALID_URL: Regex = Regex("https?://player\\.(?<host>tnaflix|empflix)\\.com/video/(?<id>\\d+)")
    }
}

/** Upstream `TNAFlixIE`: a tnaflix.com video. */
class TNAFlixIE(
    http: ExtractorHttp,
) : TnaFlixNetworkBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    titleRegex = "<title>(.+?) - (?:TNAFlix Porn Videos|TNAFlix\\.com)</title>",
    descriptionRegex = "(?s)>Description:</[^>]+>(.+?)<",
    uploaderRegex = "<span>by\\s*<a[^>]+\\bhref=[\"']/profile/[^>]+>([^<]+)<",
) {
    override suspend fun extract(url: String): InfoDict = extractNetwork(url)

    companion object {
        const val IE_KEY: String = "TNAFlix"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<host>tnaflix)\\.com/[^/]+/(?<display>[^/]+)/video(?<id>\\d+)",
        )
    }
}

/** Upstream `EMPFlixIE`: an empflix.com video. */
class EMPFlixIE(
    http: ExtractorHttp,
) : TnaFlixNetworkBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    descriptionRegex = "(?s)>Description:</[^>]+>(.+?)<",
    uploaderRegex = "<span>by\\s*<a[^>]+\\bhref=[\"']/profile/[^>]+>([^<]+)<",
) {
    override suspend fun extract(url: String): InfoDict = extractNetwork(url)

    companion object {
        const val IE_KEY: String = "EMPFlix"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<host>empflix)\\.com/(?:videos/(?<display>.+?)-|" +
                "[^/]+/(?<display2>[^/]+)/video)(?<id>[0-9]+)",
        )
    }
}

/** Upstream `MovieFapIE`: a moviefap.com video. */
class MovieFapIE(
    http: ExtractorHttp,
) : TnaFlixNetworkBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = extractNetwork(url)

    companion object {
        const val IE_KEY: String = "MovieFap"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<host>moviefap)\\.com/videos/(?<id>[0-9a-f]+)/(?<display>[^/]+)\\.html",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun regexText(value: String, pattern: String): String? {
    val match = Regex(pattern).find(value) ?: return null
    val text = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
    return text.trim().takeIf { it.isNotEmpty() }
}

private fun protoRelative(value: String): String =
    if (value.startsWith("//")) "http:$value" else value

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

private fun parseDuration(value: String): Double? {
    val parts = value.trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    var seconds = 0.0
    for (part in parts) {
        val number = part.toDoubleOrNull() ?: return null
        seconds = seconds * 60 + number
    }
    return seconds
}

private fun JsonObject.str(name: String): String? =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }
        ?.content?.takeIf { it.isNotBlank() }
