/*
 * Spotify JSON helpers — AnyDownload
 *
 * Small typed accessors over kotlinx.serialization JSON for the public
 * Spotify Web API object model. Missing or wrongly-typed fields return null;
 * nothing is coerced and no value is invented.
 */
package com.anydownlod.core.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asJsonObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asJsonArray(): JsonArray? = this as? JsonArray

internal fun JsonObject?.str(key: String): String? {
    val primitive = this?.get(key) as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    return primitive.content.takeIf { it.isNotEmpty() }
}

internal fun JsonObject?.num(key: String): Long? =
    (this?.get(key) as? JsonPrimitive)?.longOrNull

internal fun JsonObject?.dbl(key: String): Double? =
    (this?.get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()

internal fun JsonObject?.int(key: String): Int? = num(key)?.let {
    if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null
}

internal fun JsonObject?.flag(key: String): Boolean? =
    (this?.get(key) as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject?.obj(key: String): JsonObject? = (this?.get(key)).asJsonObject()

internal fun JsonObject?.array(key: String): JsonArray? = (this?.get(key)).asJsonArray()

/** Non-null object items of an array; a mixed list keeps only its objects. */
internal fun JsonArray?.objects(): List<JsonObject> =
    this?.mapNotNull { it as? JsonObject } ?: emptyList()

/** `artists: [{name: ...}]` to a plain name list. */
internal fun JsonObject?.artistNames(key: String = "artists"): List<String> =
    array(key).objects().mapNotNull { it.str("name") }

/**
 * The URL of the largest image by area, falling back to the first image when
 * Spotify did not give dimensions. Null when there is no usable image.
 */
internal fun JsonObject?.largestImageUrl(key: String = "images"): String? {
    val images = array(key).objects()
    if (images.isEmpty()) return null
    val largest = images.maxByOrNull { image ->
        val width = image.num("width") ?: 0L
        val height = image.num("height") ?: 0L
        width * height
    } ?: return null
    return largest.str("url")
}

/** The leading four-digit year of a Spotify `release_date`, or null. */
internal fun yearOf(releaseDate: String?): Int? {
    val text = releaseDate?.trim().orEmpty()
    if (text.length < 4) return null
    return text.take(4).toIntOrNull()
}

/** Percent-encodes one query-component value; spaces become `%20`. */
internal fun encodeQueryComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val unreserved = (code in 'A'.code..'Z'.code) ||
            (code in 'a'.code..'z'.code) ||
            (code in '0'.code..'9'.code) ||
            code == '-'.code || code == '_'.code || code == '.'.code || code == '~'.code
        if (unreserved) {
            append(code.toChar())
        } else {
            append('%')
            append(HEX[code shr 4])
            append(HEX[code and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
