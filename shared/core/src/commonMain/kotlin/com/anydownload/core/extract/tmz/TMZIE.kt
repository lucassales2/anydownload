/*
 * TMZ extractor — AnyDownload
 *
 * Kotlin translation of `tmz.py` from `yt_dlp/extractor/tmz.py` at upstream
 * tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tmz.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the JSON-LD VideoObject mapping (name/description/thumbnailUrl/
 * uploadDate/duration/contentUrl), the `.cueVideoById` YouTube fallback, and
 * the `twitter-tweet` blockquote link fallback. Limitations: the port's
 * JsonLd has no `_json_ld` VideoObject walker, so the mapping is written
 * here; the YouTube fallback dispatches one child entry at the watch URL
 * (upstream passes a bare id); the Twitter dispatch is one child entry;
 * `author`/`uploadDate` fold into `uploader`/`uploadDate`. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.tmz

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.JsonLd
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `TMZIE`: any tmz.com page. */
class TMZIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val webpage = http.downloadWebpage(url)

        val jsonLd = JsonLd.entries(webpage)
            .firstOrNull { it.str("@type")?.contains("VideoObject") == true || it.str("contentUrl") != null }
        if (jsonLd != null && (jsonLd.str("url") != null || jsonLd.str("contentUrl") != null)) {
            return fromJsonLd(jsonLd, url)
        }

        // Upstream `url_result(match.group('id'))` for the YouTube Player API.
        val cueId = ExtractorUtils.searchRegex(
            "\\.cueVideoById\\(\\s*([\"'])(.*?)\\1",
            webpage,
            group = 2,
        )
        if (cueId != null) {
            return InfoDict(
                id = cueId,
                entries = listOf(InfoEntry(id = cueId, url = "https://www.youtube.com/watch?v=$cueId")),
                webpageUrl = url,
                extractor = "tmz",
                extractorKey = ieKey,
            )
        }

        // Upstream `get_element_by_attribute('class', 'twitter-tweet', webpage)`.
        val tweet = elementByClass(webpage, "twitter-tweet")
        if (tweet != null) {
            for (match in TWEET_LINK.findAll(tweet)) {
                val link = match.groupValues[2]
                if ("/status/" in link) {
                    return InfoDict(
                        id = null,
                        entries = listOf(InfoEntry(url = link)),
                        webpageUrl = url,
                        extractor = "tmz",
                        extractorKey = ieKey,
                    )
                }
            }
        }

        throw ExtractionError.Unavailable("No video found!")
    }

    /** The `_json_ld` VideoObject fields the info dict models. */
    private fun fromJsonLd(jsonLd: JsonObject, url: String): InfoDict {
        val contentUrl = jsonLd.str("contentUrl")
        val formats = mutableListOf<MediaFormat>()
        if (contentUrl != null) {
            formats += if (ExtractorUtils.determineExt(contentUrl) == "m3u8") {
                MediaFormat(formatId = "hls", url = contentUrl, ext = "mp4", protocol = "m3u8_native")
            } else {
                MediaFormat(url = contentUrl)
            }
        }
        val thumbnails = mutableListOf<Thumbnail>()
        when (val thumbnail = jsonLd["thumbnailUrl"]) {
            is JsonPrimitive -> thumbnail.content?.let { thumbnails += Thumbnail(url = it) }
            is JsonArray -> for (element in thumbnail) {
                (element as? JsonPrimitive)?.content?.let { thumbnails += Thumbnail(url = it) }
            }
            else -> Unit
        }
        val uploader = when (val author = jsonLd["author"]) {
            is JsonPrimitive -> author.content?.takeIf { it.isNotBlank() }
            is JsonObject -> author.str("name")
            else -> null
        }
        return InfoDict(
            id = jsonLd.str("id") ?: url,
            title = jsonLd.str("name"),
            description = jsonLd.str("description"),
            uploadDate = (jsonLd.str("uploadDate") ?: jsonLd.str("datePublished"))
                ?.let(ExtractorUtils::unifiedStrdate),
            duration = ExtractorUtils.parseDuration(jsonLd.str("duration")),
            thumbnails = thumbnails,
            uploader = uploader,
            formats = formats,
            webpageUrl = url,
            extractor = "tmz",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TMZ"

        private val TWEET_LINK = Regex("<a[^>]+href=\\s*([\"'])(.*?)\\1")

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tmz\\.com/.*")
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

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}
