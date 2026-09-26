/*
 * Audio match model — AnyDownload
 *
 * The matcher's public shape. A Spotify [SongRecord] becomes one [AudioMatch]
 * or a typed [SpotifyMatchError.NoMatch]; the matcher never downloads and
 * never writes a media file. Fallback providers (T-089) add more
 * [AudioSource]s; this task ships YouTube Music then YouTube.
 */
package com.anydownlod.core.music

/** Where a matched URL comes from. T-089 adds the fallback providers. */
enum class AudioSource {
    YOUTUBE_MUSIC,
    YOUTUBE,
    SOUNDCLOUD,
    BANDCAMP,
    PIPED,
    SLIDER_KZ,
}

/** One candidate URL a provider search returned. */
data class AudioCandidate(
    val source: AudioSource,
    val url: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val channel: String? = null,
    val durationSeconds: Double? = null,
    val album: String? = null,
    val verified: Boolean = false,
    val viewCount: Long? = null,
)

/** The matcher's explicit options; spotDL's defaults are the defaults here. */
data class MatcherOptions(
    /** Only verified results may win (spotDL `--only-verified-results`). */
    val onlyVerified: Boolean = false,
    /** Score results; false takes the first result (spotDL `--dont-filter-results`). */
    val filterResults: Boolean = true,
)

/** One winning URL, its score, and the Spotify record that asked for it. */
data class AudioMatch(
    val url: String,
    val source: AudioSource,
    val score: Double,
    val record: SongRecord,
    /** True for a `YouTubeURL|SpotifyURL` pair that skipped the search. */
    val manual: Boolean = false,
)

/** The typed miss. The message names the song and never a provider body. */
sealed class SpotifyMatchError(message: String) : Exception(message) {
    class NoMatch(
        val song: String,
        val tried: List<AudioSource> = emptyList(),
    ) : SpotifyMatchError(
        if (tried.isEmpty()) {
            "No audio match was found for \"$song\"."
        } else {
            "No audio match was found for \"$song\" (tried ${tried.joinToString(", ") { it.displayName() }})."
        },
    )
}

internal fun AudioSource.displayName(): String = when (this) {
    AudioSource.YOUTUBE_MUSIC -> "YouTube Music"
    AudioSource.YOUTUBE -> "YouTube"
    AudioSource.SOUNDCLOUD -> "SoundCloud"
    AudioSource.BANDCAMP -> "Bandcamp"
    AudioSource.PIPED -> "Piped"
    AudioSource.SLIDER_KZ -> "slider.kz"
}

/**
 * One search backend. Implementations return candidate URLs only; they never
 * download. [matchedByIsrc] marks a search whose query is an ISRC, which
 * providers that [supportsIsrc] may answer directly.
 */
interface AudioProvider {
    val source: AudioSource
    val supportsIsrc: Boolean

    suspend fun search(query: String, matchedByIsrc: Boolean = false): List<AudioCandidate>
}
