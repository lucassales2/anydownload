package com.anydownlod.core.extract.harness

/**
 * Small pure-Kotlin MD5 for the upstream `md5:` matcher. Kotlin common has no
 * message digest, so the algorithm is implemented here; the harness never
 * uses it for security.
 */
internal fun md5Hex(input: ByteArray): String {
    val digest = md5(input)
    val hex = "0123456789abcdef"
    val out = StringBuilder(32)
    for (byte in digest) {
        val value = byte.toInt() and 0xff
        out.append(hex[value ushr 4]).append(hex[value and 0x0f])
    }
    return out.toString()
}

private fun rotateLeft(value: Int, bits: Int): Int = (value shl bits) or (value ushr (32 - bits))

private fun md5(input: ByteArray): ByteArray {
    val message = pad(input)
    val shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    val k = IntArray(64) { index ->
        (kotlin.math.abs(kotlin.math.sin(index + 1.0)) * 4_294_967_296.0).toLong().toInt()
    }

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476

    for (chunk in 0 until message.size / 64) {
        val words = IntArray(16)
        for (index in 0 until 16) {
            val offset = chunk * 64 + index * 4
            words[index] = (message[offset].toInt() and 0xff) or
                ((message[offset + 1].toInt() and 0xff) shl 8) or
                ((message[offset + 2].toInt() and 0xff) shl 16) or
                ((message[offset + 3].toInt() and 0xff) shl 24)
        }
        var a = a0
        var b = b0
        var c = c0
        var d = d0
        for (index in 0 until 64) {
            val f: Int
            val g: Int
            when {
                index < 16 -> {
                    f = (b and c) or (b.inv() and d)
                    g = index
                }

                index < 32 -> {
                    f = (d and b) or (d.inv() and c)
                    g = (5 * index + 1) % 16
                }

                index < 48 -> {
                    f = b xor c xor d
                    g = (3 * index + 5) % 16
                }

                else -> {
                    f = c xor (b or d.inv())
                    g = (7 * index) % 16
                }
            }
            val temp = d
            d = c
            c = b
            b += rotateLeft(a + f + k[index] + words[g], shifts[index])
            a = temp
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    val out = ByteArray(16)
    val words = intArrayOf(a0, b0, c0, d0)
    for (index in 0 until 4) {
        out[index * 4] = (words[index] and 0xff).toByte()
        out[index * 4 + 1] = ((words[index] ushr 8) and 0xff).toByte()
        out[index * 4 + 2] = ((words[index] ushr 16) and 0xff).toByte()
        out[index * 4 + 3] = ((words[index] ushr 24) and 0xff).toByte()
    }
    return out
}

private fun pad(input: ByteArray): ByteArray {
    val lengthBits = input.size.toLong() * 8
    var padLength = 64 - ((input.size + 9) % 64)
    if (padLength == 64) padLength = 0
    val total = input.size + 1 + padLength + 8
    val out = ByteArray(total)
    input.copyInto(out)
    out[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        out[total - 8 + index] = ((lengthBits ushr (8 * index)) and 0xff).toByte()
    }
    return out
}
