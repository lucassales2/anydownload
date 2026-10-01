package com.anydownload.core.extract

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Known-vector checks for the pure-Kotlin SHA-512 helper the Vice preplay
 * signature uses. Vectors are the FIPS 180-4 / NIST examples.
 */
class Sha512Test {

    @Test
    fun knownVectorsMatch() {
        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            sha512Hex(ByteArray(0)),
        )
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            sha512Hex("abc".encodeToByteArray()),
        )
        assertEquals(
            "07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb64" +
                "2e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6",
            sha512Hex("The quick brown fox jumps over the lazy dog".encodeToByteArray()),
        )
    }

    @Test
    fun multiBlockInputMatches() {
        // 200 bytes crosses the 128-byte padding boundary.
        val input = "a".repeat(200)
        assertEquals(
            "4b11459c33f52a22ee8236782714c150a3b2c60994e9acee17fe68947a3e6789" +
                "f31e7668394592da7bef827cddca88c4e6f86e4df7ed1ae6cba71f3e98faee9f",
            sha512Hex(input.encodeToByteArray()),
        )
    }
}
