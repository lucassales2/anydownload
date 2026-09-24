/*
 * Format selector — AnyDownload
 *
 * Translation of the `_build_selector_function` selection semantics from
 * `yt_dlp/YoutubeDL.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), re-read 2026-09-24. Unlicense;
 * see shared/core/NOTICE.md. `YoutubeDL.py` is not vendored.
 *
 * D4 has a one-download job model, so a comma list or a group yields the
 * first non-empty selection instead of every selection upstream would
 * download, and format checking (`_check_formats`) is represented by
 * dropping DRM formats. Everything else — the `/` fallback, the `best`/
 * `worst` reversal, the `*`/type filters, the `.N` pick, and the incomplete
 * format fallback — follows upstream.
 */
package com.anydownlod.core.format

import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.MediaFormat

/** One selector outcome. */
sealed interface Selection {
    data class Single(val format: MediaFormat) : Selection
    data class Merge(val video: MediaFormat, val audio: MediaFormat) : Selection
    data object None : Selection
}

object FormatSelector {

    private val formatName = Regex("^(best|worst|b|w)(video|audio|v|a)?(\\*)?(?:\\.([0-9]+))?$")

    /**
     * Sorts [info]'s formats with the default order plus [sort], then
     * evaluates [spec]. DRM formats are never selectable.
     */
    fun select(
        info: InfoDict,
        spec: FormatSpec,
        sort: List<String> = emptyList(),
    ): Selection {
        val formats = info.formats.filter { it.hasDrm != true }
        if (formats.isEmpty()) return Selection.None
        val ordered = FormatSorter.sort(formats, FormatSorter.order(sort))
        return evaluate(spec, ordered, InfoContext.from(formats)).firstOrNull() ?: Selection.None
    }

    /** Convenience for callers holding the specification text. */
    fun select(info: InfoDict, specText: String, sort: List<String> = emptyList()): Selection =
        select(info, FormatSpec.parse(specText), sort)

    private fun evaluate(spec: FormatSpec, formats: List<MediaFormat>, info: InfoContext): List<Selection> {
        val filtered = if (spec.filters.isEmpty()) formats else formats.filter { format ->
            spec.filters.all { it.matches(format) }
        }
        return when (spec) {
            is FormatSpec.Single -> selectSingle(spec, filtered, info)
            is FormatSpec.Choices -> firstNonEmpty(spec.children, filtered, info)
            is FormatSpec.Group -> firstNonEmpty(spec.children, filtered, info)
            is FormatSpec.Fallback -> evaluate(spec.first, filtered, info)
                .ifEmpty { evaluate(spec.second, filtered, info) }

            is FormatSpec.Merge -> merge(evaluate(spec.first, filtered, info), evaluate(spec.second, filtered, info))
        }
    }

    /** D4 picks one job per spec; the first child that yields anything wins. */
    private fun firstNonEmpty(
        children: List<FormatSpec>,
        formats: List<MediaFormat>,
        info: InfoContext,
    ): List<Selection> {
        for (child in children) {
            val result = evaluate(child, formats, info)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun selectSingle(spec: FormatSpec.Single, formats: List<MediaFormat>, info: InfoContext): List<Selection> {
        val name = spec.name.ifEmpty { "best" }
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

    private fun merge(videoSide: List<Selection>, audioSide: List<Selection>): List<Selection> {
        val video = videoSide.firstOrNull { it is Selection.Single } as? Selection.Single ?: return emptyList()
        val audio = audioSide.firstOrNull { it is Selection.Single } as? Selection.Single ?: return emptyList()
        return listOf(Selection.Merge(video.format, audio.format))
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
