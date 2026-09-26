/*
 * Spotify user library — AnyDownload
 *
 * The four `--user-auth` queries spotDL v4.5.2 (commit cd4a4203) supports:
 * `saved`, `all-user-playlists`, `all-user-saved-albums`, and
 * `all-user-followed-artists`. They need the on-device user token; a public
 * query never touches this class. Records come from the same shared mapping
 * as T-084 and are queued by the T-086 path.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** The four library queries. */
sealed interface SpotifyLibraryQuery {
    data object Saved : SpotifyLibraryQuery
    data object UserPlaylists : SpotifyLibraryQuery
    data object SavedAlbums : SpotifyLibraryQuery
    data object FollowedArtists : SpotifyLibraryQuery
}

/** Parses spotDL's library query words; null for anything else. */
object SpotifyLibraryQueries {
    const val SAVED: String = "saved"
    const val USER_PLAYLISTS: String = "all-user-playlists"
    const val SAVED_ALBUMS: String = "all-user-saved-albums"
    const val FOLLOWED_ARTISTS: String = "all-user-followed-artists"

    fun parse(raw: String): SpotifyLibraryQuery? = when (raw.trim().lowercase()) {
        SAVED -> SpotifyLibraryQuery.Saved
        USER_PLAYLISTS -> SpotifyLibraryQuery.UserPlaylists
        SAVED_ALBUMS -> SpotifyLibraryQuery.SavedAlbums
        FOLLOWED_ARTISTS -> SpotifyLibraryQuery.FollowedArtists
        else -> null
    }
}

/**
 * Reads the user's library with the stored token. Expansion of each playlist,
 * album, and artist goes through the same [SpotifyWebApiClient] the public
 * metadata path uses, but with the user token so private lists resolve.
 */
class SpotifyLibraryClient(
    private val http: ExtractorHttp,
    private val tokenStore: SpotifyTokenStore,
    private val apiBaseUrl: String = "https://api.spotify.com/v1",
    private val pageSize: Int = 50,
    private val maxPages: Int = 100,
) {

    private val api: SpotifyWebApiClient = SpotifyWebApiClient(
        http = http,
        tokens = SpotifyStoredTokenSource(tokenStore),
        backend = SpotifyBackend.OFFICIAL_WEB_API,
        apiBaseUrl = apiBaseUrl,
        pageSize = pageSize,
        maxPages = maxPages,
    )

    suspend fun resolve(query: SpotifyLibraryQuery): SongListResult {
        requireToken()
        return when (query) {
            SpotifyLibraryQuery.Saved -> saved()
            SpotifyLibraryQuery.UserPlaylists -> userPlaylists()
            SpotifyLibraryQuery.SavedAlbums -> savedAlbums()
            SpotifyLibraryQuery.FollowedArtists -> followedArtists()
        }
    }

    private fun requireToken() {
        tokenStore.load()?.takeIf { it.isNotBlank() }
            ?: throw SpotifyMetadataError.NotAuthorized("Log in to Spotify to use this query.")
    }

    // ---------------------------------------------------------------- queries

    private suspend fun saved(): SongListResult {
        val entries = mutableListOf<SongListEntry>()
        var offset = 0
        var pages = 0
        var total: Int? = null
        while (true) {
            val page = get("$apiBaseUrl/me/tracks?limit=$pageSize&offset=$offset")
            total = page.int("total") ?: total
            page.array("items").objects().forEachIndexed { index, item ->
                val track = item.obj("track") ?: item.obj("item")
                entries += songRecordEntry(
                    track = track,
                    list = ListContext(
                        name = "Saved",
                        position = offset + index + 1,
                        length = total,
                    ),
                    albumOverride = null,
                )
            }
            if (page.str("next") == null) break
            pages++
            if (pages >= maxPages) throw SpotifyMetadataError.Unavailable("This library is too large to expand.")
            offset += pageSize
        }
        return SongListResult(name = "Saved", entries = entries)
    }

    private suspend fun userPlaylists(): SongListResult {
        val items = paginate("$apiBaseUrl/me/playlists") { it.array("items").objects() }
        return expandLists(items, name = "Your playlists") { item ->
            item.str("id")?.let { id -> api.resolve(SpotifyQuery.Playlist(id, "spotify:playlist:$id")) }
        }
    }

    private suspend fun savedAlbums(): SongListResult {
        val items = paginate("$apiBaseUrl/me/albums") { it.array("items").objects() }
        return expandLists(items, name = "Saved albums") { item ->
            item.obj("album")?.str("id")?.let { id -> api.resolve(SpotifyQuery.Album(id, "spotify:album:$id")) }
        }
    }

    private suspend fun followedArtists(): SongListResult {
        val items = mutableListOf<JsonObject>()
        var url: String? = "$apiBaseUrl/me/following?type=artist&limit=$pageSize"
        var pages = 0
        while (url != null) {
            val page = get(url)
            val artists = page.obj("artists") ?: break
            items += artists.array("items").objects()
            url = artists.str("next")
            pages++
            if (pages >= maxPages) throw SpotifyMetadataError.Unavailable("This library is too large to expand.")
        }
        return expandLists(items, name = "Followed artists") { item ->
            item.str("id")?.let { id -> api.resolve(SpotifyQuery.Artist(id, "spotify:artist:$id")) }
        }
    }

    // ------------------------------------------------------------- pagination

    private suspend fun paginate(
        path: String,
        itemsOf: (JsonObject) -> List<JsonObject>,
    ): List<JsonObject> {
        val collected = mutableListOf<JsonObject>()
        var offset = 0
        var pages = 0
        while (true) {
            val separator = if (path.contains('?')) '&' else '?'
            val page = get("$path${separator}limit=$pageSize&offset=$offset")
            collected += itemsOf(page)
            if (page.str("next") == null) break
            pages++
            if (pages >= maxPages) throw SpotifyMetadataError.Unavailable("This library is too large to expand.")
            offset += pageSize
        }
        return collected
    }

    private suspend fun expandLists(
        items: List<JsonObject>,
        name: String,
        expand: suspend (JsonObject) -> SongListResult?,
    ): SongListResult {
        val entries = mutableListOf<SongListEntry>()
        for (item in items) {
            val result = try {
                expand(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpotifyMetadataError) {
                entries += SongListEntry.Unavailable(
                    reason = UnavailableReason.FETCH_FAILED,
                    id = item.str("id"),
                    title = item.str("name"),
                    message = error.message,
                )
                continue
            } ?: continue
            entries += result.entries
        }
        return SongListResult(name = name, entries = entries)
    }

    // ------------------------------------------------------------------- HTTP

    private suspend fun get(url: String): JsonObject {
        val token = tokenStore.load()?.takeIf { it.isNotBlank() }
            ?: throw SpotifyMetadataError.NotAuthorized("Log in to Spotify to use this query.")
        val element = try {
            http.downloadJson(
                url = url,
                headers = mapOf("accept" to "application/json"),
                authorization = "Bearer $token",
            )
        } catch (error: ExtractionError) {
            throw error.toSpotifyError()
        }
        return element as? JsonObject ?: throw SpotifyMetadataError.Malformed()
    }
}
