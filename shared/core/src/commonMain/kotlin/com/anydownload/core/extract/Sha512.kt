/*
 * Pure-Kotlin SHA-512 — AnyDownload
 *
 * Kotlin common has no message digest. The Vice preplay request signing in
 * `ViceIE` needs SHA-512 (`hashlib.sha512(f'{video_id}:GET:{exp}')` in
 * upstream `vice.py` at tag `2026.08.19`, commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf, read 2026-10-01; Unlicense, see
 * shared/core/NOTICE.md). This is a plain FIPS 180-4 implementation over
 * public request fields and is never used for security.
 */
package com.anydownload.core.extract

/** Lowercase hex SHA-512 of [input]. */
internal fun sha512Hex(input: ByteArray): String {
    val digest = sha512(input)
    val hex = "0123456789abcdef"
    val out = StringBuilder(128)
    for (byte in digest) {
        val value = byte.toInt() and 0xff
        out.append(hex[value ushr 4]).append(hex[value and 0x0f])
    }
    return out.toString()
}

private fun rotateRight(value: Long, bits: Int): Long = (value ushr bits) or (value shl (64 - bits))

private fun sha512(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8
    val zeros = ((112 - (input.size + 1) % 128) + 128) % 128
    val message = ByteArray(input.size + 1 + zeros + 16)
    input.copyInto(message)
    message[input.size] = 0x80.toByte()
    // The high 64 length bits are zero for every input this port reads.
    for (index in 0 until 8) {
        message[message.size - 8 + index] = ((bitLength ushr (56 - 8 * index)) and 0xff).toByte()
    }

    val words = LongArray(80)
    val hash = longArrayOf(
        0x6a09e667f3bcc908L, 0xbb67ae8584caa73buL.toLong(), 0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1uL.toLong(),
        0x510e527fade682d1L, 0x9b05688c2b3e6c1fuL.toLong(), 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L,
    )

    for (chunk in 0 until message.size / 128) {
        for (index in 0 until 16) {
            val offset = chunk * 128 + index * 8
            var value = 0L
            for (byte in 0 until 8) {
                value = (value shl 8) or (message[offset + byte].toLong() and 0xff)
            }
            words[index] = value
        }
        for (index in 16 until 80) {
            val previous15 = words[index - 15]
            val previous2 = words[index - 2]
            val s0 = rotateRight(previous15, 1) xor rotateRight(previous15, 8) xor (previous15 ushr 7)
            val s1 = rotateRight(previous2, 19) xor rotateRight(previous2, 61) xor (previous2 ushr 6)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }

        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]

        for (index in 0 until 80) {
            val s1 = rotateRight(e, 14) xor rotateRight(e, 18) xor rotateRight(e, 41)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + choice + K[index] + words[index]
            val s0 = rotateRight(a, 28) xor rotateRight(a, 34) xor rotateRight(a, 39)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + majority

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h
    }

    val out = ByteArray(64)
    for (index in 0 until 8) {
        for (byte in 0 until 8) {
            out[index * 8 + byte] = ((hash[index] ushr (56 - byte * 8)) and 0xff).toByte()
        }
    }
    return out
}

/** FIPS 180-4 round constants: the first 64 bits of the cube roots of the first 80 primes. */
private val K: LongArray = longArrayOf(
    0x428a2f98d728ae22L, 0x7137449123ef65cdL, 0xb5c0fbcfec4d3b2fuL.toLong(), 0xe9b5dba58189dbbcuL.toLong(),
    0x3956c25bf348b538L, 0x59f111f1b605d019L, 0x923f82a4af194f9buL.toLong(), 0xab1c5ed5da6d8118uL.toLong(),
    0xd807aa98a3030242uL.toLong(), 0x12835b0145706fbeL, 0x243185be4ee4b28cL, 0x550c7dc3d5ffb4e2L,
    0x72be5d74f27b896fL, 0x80deb1fe3b1696b1uL.toLong(), 0x9bdc06a725c71235uL.toLong(), 0xc19bf174cf692694uL.toLong(),
    0xe49b69c19ef14ad2uL.toLong(), 0xefbe4786384f25e3uL.toLong(), 0x0fc19dc68b8cd5b5L, 0x240ca1cc77ac9c65L,
    0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L, 0x5cb0a9dcbd41fbd4L, 0x76f988da831153b5L,
    0x983e5152ee66dfabuL.toLong(), 0xa831c66d2db43210uL.toLong(), 0xb00327c898fb213fuL.toLong(), 0xbf597fc7beef0ee4uL.toLong(),
    0xc6e00bf33da88fc2uL.toLong(), 0xd5a79147930aa725uL.toLong(), 0x06ca6351e003826fL, 0x142929670a0e6e70L,
    0x27b70a8546d22ffcL, 0x2e1b21385c26c926L, 0x4d2c6dfc5ac42aedL, 0x53380d139d95b3dfL,
    0x650a73548baf63deL, 0x766a0abb3c77b2a8L, 0x81c2c92e47edaee6uL.toLong(), 0x92722c851482353buL.toLong(),
    0xa2bfe8a14cf10364uL.toLong(), 0xa81a664bbc423001uL.toLong(), 0xc24b8b70d0f89791uL.toLong(), 0xc76c51a30654be30uL.toLong(),
    0xd192e819d6ef5218uL.toLong(), 0xd69906245565a910uL.toLong(), 0xf40e35855771202auL.toLong(), 0x106aa07032bbd1b8L,
    0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L, 0x2748774cdf8eeb99L, 0x34b0bcb5e19b48a8L,
    0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL, 0x5b9cca4f7763e373L, 0x682e6ff3d6b2b8a3L,
    0x748f82ee5defb2fcL, 0x78a5636f43172f60L, 0x84c87814a1f0ab72uL.toLong(), 0x8cc702081a6439ecuL.toLong(),
    0x90befffa23631e28uL.toLong(), 0xa4506cebde82bde9uL.toLong(), 0xbef9a3f7b2c67915uL.toLong(), 0xc67178f2e372532buL.toLong(),
    0xca273eceea26619cuL.toLong(), 0xd186b8c721c0c207uL.toLong(), 0xeada7dd6cde0eb1euL.toLong(), 0xf57d4f7fee6ed178uL.toLong(),
    0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L, 0x113f9804bef90daeL, 0x1b710b35131c471bL,
    0x28db77f523047d84L, 0x32caab7b40c72493L, 0x3c9ebe0a15c9bebcL, 0x431d67c49c100d4cL,
    0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL, 0x5fcb6fab3ad6faecL, 0x6c44198c4a475817L,
)
