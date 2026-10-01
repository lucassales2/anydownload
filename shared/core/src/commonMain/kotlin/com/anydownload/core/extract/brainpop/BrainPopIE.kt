/*
 * BrainPOP extractors — AnyDownload
 *
 * Kotlin translation of the public API/page subset of `brainpop.py` from
 * `yt_dlp/extractor/brainpop.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `brainpop.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public published-content API (movie/topic data, access check,
 * high/low and audio-description keys, localizations, subtitle URLs) and the
 * legacy page scans (`var content =`, `ec_token`). Content that needs a
 * login fails typed with the API reason; manifest parsing is not translated,
 * so an m3u8 URL becomes one HLS row. No cookie, user token, or private URL
 * is stored here.
 */
package com.anydownload.core.extract.brainpop

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared upstream `BrainPOPBaseIE` format assembly. */
abstract class BrainPOPBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    protected val origin: String,
    private val videoUrl: String,
    private val hlsUrl: String,
    protected val cdnUrl: String,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_assemble_formats`. */
    protected fun assembleFormats(
        slug: String,
        formatId: String,
        token: String,
        extraFields: Map<String, Any?>,
    ): List<MediaFormat> {
        val hls = joinUrl(hlsUrl, "$slug.m3u8?$token")
        return listOf(
            MediaFormat(
                formatId = "$formatId-hls",
                url = hls,
                ext = "mp4",
                protocol = "m3u8_native",
            ),
            MediaFormat(
                formatId = formatId,
                url = joinUrl(videoUrl, "$slug?$token"),
            ),
        ).map { format ->
            format.copy(
                language = extraFields["language"] as? String,
                languagePreference = extraFields["languagePreference"] as? Double,
                preference = extraFields["quality"] as? Int,
                sourcePreference = extraFields["sourcePreference"] as? Int,
                formatNote = extraFields["formatNote"] as? String,
            )
        }
    }

    /** Upstream `_extract_adaptive_formats`. */
    protected fun extractAdaptiveFormats(
        data: JsonObject,
        token: String,
        keyFormat: String,
        extraFields: Map<String, Any?>,
    ): List<MediaFormat> {
        val out = mutableListOf<MediaFormat>()
        val additional = listOf(
            "%s" to emptyMap<String, Any?>(),
            "ad_%s" to mapOf(
                "formatNote" to "Audio description",
                "sourcePreference" to -2,
            ),
        )
        for ((additionalKeyFormat, additionalFields) in additional) {
            for ((keyQuality, keyIndex) in listOf("high", "low").withIndex()) {
                val fullKeyIndex = additionalKeyFormat.replace(
                    "%s",
                    keyFormat.replace("%s", keyIndex.toString()),
                )
                val slug = data.str(fullKeyIndex) ?: continue
                out += assembleFormats(
                    slug = slug,
                    formatId = fullKeyIndex,
                    token = token,
                    extraFields = mapOf("quality" to (-1 - keyQuality)) + additionalFields + extraFields,
                )
            }
        }
        return out
    }

    /** Upstream `_parse_js_topic_data` for the legacy page scan. */
    protected fun parseJsTopicData(
        topicData: JsonObject,
        token: String,
        extraFields: Map<String, Any?>,
    ): InfoDict {
        val movieData = topicData.obj("movies")
            ?: throw ExtractionError.Malformed("The BrainPOP page had no movie data.")
        return InfoDict(
            id = topicData.primitive("EntryID"),
            title = topicData.str("name"),
            description = topicData.str("synopsis"),
            formats = extractAdaptiveFormats(movieData, token, "%s", extraFields),
            extractor = "brainpop",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `BrainPOPIE`: a brainpop.com movie. */
class BrainPOPIE(
    http: ExtractorHttp,
) : BrainPOPBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    origin = "https://www.brainpop.com",
    videoUrl = "https://svideos.brainpop.com",
    hlsUrl = "https://hls.brainpop.com",
    cdnUrl = "https://cdn.brainpop.com",
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val slug = match.groups["slug"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val movieData = (http.downloadJson(
            "https://api.brainpop.com/api/content/published/bp/en/$slug/movie?full=1",
        ) as? JsonObject)?.obj("data")
            ?: throw ExtractionError.Malformed("The BrainPOP movie API returned no data.")
        if (movieData.obj("access")?.boolean("allow") != true) {
            val reason = movieData.obj("access")?.str("reason") ?: "This video is not available."
            if ("logged" in reason) {
                throw ExtractionError.LoginRequired(reason)
            }
            throw ExtractionError.NoFormats(reason)
        }
        val topicData = (http.downloadJson(
            "https://api.brainpop.com/api/content/published/bp/en/$slug?full=1",
        ) as? JsonObject)?.obj("data")?.obj("topic")
            ?: movieData.obj("topic")
            ?: throw ExtractionError.Malformed("The BrainPOP topic API returned no topic.")
        val feature = movieData.obj("feature")
            ?: throw ExtractionError.Malformed("The BrainPOP movie had no feature.")
        val featureData = feature.obj("data")
            ?: throw ExtractionError.Malformed("The BrainPOP feature had no data.")
        val token = featureData.str("token").orEmpty()
        val formats = mutableListOf<MediaFormat>()
        formats += extractAdaptiveFormats(
            featureData,
            token,
            "%s_v2",
            mapOf(
                "language" to (feature.str("language") ?: "en"),
                "languagePreference" to 10.0,
            ),
        )
        for ((language, element) in feature.obj("localization").orEmpty()) {
            val localized = element as? JsonObject ?: continue
            formats += extractAdaptiveFormats(
                localized,
                localized.str("token").orEmpty(),
                "%s_v2",
                mapOf("language" to language, "languagePreference" to -10.0),
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The BrainPOP movie returned no playable format.")
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        for ((name, element) in featureData) {
            val language = Regex("^subtitles_(\\w+)$").find(name)?.groupValues?.get(1) ?: continue
            val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            subtitles += SubtitleTrack(
                language = language,
                formats = listOf(SubtitleFormat(ext = "vtt", url = cdnUrl + value)),
            )
        }
        return InfoDict(
            id = topicData.primitive("topic_id"),
            title = topicData.str("name"),
            description = topicData.str("synopsis"),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "brainpop",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrainPOP"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?brainpop\\.com/(?<slug>[^/]+/[^/]+/(?<id>[^/?#\u0026]+))",
        )
    }
}

/** Shared legacy page-scan extraction for the localized sites. */
private suspend fun extractLegacy(
    http: ExtractorHttp,
    url: String,
    displayId: String,
    origin: String,
): Pair<JsonObject, String> {
    val webpage = http.downloadWebpage(url)
    val marker = Regex("var\\s+content\\s*=").find(webpage)
        ?: throw ExtractionError.Malformed("The BrainPOP page had no content data.")
    val start = webpage.indexOf('{', marker.range.last + 1)
    val end = webpage.indexOf(';', start)
    val json = if (start >= 0 && end > start) webpage.substring(start, end) else ""
    val content = ExtractorUtils.parseJson(json) as? JsonObject
        ?: throw ExtractionError.Malformed("The BrainPOP content data was not JSON.")
    val topic = content.obj("category")?.obj("unit")?.obj("topic")
        ?: throw ExtractionError.Malformed("The BrainPOP page had no topic data.")
    val token = Regex("ec_token\\s*:\\s*['\"]([^'\"]+)").find(webpage)?.groupValues?.get(1)
        ?: throw ExtractionError.Malformed("The BrainPOP page had no video token.")
    return topic to token
}

/** Upstream `BrainPOPJrIE`: jr.brainpop.com. */
class BrainPOPJrIE(
    http: ExtractorHttp,
) : BrainPOPBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    origin = "https://jr.brainpop.com",
    videoUrl = "https://svideos-jr.brainpop.com",
    hlsUrl = "https://hls-jr.brainpop.com",
    cdnUrl = "https://cdn-jr.brainpop.com",
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (topic, token) = extractLegacy(http, url, displayId, origin)
        return parseJsTopicData(topic, token, emptyMap()).copy(
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrainPOPJr"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?jr\\.brainpop\\.com/(?<slug>[^/]+/[^/]+/(?<id>[^/?#\u0026]+))",
        )
    }
}

/** Upstream `BrainPOPELLIE`: ell.brainpop.com. */
class BrainPOPELLIE(
    http: ExtractorHttp,
) : BrainPOPBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    origin = "https://ell.brainpop.com",
    videoUrl = "https://svideos-esl.brainpop.com",
    hlsUrl = "https://hls-esl.brainpop.com",
    cdnUrl = "https://cdn-esl.brainpop.com",
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (topic, token) = extractLegacy(http, url, displayId, origin)
        return parseJsTopicData(topic, token, emptyMap()).copy(
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrainPOPELL"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?ell\\.brainpop\\.com/(?<slug>[^/]+/[^/]+/(?<id>[^/?#\u0026]+))",
        )
    }
}

/** Upstream `BrainPOPEspIE`: esp.brainpop.com. */
class BrainPOPEspIE(
    http: ExtractorHttp,
) : BrainPOPBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    origin = "https://esp.brainpop.com",
    videoUrl = "https://svideos.brainpop.com",
    hlsUrl = "https://hls.brainpop.com",
    cdnUrl = "https://cdn.brainpop.com/mx",
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (topic, token) = extractLegacy(http, url, displayId, origin)
        return parseJsTopicData(topic, token, emptyMap()).copy(
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrainPOPEsp"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?esp\\.brainpop\\.com/(?<slug>[^/]+/[^/]+/(?<id>[^/?#\u0026]+))",
        )
    }
}

/** Upstream `BrainPOPFrIE`: fr.brainpop.com. */
class BrainPOPFrIE(
    http: ExtractorHttp,
) : BrainPOPBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    origin = "https://fr.brainpop.com",
    videoUrl = "https://svideos.brainpop.com",
    hlsUrl = "https://hls.brainpop.com",
    cdnUrl = "https://cdn.brainpop.com/fr",
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (topic, token) = extractLegacy(http, url, displayId, origin)
        return parseJsTopicData(topic, token, emptyMap()).copy(
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrainPOPFr"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?fr\\.brainpop\\.com/(?<slug>[^/]+/[^/]+/(?<id>[^/?#\u0026]+))",
        )
    }
}

/** Upstream `BrainPOPIlIE`: il.brainpop.com. */
class BrainPOPIlIE(
    http: ExtractorHttp,
) : BrainPOPBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    origin = "https://il.brainpop.com",
    videoUrl = "https://svideos.brainpop.com",
    hlsUrl = "https://hls.brainpop.com",
    cdnUrl = "https://cdn.brainpop.com/he",
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val (topic, token) = extractLegacy(http, url, displayId, origin)
        return parseJsTopicData(topic, token, emptyMap()).copy(
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrainPOPIl"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?il\\.brainpop\\.com/(?<slug>[^/]+/[^/]+/(?<id>[^/?#\u0026]+))",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun joinUrl(base: String, path: String): String =
    if (base.endsWith("/")) base + path else "$base/$path"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
