package com.anydownload.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * T-124 `--playlist-items`: 1-based indexes, negative indexes from the end,
 * ranges with an optional step, and typed rejection of malformed specs.
 */
class PlaylistItemSelectionTest {

    private val entries = listOf("a", "b", "c", "d", "e")

    @Test
    fun blankSelectsEveryEntry() {
        assertEquals(entries, PlaylistItemSelection.select(entries, ""))
        assertEquals(entries, PlaylistItemSelection.select(entries, "  "))
    }

    @Test
    fun singleAndNegativeIndexesSelectOneEntry() {
        assertEquals(listOf("a"), PlaylistItemSelection.select(entries, "1"))
        assertEquals(listOf("c"), PlaylistItemSelection.select(entries, "3"))
        assertEquals(listOf("e"), PlaylistItemSelection.select(entries, "-1"))
        assertEquals(listOf("d"), PlaylistItemSelection.select(entries, "-2"))
        assertEquals(emptyList(), PlaylistItemSelection.select(entries, "9"))
    }

    @Test
    fun rangesAreInclusiveAndSupportAStep() {
        assertEquals(listOf("b", "c", "d"), PlaylistItemSelection.select(entries, "2-4"))
        assertEquals(listOf("b", "c", "d"), PlaylistItemSelection.select(entries, "2:4"))
        assertEquals(listOf("a", "c", "e"), PlaylistItemSelection.select(entries, "1:5:2"))
        assertEquals(listOf("e", "d", "c", "b", "a"), PlaylistItemSelection.select(entries, "5:1:-1"))
        assertEquals(listOf("a", "b"), PlaylistItemSelection.select(entries, ":2"))
    }

    @Test
    fun openEndedRangesRunToTheEnd() {
        assertEquals(listOf("c", "d", "e"), PlaylistItemSelection.select(entries, "3-"))
        assertEquals(listOf("d", "e"), PlaylistItemSelection.select(entries, "-2-"))
        assertEquals(listOf("c", "d", "e"), PlaylistItemSelection.select(entries, "3:inf"))
        assertEquals(listOf("c", "d", "e"), PlaylistItemSelection.select(entries, "3:infinite"))
    }

    @Test
    fun commaSeparatedPartsKeepSpecOrderWithoutDuplicates() {
        assertEquals(listOf("a", "c", "e"), PlaylistItemSelection.select(entries, "1,3,5"))
        assertEquals(listOf("e", "a", "b"), PlaylistItemSelection.select(entries, "5,1,1,2"))
    }

    @Test
    fun malformedSpecsThrow() {
        assertFailsWith<IllegalArgumentException> { PlaylistItemSelection.select(entries, "1,,2") }
        assertFailsWith<IllegalArgumentException> { PlaylistItemSelection.select(entries, "x") }
        assertFailsWith<IllegalArgumentException> { PlaylistItemSelection.select(entries, "1:2:0") }
        assertFailsWith<IllegalArgumentException> { PlaylistItemSelection.select(entries, ",") }
        assertFailsWith<IllegalArgumentException> { PlaylistItemSelection.select(entries, "1:2:3:4") }
    }
}
