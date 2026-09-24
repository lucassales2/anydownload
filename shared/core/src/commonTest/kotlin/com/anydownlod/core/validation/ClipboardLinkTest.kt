package com.anydownlod.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipboardLinkTest {
    @Test
    fun oneHttpLinkIsCompatible() {
        assertEquals(
            "https://example.com/watch?v=1",
            ClipboardLink.compatibleUrl("  https://example.com/watch?v=1  "),
        )
        assertEquals("http://example.com/a", ClipboardLink.compatibleUrl("http://example.com/a"))
    }

    @Test
    fun batchesSchemesAndBlankTextAreNotASingleLink() {
        assertNull(ClipboardLink.compatibleUrl(null))
        assertNull(ClipboardLink.compatibleUrl("   "))
        assertNull(ClipboardLink.compatibleUrl("example.com/watch"))
        assertNull(ClipboardLink.compatibleUrl("ftp://example.com/a"))
        assertNull(ClipboardLink.compatibleUrl("https://example.com/a\nhttps://example.com/b"))
        assertNull(ClipboardLink.compatibleUrl("see https://example.com/a"))
    }
}
