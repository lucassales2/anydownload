/*
 * Spotify list options and file seam — AnyDownload
 *
 * The list-level behavior T-087 adds on top of the per-song download: the
 * output template, overwrite mode, m3u, archive, skip-explicit, playlist
 * numbering, and scan-for-songs. The store seam is root-scoped; hosts wire a
 * real implementation, tests use the in-memory fake, and web/mobile fall back
 * to [SpotifyListStore.Unavailable] until their host task lands.
 */
package com.anydownlod.core.music

import com.anydownlod.core.domain.OverwriteMode

/** The list-level options of one Spotify expansion. */
data class SpotifyListOptions(
    val outputTemplate: String = SpotifyOutputTemplate.DEFAULT,
    val restrict: SpotifyRestrict = SpotifyRestrict.NONE,
    /** spotDL's default: skip an existing file and do not download again. */
    val overwrite: OverwriteMode = OverwriteMode.SKIP,
    val writeM3u: Boolean = false,
    /** Relative m3u path inside the download root; null derives from the list name. */
    val m3uName: String? = null,
    val archive: Boolean = false,
    /** Relative archive path inside the download root. */
    val archiveName: String = DEFAULT_ARCHIVE,
    val skipExplicit: Boolean = false,
    val playlistNumbering: Boolean = false,
    val scanForSongs: Boolean = false,
) {
    companion object {
        const val DEFAULT_ARCHIVE: String = ".spotify-archive.txt"
    }
}

/**
 * Lyrics behavior for one Spotify expansion. Providers are tried in order;
 * the default is genius, azlyrics, musixmatch, then synced. `generate-lrc`
 * writes a sibling `.lrc` only when the synced provider returned timed lines.
 */
data class SpotifyLyricsOptions(
    val enabled: Boolean = true,
    val providers: List<String> = LyricsProviders.DEFAULT_ORDER,
    val generateLrc: Boolean = false,
)

/**
 * Root-scoped file access for the list sidecars. Paths are relative to the
 * download root and are validated by the host implementation; a missing store
 * makes the m3u/archive/scan features no-ops.
 */
interface SpotifyListStore {
    fun exists(relativePath: String): Boolean

    fun readLines(relativePath: String): List<String>?

    fun write(relativePath: String, content: String): Boolean

    /** Deletes a root-relative file; true when one was removed. */
    fun delete(relativePath: String): Boolean = false

    /** Every file under the download root, relative and normalized. */
    fun list(): List<String> = emptyList()

    companion object {
        val Unavailable: SpotifyListStore = object : SpotifyListStore {
            override fun exists(relativePath: String): Boolean = false

            override fun readLines(relativePath: String): List<String>? = null

            override fun write(relativePath: String, content: String): Boolean = false
        }
    }
}
