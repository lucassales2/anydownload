package com.anydownlod.ui.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClipboardWatchTest {
    @Test
    fun emptyFieldIsFilledAndTheSameCopyIsIgnored() {
        val watch = ClipboardWatch()

        val first = watch.poll("https://example.com/watch?v=1", "")
        val repeat = watch.poll("https://example.com/watch?v=1", "")

        assertEquals(ClipboardEvent.Fill("https://example.com/watch?v=1"), first)
        assertEquals(ClipboardEvent.None, repeat)
    }

    @Test
    fun occupiedFieldIsASuggestionAndAMatchingFieldIsNot() {
        val watch = ClipboardWatch()

        assertEquals(
            ClipboardEvent.Suggest("https://example.com/watch?v=1"),
            watch.poll("https://example.com/watch?v=1", "https://example.com/other"),
        )
        assertEquals(
            ClipboardEvent.None,
            ClipboardWatch().poll("https://example.com/watch?v=1", "https://example.com/watch?v=1"),
        )
    }

    @Test
    fun aNonLinkIsRememberedWithoutAnOffer() {
        val watch = ClipboardWatch()

        assertEquals(ClipboardEvent.None, watch.poll("hello", ""))
        assertIs<ClipboardEvent.Fill>(watch.poll("https://example.com/a", ""))
    }

    @Test
    fun aLinkAlreadyOfferedIsSkipped() {
        val watch = ClipboardWatch()

        assertEquals(
            ClipboardEvent.None,
            watch.poll("https://example.com/watch?v=1", "", alreadyHandledUrl = "https://example.com/watch?v=1"),
        )
    }
}
