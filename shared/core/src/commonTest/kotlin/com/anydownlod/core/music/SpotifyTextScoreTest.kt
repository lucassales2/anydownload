package com.anydownlod.core.music

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The prefix-search scorer is a plain normalized Levenshtein similarity; it
 * only has to rank candidates, not match rapidfuzz exactly.
 */
class SpotifyTextScoreTest {

    @Test
    fun identicalTextScoresFullMarks() {
        assertTrue(SpotifyTextScore.ratio("Whenever You Need Somebody", "whenever you need somebody") == 100.0)
    }

    @Test
    fun aCloserNameOutranksAnUnrelatedOne() {
        val term = "Whenever You Need Somebody"
        val close = SpotifyTextScore.ratio(term, "Whenever You Need Somebody (Deluxe)")
        val unrelated = SpotifyTextScore.ratio(term, "Completely Different Album")
        assertTrue(close > unrelated, "close=$close unrelated=$unrelated")
        assertTrue(close > 70.0)
        assertTrue(unrelated < 50.0)
    }

    @Test
    fun emptyTextScoresFullMarksAndNeverDividesByZero() {
        assertTrue(SpotifyTextScore.ratio("", "") == 100.0)
        assertTrue(SpotifyTextScore.ratio("", "anything") == 0.0)
    }
}
