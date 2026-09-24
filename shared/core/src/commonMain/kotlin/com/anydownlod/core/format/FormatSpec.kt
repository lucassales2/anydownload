/*
 * Format specification parser — AnyDownload
 *
 * Translation of the `build_format_selector` parser and `_build_format_filter`
 * from `yt_dlp/YoutubeDL.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), re-read 2026-09-24.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license and covers the grammar D4 uses:
 * `best`/`worst`/`b`/`w` with video/audio types and `*`, explicit format ids,
 * `[key op value]` filters, `/` fallback, `,` lists, `()` groups, and `+`
 * merge nodes. `YoutubeDL.py` itself is not vendored; see shared/core/NOTICE.md
 * and port/manifest.json.
 */
package com.anydownlod.core.format

import com.anydownlod.core.extract.MediaFormat

/** A parse failure; the message never carries format values. */
class FormatSpecException(message: String) : IllegalArgumentException(message)

/** One `[key op value]` filter, translated from upstream `_build_format_filter`. */
sealed interface FormatFilter {
    fun matches(format: MediaFormat): Boolean

    enum class NumericOp {
        LT, LE, GT, GE, EQ, NE,
        ;

        companion object {
            fun fromSymbol(symbol: String): NumericOp = when (symbol) {
                "<" -> LT
                "<=" -> LE
                ">" -> GT
                ">=" -> GE
                "=" -> EQ
                "!=" -> NE
                else -> throw FormatSpecException("Unknown numeric operator.")
            }
        }
    }

    enum class StringOp {
        EQ, PREFIX, SUFFIX, CONTAINS, REGEX,
        ;

        companion object {
            fun fromSymbol(symbol: String): StringOp = when (symbol) {
                "=" -> EQ
                "^=" -> PREFIX
                "$=" -> SUFFIX
                "*=" -> CONTAINS
                "~=" -> REGEX
                else -> throw FormatSpecException("Unknown string operator.")
            }
        }
    }

    data class Numeric(
        val key: String,
        val op: NumericOp,
        val value: Double,
        /** Upstream `?`: a missing attribute passes instead of failing. */
        val noneInclusive: Boolean = false,
    ) : FormatFilter {
        override fun matches(format: MediaFormat): Boolean {
            val actual = FormatFieldValue.number(format, key) ?: return noneInclusive
            return when (op) {
                NumericOp.LT -> actual < value
                NumericOp.LE -> actual <= value
                NumericOp.GT -> actual > value
                NumericOp.GE -> actual >= value
                NumericOp.EQ -> actual == value
                NumericOp.NE -> actual != value
            }
        }
    }

    data class Text(
        val key: String,
        val op: StringOp,
        val value: String,
        val negated: Boolean = false,
        val noneInclusive: Boolean = false,
    ) : FormatFilter {
        override fun matches(format: MediaFormat): Boolean {
            val actual = FormatFieldValue.text(format, key) ?: return noneInclusive
            val result = when (op) {
                StringOp.EQ -> actual == value
                StringOp.PREFIX -> actual.startsWith(value)
                StringOp.SUFFIX -> actual.endsWith(value)
                StringOp.CONTAINS -> actual.contains(value)
                StringOp.REGEX -> runCatching { Regex(value).containsMatchIn(actual) }.getOrDefault(false)
            }
            return if (negated) !result else result
        }
    }
}

/**
 * The one place filter keys are read from [MediaFormat]; shared by
 * [FormatFilter] and the sorter.
 */
internal object FormatFieldValue {
    fun number(format: MediaFormat, key: String): Double? = when (key.lowercase()) {
        "height" -> format.height?.toDouble()
        "width" -> format.width?.toDouble()
        "fps" -> format.fps
        "filesize" -> format.filesize?.toDouble()
        "filesize_approx" -> format.filesizeApprox?.toDouble()
        "tbr" -> format.tbr
        "vbr" -> format.vbr
        "abr" -> format.abr
        "asr" -> format.asr?.toDouble()
        "audio_channels", "channels" -> format.audioChannels?.toDouble()
        "quality" -> format.quality?.toDoubleOrNull()
        "source_preference", "source" -> format.sourcePreference?.toDouble()
        "language_preference", "lang" -> format.languagePreference
        else -> null
    }

    fun text(format: MediaFormat, key: String): String? = when (key.lowercase()) {
        "ext" -> format.ext
        "vcodec" -> format.vcodec
        "acodec" -> format.acodec
        "format_id", "id" -> format.formatId
        "protocol" -> format.protocol
        "language" -> format.language
        "container" -> format.container
        "format_note" -> format.formatNote
        "dynamic_range", "hdr" -> format.dynamicRange
        else -> null
    }
}

/**
 * The parsed format specification tree, mirroring the upstream
 * `FormatSelector` named tuple: `SINGLE`, `GROUP`, `PICKFIRST`, `MERGE`.
 */
sealed interface FormatSpec {
    val filters: List<FormatFilter>

    /** An atom (`best`, `bv*`, `137`, or the implicit `best` from `[...]`). */
    data class Single(
        val name: String = "best",
        override val filters: List<FormatFilter> = emptyList(),
    ) : FormatSpec

    /** Upstream `,`: a list of selections. */
    data class Choices(
        val children: List<FormatSpec>,
        override val filters: List<FormatFilter> = emptyList(),
    ) : FormatSpec

    /** Upstream `/`: the second side is only tried when the first yields nothing. */
    data class Fallback(
        val first: FormatSpec,
        val second: FormatSpec,
        override val filters: List<FormatFilter> = emptyList(),
    ) : FormatSpec

    /** Upstream `+`: a merge of two selections. */
    data class Merge(
        val first: FormatSpec,
        val second: FormatSpec,
        override val filters: List<FormatFilter> = emptyList(),
    ) : FormatSpec

    /** Upstream `(...)`: a parenthesized selection list. */
    data class Group(
        val children: List<FormatSpec>,
        override val filters: List<FormatFilter> = emptyList(),
    ) : FormatSpec

    companion object {
        /** Parses one upstream format specification, or throws [FormatSpecException]. */
        fun parse(text: String): FormatSpec {
            val parser = Parser(tokenize(text))
            val spec = parser.parseSpec()
                ?: throw FormatSpecException("Empty format specification.")
            if (parser.peek() != null) throw FormatSpecException("Unexpected trailing token.")
            return spec
        }
    }
}

private sealed interface Token {
    data class Atom(val text: String) : Token
    data class Filter(val text: String) : Token
    data object Slash : Token
    data object Plus : Token
    data object Comma : Token
    data object Open : Token
    data object Close : Token
}

private fun tokenize(input: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var index = 0
    while (index < input.length) {
        when (val char = input[index]) {
            ' ', '\t', '\n', '\r' -> index++
            '/' -> { tokens += Token.Slash; index++ }
            '+' -> { tokens += Token.Plus; index++ }
            ',' -> { tokens += Token.Comma; index++ }
            '(' -> { tokens += Token.Open; index++ }
            ')' -> { tokens += Token.Close; index++ }
            '[' -> {
                val end = input.indexOf(']', index + 1)
                if (end < 0) throw FormatSpecException("Unclosed filter bracket.")
                tokens += Token.Filter(input.substring(index + 1, end))
                index = end + 1
            }

            ']' -> throw FormatSpecException("Unexpected ']'.")
            else -> {
                val start = index
                while (index < input.length && input[index] !in "/+,()[]") index++
                val text = input.substring(start, index).filterNot { it.isWhitespace() }
                if (text.isNotEmpty()) tokens += Token.Atom(text)
            }
        }
    }
    return tokens
}

/**
 * Recursive descent over the upstream grammar: `spec := fallback (',' fallback)*`,
 * `fallback := merge ('/' fallback)?`, `merge := atom ('+' merge)?`,
 * `atom := primary filter*`.
 */
private class Parser(private val tokens: List<Token>) {
    private var index = 0

    fun peek(): Token? = tokens.getOrNull(index)

    private fun next(): Token? = tokens.getOrNull(index++)

    fun parseSpec(): FormatSpec? {
        val first = parseFallback() ?: return null
        val children = mutableListOf(first)
        while (peek() is Token.Comma) {
            index++
            val next = parseFallback()
                ?: throw FormatSpecException("\",\" must follow a format selector.")
            children += next
        }
        return if (children.size == 1) first else FormatSpec.Choices(children)
    }

    private fun parseFallback(): FormatSpec? {
        val left = parseMerge() ?: return null
        if (peek() is Token.Slash) {
            index++
            val right = parseFallback()
                ?: throw FormatSpecException("\"/\" must follow a format selector.")
            return FormatSpec.Fallback(left, right)
        }
        return left
    }

    private fun parseMerge(): FormatSpec? {
        val left = parseAtom() ?: return null
        if (peek() is Token.Plus) {
            index++
            val right = parseMerge()
                ?: throw FormatSpecException("\"+\" must follow a format selector.")
            return FormatSpec.Merge(left, right)
        }
        return left
    }

    private fun parseAtom(): FormatSpec? {
        val base: FormatSpec? = when (val token = peek()) {
            is Token.Atom -> {
                index++
                FormatSpec.Single(token.text)
            }

            is Token.Open -> {
                index++
                val inner = parseSpec()
                if (peek() !is Token.Close) throw FormatSpecException("Missing closing parenthesis.")
                index++
                val children = when (inner) {
                    null -> emptyList()
                    is FormatSpec.Choices -> inner.children
                    else -> listOf(inner)
                }
                FormatSpec.Group(children)
            }

            is Token.Filter -> FormatSpec.Single()
            else -> null
        }
        if (base == null) return null
        val filters = mutableListOf<FormatFilter>()
        while (peek() is Token.Filter) {
            filters += parseFilter((next() as Token.Filter).text)
        }
        return if (filters.isEmpty()) base else base.withFilters(filters)
    }
}

private fun FormatSpec.withFilters(filters: List<FormatFilter>): FormatSpec = when (this) {
    is FormatSpec.Single -> copy(filters = this.filters + filters)
    is FormatSpec.Choices -> copy(filters = this.filters + filters)
    is FormatSpec.Fallback -> copy(filters = this.filters + filters)
    is FormatSpec.Merge -> copy(filters = this.filters + filters)
    is FormatSpec.Group -> copy(filters = this.filters + filters)
}

private val numericFilter = Regex(
    "^\\s*([\\w.-]+)\\s*(<=|>=|!=|=|<|>)(\\s*\\?)?\\s*" +
        "([0-9.]+(?:[kKmMgGtTpPeEzZyY]i?[Bb]?)?)\\s*$",
)
private val stringKey = Regex("^\\s*([a-zA-Z0-9._-]+)\\s*")
private val stringOperator = Regex("^(!\\s*)?(\\^=|\\$=|\\*=|~=|=)\\s*(\\?\\s*)?")

internal fun parseFilter(text: String): FormatFilter {
    numericFilter.matchEntire(text)?.let { match ->
        val key = match.groupValues[1].lowercase()
        val op = FormatFilter.NumericOp.fromSymbol(match.groupValues[2])
        val noneInclusive = match.groupValues[3].isNotEmpty()
        val raw = match.groupValues[4]
        val value = parseFilterNumber(raw)
            ?: throw FormatSpecException("Invalid filter value.")
        return FormatFilter.Numeric(key, op, value, noneInclusive)
    }

    var rest = text
    val keyMatch = stringKey.find(rest) ?: throw FormatSpecException("Invalid filter specification.")
    val key = keyMatch.groupValues[1].lowercase()
    rest = rest.substring(keyMatch.value.length)

    val opMatch = stringOperator.find(rest) ?: throw FormatSpecException("Invalid filter operator.")
    val negated = opMatch.groupValues[1].isNotEmpty()
    val op = FormatFilter.StringOp.fromSymbol(opMatch.groupValues[2])
    val noneInclusive = opMatch.groupValues[3].isNotEmpty()
    rest = rest.substring(opMatch.value.length).trimEnd()

    val value = when {
        rest.startsWith("\"") || rest.startsWith("'") -> {
            val quote = rest.first()
            val end = rest.indexOf(quote, startIndex = 1)
            if (end < 0) throw FormatSpecException("Unclosed filter value.")
            rest.substring(1, end)
        }

        rest.isNotEmpty() && rest.all { it.isLetterOrDigit() || it in "._-" } -> rest
        else -> throw FormatSpecException("Invalid filter value.")
    }
    if (value.isEmpty()) throw FormatSpecException("Empty filter value.")
    return FormatFilter.Text(key, op, value, negated, noneInclusive)
}

private fun parseFilterNumber(raw: String): Double? {
    raw.toDoubleOrNull()?.let { return it }
    return parseFilesize(raw) ?: parseFilesize(raw + "B")
}

private val filesizePattern = Regex(
    "^([0-9]+(?:\\.[0-9]+)?)\\s*([kKmMgGtTpPeEzZyY])?\\s*(i?[bB])?$",
)

/**
 * Upstream `parse_filesize`: binary multipliers for `K`..`Y`, bytes for a
 * bare number or a `B` suffix.
 */
internal fun parseFilesize(value: String?): Double? {
    val text = value?.trim() ?: return null
    if (text.isEmpty()) return null
    val match = filesizePattern.matchEntire(text) ?: return null
    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].uppercase().firstOrNull() ?: return number
    val exponent = "KMGTPEZY".indexOf(unit) + 1
    if (exponent <= 0) return number
    var multiplier = 1.0
    repeat(exponent) { multiplier *= 1024.0 }
    return number * multiplier
}
