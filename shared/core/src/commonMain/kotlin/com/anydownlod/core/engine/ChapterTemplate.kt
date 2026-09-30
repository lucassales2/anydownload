/*
 * Chapter template — AnyDownload (T-016)
 *
 * Translation of the chapter output template handling in
 * `yt_dlp/postprocessor/modify_chapters.py` and the `%(...)s` field
 * substitution in `yt_dlp/YoutubeDL.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30. Unlicense;
 * see shared/core/NOTICE.md.
 *
 * Only the fields the app's chapter template uses are supported; an unknown
 * field or a path that would leave the download root returns null so the
 * engine can fail the job typed instead of writing outside the root.
 */
package com.anydownlod.core.engine

import com.anydownlod.core.extract.Chapter

object ChapterTemplate {

    /**
     * Renders [template] for one chapter. Returns the confined relative path,
     * or null when a field is unknown or the result escapes the root.
     */
    fun render(
        template: String,
        mediaTitle: String?,
        chapter: Chapter,
        sectionNumber: Int,
        ext: String?,
    ): String? {
        val title = mediaTitle?.trim().orEmpty().ifEmpty { "download" }
        val sectionTitle = chapter.title?.trim().orEmpty().ifEmpty { "Chapter $sectionNumber" }
        val suffix = ext?.lowercase()?.takeIf { it.isNotEmpty() && it.length <= 8 && it.all(Char::isLetterOrDigit) }
        if (template.contains("%(ext)s") && suffix == null) return null
        var rendered = template
            .replace("%(title)s", title)
            .replace("%(section_number)02d", sectionNumber.toString().padStart(2, '0'))
            .replace("%(section_number)s", sectionNumber.toString())
            .replace("%(section_title)s", sectionTitle)
            .replace("%(ext)s", suffix.orEmpty())
        if (rendered.contains("%(")) return null
        rendered = rendered.replace("\\\\", "/").replace('\\', '/')
        if (rendered.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(rendered)) return null
        val segments = rendered.split('/')
        val safe = mutableListOf<String>()
        for (segment in segments) {
            if (segment.isEmpty() || segment == "." || segment == "..") return null
            var cleaned = segment.map { character ->
                when {
                    character == ':' || character < ' ' -> '_'
                    else -> character
                }
            }.joinToString("")
            while (cleaned.startsWith(".")) cleaned = cleaned.removePrefix(".")
            if (cleaned.isEmpty()) return null
            safe += cleaned
        }
        val path = safe.joinToString("/")
        if (suffix != null && !path.lowercase().endsWith(".$suffix")) {
            // A template without %(ext)s still needs a usable container.
            return "$path.$suffix"
        }
        return path
    }
}
