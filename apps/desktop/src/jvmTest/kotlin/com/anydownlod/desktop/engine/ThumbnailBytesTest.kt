package com.anydownlod.desktop.engine

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThumbnailBytesTest {
    @Test
    fun onlyHttpUrlsAreFetched() {
        assertTrue(ThumbnailBytes.allowed("https://example.com/t.jpg"))
        assertTrue(ThumbnailBytes.allowed("http://example.com/t.jpg"))
        assertFalse(ThumbnailBytes.allowed("file:///tmp/t.jpg"))
        assertFalse(ThumbnailBytes.allowed("not a url"))
    }

    @Test
    fun readRejectsAChunkPastTheCap() {
        val bytes = ByteArray(32) { it.toByte() }

        assertNull(ThumbnailBytes.readLimited(ByteArrayInputStream(bytes), 16))
        assertEquals(32, ThumbnailBytes.readLimited(ByteArrayInputStream(bytes), 64)?.size)
        assertNull(ThumbnailBytes.readLimited(ByteArrayInputStream(ByteArray(0)), 64))
    }
}
