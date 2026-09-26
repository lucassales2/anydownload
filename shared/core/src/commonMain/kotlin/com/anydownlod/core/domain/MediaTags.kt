package com.anydownlod.core.domain

/**
 * Metadata the Spotify path embeds into a finished audio file.
 *
 * Only fields the host toolkit can write are set; the engine passes this to
 * `MediaToolkit.embedTags` after the audio is written. Values come from the
 * public Spotify record and never carry a URL, token, or secret.
 */
data class MediaTags(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isrc: String? = null,
    /** Plain or LRC lyrics; embedded only when the host container supports them. */
    val lyrics: String? = null,
)
