package com.anydownlod.desktop.engine

/** One parsed line of the stable yt-dlp stdout templates. */
sealed interface YtDlpEvent {
    data class Title(val title: String) : YtDlpEvent

    data class Progress(
        val downloadedBytes: Long?,
        val totalBytes: Long?,
        val speedBytesPerSecond: Double?,
        val etaSeconds: Long?,
        val postprocessing: Boolean,
    ) : YtDlpEvent

    data class FinalFile(val path: String) : YtDlpEvent

    /** One line of a `--flat-playlist --print` scan. */
    data class Entry(val url: String) : YtDlpEvent
}

/**
 * Parses the progress and print templates in [YtDlpArguments]. Unknown lines
 * are ignored, and `NA`/unknown numbers stay null rather than becoming zero.
 */
object YtDlpProgressParser {
    fun parse(line: String): YtDlpEvent? {
        val trimmed = line.trimEnd()
        return when {
            trimmed.startsWith(YtDlpArguments.TITLE_PREFIX) ->
                YtDlpEvent.Title(trimmed.removePrefix(YtDlpArguments.TITLE_PREFIX))

            trimmed.startsWith(YtDlpArguments.FILE_PREFIX) ->
                YtDlpEvent.FinalFile(trimmed.removePrefix(YtDlpArguments.FILE_PREFIX))

            trimmed.startsWith(YtDlpArguments.ENTRY_PREFIX) ->
                YtDlpEvent.Entry(trimmed.removePrefix(YtDlpArguments.ENTRY_PREFIX))

            trimmed.startsWith(YtDlpArguments.POSTPROCESS_PREFIX) ->
                YtDlpEvent.Progress(
                    downloadedBytes = null,
                    totalBytes = null,
                    speedBytesPerSecond = null,
                    etaSeconds = null,
                    postprocessing = true,
                )

            trimmed.startsWith(YtDlpArguments.PROGRESS_PREFIX) ->
                parseProgress(trimmed.removePrefix(YtDlpArguments.PROGRESS_PREFIX))

            else -> null
        }
    }

    private fun parseProgress(payload: String): YtDlpEvent.Progress {
        // status|downloaded|total|estimate|speed|eta
        val parts = payload.split('|')
        val status = parts.getOrNull(0).orEmpty()
        val downloaded = parts.getOrNull(1)?.toLongOrNull()
        val total = parts.getOrNull(2)?.toLongOrNull()
        val estimate = parts.getOrNull(3)?.toLongOrNull()
        val speed = parts.getOrNull(4)?.toDoubleOrNull()
        val eta = parts.getOrNull(5)?.toLongOrNull()
        val effectiveTotal = when {
            total != null && total > 0 -> total
            estimate != null && estimate > 0 -> estimate
            else -> null
        }
        return YtDlpEvent.Progress(
            downloadedBytes = downloaded,
            totalBytes = effectiveTotal,
            speedBytesPerSecond = speed,
            etaSeconds = eta,
            postprocessing = status.equals("postprocessing", ignoreCase = true),
        )
    }
}
