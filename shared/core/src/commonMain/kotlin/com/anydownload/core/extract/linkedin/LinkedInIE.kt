/*
 * LinkedIn extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `linkedin.py` from
 * `yt_dlp/extractor/linkedin.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `linkedin.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public post page video element (`data-sources` JSON and
 * `data-captions-url`) plus the og/json-ld metadata. LinkedIn Learning and
 * LinkedIn Events need the `JSESSIONID` / `li_at` cookies, so those URL
 * forms match and fail typed. The port does not carry like counts, so they
 * are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.linkedin

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

/** Upstream `LinkedInIE`: a public post with a video. */
class LinkedInIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val videoTag = Regex("(<video[^>]+>)").find(webpage)
            ?: throw ExtractionError.Malformed("The LinkedIn page had no video element.")
        val attributes = tagAttributes(videoTag.value)
        val sources = ExtractorUtils.parseJson(attributes["data-sources"].orEmpty())
        val formats = mutableListOf<MediaFormat>()
        for (element in (sources as? JsonArray).orEmpty()) {
            val source = element as? JsonObject ?: continue
            val sourceUrl = source.str("src") ?: continue
            formats += MediaFormat(
                url = sourceUrl,
                ext = ExtractorUtils.mimetype2ext(source.str("type")),
                tbr = source.str("data-bitrate")?.toDoubleOrNull()?.let { it * 1000.0 },
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The LinkedIn video element had no sources.")
        }
        val captionsUrl = attributes["data-captions-url"]
        val subtitles = if (!captionsUrl.isNullOrBlank()) {
            listOf(
                SubtitleTrack(
                    language = "en",
                    formats = listOf(SubtitleFormat(ext = "vtt", url = captionsUrl)),
                ),
            )
        } else {
            emptyList()
        }
        val thumbnail = metaContent(webpage, "og:image")
        return InfoDict(
            id = videoId,
            title = metaContent(webpage, "og:title") ?: titleTag(webpage),
            description = metaContent(webpage, "og:description"),
            channel = jsonLdAuthor(webpage),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "linkedin",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "LinkedIn"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?linkedin\\.com/posts/[^/?#]+-(?<id>\\d+)-\\w{4}/?(?:[?#]|$)|" +
                "https?://(?:www\\.)?linkedin\\.com/feed/update/urn:li:activity:(?<id2>\\d+)",
        )
    }
}

/** Upstream `LinkedInLearningIE`: a Learning lesson. */
class LinkedInLearningIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "LinkedIn Learning needs the JSESSIONID cookie, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "LinkedInLearning"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?linkedin\\.com/learning/(?<course>[^/]+)/(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `LinkedInLearningCourseIE`: a Learning course. */
class LinkedInLearningCourseIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "LinkedIn Learning needs the JSESSIONID cookie, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "LinkedInLearningCourse"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?linkedin\\.com/learning/(?<id>[^/?#]+)")
    }
}

/** Upstream `LinkedInEventsIE`: an event page. */
class LinkedInEventsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "LinkedIn Events needs the li_at cookie, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "LinkedInEvents"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?linkedin\\.com/events/(?<id>[\\w-]+)")
    }
}

// ------------------------------------------------------------------ helpers

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

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

private fun titleTag(webpage: String): String? =
    Regex("(?s)<title[^>]*>(.*?)</title>").find(webpage)?.groupValues?.get(1)
        ?.trim()?.takeIf { it.isNotEmpty() }

/** Upstream `SocialMediaPosting.author.name` from the json-ld block. */
private fun jsonLdAuthor(webpage: String): String? {
    for (match in Regex("(?s)<script[^>]+type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>")
        .findAll(webpage)) {
        val element = ExtractorUtils.parseJson(match.groupValues[1])
        val objects = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
        for (obj in objects) {
            if (obj.str("@type") != "SocialMediaPosting") continue
            return obj.obj("author")?.str("name")
        }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
