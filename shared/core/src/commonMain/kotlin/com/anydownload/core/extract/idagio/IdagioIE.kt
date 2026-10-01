/*
 * IDAGIO extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `idagio.py` from
 * `yt_dlp/extractor/idagio.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `idagio.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `api.idagio.com` track metadata/content JSON, the
 * recording/album/playlist/personal-playlist metadata JSON, and the track
 * entries. A track becomes one mp3 row with vcodec none; the location-blocked
 * metadata is the typed geo wall. The port does not carry artists, composers,
 * genres, tags, creators, or display_id, and a 406 metadata response surfaces
 * as typed Unavailable rather than GeoRestricted. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.idagio

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

private const val METADATA_BASE = "https://api.idagio.com/v2.0/metadata"

/** Upstream `IdagioTrackIE`: one recording track. */
class IdagioTrackIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val trackId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream downloads this with fatal=False and expected_status=406.
        val trackInfo = try {
            http.downloadJson("$METADATA_BASE/tracks/$trackId") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        if (trackInfo?.str("error_code") == "idagio.error.blocked.location") {
            throw ExtractionError.GeoRestricted()
        }
        val contentInfo = http.downloadJson(
            "https://api.idagio.com/v1.8/content/track/$trackId" +
                "?quality=0&format=2&client_type=web-4",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The IDAGIO content API was not an object.")
        val contentUrl = contentInfo.str("url")
            ?: throw ExtractionError.NoFormats("The IDAGIO content response had no URL.")
        val result = trackInfo?.obj("result")
        return InfoDict(
            id = trackId,
            title = result?.obj("piece")?.str("title"),
            duration = result?.number("duration"),
            uploadDate = result?.obj("recording")?.number("created_at")?.toLong()?.div(1000)
                ?.let(ExtractorUtils::epochSecondsToDate),
            formats = listOf(
                MediaFormat(url = contentUrl, ext = "mp3", vcodec = MediaFormat.CODEC_NONE),
            ),
            webpageUrl = url,
            extractor = "idagio",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "IdagioTrack"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?app\\.idagio\\.com(?:/[a-z]{2})?/recordings/\\d+" +
                "\\?(?:[^#]+&)?trackId=(?<id>\\d+)",
        )
    }
}

/** The modeled playlist metadata (artists/composers/tags are dropped). */
data class IdagioPlaylistMeta(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val uploadDate: String? = null,
)

/**
 * Upstream `IdagioPlaylistBaseIE`: the shared track-entry walk and playlist
 * shell for the four playlist classes.
 */
abstract class IdagioPlaylistBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected abstract fun apiUrl(playlistId: String): String

    protected abstract fun parsePlaylistMetadata(playlistInfo: JsonObject): IdagioPlaylistMeta

    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(apiUrl(playlistId)) as? JsonObject
            ?: throw ExtractionError.Malformed("The IDAGIO playlist API was not an object.")
        val result = response.obj("result")
            ?: throw ExtractionError.Malformed("The IDAGIO playlist response had no result.")
        val meta = parsePlaylistMetadata(result)
        val entries = mutableListOf<InfoEntry>()
        for (element in result.array("tracks").orEmpty()) {
            val track = element as? JsonObject ?: continue
            val trackId = track.primitive("id") ?: continue
            val recordingId = track.obj("recording")?.primitive("id") ?: continue
            entries += InfoEntry(
                id = trackId,
                url = "https://app.idagio.com/recordings/$recordingId?trackId=$trackId",
            )
        }
        return InfoDict(
            id = meta.id ?: playlistId,
            title = meta.title,
            description = meta.description,
            uploadDate = meta.uploadDate,
            thumbnails = listOfNotNull(meta.thumbnail?.let { Thumbnail(url = it) }),
            entries = entries,
            webpageUrl = url,
            extractorKey = ieKey,
        )
    }
}

/** Upstream `IdagioRecordingIE`: a full recording with its track list. */
class IdagioRecordingIE(
    http: ExtractorHttp,
) : IdagioPlaylistBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun apiUrl(playlistId: String): String = "$METADATA_BASE/recordings/$playlistId"

    override fun parsePlaylistMetadata(playlistInfo: JsonObject): IdagioPlaylistMeta = IdagioPlaylistMeta(
        title = playlistInfo.obj("work")?.str("title"),
        uploadDate = playlistInfo.number("created_at")?.toLong()?.div(1000)
            ?.let(ExtractorUtils::epochSecondsToDate),
    )

    companion object {
        const val IE_KEY: String = "IdagioRecording"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?app\\.idagio\\.com(?:/[a-z]{2})?/recordings/(?<id>\\d+)" +
                "(?![^#]*[&?]trackId=\\d+)",
        )
    }
}

/** Upstream `IdagioAlbumIE`: an album with its track list. */
class IdagioAlbumIE(
    http: ExtractorHttp,
) : IdagioPlaylistBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun apiUrl(playlistId: String): String = "$METADATA_BASE/albums/$playlistId"

    override fun parsePlaylistMetadata(playlistInfo: JsonObject): IdagioPlaylistMeta = IdagioPlaylistMeta(
        id = playlistInfo.str("id"),
        title = playlistInfo.str("title"),
        description = playlistInfo.str("description"),
        thumbnail = playlistInfo.str("imageUrl"),
        uploadDate = timestampToDate(playlistInfo["publishDate"]),
    )

    companion object {
        const val IE_KEY: String = "IdagioAlbum"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?app\\.idagio\\.com(?:/[a-z]{2})?/albums/(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `IdagioPlaylistIE`: an editorial playlist. */
class IdagioPlaylistIE(
    http: ExtractorHttp,
) : IdagioPlaylistBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun apiUrl(playlistId: String): String = "https://api.idagio.com/v2.0/playlists/$playlistId"

    override fun parsePlaylistMetadata(playlistInfo: JsonObject): IdagioPlaylistMeta = IdagioPlaylistMeta(
        id = playlistInfo.str("id"),
        title = playlistInfo.str("title"),
        description = playlistInfo.str("description"),
        thumbnail = playlistInfo.str("imageUrl"),
    )

    companion object {
        const val IE_KEY: String = "IdagioPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?app\\.idagio\\.com(?:/[a-z]{2})?/playlists/(?!personal/)(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `IdagioPersonalPlaylistIE`: a user's personal playlist. */
class IdagioPersonalPlaylistIE(
    http: ExtractorHttp,
) : IdagioPlaylistBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun apiUrl(playlistId: String): String =
        "https://api.idagio.com/v1.0/personal-playlists/$playlistId"

    override fun parsePlaylistMetadata(playlistInfo: JsonObject): IdagioPlaylistMeta = IdagioPlaylistMeta(
        title = playlistInfo.str("title"),
        thumbnail = playlistInfo.str("image_url"),
        uploadDate = playlistInfo.number("created_at")?.toLong()?.div(1000)
            ?.let(ExtractorUtils::epochSecondsToDate),
    )

    companion object {
        const val IE_KEY: String = "IdagioPersonalPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?app\\.idagio\\.com(?:/[a-z]{2})?/playlists/personal/(?<id>[\\da-f-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `unified_timestamp` for an epoch number or a date string. */
private fun timestampToDate(element: JsonElement?): String? {
    val primitive = element as? JsonPrimitive ?: return null
    val text = primitive.content
    return if (!primitive.isString && text.toLongOrNull() != null) {
        ExtractorUtils.epochSecondsToDate(text.toLong())
    } else {
        ExtractorUtils.unifiedStrdate(text)
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
