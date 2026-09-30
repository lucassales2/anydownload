/*
 * ThePlatform base — AnyDownload
 *
 * Kotlin translation of the metadata and SMIL subset of `ThePlatformBaseIE`
 * from `yt_dlp/extractor/theplatform.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `theplatform.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `_download_theplatform_metadata`, `_parse_theplatform_metadata`, and
 * a small SMIL manifest reader for the `link.theplatform.com` documents the
 * NBC extractors request. The full `ThePlatformIE` URL extractor, F4M
 * formats, cookie-derived `hdnea3`, geo-verification headers, and hmac
 * signing of feed URLs stay with the D18 `theplatform.py` file. No cookie,
 * token, or signed URL is stored here.
 */
package com.anydownlod.core.extract.theplatform

import com.anydownlod.core.extract.Chapter
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.adobepass.AdobePassIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The fields of `_parse_theplatform_metadata` the port's InfoDict models. */
data class ThePlatformMetadata(
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Double? = null,
    val uploadDate: String? = null,
    val uploader: String? = null,
    val ageLimit: Int? = null,
    val chapters: List<Chapter> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
)

/** One `<video>` element of a SMIL manifest. */
data class SmilVideo(
    val src: String,
    val durationMs: Double? = null,
    val type: String? = null,
    val width: Long? = null,
    val height: Long? = null,
)

/**
 * The generated `link.theplatform.com` SMIL documents use a fixed namespace
 * and simple attributes; this reads the elements the NBC extractors need.
 */
object SmilManifest {
    private val element = Regex("<([A-Za-z][-A-Za-z0-9_:]*)((?:\\s+[^>]*)?)/?>", RegexOption.DOT_MATCHES_ALL)
    private val attribute = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun videos(xml: String): List<SmilVideo> = elements(xml, "video").mapNotNull { tag ->
        val src = attribute(tag, "src")?.let { ExtractorUtils.unescapeHtml(it) } ?: return@mapNotNull null
        SmilVideo(
            src = src,
            durationMs = attribute(tag, "dur")?.removeSuffix("ms")?.toDoubleOrNull(),
            type = attribute(tag, "type"),
            width = attribute(tag, "width")?.toLongOrNull(),
            height = attribute(tag, "height")?.toLongOrNull(),
        )
    }

    fun videoSources(xml: String): List<String> = videos(xml).map { it.src }

    /** Upstream `param[@name=exception]/@value`, the first one. */
    fun exceptionValue(xml: String): String? =
        elements(xml, "param").firstOrNull { attribute(it, "name") == "exception" }
            ?.let { attribute(it, "value") }

    /** Upstream `ref/@abstract`, the first one. */
    fun refAbstract(xml: String): String? =
        elements(xml, "ref").firstNotNullOfOrNull { attribute(it, "abstract") }

    /** `textstream` tracks: `(src, lang, type)`. */
    fun textStreams(xml: String): List<Triple<String, String?, String?>> =
        elements(xml, "textstream").mapNotNull { tag ->
            val src = attribute(tag, "src") ?: return@mapNotNull null
            Triple(src, attribute(tag, "lang"), attribute(tag, "type"))
        }

    private fun elements(xml: String, name: String): List<String> =
        element.findAll(xml)
            .filter { it.groupValues[1].substringAfter(':') == name }
            .map { it.value }
            .toList()

    private fun attribute(tag: String, name: String): String? {
        for (match in attribute.findAll(tag)) {
            if (match.groupValues[1].equals(name, ignoreCase = true)) {
                return match.groupValues[2].ifEmpty { match.groupValues[3] }
            }
        }
        return null
    }
}

/**
 * Upstream `ThePlatformBaseIE`: the metadata and SMIL calls shared by the
 * ThePlatform-backed site extractors. It extends the translated
 * [AdobePassIE] because upstream uses the Adobe Pass hooks in the same path.
 */
abstract class ThePlatformBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : AdobePassIE(ieKey = ieKey, http = http, validUrl = validUrl) {

    /** Upstream `_download_theplatform_metadata`. */
    protected suspend fun downloadTheplatformMetadata(
        path: String,
        videoId: String,
        fatal: Boolean = true,
    ): JsonObject? {
        val json = try {
            http.downloadJson("https://link.theplatform.com/s/$path?format=preview")
        } catch (error: ExtractionError) {
            if (fatal) throw error else return null
        }
        return json as? JsonObject
            ?: if (fatal) throw ExtractionError.Malformed("The ThePlatform metadata was not an object.") else null
    }

    /** Upstream `_parse_theplatform_metadata` for the modeled fields. */
    protected fun parseTheplatformMetadata(metadata: JsonObject?): ThePlatformMetadata {
        if (metadata == null) return ThePlatformMetadata()
        val chapters = metadata.array("chapters").orEmpty().mapNotNull { element ->
            val chapter = element as? JsonObject ?: return@mapNotNull null
            val start = chapter.number("startTime")?.div(1000.0) ?: return@mapNotNull null
            Chapter(startTime = start, endTime = chapter.number("endTime")?.div(1000.0))
        }.toList()
        val keepChapters = chapters.size > 1 || chapters.firstOrNull()?.endTime != null

        val subtitles = metadata.array("captions").orEmpty().mapNotNull { element ->
            val caption = element as? JsonObject ?: return@mapNotNull null
            val url = ExtractorUtils.urlOrNone(caption.str("src")) ?: return@mapNotNull null
            SubtitleTrack(
                language = caption.str("lang") ?: "en",
                formats = listOf(
                    SubtitleFormat(
                        ext = ExtractorUtils.mimetype2ext(caption.str("type")) ?: "vtt",
                        url = url,
                    ),
                ),
            )
        }.toList()

        val ageLimit = metadata.array("ratings").orEmpty()
            .mapNotNull { element -> (element as? JsonObject)?.str("rating") }
            .firstNotNullOfOrNull(ExtractorUtils::parseAgeLimit)

        return ThePlatformMetadata(
            title = metadata.str("title"),
            description = metadata.str("description"),
            thumbnailUrl = ExtractorUtils.urlOrNone(metadata.str("defaultThumbnailUrl")),
            durationSeconds = metadata.number("duration")?.div(1000.0),
            uploadDate = metadata.number("pubDate")?.div(1000)?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            uploader = metadata.str("billingCode"),
            ageLimit = ageLimit,
            chapters = if (keepChapters) chapters else emptyList(),
            subtitles = subtitles,
        )
    }

    /**
     * Upstream `_download_xml` for one SMIL manifest: a bounded GET with the
     * query upstream passes to `link.theplatform.com`.
     */
    protected suspend fun downloadSmil(url: String, query: Map<String, String>): String {
        val separator = if (url.contains('?')) '&' else '?'
        val encoded = query.entries.joinToString("&") { (key, value) ->
            encodeQueryValue(key) + "=" + encodeQueryValue(value)
        }
        return http.downloadWebpage("$url$separator$encoded")
    }
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
private const val HEX_DIGITS = "0123456789ABCDEF"

/** `urllib.parse.urlencode` component encoding, `+` for space. */
internal fun encodeQueryValue(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val character = byte.toInt().toChar()
        when {
            character in UNRESERVED -> append(character)
            character == ' ' -> append('+')
            else -> append('%')
                .append(HEX_DIGITS[(byte.toInt() shr 4) and 0xF])
                .append(HEX_DIGITS[byte.toInt() and 0xF])
        }
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
