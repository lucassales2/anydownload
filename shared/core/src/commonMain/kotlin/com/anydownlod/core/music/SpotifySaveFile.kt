/*
 * Spotify save file — AnyDownload
 *
 * The `.spotdl` JSON document `save` writes and `sync` (T-091) reuses. It
 * holds song records plus the matched URL and lyrics from `--preload`; it is
 * never a media file. The format is our own; spotDL's Python file format is
 * not copied.
 */
package com.anydownlod.core.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One saved song: the Spotify record plus what preload resolved. */
@Serializable
data class SpotifySavedSong(
    val record: SongRecord,
    val downloadUrl: String? = null,
    val lyrics: String? = null,
    /**
     * Relative audio files this sync created for the song. `sync` removes
     * only these; a file no save file names is never touched.
     */
    val createdFiles: List<String> = emptyList(),
)

/** The whole `.spotdl` document. */
@Serializable
data class SpotifySaveFile(
    val version: Int = VERSION,
    val query: String = "",
    /** True for a file written by `sync`; false for a plain `save`. */
    val sync: Boolean = false,
    val songs: List<SpotifySavedSong> = emptyList(),
) {
    companion object {
        const val VERSION: Int = 1
        const val EXTENSION: String = ".spotdl"
    }
}

/** Encode/decode plus the root-safety check every path must pass. */
object SpotifySaveFiles {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isSaveFile(relativePath: String): Boolean =
        relativePath.trim().endsWith(SpotifySaveFile.EXTENSION, ignoreCase = true)

    fun encode(file: SpotifySaveFile): String = json.encodeToString(file)

    fun decode(text: String): SpotifySaveFile {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "The save file is empty." }
        return json.decodeFromString(trimmed)
    }
}

/**
 * Root-relative path rules for the list sidecars. An absolute path, a `..`
 * segment, or a Windows drive/backslash is refused before any file operation.
 */
object SpotifyListPaths {
    fun validate(relativePath: String): String {
        val trimmed = relativePath.trim()
        require(trimmed.isNotEmpty()) { "The path is empty." }
        require(!trimmed.startsWith("/")) { "The path must be relative to the download root." }
        val segments = trimmed.split('/')
        require(segments.none { it == ".." || it.contains('\\') || it.contains(':') }) {
            "The path escapes the download root."
        }
        val normalized = segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
        require(normalized.isNotEmpty()) { "The path escapes the download root." }
        return normalized
    }
}
