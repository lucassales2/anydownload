/*
 * Spotify text score — AnyDownload
 *
 * The `album:`/`playlist:`/`artist:` searches pick the best-named result,
 * mirroring spotDL v4.5.2's `fuzz.ratio` choice without copying rapidfuzz.
 * This is a plain normalized Levenshtein similarity in 0..100.
 */
package com.anydownlod.core.music

internal object SpotifyTextScore {

    /** Similarity of [candidate] to [term] in 0..100; 100 is identical. */
    fun ratio(term: String, candidate: String): Double {
        val left = term.trim().lowercase()
        val right = candidate.trim().lowercase()
        val longest = maxOf(left.length, right.length)
        if (longest == 0) return 100.0
        val distance = levenshtein(left, right)
        return (1.0 - distance.toDouble() / longest) * 100.0
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
