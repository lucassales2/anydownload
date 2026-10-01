/*
 * Clip range — AnyDownload (T-016)
 *
 * Translation of the `--download-sections` time parsing and the URL
 * timestamp precedence in `yt_dlp/YoutubeDL.py` (`_download_retcode`,
 * `sanitize_info` start/end handling) at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30. Unlicense;
 * see shared/core/NOTICE.md.
 *
 * The app's add form carries separate start/end fields. An explicit field
 * wins over a URL timestamp; with no explicit field, a `t=`/`start=` query
 * parameter sets the start. Times accept `HH:MM:SS.mmm`, `MM:SS`, and plain
 * seconds. The cut is stream-copy in the toolkit, so it lands on the nearest
 * keyframe before the requested start; that accuracy limit is documented.
 */
package com.anydownload.core.engine

/** One validated clip: [startMillis] inclusive, [endMillis] exclusive or null. */
data class ClipRange(
    val startMillis: Long,
    val endMillis: Long?,
)

object ClipRangeParser {

    sealed interface Result {
        /** [range] is null when the request has no clip at all. */
        data class Ok(val range: ClipRange?) : Result

        /** A redacted reason the range is unusable. */
        data class Invalid(val reason: String) : Result
    }

    /**
     * Resolves the clip for one job. [startField]/[endField] are the add-form
     * values; [sourceUrl] supplies a fallback `t`/`start` timestamp only when
     * both fields are blank.
     */
    fun parse(startField: String?, endField: String?, sourceUrl: String?): Result {
        val startText = startField?.trim().orEmpty()
        val endText = endField?.trim().orEmpty()
        if (startText.isEmpty() && endText.isEmpty()) {
            val urlStart = urlStartMillis(sourceUrl) ?: return Result.Ok(null)
            return Result.Ok(ClipRange(urlStart, null))
        }
        val start = if (startText.isEmpty()) 0L else parseMillis(startText)
            ?: return Result.Invalid("The clip start is not a valid time.")
        val end = if (endText.isEmpty()) null else parseMillis(endText)
            ?: return Result.Invalid("The clip end is not a valid time.")
        if (start < 0L) return Result.Invalid("The clip start cannot be negative.")
        if (end != null && end <= start) return Result.Invalid("The clip end must be after the start.")
        return Result.Ok(ClipRange(start, end))
    }

    /** The URL's `t` or `start` query value in milliseconds, or null. */
    fun urlStartMillis(sourceUrl: String?): Long? {
        val query = sourceUrl?.substringAfter('?', "")?.substringBefore('#') ?: return null
        for (part in query.split('&')) {
            val name = part.substringBefore('=').lowercase()
            if (name != "t" && name != "start") continue
            val value = part.substringAfter('=', "").removeSuffix("s")
            return parseMillis(value)
        }
        return null
    }

    /** `HH:MM:SS.mmm`, `MM:SS`, `SS.mmm`, `1h2m3s`, or plain seconds. */
    fun parseMillis(value: String): Long? {
        val text = value.trim().lowercase()
        if (text.isEmpty()) return null
        if (text.contains(':')) {
            val parts = text.split(':')
            val (hours, minutes, seconds) = when (parts.size) {
                2 -> Triple(0.0, parts[0].toDoubleOrNull() ?: return null, parts[1].toDoubleOrNull() ?: return null)
                3 -> Triple(
                    parts[0].toDoubleOrNull() ?: return null,
                    parts[1].toDoubleOrNull() ?: return null,
                    parts[2].toDoubleOrNull() ?: return null,
                )

                else -> return null
            }
            return ((hours * 3600.0 + minutes * 60.0 + seconds) * 1000.0).toLong()
        }
        val compact = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?$""").matchEntire(text)
        if (compact != null && compact.value.any { it.isDigit() }) {
            val hours = compact.groups[1]?.value?.toDoubleOrNull() ?: 0.0
            val minutes = compact.groups[2]?.value?.toDoubleOrNull() ?: 0.0
            val seconds = compact.groups[3]?.value?.toDoubleOrNull() ?: 0.0
            return ((hours * 3600.0 + minutes * 60.0 + seconds) * 1000.0).toLong()
        }
        return text.toDoubleOrNull()?.let { (it * 1000.0).toLong() }
    }
}
