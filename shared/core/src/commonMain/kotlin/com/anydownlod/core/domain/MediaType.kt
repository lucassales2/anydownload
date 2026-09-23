package com.anydownlod.core.domain

/**
 * Media kind requested for a download. The local desktop engine maps each
 * value onto an output mode: video or audio media, captions-only, or
 * thumbnail-only.
 */
enum class MediaType(val wireName: String) {
    VIDEO("video"),
    AUDIO("audio"),
    CAPTIONS("captions"),
    THUMBNAIL("thumbnail"),
    ;

    companion object {
        /** Returns `null` for values this client does not recognize yet. */
        fun fromWire(value: String): MediaType? = entries.firstOrNull { it.wireName == value.lowercase() }
    }
}
