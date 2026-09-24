/*
 * AES-128-CBC tests — AnyDownload (T-073)
 *
 * Known-answer vectors: NIST SP 800-38A F.2.1 (CBC-AES128) and an
 * openssl-generated PKCS7 fixture. Unlicense; see shared/core/NOTICE.md.
 */
package com.anydownlod.core.download

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class Aes128CbcTest {

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    @Test
    fun nistSp80038aCbcAes128Vector() {
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = hex("000102030405060708090a0b0c0d0e0f")
        val ciphertext = hex("7649abac8119b246cee98e9b12e9197d5086cb9b507219ee95db113a917678b2")
        val expected = hex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51")
        assertContentEquals(expected, Aes128Cbc.decrypt(key, iv, ciphertext, padding = false))
    }

    @Test
    fun pkcs7PaddedFragmentRoundTrips() {
        val key = hex("000102030405060708090a0b0c0d0e0f")
        val iv = hex("101112131415161718191a1b1c1d1e1f")
        val ciphertext = hex("8997c6837d7190199fa790420922462ed14ca30d11d87e11a5f2c6fbec3c137aa9a3868639fe00aeb1bdcdee1e85e1f2")
        val expected = "TS-FRAGMENT-ONE\nTS-FRAGMENT-TWO\n".encodeToByteArray()
        assertContentEquals(expected, Aes128Cbc.decrypt(key, iv, ciphertext))
    }

    @Test
    fun nonBlockAlignedCiphertextIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            Aes128Cbc.decrypt(hex("000102030405060708090a0b0c0d0e0f"), ByteArray(16), ByteArray(17))
        }
    }
}
