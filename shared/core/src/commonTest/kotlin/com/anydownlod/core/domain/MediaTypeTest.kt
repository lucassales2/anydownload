package com.anydownlod.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaTypeTest {
    @Test
    fun knownWireNamesMap() {
        assertEquals(MediaType.VIDEO, MediaType.fromWire("video"))
        assertEquals(MediaType.AUDIO, MediaType.fromWire("audio"))
        assertEquals(MediaType.CAPTIONS, MediaType.fromWire("captions"))
        assertEquals(MediaType.THUMBNAIL, MediaType.fromWire("thumbnail"))
    }

    @Test
    fun unknownWireNamesReturnNull() {
        assertNull(MediaType.fromWire("playlist"))
    }
}
