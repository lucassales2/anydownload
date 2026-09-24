/*
 * Upstream extractor name list — AnyDownload repository tooling (T-055).
 *
 * port/upstream-extractors.json stores only data: the sorted class names from
 * yt_dlp/extractor/_extractors.py at the pinned revision, with the tag and
 * commit they came from. It never stores upstream source. The fetched
 * _extractors.py itself is not committed.
 */
package com.anydownlod.portmanifest

import kotlinx.serialization.Serializable

@Serializable
data class UpstreamExtractors(
    val repository: String,
    val tag: String,
    val commit: String,
    val sourcePath: String,
    val count: Int,
    val extractors: List<String>,
)

object ExtractorsPy {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /** `from .module import (\n    NameIE,\n)` blocks. */
    private val blockImport = Regex(
        "^[ \\t]*from[ \\t]+\\.[A-Za-z0-9_.]+[ \\t]+import[ \\t]*\\(\\n(.*?)^\\)",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )

    /** `from .module import NameIE` and comma-separated single-line forms. */
    private val simpleImport = Regex(
        "^[ \\t]*from[ \\t]+\\.[A-Za-z0-9_.]+[ \\t]+import[ \\t]+([A-Za-z0-9_, \\t]+)[ \\t]*$",
        RegexOption.MULTILINE,
    )

    private data class ImportMatch(val index: Int, val names: List<String>)

    /**
     * Names in source order, deduplicated. The upstream file imports exactly
     * one extractor class per name; comments and unrelated lines are ignored.
     */
    fun parseNames(source: String): List<String> {
        val matches = mutableListOf<ImportMatch>()
        for (match in blockImport.findAll(source)) {
            matches += ImportMatch(match.range.first, identifier.findAll(match.groupValues[1]).map { it.value }.toList())
        }
        for (match in simpleImport.findAll(source)) {
            matches += ImportMatch(
                match.range.first,
                match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
        matches.sortBy { it.index }
        val names = LinkedHashSet<String>()
        for (match in matches) names += match.names
        return names.toList()
    }
}
