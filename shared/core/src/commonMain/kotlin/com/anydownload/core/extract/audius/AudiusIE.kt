/*
 * Audius extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `audius.py` from
 * `yt_dlp/extractor/audius.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `audius.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `api.audius.co` host list, the `/resolve`, `/tracks`, and
 * `/playlists` JSON, the artwork map, and the playlist/profile entry lists.
 * The `track`/`genre` labels and the like/repost counts the port does not
 * model are dropped (artist fills `uploader`); a resolve 404 surfaces as
 * typed Unavailable because the port cannot read a non-2xx body. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.audius

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

private val ARTWORK_MAP = mapOf("150x150" to 150, "480x480" to 480, "1000x1000" to 1000)

/** Upstream `AudiusBaseIE`: the host selection and API helpers. */
abstract class AudiusBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected var apiBase: String? = null

    protected suspend fun selectApiBase() {
        if (apiBase != null) return
        val response = http.downloadJson("https://api.audius.co/") as? JsonObject
            ?: throw ExtractionError.Malformed("The Audius host list was not an object.")
        val hosts = response.array("data")?.mapNotNull { (it as? JsonPrimitive)?.content }
        if (hosts.isNullOrEmpty()) throw ExtractionError.Malformed("Unable to get available API hosts.")
        apiBase = hosts[Random.nextInt(hosts.size)]
    }

    protected suspend fun apiRequest(
        path: String,
        itemId: String?,
        note: String = "Downloading JSON metadata",
    ): JsonElement {
        selectApiBase()
        val response = try {
            http.downloadJson("$apiBase/v1$path")
        } catch (error: ExtractionError.Malformed) {
            throw ExtractionError.Unavailable("An error occurred while receiving data. Try again.")
        } catch (error: ExtractionError) {
            throw error
        }
        return responseData(response)
    }

    protected suspend fun resolveUrl(url: String, itemId: String): JsonElement =
        apiRequest("/resolve?url=$url", itemId)

    private fun responseData(response: JsonElement): JsonElement {
        val obj = response as? JsonObject
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        obj["data"]?.let { return it }
        if (obj.keys.size == 1 && obj.str("message") != null) {
            throw ExtractionError.Unavailable("API error: ${obj.str("message")}")
        }
        throw ExtractionError.Malformed("Unexpected API response.")
    }

    /** Upstream `_prepare_url`: restore the backslashes Chrome replaced. */
    protected fun prepareUrl(url: String, title: String): String {
        val decodedUrl = percentDecode(url)
        val decodedTitle = percentDecode(title)
        if ('/' in decodedTitle || "%2F" in title) {
            val fixedTitle = decodedTitle.replace("/", "%5C").replace("%2F", "%5C")
            return decodedUrl.replace(decodedTitle, fixedTitle)
        }
        return decodedUrl
    }
}

/** Upstream `AudiusIE`: a public track page. */
open class AudiusIE(
    http: ExtractorHttp,
    ieKeyName: String = IE_KEY,
    validUrl: Regex = VALID_URL,
) : AudiusBaseIE(ieKey = ieKeyName, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val title = match.groups["title"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val preparedUrl = prepareUrl(url, title)
        val trackData = resolveUrl(preparedUrl, title)
        return trackInfo(trackData, title)
    }

    /** The shared single-track info dict. */
    protected fun trackInfo(trackData: JsonElement, fallbackTitle: String?): InfoDict {
        val track = trackData as? JsonObject
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        val trackId = track.str("id")
            ?: throw ExtractionError.Malformed("Unable to get the track id.")
        val thumbnails = mutableListOf<Thumbnail>()
        for ((quality, value) in track.obj("artwork").orEmpty()) {
            val thumbnailUrl = (value as? JsonPrimitive)?.content
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            thumbnails += Thumbnail(url = thumbnailUrl, preference = ARTWORK_MAP[quality])
        }
        return InfoDict(
            id = trackId,
            title = track.str("title") ?: fallbackTitle,
            description = track.str("description"),
            duration = track.number("duration"),
            uploader = track.obj("user")?.str("name"),
            viewCount = track.number("play_count")?.toLong(),
            thumbnails = thumbnails,
            formats = listOf(
                MediaFormat(url = "$apiBase/v1/tracks/$trackId/stream", ext = "mp3"),
            ),
            webpageUrl = null,
            extractor = "audius",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Audius"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:audius\\.co/(?<uploader>[\\w\\d-]+)" +
                "(?!/album|/playlist)/(?<title>\\S+))",
        )
    }
}

/** Upstream `AudiusTrackIE`: an `audius:` id or API link. */
class AudiusTrackIE(
    http: ExtractorHttp,
) : AudiusIE(http, IE_KEY, VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val trackId = VALID_URL.find(url)?.groups?.get("trackid")?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val trackData = apiRequest("/tracks/$trackId", trackId)
        return trackInfo(trackData, null)
    }

    companion object {
        const val IE_KEY: String = "AudiusTrack"

        val VALID_URL: Regex = Regex(
            "(?:audius:)(?:https?://(?:www\\.)?.+/v1/tracks/)?(?<trackid>\\w+)",
        )
    }
}

/** Upstream `AudiusPlaylistIE`: a public playlist/album page. */
class AudiusPlaylistIE(
    http: ExtractorHttp,
) : AudiusBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        selectApiBase()
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val title = match.groups["title"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val preparedUrl = prepareUrl(url, title)
        val playlistResponse = resolveUrl(preparedUrl, title) as? JsonArray
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        if (playlistResponse.size != 1) throw ExtractionError.Malformed("Unexpected API response.")
        val playlistData = playlistResponse[0] as? JsonObject
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        val playlistId = playlistData.str("id")
            ?: throw ExtractionError.Malformed("Unable to get the playlist id.")
        val tracks = apiRequest("/playlists/$playlistId/tracks", title) as? JsonArray
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        val entries = tracks.mapNotNull { element ->
            val trackId = (element as? JsonObject)?.str("id") ?: return@mapNotNull null
            InfoEntry(id = trackId, url = "audius:$trackId")
        }
        return InfoDict(
            id = playlistId,
            title = playlistData.str("playlist_name") ?: title,
            description = playlistData.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "audius:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "AudiusPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?audius\\.co/(?<uploader>[\\w\\d-]+)/(?:album|playlist)/(?<title>\\S+)",
        )
    }
}

/** Upstream `AudiusProfileIE`: a public profile/artist page. */
class AudiusProfileIE(
    http: ExtractorHttp,
) : AudiusBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        selectApiBase()
        val profileId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val profileResponse = apiRequest("/full/users/handle/$profileId", profileId) as? JsonArray
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        val profileData = profileResponse.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("Could not download the profile info.")
        val audiusId = profileData.str("id")
            ?: throw ExtractionError.Malformed("Unable to get the profile id.")
        val tracks = apiRequest("/full/users/handle/$profileId/tracks", profileId) as? JsonArray
            ?: throw ExtractionError.Malformed("Unexpected API response.")
        val entries = tracks.mapNotNull { element ->
            val trackId = (element as? JsonObject)?.str("id") ?: return@mapNotNull null
            InfoEntry(id = trackId, url = "audius:$trackId")
        }
        return InfoDict(
            id = audiusId,
            title = profileId,
            description = profileData.str("bio"),
            entries = entries,
            webpageUrl = url,
            extractor = "audius:artist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "AudiusProfile"

        val VALID_URL: Regex = Regex(
            "https?://(?:www)?audius\\.co/(?<id>[^\\/]+)/?(?:[?#]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun percentDecode(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                bytes += code.toByte()
                index += 3
                continue
            }
        }
        bytes += character.toString().encodeToByteArray().toList()
        index++
    }
    return bytes.toByteArray().decodeToString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
