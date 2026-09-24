package com.anydownlod.core.engine

import com.anydownlod.core.domain.DownloadOptions

/**
 * Builds the artifact relative path for a direct-file download.
 *
 * The filename derives only from allowlisted inputs: the URL path, and the
 * optional [DownloadOptions.filenamePrefix] and [DownloadOptions
 * .destinationFolder]. No user text is written to disk verbatim: names are
 * normalized, path separators and control characters are neutralized, leading
 * dots are dropped, and any traversal is rejected. The result is relative to
 * the download root and never absolute.
 */
object ArtifactName {

    /** Thrown when the options would place the artifact outside the root. */
    class EscapesRoot(message: String) : IllegalArgumentException(message)

    fun build(url: String, options: DownloadOptions): String {
        val folder = normalizeFolder(options.destinationFolder)
        val base = fileNameFromUrl(url)
        val name = if (options.filenamePrefix.isNullOrBlank()) base else "${options.filenamePrefix} $base"
        return if (folder == null) name else "$folder/$name"
    }

    private fun fileNameFromUrl(url: String): String {
        val path = url.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
            .substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/').trim()
        val sanitized = sanitize(lastSegment)
        return when {
            sanitized.isEmpty() || sanitized == "." || sanitized == ".." -> "download"
            else -> sanitized
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.map { c ->
            when {
                c == '/' || c == '\\' || c == ':' || c < ' ' -> '_'
                else -> c
            }
        }.joinToString("")
        // Drop leading dots so "." and ".." cannot be produced and hidden files
        // are not implied.
        var start = 0
        while (start < cleaned.length && cleaned[start] == '.') start++
        return cleaned.substring(start)
    }

    private fun normalizeFolder(destinationFolder: String?): String? {
        if (destinationFolder.isNullOrBlank()) return null
        val segments = destinationFolder.split('/').flatMap { it.split('\\') }
        val parts = mutableListOf<String>()
        for (segment in segments) {
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." || segment.contains(':') ->
                    throw EscapesRoot("The destination folder escapes the download root.")
                else -> parts += segment
            }
        }
        return parts.joinToString("/").ifEmpty { null }
    }
}