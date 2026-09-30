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

    private val newDateCall = Regex("""new\s+Date\s*\(\s*(['"])(.*?)\1\s*\)""", RegexOption.DOT_MATCHES_ALL)
    private val newCall = Regex("""new\s+\w+\s*\(.*?\)""", RegexOption.DOT_MATCHES_ALL)
    private val parseIntegerCall = Regex("""parseInt\s*\(\s*['"]?(\d+)['"]?[^)]*\)""")
    private val unquotedKey = Regex("""([{,]\s*)([A-Za-z_$][A-Za-z0-9_$]*)(\s*:)""")
    private val undefinedValue = Regex("""\bundefined\b""")
    private val voidZero = Regex("""\bvoid\s+0\b""")
    private val hexInteger = Regex("""\b0[xX]([0-9a-fA-F]+)\b""")
    private val octalInteger = Regex("""\b0([0-7]+)\b""")
    private val trailingComma = Regex(""",\s*([\]}])""")

    /**
     * Upstream `js_to_json` subset (`_utils.py` at the pin). Normalizes a JS
     * literal into strict JSON for [parseJson]: comments are dropped,
     * single-quoted and backtick strings become double-quoted, unquoted
     * object keys are quoted, `undefined`/`void 0` become `null`, hex and
     * octal integers become decimal, `new Date("...")`/`new X(...)` wrappers
     * and `parseInt(...)` become values, and trailing commas are removed.
     * `vars` substitution, template interpolation, and `new Map(...)` stay
     * unported.
     */
    fun jsToJson(code: String): String {
        var text = newDateCall.replace(code) { match -> jsonString(match.groupValues[2]) }
        text = newCall.replace(text) { match -> jsonString(match.value) }
        text = parseIntegerCall.replace(text) { match -> match.groupValues[1] }

        val out = StringBuilder(text.length)
        val segment = StringBuilder()

        fun flushSegment() {
            if (segment.isEmpty()) return
            var piece = segment.toString()
            piece = unquotedKey.replace(piece) { match ->
                "${match.groupValues[1]}\"${match.groupValues[2]}\"${match.groupValues[3]}"
            }
            piece = undefinedValue.replace(piece, "null")
            piece = voidZero.replace(piece, "null")
            piece = hexInteger.replace(piece) { match ->
                match.groupValues[1].toLongOrNull(16)?.toString() ?: match.value
            }
            piece = octalInteger.replace(piece) { match ->
                match.groupValues[1].toLongOrNull(8)?.toString() ?: match.value
            }
            piece = trailingComma.replace(piece) { match -> match.groupValues[1] }
            out.append(piece)
            segment.setLength(0)
        }

        var index = 0
        while (index < text.length) {
            val char = text[index]
            when (char) {
                '\'', '`' -> {
                    flushSegment()
                    index++
                    val content = StringBuilder()
                    while (index < text.length && text[index] != char) {
                        if (text[index] == '\\' && index + 1 < text.length) {
                            val next = text[index + 1]
                            if (next == char) {
                                content.append(char)
                            } else {
                                content.append('\\').append(next)
                            }
                            index += 2
                        } else {
                            content.append(text[index])
                            index++
                        }
                    }
                    index++ // closing quote
                    out.append('"').append(escapeUnescapedQuotes(content.toString())).append('"')
                }

                '"' -> {
                    flushSegment()
                    out.append('"')
                    index++
                    while (index < text.length && text[index] != '"') {
                        if (text[index] == '\\' && index + 1 < text.length) {
                            out.append(text[index]).append(text[index + 1])
                            index += 2
                        } else {
                            out.append(text[index])
                            index++
                        }
                    }
                    if (index < text.length) {
                        out.append('"')
                        index++
                    }
                }

                else -> {
                    when {
                        char == '/' && index + 1 < text.length && text[index + 1] == '*' -> {
                            index += 2
                            while (index + 1 < text.length && !(text[index] == '*' && text[index + 1] == '/')) index++
                            index = (index + 2).coerceAtMost(text.length)
                        }

                        char == '/' && index + 1 < text.length && text[index + 1] == '/' -> {
                            index += 2
                            while (index < text.length && text[index] != '\n') index++
                            segment.append(' ')
                        }

                        else -> {
                            segment.append(char)
                            index++
                        }
                    }
                }
            }
        }
        flushSegment()
        return out.toString()
    }

    /** One JS value as a strict JSON string literal. */
    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun escapeUnescapedQuotes(content: String): String {
        val out = StringBuilder(content.length)
        for (index in content.indices) {
            val char = content[index]
            if (char == '"') {
                var backslashes = 0
                var probe = index - 1
                while (probe >= 0 && content[probe] == '\\') {
                    backslashes++
                    probe--
                }
                if (backslashes % 2 == 0) out.append('\\')
            }
            out.append(char)
        }
        return out.toString()
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

    private val usAgeRatings = mapOf("G" to 0, "PG" to 10, "PG-13" to 13, "R" to 16, "NC" to 18)
    private val tvParentalAgeRatings = mapOf(
        "TV-Y" to 0,
        "TV-Y7" to 7,
        "TV-G" to 0,
        "TV-PG" to 0,
        "TV-14" to 14,
        "TV-MA" to 17,
    )
    private val tvAgeRatings = Regex("^TV[_-]?(Y7|Y|G|PG|14|MA)$", RegexOption.IGNORE_CASE)

    /** Upstream `parse_age_limit`. */
    fun parseAgeLimit(value: String?): Int? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        usAgeRatings[text.uppercase()]?.let { return it }
        Regex("^(?<age>\\d{1,2})\\+?$").matchEntire(text)?.let {
            return it.groups["age"]?.value?.toIntOrNull()
        }
        val match = tvAgeRatings.matchEntire(text.uppercase()) ?: return null
        return tvParentalAgeRatings["TV-" + match.groupValues[1].uppercase()]
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

    /**
     * The `KNOWN_EXTENSIONS` subset `determine_ext` needs for the generic
     * HLS/DASH slice; upstream's full set stays out (T-136).
     */
    private val knownExtensions = setOf(
        "mp4", "m4v", "mov", "webm", "mkv", "flv", "3gp", "3g2", "avi",
        "m4a", "mp3", "opus", "ogg", "oga", "flac", "wav", "aac",
        "ts", "m3u8", "m3u", "mpd", "f4m", "ism",
    )

    /**
     * Upstream `determine_ext`: the tail after the last dot before the query,
     * when it is an alphanumeric token or a known extension followed by a
     * slash; [defaultExt] otherwise.
     */
    fun determineExt(url: String?, defaultExt: String = "unknown_video"): String {
        if (url == null || '.' !in url) return defaultExt
        val beforeQuery = url.substringBefore('?')
        val dot = beforeQuery.lastIndexOf('.')
        val guess = if (dot >= 0) beforeQuery.substring(dot + 1) else beforeQuery
        return when {
            guess.isNotEmpty() && guess.all { it.isLetterOrDigit() } -> guess
            guess.trimEnd('/') in knownExtensions -> guess.trimEnd('/')
            else -> defaultExt
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

    /**
     * Upstream `datetime.utcfromtimestamp` subset: the UTC `YYYYMMDD` date
     * for a Unix timestamp in seconds, from 1970 on.
     */
    fun epochSecondsToDate(epochSeconds: Long): String {
        val days = epochSeconds / 86_400
        var year = 1970
        var remaining = days
        while (true) {
            val length = if (isLeapYear(year)) 366 else 365
            if (remaining < length) break
            remaining -= length
            year++
        }
        val monthLengths = intArrayOf(
            31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
        )
        var month = 0
        while (remaining >= monthLengths[month]) {
            remaining -= monthLengths[month]
            month++
        }
        return formatDate(year, month + 1, remaining.toInt() + 1) ?: "19700101"
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    private fun formatDate(year: Int, month: Int, day: Int): String? {
        if (year <= 0 || month !in 1..12 || day !in 1..31) return null
        return "${year.toString().padStart(4, '0')}${month.toString().padStart(2, '0')}${
            day.toString().padStart(2, '0')
        }"
    }
}
