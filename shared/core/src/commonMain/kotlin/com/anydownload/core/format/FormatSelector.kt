/*
 * Format selector — AnyDownload
 *
 * Translation of the `_build_selector_function` selection semantics from
 * `yt_dlp/YoutubeDL.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), re-read 2026-09-24; the `,`,
 * `all`, and `mergeall` branches were re-read 2026-09-29 (T-133). Unlicense;
 * see shared/core/NOTICE.md. `YoutubeDL.py` is not vendored.
 *
 * [selectAll] returns every selection a spec yields, the way upstream's
 * selector generator does: a `,` list concatenates its children, a group
 * passes through, `/` keeps the first non-empty child, `+` is the product of
 * its sides, `all` yields every format best first, and `mergeall` yields one
 * merged selection over every usable stream. [select] keeps the D4
 * one-download callers working by returning the first selection. Format
 * checking (`_check_formats`) is represented by dropping DRM formats; the
 * `best`/`worst` reversal, the `*`/type filters, the `.N` pick, and the
 * incomplete-format fallback follow upstream.
 */
package com.anydownload.core.format

import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.MediaFormat

/** One selector outcome. */
sealed interface Selection {
    data class Single(val format: MediaFormat) : Selection

    /** Upstream `+` over two single-stream selections. */
    data class Merge(val video: MediaFormat, val audio: MediaFormat) : Selection

    /** Upstream `mergeall`: one selection covering several streams, best first. */
    data class MergeAll(val formats: List<MediaFormat>) : Selection

    data object None : Selection
}

object FormatSelector {

    private val formatName = Regex("^(best|worst|b|w)(video|audio|v|a)?(\\*)?(?:\\.([0-9]+))?$")

    /**
     * Sorts [info]'s formats with the default order plus [sort], then returns
     * every selection [spec] yields. DRM formats are never selectable.
     */
    fun selectAll(
        info: InfoDict,
        spec: FormatSpec,
        sort: List<String> = emptyList(),
    ): List<Selection> {
        val formats = info.formats.filter { it.hasDrm != true }
        if (formats.isEmpty()) return emptyList()
        val ordered = FormatSorter.sort(formats, FormatSorter.order(sort))
        return evaluate(spec, ordered, InfoContext.from(formats))
    }

    /**
     * The first selection [spec] yields, for the D4 one-download callers.
     * Multi-selection specs are unaffected; this is a convenience view.
     */
    fun select(
        info: InfoDict,
        spec: FormatSpec,
        sort: List<String> = emptyList(),
    ): Selection = selectAll(info, spec, sort).firstOrNull() ?: Selection.None

    /** Convenience for callers holding the specification text. */
    fun select(info: InfoDict, specText: String, sort: List<String> = emptyList()): Selection =
        select(info, FormatSpec.parse(specText), sort)

    /** Every selection for callers holding the specification text. */
    fun selectAll(info: InfoDict, specText: String, sort: List<String> = emptyList()): List<Selection> =
        selectAll(info, FormatSpec.parse(specText), sort)

    private fun evaluate(spec: FormatSpec, formats: List<MediaFormat>, info: InfoContext): List<Selection> {
        val filtered = if (spec.filters.isEmpty()) formats else formats.filter { format ->
            spec.filters.all { it.matches(format) }
        }
        return when (spec) {
            is FormatSpec.Single -> selectSingle(spec, filtered, info)
            // Upstream `,` and `()` yield every child selection in order.
            is FormatSpec.Choices -> spec.children.flatMap { evaluate(it, filtered, info) }
            is FormatSpec.Group -> spec.children.flatMap { evaluate(it, filtered, info) }
            is FormatSpec.Fallback -> evaluate(spec.first, filtered, info)
                .ifEmpty { evaluate(spec.second, filtered, info) }

            is FormatSpec.Merge -> mergeProduct(
                evaluate(spec.first, filtered, info),
                evaluate(spec.second, filtered, info),
            )
        }
    }

    private fun selectSingle(spec: FormatSpec.Single, formats: List<MediaFormat>, info: InfoContext): List<Selection> {
        val name = spec.name.ifEmpty { "best" }
        if (name == FormatSpec.ALL) {
            // Upstream `ctx['formats'][::-1]`: formats are sorted worst-first,
            // so the reverse is best-first.
            return formats.reversed().map { Selection.Single(it) }
        }
        if (name == FormatSpec.MERGEALL) {
            val usable = formats.filter {
                it.vcodec != MediaFormat.CODEC_NONE || it.acodec != MediaFormat.CODEC_NONE
            }
            if (usable.isEmpty()) return emptyList()
            return listOf(Selection.MergeAll(usable.reversed()))
        }
        val match = formatName.matchEntire(name)
            ?: return formats.firstOrNull { it.formatId == name }?.let { listOf(Selection.Single(it)) } ?: emptyList()

        val bestFirst = match.groupValues[1].startsWith("b")
        val type = match.groupValues[2].firstOrNull()
        val modified = match.groupValues[3].isNotEmpty()
        val pickIndex = (match.groupValues[4].toIntOrNull() ?: 1) - 1
        val bareBestWorst = type == null && !modified

        var matches = filterByName(formats, type, modified)
        if (matches.isEmpty() && bareBestWorst && info.incompleteFormats) {
            matches = formats.filter {
                it.vcodec != MediaFormat.CODEC_NONE || it.acodec != MediaFormat.CODEC_NONE
            }
        }
        if (bestFirst) matches = matches.reversed()
        return matches.getOrNull(pickIndex)?.let { listOf(Selection.Single(it)) } ?: emptyList()
    }

    private fun filterByName(
        formats: List<MediaFormat>,
        type: Char?,
        modified: Boolean,
    ): List<MediaFormat> {
        val hasEither = { format: MediaFormat ->
            format.vcodec != MediaFormat.CODEC_NONE || format.acodec != MediaFormat.CODEC_NONE
        }
        return when {
            type == 'v' && modified ->
                formats.filter { it.vcodec != MediaFormat.CODEC_NONE && hasEither(it) }

            type == 'a' && modified ->
                formats.filter { it.acodec != MediaFormat.CODEC_NONE && hasEither(it) }

            type == 'v' -> formats.filter {
                it.vcodec != MediaFormat.CODEC_NONE && it.acodec == MediaFormat.CODEC_NONE
            }

            type == 'a' -> formats.filter {
                it.acodec != MediaFormat.CODEC_NONE && it.vcodec == MediaFormat.CODEC_NONE
            }

            modified -> formats.filter(hasEither)
            else -> formats.filter {
                it.vcodec != MediaFormat.CODEC_NONE && it.acodec != MediaFormat.CODEC_NONE
            }
        }
    }

    /**
     * Upstream `+` is `itertools.product`: every pair of selections, merged.
     * Two single-stream sides become a [Selection.Merge]; a side that already
     * carries several streams folds into [Selection.MergeAll].
     */
    private fun mergeProduct(left: List<Selection>, right: List<Selection>): List<Selection> {
        if (left.isEmpty() || right.isEmpty()) return emptyList()
        val result = mutableListOf<Selection>()
        for (first in left) {
            for (second in right) {
                result += combine(first, second)
            }
        }
        return result
    }

    private fun combine(first: Selection, second: Selection): Selection {
        val firstSingle = first as? Selection.Single
        val secondSingle = second as? Selection.Single
        return if (firstSingle != null && secondSingle != null) {
            Selection.Merge(firstSingle.format, secondSingle.format)
        } else {
            Selection.MergeAll(streams(first) + streams(second))
        }
    }

    private fun streams(selection: Selection): List<MediaFormat> = when (selection) {
        is Selection.Single -> listOf(selection.format)
        is Selection.Merge -> listOf(selection.video, selection.audio)
        is Selection.MergeAll -> selection.formats
        Selection.None -> emptyList()
    }

    private class InfoContext(val incompleteFormats: Boolean) {
        companion object {
            fun from(formats: List<MediaFormat>): InfoContext {
                val noVideo = formats.all { it.vcodec == MediaFormat.CODEC_NONE }
                val noAudio = formats.all { it.acodec == MediaFormat.CODEC_NONE }
                return InfoContext(noVideo || noAudio)
            }
        }
    }
}
