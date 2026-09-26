/*
 * Spotify query parsing — AnyDownload
 *
 * Reimplements the query classification spotDL v4.5.2 (commit cd4a4203) does
 * in `utils/search.py`: a track/album/playlist/artist URL, a `spotify:` URI,
 * an `album:`/`playlist:`/`artist:` prefixed search, or plain text that
 * searches for one track. No spotDL Python is copied or vendored.
 */
package com.anydownlod.core.music

/**
 * One thing the user asked Spotify for. [raw] is the original input and is
 * never logged; error messages use the typed fields instead.
 */
sealed interface SpotifyQuery {
    val raw: String

    data class Track(val id: String, override val raw: String) : SpotifyQuery
    data class Album(val id: String, override val raw: String) : SpotifyQuery
    data class Playlist(val id: String, override val raw: String) : SpotifyQuery
    data class Artist(val id: String, override val raw: String) : SpotifyQuery

    /** Plain text; the first matching track becomes the record. */
    data class TextSearch(val term: String, override val raw: String) : SpotifyQuery

    /** `album:...`; the best matching album is expanded. */
    data class AlbumSearch(val term: String, override val raw: String) : SpotifyQuery

    /** `playlist:...`; the best matching playlist is expanded. */
    data class PlaylistSearch(val term: String, override val raw: String) : SpotifyQuery

    /** `artist:...`; the best matching artist is expanded. */
    data class ArtistSearch(val term: String, override val raw: String) : SpotifyQuery
}

/** The public embed page for an entity query; null for a text search. */
internal fun SpotifyQuery.embedUrl(): String? = when (this) {
    is SpotifyQuery.Track -> "https://open.spotify.com/embed/track/$id"
    is SpotifyQuery.Album -> "https://open.spotify.com/embed/album/$id"
    is SpotifyQuery.Playlist -> "https://open.spotify.com/embed/playlist/$id"
    is SpotifyQuery.Artist -> "https://open.spotify.com/embed/artist/$id"
    is SpotifyQuery.TextSearch,
    is SpotifyQuery.AlbumSearch,
    is SpotifyQuery.PlaylistSearch,
    is SpotifyQuery.ArtistSearch,
    -> null
}

/**
 * Parses the link field's Spotify input. Returns null for a blank string;
 * every other string becomes some query, because the caller decides whether
 * the field holds a Spotify query at all.
 */
object SpotifyQueryParser {

    private val spotifyUri = Regex(
        "^spotify:(track|album|playlist|artist):([A-Za-z0-9]+)$",
        RegexOption.IGNORE_CASE,
    )

    private val entityKinds = setOf("track", "album", "playlist", "artist")

    fun parse(raw: String): SpotifyQuery? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        spotifyUri.matchEntire(trimmed)?.let { match ->
            val kind = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            return when (kind) {
                "track" -> SpotifyQuery.Track(id, trimmed)
                "album" -> SpotifyQuery.Album(id, trimmed)
                "playlist" -> SpotifyQuery.Playlist(id, trimmed)
                else -> SpotifyQuery.Artist(id, trimmed)
            }
        }

        parseUrl(trimmed)?.let { return it }

        return when {
            trimmed.startsWith("album:", ignoreCase = true) ->
                SpotifyQuery.AlbumSearch(trimmed.substringAfter(':').trim(), trimmed)

            trimmed.startsWith("playlist:", ignoreCase = true) ->
                SpotifyQuery.PlaylistSearch(trimmed.substringAfter(':').trim(), trimmed)

            trimmed.startsWith("artist:", ignoreCase = true) ->
                SpotifyQuery.ArtistSearch(trimmed.substringAfter(':').trim(), trimmed)

            else -> SpotifyQuery.TextSearch(trimmed, trimmed)
        }
    }

    /**
     * True when the link field should go to the Spotify path: a `spotify:`
     * URI, an `open.spotify.com`/`play.spotify.com` link, an
     * `album:`/`playlist:`/`artist:` prefix, or plain text that is not an
     * HTTP(S) URL. A YouTube or other page URL stays on the extractor path.
     */
    fun isSpotifyInput(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        if (spotifyUri.matches(trimmed)) return true
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return lower.substringAfter("://").substringBefore('/').let { authority ->
                authority == "open.spotify.com" || authority == "play.spotify.com"
            }
        }
        if (lower.startsWith("album:") || lower.startsWith("playlist:") || lower.startsWith("artist:")) {
            return true
        }
        return true
    }

    /** `open.spotify.com/{intl-xx/}{kind}/{id}` and the `play.spotify.com` alias. */
    private fun parseUrl(raw: String): SpotifyQuery? {
        val withoutScheme = raw.substringAfter("://", raw)
        val authority = withoutScheme.substringBefore('/').lowercase()
        if (authority != "open.spotify.com" && authority != "play.spotify.com") return null
        val path = withoutScheme.substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        val kindIndex = segments.indexOfFirst { it.lowercase() in entityKinds }
        if (kindIndex < 0) return null
        val kind = segments[kindIndex].lowercase()
        val id = segments.getOrNull(kindIndex + 1) ?: return null
        if (!id.all { it.isLetterOrDigit() }) return null
        return when (kind) {
            "track" -> SpotifyQuery.Track(id, raw)
            "album" -> SpotifyQuery.Album(id, raw)
            "playlist" -> SpotifyQuery.Playlist(id, raw)
            else -> SpotifyQuery.Artist(id, raw)
        }
    }
}
