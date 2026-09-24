package com.anydownlod.ui.preview

/** Clock time for a preview duration. Hours appear only when the length needs them. */
internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "$hours:${minutes.pad2()}:${secs.pad2()}"
    } else {
        "$minutes:${secs.pad2()}"
    }
}

/** Groups digits by thousands. The preview view count uses this. */
internal fun formatCount(value: Long): String {
    if (value == Long.MIN_VALUE) return value.toString()
    val negative = value < 0
    val digits = if (negative) (-value).toString() else value.toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

private fun Long.pad2(): String = toString().padStart(2, '0')
