package com.anydownlod.ui.shell

import com.anydownlod.core.domain.JobState
import kotlin.math.roundToInt

/** Human label for a job state. [JobState.UNKNOWN] keeps its raw wire label. */
fun JobState.displayLabel(): String = when (this) {
    JobState.RESOLVING -> "Resolving"
    JobState.PENDING -> "Pending"
    JobState.SCHEDULED -> "Waiting"
    JobState.QUEUED -> "Queued"
    JobState.DOWNLOADING -> "Downloading"
    JobState.POSTPROCESSING -> "Post-processing"
    JobState.COMPLETED -> "Completed"
    JobState.FAILED -> "Failed"
    JobState.CANCELLED -> "Cancelled"
    JobState.UNKNOWN -> wireName
}

/** Decimal byte formatting for download rows. */
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000.0 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    return if (unit == 0) "$bytes B" else "${roundToOneDecimal(value)} ${units[unit]}"
}

private fun roundToOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}.0" else rounded.toString()
}

/** Speed uses the same decimal units as [formatBytes]. */
fun formatSpeed(bytesPerSecond: Double): String = "${formatBytes(bytesPerSecond.toLong())}/s"

/** Compact ETA; the caller only shows this when the engine reported one. */
fun formatEta(seconds: Long): String {
    if (seconds < 0) return "unknown"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${pad2(minutes)}m"
        minutes > 0 -> "${minutes}m ${pad2(secs)}s"
        else -> "${secs}s"
    }
}

/** Static UTC due time for a `SCHEDULED` row, without a platform date library. */
fun formatDueTime(epochMillis: Long): String {
    val totalSeconds = if (epochMillis < 0) 0L else epochMillis / 1000
    val days = totalSeconds / 86_400
    val secondOfDay = totalSeconds % 86_400
    val (year, month, day) = civilFromDays(days)
    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60
    return "${pad4(year)}-${pad2(month.toLong())}-${pad2(day.toLong())} ${pad2(hour)}:${pad2(minute)} UTC"
}

private fun pad2(value: Long): String = value.toString().padStart(2, '0')

/** The same UTC minute without the suffix, for dense table cells. */
fun formatUtcMinute(epochMillis: Long): String = formatDueTime(epochMillis).removeSuffix(" UTC")

private fun pad4(value: Int): String = value.toString().padStart(4, '0')

/** Howard Hinnant's civil-from-days algorithm, kept small and UTC-only. */
private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    val shifted = daysSinceEpoch + 719_468
    val era = (if (shifted >= 0) shifted else shifted - 146_096) / 146_097
    val dayOfEra = shifted - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
    return Triple((if (month <= 2) year + 1 else year).toInt(), month.toInt(), day.toInt())
}
