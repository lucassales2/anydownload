/*
 * Spotify Web API metadata client — AnyDownload
 *
 * Reads the public Spotify Web API object model and turns it into
 * [SongRecord]s. Both the unauthenticated and official clients share this
 * parser; only the token source differs. Reimplements the entity expansion
 * spotDL v4.5.2 (commit cd4a4203) does in `types/song.py`, `types/album.py`,
 * `types/playlist.py`, and `types/artist.py` without copying its Python.
 *
 * Spotify audio streams, preview URLs, and DRM are never read or returned.
 * Artwork URLs are public images, not media streams.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The album fields a track object may not carry (album expansion). */
internal data class AlbumContext(
    val id: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val type: String? = null,
    val artworkUrl: String? = null,
    val releaseDate: String? = null,
    val tracksCount: Int? = null,
    val discCount: Int? = null,
    val genres: List<String> = emptyList(),
    val publisher: String? = null,
)

/** The list context spotDL attaches while expanding a list. */
internal data class ListContext(
    val name: String? = null,
    val url: String? = null,
    val position: Int? = null,
    val length: Int? = null,
)

internal fun albumContextOf(album: JsonObject?): AlbumContext = AlbumContext(
    id = album.str("id"),
    name = album.str("name"),
    artist = album.artistNames().firstOrNull(),
    type = album.str("album_type"),
    artworkUrl = album.largestImageUrl(),
    releaseDate = album.str("release_date"),
    tracksCount = album.int("total_tracks"),
    genres = album.array("genres").strings(),
    publisher = album.str("label"),
)

class SpotifyWebApiClient(
    private val http: ExtractorHttp,
    private val tokens: SpotifyTokenSource,
    override val backend: SpotifyBackend,
    private val apiBaseUrl: String = "https://api.spotify.com/v1",
    private val pageSize: Int = 50,
    private val maxPages: Int = 100,
) : SpotifyMetadataClient {

    override suspend fun resolve(query: SpotifyQuery): SongListResult = when (query) {
        is SpotifyQuery.Track -> SongListResult.of(resolveTrack(query))
        is SpotifyQuery.Album -> expandAlbum(query, query.id)
        is SpotifyQuery.Playlist -> expandPlaylist(query, query.id)
        is SpotifyQuery.Artist -> expandArtist(query, query.id)
        is SpotifyQuery.TextSearch -> SongListResult.of(searchFirstTrack(query))
        is SpotifyQuery.AlbumSearch -> expandAlbum(query, searchBestId(query, query.term, "album"))
        is SpotifyQuery.PlaylistSearch -> expandPlaylist(query, searchBestId(query, query.term, "playlist"))
        is SpotifyQuery.ArtistSearch -> expandArtist(query, searchBestId(query, query.term, "artist"))
    }

    // ------------------------------------------------------------ single track

    private suspend fun resolveTrack(query: SpotifyQuery): SongRecord {
        val track = apiGet(query, "/tracks/${(query as SpotifyQuery.Track).id}")
        return when (val entry = trackEntry(track, list = null, albumOverride = null)) {
            is SongListEntry.Song -> entry.record
            is SongListEntry.Unavailable -> throw SpotifyMetadataError.NotFound(
                if (entry.reason == UnavailableReason.LOCAL_TRACK) {
                    "This Spotify track is a local file and has no streamable metadata."
                } else {
                    "This Spotify track has no usable metadata."
                },
            )
        }
    }

    // ------------------------------------------------------------------ album

    private suspend fun expandAlbum(query: SpotifyQuery, albumId: String): SongListResult {
        val album = apiGet(query, "/albums/$albumId")
        val name = album.str("name") ?: throw SpotifyMetadataError.NotFound()
        val albumUrl = album.obj("external_urls").str("spotify")
        val tracks = collectAlbumTracks(query, albumId)
        val discCount = tracks.mapNotNull { it.int("disc_number") }.maxOrNull()?.takeIf { it > 0 } ?: 1
        val context = albumContextOf(album).copy(discCount = discCount)
        val list = ListContext(name = name, url = albumUrl, length = album.int("total_tracks"))
        val entries = tracks.map { track -> trackEntry(track, list, context) }
        return SongListResult(
            name = name,
            url = albumUrl,
            coverUrl = context.artworkUrl,
            entries = entries,
        )
    }

    private suspend fun collectAlbumTracks(query: SpotifyQuery, albumId: String): List<JsonObject> =
        paginate(query, "/albums/$albumId/tracks") { page ->
            page.array("items").objects()
        }

    // --------------------------------------------------------------- playlist

    private suspend fun expandPlaylist(query: SpotifyQuery, playlistId: String): SongListResult {
        val playlist = apiGet(query, "/playlists/$playlistId")
        val name = playlist.str("name") ?: throw SpotifyMetadataError.NotFound()
        val playlistUrl = playlist.obj("external_urls").str("spotify")
        val cover = playlist.largestImageUrl()
        val total = playlist.obj("tracks").int("total")
        val items = paginate(query, "/playlists/$playlistId/tracks") { page ->
            page.array("items").objects()
        }
        val entries = items.mapIndexed { index, item ->
            val track = item.obj("track") ?: item.obj("item")
            val list = ListContext(
                name = name,
                url = playlistUrl,
                position = index + 1,
                length = total,
            )
            // The playlist item carries `is_local` next to the track object;
            // the track object may carry it too on older responses.
            if (item.flag("is_local") == true || track?.flag("is_local") == true) {
                SongListEntry.Unavailable(
                    reason = UnavailableReason.LOCAL_TRACK,
                    id = track?.str("id"),
                    title = track?.str("name"),
                )
            } else {
                trackEntry(track = track, list = list, albumOverride = null)
            }
        }
        return SongListResult(name = name, url = playlistUrl, coverUrl = cover, entries = entries)
    }

    // ----------------------------------------------------------------- artist

    private suspend fun expandArtist(query: SpotifyQuery, artistId: String): SongListResult {
        val artist = apiGet(query, "/artists/$artistId")
        val name = artist.str("name") ?: throw SpotifyMetadataError.NotFound()
        val artistUrl = artist.obj("external_urls").str("spotify")
        val albums = paginate(query, "/artists/$artistId/albums?include_groups=album,single,compilation") { page ->
            page.array("items").objects()
        }
        val entries = mutableListOf<SongListEntry>()
        val seenAlbums = mutableSetOf<String>()
        val seenSongs = mutableSetOf<String>()
        for (album in albums) {
            val albumName = album.str("name") ?: continue
            val albumKey = albumName.trim().lowercase()
            if (!seenAlbums.add(albumKey)) continue
            val albumId = album.str("id")
            if (albumId == null) {
                entries += SongListEntry.Unavailable(
                    reason = UnavailableReason.FETCH_FAILED,
                    title = albumName,
                )
                continue
            }
            val expanded = try {
                expandAlbum(query, albumId)
            } catch (error: SpotifyMetadataError) {
                entries += SongListEntry.Unavailable(
                    reason = UnavailableReason.FETCH_FAILED,
                    id = albumId,
                    title = albumName,
                    message = error.message,
                )
                continue
            }
            for (entry in expanded.entries) {
                when (entry) {
                    is SongListEntry.Song -> {
                        if (seenSongs.add(entry.record.dedupeKey)) {
                            entries += SongListEntry.Song(
                                entry.record.copy(listName = name, listUrl = artistUrl),
                            )
                        }
                    }

                    is SongListEntry.Unavailable -> entries += entry
                }
            }
        }
        return SongListResult(name = name, url = artistUrl, coverUrl = artist.largestImageUrl(), entries = entries)
    }

    // ----------------------------------------------------------------- search

    private suspend fun searchFirstTrack(query: SpotifyQuery.TextSearch): SongRecord {
        val items = searchItems(query, query.term, "track")
        val first = items.firstOrNull() ?: throw SpotifyMetadataError.NoResults(
            "No Spotify track matched this search.",
        )
        return when (val entry = trackEntry(first, list = null, albumOverride = null)) {
            is SongListEntry.Song -> entry.record
            is SongListEntry.Unavailable -> throw SpotifyMetadataError.NoResults(
                "The first Spotify match could not be used.",
            )
        }
    }

    private suspend fun searchBestId(query: SpotifyQuery, term: String, type: String): String {
        val items = searchItems(query, term, type)
        if (items.isEmpty()) throw SpotifyMetadataError.NoResults("No Spotify match was found for this search.")
        val best = items.maxByOrNull { item -> SpotifyTextScore.ratio(term, item.str("name").orEmpty()) }
            ?: throw SpotifyMetadataError.NoResults()
        return best.str("id") ?: throw SpotifyMetadataError.NoResults("The Spotify match had no id.")
    }

    private suspend fun searchItems(query: SpotifyQuery, term: String, type: String): List<JsonObject> {
        if (term.isBlank()) throw SpotifyMetadataError.BadQuery("The Spotify search text is empty.")
        val plural = when (type) {
            "track" -> "tracks"
            "album" -> "albums"
            "playlist" -> "playlists"
            "artist" -> "artists"
            else -> "${type}s"
        }
        val json = apiGet(query, "/search?q=${encodeQueryComponent(term)}&type=$type&limit=$pageSize")
        return json.obj(plural).array("items").objects()
    }

    // ------------------------------------------------------------- pagination

    /** Follows `next` until Spotify says there is none, with a hard page cap. */
    private suspend fun paginate(
        query: SpotifyQuery,
        path: String,
        itemsOf: (JsonObject) -> List<JsonObject>,
    ): List<JsonObject> {
        val collected = mutableListOf<JsonObject>()
        var offset = 0
        var pages = 0
        while (true) {
            val separator = if (path.contains('?')) '&' else '?'
            val page = apiGet(query, "$path${separator}limit=$pageSize&offset=$offset")
            collected += itemsOf(page)
            if (page.str("next") == null) break
            pages++
            if (pages >= maxPages) {
                throw SpotifyMetadataError.Unavailable("This Spotify list is too long to expand.")
            }
            offset += pageSize
        }
        return collected
    }

    // ------------------------------------------------------------------- HTTP

    private suspend fun apiGet(query: SpotifyQuery, path: String): JsonObject {
        val url = if (path.startsWith("http")) path else "$apiBaseUrl$path"
        return try {
            getJson(url, tokens.token(query))
        } catch (error: SpotifyMetadataError.NotAuthorized) {
            // A cached session may have expired mid-expansion; refresh once.
            tokens.invalidate()
            getJson(url, tokens.token(query))
        }
    }

    private suspend fun getJson(url: String, token: SpotifyAccessToken): JsonObject {
        val element = try {
            http.downloadJson(
                url = url,
                headers = mapOf("accept" to "application/json"),
                authorization = "Bearer ${token.value}",
            )
        } catch (error: ExtractionError) {
            throw error.toSpotifyError()
        }
        return element as? JsonObject ?: throw SpotifyMetadataError.Malformed()
    }

    // -------------------------------------------------------------- record map

    private fun trackEntry(
        track: JsonObject?,
        list: ListContext?,
        albumOverride: AlbumContext?,
    ): SongListEntry = songRecordEntry(track, list, albumOverride)
}

/**
 * Shared track-to-record mapping. The metadata client, the playlist/album
 * expansion, and the library client all use it so every path returns the same
 * record shape.
 */
internal fun songRecordEntry(
    track: JsonObject?,
    list: ListContext?,
    albumOverride: AlbumContext?,
): SongListEntry {
    if (track == null) return SongListEntry.Unavailable(UnavailableReason.NOT_A_TRACK)
    val id = track.str("id")
    val displayName = track.str("name")
    if (track.flag("is_local") == true) {
        return SongListEntry.Unavailable(UnavailableReason.LOCAL_TRACK, id = id, title = displayName)
    }
    val type = track.str("type")
    if (type != null && type != "track") {
        return SongListEntry.Unavailable(UnavailableReason.NOT_A_TRACK, id = id, title = displayName)
    }
    val duration = track.num("duration_ms")
    if (duration == null || duration <= 0) {
        return SongListEntry.Unavailable(UnavailableReason.NO_DURATION, id = id, title = displayName)
    }
    val title = displayName
        ?: return SongListEntry.Unavailable(UnavailableReason.MISSING_DATA, id = id)
    val album = albumOverride ?: albumContextOf(track.obj("album"))
    val record = SongRecord(
        songId = id,
        title = title,
        artists = track.artistNames(),
        album = album.name,
        albumArtist = album.artist,
        albumId = album.id,
        albumType = album.type,
        durationMs = duration,
        isrc = track.obj("external_ids").str("isrc"),
        artworkUrl = album.artworkUrl,
        trackNumber = track.int("track_number"),
        tracksCount = album.tracksCount,
        discNumber = track.int("disc_number"),
        discCount = album.discCount,
        year = yearOf(album.releaseDate),
        releaseDate = album.releaseDate,
        genres = album.genres,
        publisher = album.publisher,
        explicit = track.flag("explicit") ?: false,
        spotifyUrl = track.obj("external_urls").str("spotify"),
        listName = list?.name,
        listUrl = list?.url,
        listPosition = list?.position,
        listLength = list?.length,
    )
    return SongListEntry.Song(record)
}

/** String items of a JSON array; non-strings are ignored. */
internal fun JsonArray?.strings(): List<String> =
    this?.mapNotNull { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
    } ?: emptyList()
