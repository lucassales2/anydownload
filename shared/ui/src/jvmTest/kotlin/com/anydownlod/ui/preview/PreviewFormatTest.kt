package com.anydownlod.ui.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewFormatTest {
    @Test
    fun durationAndCounts() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("1:05", formatDuration(65))
        assertEquals("1:01:01", formatDuration(3661))
        assertEquals("1,200", formatCount(1200))
        assertEquals("0", formatCount(0))
    }
}
