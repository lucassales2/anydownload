/*
 * Pure-Kotlin SHA-1 — AnyDownload
 *
 * Kotlin common has no message digest. The Iwara file-listing request signing
 * in `IwaraIE` needs SHA-1 (`hashlib.sha1('_'.join((path, expires, salt)))`
 * in upstream `iwara.py` at tag `2026.08.19`, commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf, read 2026-10-01; Unlicense, see
 * shared/core/NOTICE.md). This is a plain FIPS 180-4 implementation over
 * public request fields and is never used for security.
 */
package com.anydownload.core.extract

/** Lowercase hex SHA-1 of [input]. */
internal fun sha1Hex(input: ByteArray): String {
    val digest = sha1(input)
    val hex = "0123456789abcdef"
    val out = StringBuilder(40)
    for (byte in digest) {
        val value = byte.toInt() and 0xff
        out.append(hex[value ushr 4]).append(hex[value and 0x0f])
    }
    return out.toString()
}

private fun rotateLeft(value: Int, bits: Int): Int = (value shl bits) or (value ushr (32 - bits))

private fun sha1(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8
    val zeros = ((56 - (input.size + 1) % 64) + 64) % 64
    val message = ByteArray(input.size + 1 + zeros + 8)
    input.copyInto(message)
    message[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        message[message.size - 8 + index] = ((bitLength ushr (56 - 8 * index)) and 0xff).toByte()
    }

    val words = IntArray(80)
    var h0 = 0x67452301
    var h1 = 0xefcdab89.toInt()
    var h2 = 0x98badcfe.toInt()
    var h3 = 0x10325476
    var h4 = 0xc3d2e1f0.toInt()

    for (chunk in 0 until message.size / 64) {
        for (index in 0 until 16) {
            val offset = chunk * 64 + index * 4
            words[index] = ((message[offset].toInt() and 0xff) shl 24) or
                ((message[offset + 1].toInt() and 0xff) shl 16) or
                ((message[offset + 2].toInt() and 0xff) shl 8) or
                (message[offset + 3].toInt() and 0xff)
        }
        for (index in 16 until 80) {
            words[index] = rotateLeft(
                words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16],
                1,
            )
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        for (index in 0 until 80) {
            val f: Int
            val k: Int
            when {
                index < 20 -> {
                    f = (b and c) or (b.inv() and d)
                    k = 0x5a827999
                }

                index < 40 -> {
                    f = b xor c xor d
                    k = 0x6ed9eba1
                }

                index < 60 -> {
                    f = (b and c) or (b and d) or (c and d)
                    k = 0x8f1bbcdc.toInt()
                }

                else -> {
                    f = b xor c xor d
                    k = 0xca62c1d6.toInt()
                }
            }
            val temp = rotateLeft(a, 5) + f + e + k + words[index]
            e = d
            d = c
            c = rotateLeft(b, 30)
            b = a
            a = temp
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    val out = ByteArray(20)
    for ((index, value) in listOf(h0, h1, h2, h3, h4).withIndex()) {
        for (byte in 0 until 4) {
            out[index * 4 + byte] = ((value ushr (24 - byte * 8)) and 0xff).toByte()
        }
    }
    return out
}
