/*
 * JioSaavn extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `jiosaavn.py` from
 * `yt_dlp/extractor/jiosaavn.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `jiosaavn.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `webapi.get` metadata API, the `song.generateAuthToken`
 * format endpoint, and the album/playlist/show/artist listings (five pages
 * eagerly). Artist/label credits and the language mapping the port does not
 * carry are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.jiosaavn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_URL = "https://www.jiosaavn.com/api.php"
private const val MAX_PAGES = 5

/** Upstream `JioSaavnSongIE`: a song. */
class JioSaavnSongIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val songId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi(http, "song", songId)?.array("songs")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The song API returned no song.")
        return extractSong(data).copy(
            id = data.str("id") ?: songId,
            formats = extractFormats(http, data),
            webpageUrl = url,
            extractor = "jiosaavn",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "JioSaavnSong"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:jio)?saavn\\.com(?:/song/[^/?#]+/|/s/song/(?:[^/?#]+/){3})(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `JioSaavnShowIE`: an episode. */
class JioSaavnShowIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val episodeId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi(http, "episode", episodeId)?.array("episodes")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The episode API returned no episode.")
        return extractEpisode(data).copy(
            id = data.str("id") ?: episodeId,
            formats = extractFormats(http, data),
            webpageUrl = url,
            extractor = "jiosaavn:show",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "JioSaavnShow"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:jio)?saavn\\.com/shows/[^/?#]+/(?<id>[^/?#]{11,})/?(?:$|[?#])",
        )
    }
}

/** Upstream `JioSaavnAlbumIE`: an album. */
class JioSaavnAlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val albumId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi(http, "album", albumId)
            ?: throw ExtractionError.Malformed("The album API returned no album.")
        return InfoDict(
            id = albumId,
            title = data.str("title"),
            entries = songEntries(data.array("songs").orEmpty()),
            webpageUrl = url,
            extractor = "jiosaavn:album",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "JioSaavnAlbum"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?(?:jio)?saavn\\.com/album/[^/?#]+/(?<id>[^/?#]+)")
    }
}

/** Upstream `JioSaavnPlaylistIE`: a playlist. */
class JioSaavnPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val data = callApi(http, "playlist", playlistId, "&p=$page&n=50") ?: break
            val songs = data.array("songs").orEmpty()
            if (songs.isEmpty()) break
            entries += songEntries(songs)
            val total = data.number("list_count")?.toInt() ?: songs.size
            if (page * 50 >= total) break
            page++
        }
        val first = callApi(http, "playlist", playlistId, "&p=1&n=50")
        return InfoDict(
            id = playlistId,
            title = first?.str("listname"),
            entries = entries,
            webpageUrl = url,
            extractor = "jiosaavn:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "JioSaavnPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:jio)?saavn\\.com" +
                "(?:/s/playlist/(?:[^/?#]+/){2}|/featured/[^/?#]+/)(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `JioSaavnShowPlaylistIE`: a show season. */
class JioSaavnShowPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val showSlug = match.groups["show"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["season"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = "$showSlug-$seasonId"
        val webpage = http.downloadWebpage(url)
        val initial = balancedAfter(webpage, Regex("window\\.__INITIAL_DATA__\\s*="))
            ?: throw ExtractionError.Malformed("The show page had no initial data.")
        val showView = ExtractorUtils.parseJson(initial)?.obj("showView")
        val showId = showView?.primitiveText("current_id")
            ?: throw ExtractionError.Malformed("The show page had no current id.")
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val data = callApi(
                http,
                "show",
                showId,
                "&__call=show.getAllEpisodes&show_id=$showId&season_number=$seasonId&api_version=4&sort_order=desc&p=$page",
            ) ?: break
            val items = data.array("episodes").orEmpty()
            if (items.isEmpty()) break
            for (element in items) {
                val episode = element as? JsonObject ?: continue
                val permaUrl = episode.str("perma_url")
                    ?: episode.str("id")?.let { "https://www.jiosaavn.com/shows/$showSlug/$seasonId/$it" }
                    ?: continue
                entries += InfoEntry(id = episode.str("id"), title = episode.str("title"), url = permaUrl)
            }
            page++
        }
        return InfoDict(
            id = playlistId,
            title = showView?.obj("show")?.obj("title")?.str("text"),
            entries = entries,
            webpageUrl = url,
            extractor = "jiosaavn:show:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "JioSaavnShowPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:jio)?saavn\\.com/shows/(?<show>[^#/?]+)/(?<season>\\d+)/[^/?#]+",
        )
    }
}

/** Upstream `JioSaavnArtistIE`: an artist listing. */
class JioSaavnArtistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val artistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        var firstPage: JsonObject? = null
        var page = 0
        while (page <= MAX_PAGES) {
            val data = callApi(
                http,
                "artist",
                artistId,
                "&p=$page&n_song=50&n_album=50&sub_type=&includeMetaTags=&api_version=4&category=alphabetical&sort_order=asc",
            ) ?: break
            if (page == 0) firstPage = data
            val songs = data.array("topSongs").orEmpty()
            if (songs.isEmpty()) break
            entries += songEntries(songs)
            page++
        }
        return InfoDict(
            id = artistId,
            title = firstPage?.str("name"),
            entries = entries,
            webpageUrl = url,
            extractor = "jiosaavn:artist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "JioSaavnArtist"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?(?:jio)?saavn\\.com/artist/[^/?#]+/(?<id>[^/?#]+)")
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun callApi(
    http: ExtractorHttp,
    type: String,
    token: String,
    extra: String = "",
): JsonObject? = try {
    http.downloadJson(
        "$API_URL?__call=webapi.get&_format=json&_marker=0&ctx=web6dot0&token=$token&type=$type$extra",
    ) as? JsonObject
} catch (error: ExtractionError) {
    null
}

private suspend fun extractFormats(http: ExtractorHttp, itemData: JsonObject): List<MediaFormat> {
    val encrypted = itemData.str("encrypted_media_url")
        ?: itemData.obj("more_info")?.str("encrypted_media_url")
        ?: throw ExtractionError.NoFormats("The item had no encrypted media URL.")
    val formats = mutableListOf<MediaFormat>()
    for (bitrate in listOf("128", "320")) {
        val mediaData = try {
            http.downloadJson(
                API_URL,
                method = "POST",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = "__call=song.generateAuthToken&_format=json&bitrate=$bitrate&url=$encrypted"
                    .encodeToByteArray(),
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        } ?: continue
        val authUrl = mediaData.str("auth_url") ?: continue
        val type = mediaData.str("type")
        formats += MediaFormat(
            formatId = bitrate,
            url = authUrl,
            ext = if (type == "mp4") "m4a" else type,
            abr = bitrate.toDouble(),
            vcodec = MediaFormat.CODEC_NONE,
        )
    }
    if (formats.isEmpty()) {
        throw ExtractionError.NoFormats("The auth token API returned no playable URL.")
    }
    return formats
}

private fun extractSong(data: JsonObject): InfoDict {
    val moreInfo = data.obj("more_info")
    val thumbnail = data.str("image")?.replace(Regex("-\\d+x\\d+\\."), "-500x500.")
    return InfoDict(
        title = cleanHtml(data.str("song") ?: data.str("title")),
        description = null,
        duration = moreInfo?.number("duration"),
        channel = moreInfo?.str("label"),
        uploadDate = ExtractorUtils.unifiedStrdate(moreInfo?.str("release_date")),
        viewCount = data.number("play_count")?.toLong(),
        thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
        extractor = "jiosaavn",
        extractorKey = "JioSaavnSong",
    )
}

private fun extractEpisode(data: JsonObject): InfoDict {
    val moreInfo = data.obj("more_info")
    return extractSong(data).copy(
        description = moreInfo?.str("description"),
        channel = moreInfo?.str("show_title"),
    )
}

private fun songEntries(songs: List<JsonElement>): List<InfoEntry> = songs.mapNotNull { element ->
    val song = element as? JsonObject ?: return@mapNotNull null
    val id = song.str("id") ?: return@mapNotNull null
    val permaUrl = song.str("perma_url") ?: "https://www.jiosaavn.com/song/$id"
    InfoEntry(id = id, title = cleanHtml(song.str("song") ?: song.str("title")), url = permaUrl)
}

private fun balancedAfter(html: String, marker: Regex): String? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        i++
    }
    return null
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonElement.obj(name: String): JsonObject? = (this as? JsonObject)?.get(name) as? JsonObject

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
