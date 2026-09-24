/*
 * Extractor helpers — AnyDownload
 *
 * Kotlin translations of a subset of `yt_dlp/utils/_utils.py` and the
 * `_search_regex`/`_html_search_meta`/`_parse_json` helpers in
 * `yt_dlp/extractor/common.py`, read at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf) on 2026-09-24.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * Each function names the upstream helper it mirrors. `_utils.py` and
 * `common.py` are not vendored; only this subset is translated. See
 * shared/core/NOTICE.md and port/manifest.json.
 */
package com.anydownlod.core.extract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One step of a [ExtractorUtils.traverse] path. Mirrors the `traverse_obj`
 * path elements D4's extractors use: string keys, integer indices, `..`
 * value expansion, and type filters such as `{dict}`.
 */
sealed interface TraverseStep {
    data class Key(val name: String) : TraverseStep
    data class Index(val index: Int) : TraverseStep

    /** Upstream `..`: every object value or every array item. */
    data object Values : TraverseStep

    /** Upstream `{dict}` / `{str}` / `{list}` style filter. */
    data class Filter(val predicate: (JsonElement) -> Boolean) : TraverseStep
}

object TraverseFilters {
    val isObject: (JsonElement) -> Boolean = { it is JsonObject }
    val isArray: (JsonElement) -> Boolean = { it is JsonArray }
    val isString: (JsonElement) -> Boolean = { it is JsonPrimitive && it.isString }
    val isNumber: (JsonElement) -> Boolean = { it is JsonPrimitive && !it.isString }
}

/** Codecs parsed out of a `mimeType`; null means the codec was not declared. */
data class Codecs(val vcodec: String? = null, val acodec: String? = null)

object ExtractorUtils {

    // ------------------------------------------------------------ regex/meta

    /**
     * Upstream `_search_regex`: first match of [pattern] in [string], group
     * [group] or [default] when there is none.
     */
    fun searchRegex(
        pattern: String,
        string: String,
        flags: Set<RegexOption> = emptySet(),
        group: Int = 1,
        default: String? = null,
    ): String? {
        val match = Regex(pattern, flags).find(string) ?: return default
        val groups = match.groups
        if (group !in 0 until groups.size) return default
        return groups[group]?.value ?: default
    }

    private val metaTag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex(
        """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""",
    )

    /**
     * Upstream `_html_search_meta`: the `content` of the first `<meta>` whose
     * `name`, `property`, or `itemprop` matches one of [names].
     */
    fun htmlSearchMeta(html: String, vararg names: String): String? {
        val wanted = names.map { it.lowercase() }.toSet()
        for (tag in metaTag.findAll(html)) {
            val attributes = parseAttributes(tag.value)
            val key = (attributes["name"] ?: attributes["property"] ?: attributes["itemprop"])
                ?.lowercase() ?: continue
            if (key !in wanted) continue
            val content = attributes["content"] ?: continue
            return unescapeHtml(content)
        }
        return null
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val attributes = linkedMapOf<String, String>()
        for (match in attribute.findAll(tag)) {
            val value = match.groupValues[2]
                .ifEmpty { match.groupValues[3] }
                .ifEmpty { match.groupValues[4] }
            attributes[match.groupValues[1].lowercase()] = value
        }
        return attributes
    }

    // -------------------------------------------------------------- json/tree

    /**
     * Upstream `_parse_json`, lenient: a JSONP wrapper or surrounding text is
     * tolerated by taking the outermost object or array. Returns null unless
     * [fatal], in which case a typed [ExtractionError.Malformed] is raised.
     */
    fun parseJson(text: String?, fatal: Boolean = false): JsonElement? {
        if (text.isNullOrBlank()) {
            if (fatal) throw ExtractionError.Malformed("The response was empty.")
            return null
        }
        val trimmed = text.trim()
        runCatching { return Json.parseToJsonElement(trimmed) }
        val start = trimmed.indexOfFirst { it == '{' || it == '[' }
        val end = trimmed.indexOfLast { it == '}' || it == ']' }
        if (start in 0 until end) {
            runCatching { return Json.parseToJsonElement(trimmed.substring(start, end + 1)) }
        }
        if (fatal) throw ExtractionError.Malformed("The response was not valid JSON.")
        return null
    }

    /**
     * Upstream `traverse_obj` subset. Every surviving value is returned in
     * document order; a step that does not apply drops that branch instead of
     * failing.
     */
    fun traverse(root: JsonElement?, steps: List<TraverseStep>): List<JsonElement> {
        var current = if (root == null) emptyList() else listOf(root)
        for (step in steps) {
            current = current.flatMap { element -> applyStep(element, step) }
        }
        return current
    }

    private fun applyStep(element: JsonElement, step: TraverseStep): List<JsonElement> = when (step) {
        is TraverseStep.Key -> (element as? JsonObject)?.get(step.name)?.let(::listOf) ?: emptyList()
        is TraverseStep.Index -> (element as? JsonArray)?.getOrNull(step.index)?.let(::listOf) ?: emptyList()
        TraverseStep.Values -> when (element) {
            is JsonObject -> element.values.toList()
            is JsonArray -> element.toList()
            else -> emptyList()
        }

        is TraverseStep.Filter -> if (step.predicate(element)) listOf(element) else emptyList()
    }

    // ---------------------------------------------------------------- scalars

    private val urlWithScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://[^\\s/]+")

    /** Upstream `url_or_none`: a trimmed absolute URL, or null. */
    fun urlOrNone(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it.isWhitespace() || it.code < 0x20 }) return null
        return if (urlWithScheme.containsMatchIn(trimmed)) trimmed else null
    }

    /** Upstream `int_or_none`: booleans and non-numeric strings stay null. */
    fun intOrNone(value: Any?): Long? = when (value) {
        null -> null
        is Boolean -> null
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        is Double -> value.takeIf { it.isFinite() }?.toLong()
        is Float -> value.takeIf { it.isFinite() }?.toLong()
        is String -> value.trim().toLongOrNull()
            ?: value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
        else -> intOrNone(value.toString())
    }

    /** Upstream `float_or_none`. */
    fun floatOrNone(value: Any?): Double? = when (value) {
        null -> null
        is Boolean -> null
        is Double -> value.takeIf { it.isFinite() }
        is Float -> value.takeIf { it.isFinite() }?.toDouble()
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> floatOrNone(value.toString())
    }

    /** Upstream `str_or_none`: a non-empty string form, or null. */
    fun strOrNone(value: Any?): String? {
        if (value == null) return null
        val text = value.toString()
        return text.ifEmpty { null }
    }

    private val numericEntity = Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")
    private val backslashEscape = Regex("""\\(u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|/)""")

    /** Upstream `unescapeHTML`/`clean_html` entity and backslash decode. */
    fun unescapeHtml(value: String?): String? {
        if (value == null) return null
        val named = value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        val numeric = numericEntity.replace(named) { match ->
            val code = match.groupValues[1].ifEmpty { null }?.toIntOrNull(16)
                ?: match.groupValues[2].toIntOrNull(10)
            code?.let { char -> char.toChar().toString() } ?: match.value
        }
        return backslashEscape.replace(numeric) { match ->
            val escape = match.groupValues[1]
            when {
                escape == "/" -> "/"
                escape.startsWith("u") -> escape.substring(1).toIntOrNull(16)?.toChar()?.toString() ?: match.value
                else -> escape.substring(1).toIntOrNull(16)?.toChar()?.toString() ?: match.value
            }
        }
    }

    // ---------------------------------------------------------------- formats

    private val videoCodecs = listOf("avc", "av01", "vp0", "vp8", "vp9", "hev", "hvc", "dvh", "mp4v", "theora")
    private val audioCodecs = listOf("mp4a", "opus", "vorbis", "ac-3", "ec-3", "flac", "dts", "aac", "alac")

    /**
     * Upstream `parse_codecs`: split the `codecs=` parameter of [mimeType] and
     * classify each entry by its prefix. Missing codecs stay null.
     */
    fun parseCodecs(mimeType: String?): Codecs {
        val codecs = mimeType?.substringAfter("codecs=", "")?.trim()?.trim('"', '\'') ?: return Codecs()
        if (codecs.isEmpty()) return Codecs()
        var video: String? = null
        var audio: String? = null
        for (raw in codecs.split(',')) {
            val codec = raw.trim().trim('"', '\'')
            if (codec.isEmpty()) continue
            val lower = codec.lowercase()
            when {
                videoCodecs.any { lower.startsWith(it) } -> video = codec
                audioCodecs.any { lower.startsWith(it) } -> audio = codec
                else -> Unit
            }
        }
        return Codecs(video, audio)
    }

    /** Upstream `mimetype2ext`. Parameter suffixes and casing are ignored. */
    fun mimetype2ext(mimeType: String?): String? {
        val mime = mimeType?.trim()?.substringBefore(';')?.lowercase() ?: return null
        if (mime.isEmpty()) return null
        return when (mime) {
            "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> "m3u8"
            "application/dash+xml" -> "mpd"
            "application/vnd.ms-sstr+xml" -> "ism"
            "application/f4m", "application/f4m+xml", "application/x-f4m" -> "f4m"
            "video/mp4" -> "mp4"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/x-flac", "audio/flac" -> "flac"
            "audio/opus" -> "opus"
            "audio/ogg", "application/ogg", "audio/vorbis" -> "ogg"
            "video/webm" -> "webm"
            "audio/webm" -> "webm"
            "video/x-flv" -> "flv"
            "video/3gpp" -> "3gp"
            "video/3gpp2" -> "3g2"
            "video/quicktime" -> "mov"
            "video/x-matroska" -> "mkv"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> {
                val subtype = mime.substringAfter('/', "")
                if (subtype.isEmpty()) null else subtype.removePrefix("x-").removePrefix("vnd.")
            }
        }
    }

    // ------------------------------------------------------------------ dates

    private val iso8601Duration = Regex(
        "^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
    )

    /** Upstream `parse_iso8601` for durations: `PT1H2M3S` becomes 3723. */
    fun parseIso8601(value: String?): Long? {
        val text = value?.trim() ?: return null
        val match = iso8601Duration.matchEntire(text) ?: return null
        val weeks = match.groupValues[1].toLongOrNull() ?: 0L
        val days = match.groupValues[2].toLongOrNull() ?: 0L
        val hours = match.groupValues[3].toLongOrNull() ?: 0L
        val minutes = match.groupValues[4].toLongOrNull() ?: 0L
        val seconds = match.groupValues[5].toDoubleOrNull() ?: 0.0
        val total = weeks * 604_800 + days * 86_400 + hours * 3_600 + minutes * 60 + seconds.toLong()
        return if (match.groupValues.drop(1).all { it.isEmpty() }) null else total
    }

    private val months = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12,
    )

    private val weekdayPrefix = Regex("^(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*,?\\s+", RegexOption.IGNORE_CASE)

    /** Upstream `unified_strdate` for the common forms; emits `YYYYMMDD`. */
    fun unifiedStrdate(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        val text = weekdayPrefix.replace(trimmed, "")
        isoDate.find(text)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            return formatDate(year, month, day)
        }
        dayFirstDate.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val year = match.groupValues[3].toIntOrNull() ?: return@let
            return formatDate(year, month, day)
        }
        monthNameDate.find(text)?.let { match ->
            val month = months[match.groupValues[1].lowercase().take(3)] ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val year = match.groupValues[3].toIntOrNull() ?: return@let
            return formatDate(year, month, day)
        }
        dayMonthNameDate.find(text)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = months[match.groupValues[2].lowercase().take(3)] ?: return@let
            val year = match.groupValues[3].toIntOrNull() ?: return@let
            return formatDate(year, month, day)
        }
        return null
    }

    private val isoDate = Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})")

    private val dayFirstDate = Regex("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})")

    private val monthNameDate = Regex(
        "(?i)([A-Za-z]{3,9})\\s+(\\d{1,2}),?\\s+(\\d{4})",
    )

    private val dayMonthNameDate = Regex(
        "(?i)(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})",
    )

    private fun formatDate(year: Int, month: Int, day: Int): String? {
        if (year <= 0 || month !in 1..12 || day !in 1..31) return null
        return "${year.toString().padStart(4, '0')}${month.toString().padStart(2, '0')}${
            day.toString().padStart(2, '0')
        }"
    }
}
