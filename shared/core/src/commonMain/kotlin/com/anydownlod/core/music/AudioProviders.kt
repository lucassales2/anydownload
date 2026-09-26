/*
 * YouTube Music and YouTube audio providers — AnyDownload
 *
 * Wraps the extractor search path ([YoutubeSearch]) in the matcher's
 * [AudioProvider] seam. YouTube Music is first and supports an ISRC query;
 * YouTube is second and never verified. No new HTTP stack, no download.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.youtube.MusicSearchFilter
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.extract.youtube.YoutubeSearchResult

/** YouTube Music: songs first, then videos, and ISRC searches when asked. */
class YoutubeMusicAudioProvider(private val search: YoutubeSearch) : AudioProvider {
    override val source: AudioSource = AudioSource.YOUTUBE_MUSIC
    override val supportsIsrc: Boolean = true

    override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> {
        if (matchedByIsrc) {
            // Upstream searches the ISRC without a section filter.
            return search.searchMusic(query, filter = null).map { it.toCandidate(source) }
        }
        val songs = search.searchMusic(query, MusicSearchFilter.SONGS)
        val videos = search.searchMusic(query, MusicSearchFilter.VIDEOS)
        return (songs + videos).map { it.toCandidate(source) }
    }
}

/** Regular YouTube: `ytsearch`-style results, never verified. */
class YoutubeAudioProvider(private val search: YoutubeSearch) : AudioProvider {
    override val source: AudioSource = AudioSource.YOUTUBE
    override val supportsIsrc: Boolean = false

    override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> =
        search.searchYoutube(query).map { it.toCandidate(source) }
}

private fun YoutubeSearchResult.toCandidate(source: AudioSource): AudioCandidate = AudioCandidate(
    source = source,
    url = url,
    title = title,
    artists = artists,
    channel = channel,
    durationSeconds = durationSeconds,
    album = album,
    verified = verified,
    viewCount = viewCount,
)

/** A `YouTubeURL|SpotifyURL` pair; the audio URL is used exactly as given. */
data class ManualPair(
    val audioUrl: String,
    val spotifyUrl: String,
)

/**
 * Parses spotDL's manual pair. The left side must be a YouTube URL and the
 * right side a Spotify track URL or URI; anything else is null.
 */
object ManualPairParser {
    fun parse(raw: String): ManualPair? {
        val parts = raw.split('|')
        if (parts.size != 2) return null
        val audioUrl = parts[0].trim()
        val spotifyUrl = parts[1].trim()
        if (!isYoutubeUrl(audioUrl)) return null
        if (!isSpotifyTrackUrl(spotifyUrl)) return null
        return ManualPair(audioUrl = audioUrl, spotifyUrl = spotifyUrl)
    }

    private fun isYoutubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com/watch") ||
            lower.contains("youtu.be/") ||
            lower.contains("music.youtube.com/watch")
    }

    private fun isSpotifyTrackUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("open.spotify.com/track/") || lower.startsWith("spotify:track:")
    }
}
