package com.anydownload.core.extract

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Known-vector checks for the pure-Kotlin SHA-1 helper the Iwara file-list
 * signature uses. Vectors are the FIPS 180-4 / NIST examples.
 */
class Sha1Test {

    @Test
    fun knownVectorsMatch() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1Hex(ByteArray(0)))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1Hex("abc".encodeToByteArray()))
        assertEquals(
            "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
            sha1Hex("The quick brown fox jumps over the lazy dog".encodeToByteArray()),
        )
    }

    @Test
    fun multiBlockInputMatches() {
        // 200 bytes crosses the 64-byte padding boundary.
        assertEquals(
            "e61cfffe0d9195a525fc6cf06ca2d77119c24a40",
            sha1Hex("a".repeat(200).encodeToByteArray()),
        )
    }
}
