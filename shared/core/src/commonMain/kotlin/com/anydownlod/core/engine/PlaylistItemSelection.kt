/*
 * Playlist item selection — AnyDownload (T-124)
 *
 * Translation of `PlaylistEntries.parse_playlist_items` and
 * `PlaylistEntries.__getitem__` from `yt_dlp/utils/_utils.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30. Unlicense; see shared/core/NOTICE.md.
 *
 * The spec is a comma-separated list of 1-based indexes and ranges:
 * `1`, `1-3`, `1:3`, `1:3:2`, `-3` (from the end), `5-` (to the end), `:3`.
 * A negative index counts from the end, `inf`/`infinite` means no end, and an
 * optional step may be negative. Invalid specs throw so the engine can fail
 * the job typed instead of silently downloading the wrong rows.
 */
package com.anydownlod.core.engine

object PlaylistItemSelection {

    private val SEGMENT = Regex(
        """(?x)(?<start>[+-]?\d+)?(?<range>[:-](?<end>[+-]?\d+|inf(?:inite)?)?(?::(?<step>[+-]?\d+))?)?""",
    )

    /**
     * The selected rows of [entries] in spec order, with duplicates removed.
     * A blank spec selects everything. Throws [IllegalArgumentException] for a
     * malformed spec.
     */
    fun <T> select(entries: List<T>, spec: String): List<T> {
        if (spec.isBlank()) return entries
        val selected = LinkedHashSet<Int>()
        for (segment in spec.split(',')) {
            if (segment.isEmpty()) {
                throw IllegalArgumentException("There are two or more consecutive commas")
            }
            val match = SEGMENT.matchEntire(segment)
                ?: throw IllegalArgumentException("$segment is not a valid specification")
            val start = match.groups["start"]?.value?.toIntOrNull()
            val end = match.groups["end"]?.value
            val step = match.groups["step"]?.value?.toIntOrNull()
            if (step == 0) throw IllegalArgumentException("Step in $segment cannot be zero")

            if (match.groups["range"] == null) {
                val value = start
                    ?: throw IllegalArgumentException("$segment is not a valid specification")
                val index = resolveIndex(value, entries.size)
                if (index in entries.indices) selected += index
                continue
            }

            val stepValue = step ?: 1
            val startIndex = start?.let { resolveIndex(it, entries.size) }
                ?: if (stepValue > 0) 0 else entries.size - 1
            val stopIndex: Double = when {
                end == null || end == "inf" || end == "infinite" ->
                    if (stepValue < 0) -1.0 else Double.POSITIVE_INFINITY

                else -> {
                    val value = end.toIntOrNull()
                        ?: throw IllegalArgumentException("$segment is not a valid specification")
                    val base = resolveIndex(value, entries.size)
                    (base + if (stepValue > 0) 1 else -1).toDouble()
                }
            }

            var index = startIndex
            while (if (stepValue > 0) index < stopIndex else index > stopIndex) {
                if (index < 0) {
                    index += stepValue
                    continue
                }
                if (index >= entries.size) {
                    if (stepValue > 0) break
                    index += stepValue
                    continue
                }
                selected += index
                index += stepValue
            }
        }
        return selected.map { entries[it] }
    }

    /** Upstream `__getitem__`: positive is 1-based, negative counts from the end. */
    private fun resolveIndex(value: Int, size: Int): Int = if (value >= 0) value - 1 else size + value
}
