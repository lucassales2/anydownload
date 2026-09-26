/*
 * Song records — AnyDownload
 *
 * Reimplements the metadata shape spotDL v4.5.2 (commit cd4a4203) keeps on
 * its `Song` object. No spotDL Python is copied or vendored; the fields come
 * from the public Spotify Web API object model and from spotDL's documented
 * output-template variables. Spotify audio is never a field here: the record
 * is metadata only.
 */
package com.anydownlod.core.music

import kotlinx.serialization.Serializable

/**
 * One resolved Spotify track. A field stays null (or empty) when Spotify did
 * not return it; nothing is invented. [artworkUrl] is a public image address,
 * not a media stream, and must not be written into logs.
 */
@Serializable
data class SongRecord(
    /** Spotify track id (`track-id` in the output template). */
    val songId: String? = null,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val albumId: String? = null,
    val albumType: String? = null,
    /** Track length in milliseconds as Spotify returns it. */
    val durationMs: Long? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null,
    val tracksCount: Int? = null,
    val discNumber: Int? = null,
    val discCount: Int? = null,
    val year: Int? = null,
    /** Spotify `release_date`, kept in full for `{original-date}`. */
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val publisher: String? = null,
    val explicit: Boolean = false,
    /** The public `open.spotify.com/track/...` page, when Spotify gave one. */
    val spotifyUrl: String? = null,
    /** Context when this record came out of a playlist, album, or artist. */
    val listName: String? = null,
    val listUrl: String? = null,
    val listPosition: Int? = null,
    val listLength: Int? = null,
) {
    /** spotDL's primary artist; empty when Spotify returned no artist. */
    val artist: String get() = artists.firstOrNull().orEmpty()

    /** `{duration}` in whole seconds, rounded down like spotDL's conversion. */
    val durationSeconds: Int? get() = durationMs?.let { (it / 1000).toInt() }

    /** A stable key for dedupe inside one expansion. */
    internal val dedupeKey: String
        get() = "${songId ?: title.lowercase()}|${artists.joinToString(",").lowercase()}"
}

/** spotDL's display name: `artist - title`, or the title alone. */
internal fun songDisplayName(record: SongRecord): String =
    if (record.artist.isBlank()) record.title else "${record.artist} - ${record.title}"

/** Why one entry of a list could not become a [SongRecord]. */
@Serializable
enum class UnavailableReason {
    /** Spotify `is_local` track: it has no streamable metadata. */
    LOCAL_TRACK,

    /** The playlist entry is an episode, not a track. */
    NOT_A_TRACK,

    /** Spotify returned no usable duration for the entry. */
    NO_DURATION,

    /** Spotify returned an entry without the fields a record needs. */
    MISSING_DATA,

    /** A nested fetch (for example one artist album) failed. */
    FETCH_FAILED,
}

/** One ordered entry of an expanded album, playlist, artist, or search. */
@Serializable
sealed interface SongListEntry {

    @Serializable
    data class Song(val record: SongRecord) : SongListEntry

    /**
     * A spot that could not become a song. [title] is the entry's display
     * name when Spotify gave one; [message] is short and already redacted.
     */
    @Serializable
    data class Unavailable(
        val reason: UnavailableReason,
        val id: String? = null,
        val title: String? = null,
        val message: String? = null,
    ) : SongListEntry
}

/**
 * The result of resolving one query: an ordered list where a per-entry
 * failure keeps its place instead of dropping the rest.
 */
@Serializable
data class SongListResult(
    val name: String,
    val url: String? = null,
    val coverUrl: String? = null,
    val entries: List<SongListEntry> = emptyList(),
) {
    val songs: List<SongRecord>
        get() = entries.filterIsInstance<SongListEntry.Song>().map { it.record }

    val unavailable: List<SongListEntry.Unavailable>
        get() = entries.filterIsInstance<SongListEntry.Unavailable>()

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        /** A single resolved track, so every query kind returns the same shape. */
        fun of(record: SongRecord): SongListResult = SongListResult(
            name = record.title,
            url = record.spotifyUrl,
            coverUrl = record.artworkUrl,
            entries = listOf(SongListEntry.Song(record)),
        )
    }
}
