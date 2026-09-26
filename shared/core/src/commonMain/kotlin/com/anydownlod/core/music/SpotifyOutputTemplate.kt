/*
 * Spotify output template — AnyDownload
 *
 * Reimplements spotDL v4.5.2's `utils/formatter.py` `format_query` and
 * `restrict_filename` behavior for the documented output variables: replace
 * the `{...}` placeholders with the Spotify record's fields, keep the result
 * inside the download root, and optionally restrict the final filename. No
 * spotDL Python is copied or vendored.
 */
package com.anydownlod.core.music

/** How aggressively a filename is restricted. */
enum class SpotifyRestrict(val wireName: String) {
    NONE("none"),
    STRICT("strict"),
    ASCII("ascii"),
    ;

    companion object {
        fun fromWire(value: String): SpotifyRestrict? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/** The template could not produce a safe path inside the download root. */
sealed class SpotifyTemplateError(message: String) : Exception(message) {
    class EscapesRoot(
        message: String = "The output template escapes the download root.",
    ) : SpotifyTemplateError(message)
}

object SpotifyOutputTemplate {

    /** spotDL's default: `{artists} - {title}.{output-ext}`. */
    const val DEFAULT: String = "{artists} - {title}.{output-ext}"

    /** Every variable spotDL's `docs/usage.md` output table names. */
    val VARIABLES: Set<String> = setOf(
        "{title}", "{artists}", "{artist}", "{album}", "{album-artist}", "{genre}",
        "{disc-number}", "{disc-count}", "{duration}", "{year}", "{original-date}",
        "{track-number}", "{tracks-count}", "{isrc}", "{track-id}", "{publisher}",
        "{list-length}", "{list-position}", "{list-name}", "{output-ext}",
    )

    /**
     * Renders [record] into a root-relative path. A template or a substituted
     * value that would produce a `..` segment (or an absolute path) fails
     * typed. Unknown `{...}` text is kept as-is, like spotDL.
     */
    fun format(
        record: SongRecord,
        extension: String,
        template: String = DEFAULT,
        restrict: SpotifyRestrict = SpotifyRestrict.NONE,
    ): String {
        val effective = effectiveTemplate(template)
        var result = effective
        for ((key, value) in values(record, extension)) {
            result = result.replace(key, sanitizeValue(value))
        }
        val segments = result.split('/')
        if (segments.any { it == ".." || it.contains('\\') || it.contains(':') }) {
            throw SpotifyTemplateError.EscapesRoot()
        }
        val normalized = segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
        if (normalized.isEmpty() || result.startsWith("/")) {
            throw SpotifyTemplateError.EscapesRoot()
        }
        val parts = normalized.split('/')
        val restricted = parts.mapIndexed { index, part ->
            if (index == parts.lastIndex) restrictFilename(part, restrict) else part
        }.joinToString("/")
        if (restricted.isBlank() || restricted.split('/').any { it.isBlank() }) {
            throw SpotifyTemplateError.EscapesRoot()
        }
        return restricted
    }

    /** The template with a filename and extension when it lacks one. */
    private fun effectiveTemplate(template: String): String {
        val trimmed = template.trim()
        if (trimmed.isEmpty()) return DEFAULT
        var effective = trimmed
        if (effective.endsWith("/") || effective.endsWith("\\")) {
            effective += "/$DEFAULT"
        }
        if (!effective.endsWith(".{output-ext}")) {
            effective += ".{output-ext}"
        }
        return effective
    }

    private fun values(record: SongRecord, extension: String): Map<String, String> = mapOf(
        "{title}" to record.title,
        "{artists}" to record.artists.joinToString(", "),
        "{artist}" to record.artist,
        "{album}" to record.album.orEmpty(),
        "{album-artist}" to record.albumArtist.orEmpty(),
        "{genre}" to (record.genres.firstOrNull() ?: ""),
        "{disc-number}" to (record.discNumber?.toString() ?: ""),
        "{disc-count}" to (record.discCount?.toString() ?: ""),
        "{duration}" to (record.durationSeconds?.toString() ?: ""),
        "{year}" to (record.year?.toString() ?: ""),
        "{original-date}" to record.releaseDate.orEmpty(),
        "{track-number}" to (record.trackNumber?.toString()?.padStart(2, '0') ?: ""),
        "{tracks-count}" to (record.tracksCount?.toString() ?: ""),
        "{isrc}" to record.isrc.orEmpty(),
        "{track-id}" to record.songId.orEmpty(),
        "{publisher}" to record.publisher.orEmpty(),
        "{list-name}" to record.listName.orEmpty(),
        "{list-position}" to listPosition(record),
        "{list-length}" to (record.listLength?.toString() ?: ""),
        "{output-ext}" to extension,
    )

    private fun listPosition(record: SongRecord): String {
        val position = record.listPosition ?: return ""
        val width = record.listLength?.toString()?.length ?: 1
        return position.toString().padStart(width, '0')
    }

    /** spotDL's `sanitize_string` for one substituted value. */
    internal fun sanitizeValue(value: String): String = value
        .filterNot { it in "/?\\*|<>" }
        .replace(Regex("\\s{2,}"), " ")
        .replace('"', '\'')
        .replace(':', '-')

    /** spotDL's `restrict_filename`: strict cleanup or an ASCII fold. */
    internal fun restrictFilename(name: String, restrict: SpotifyRestrict): String = when (restrict) {
        SpotifyRestrict.NONE -> name.ifEmpty { "_" }
        SpotifyRestrict.STRICT -> cleanName(name, ascii = false)
        SpotifyRestrict.ASCII -> cleanName(name, ascii = true)
    }

    /** Cleans the stem and keeps the extension; trailing dots are dropped. */
    private fun cleanName(name: String, ascii: Boolean): String {
        val dot = name.lastIndexOf('.')
        val hasExtension = dot > 0 && dot < name.length - 1
        val stem = if (hasExtension) name.substring(0, dot) else name
        val extension = if (hasExtension) name.substring(dot) else ""
        val folded = if (ascii) asciiFold(stem) else stem
        val cleaned = folded
            .map { character ->
                when {
                    character.code < 0x20 -> '_'
                    character in "\\/:*?\"<>|" -> '_'
                    else -> character
                }
            }
            .joinToString("")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('.')
        return cleaned.ifEmpty { "_" } + extension
    }

    /** A small Latin-1 fold; characters without a mapping are dropped. */
    private fun asciiFold(value: String): String = buildString {
        for (character in value) {
            val mapped = when (character.lowercaseChar()) {
                'á', 'à', 'â', 'ä', 'ã', 'å', 'ā' -> "a"
                'æ' -> "ae"
                'ç', 'ć', 'č' -> "c"
                'ď' -> "d"
                'é', 'è', 'ê', 'ë', 'ē', 'ę' -> "e"
                'ğ' -> "g"
                'í', 'ì', 'î', 'ï', 'ī' -> "i"
                'ł' -> "l"
                'ñ', 'ń' -> "n"
                'ó', 'ò', 'ô', 'ö', 'õ', 'ø', 'ō' -> "o"
                'œ' -> "oe"
                'ř' -> "r"
                'š', 'ś' -> "s"
                'ť' -> "t"
                'ú', 'ù', 'û', 'ü', 'ū', 'ů' -> "u"
                'ý', 'ÿ' -> "y"
                'ž', 'ź', 'ż' -> "z"
                else -> null
            }
            if (mapped != null) {
                append(if (character.isUpperCase()) mapped.uppercase() else mapped)
            } else if (character.code in 0x20..0x7E) {
                append(character)
            }
        }
    }
}
