/*
 * BandLab extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `bandlab.py` from
 * `yt_dlp/extractor/bandlab.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `bandlab.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `api/v1.3` posts/revisions/albums/collections endpoints
 * with the revision/track/video parsers and playlist media items. The
 * track/album/album-type/media-type/release-date and counter fields the port
 * does not carry are dropped. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.bandlab

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `BandlabIE`: a track, post, or revision. */
class BandlabIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val urlType = match.groups["urlType"]?.value
        val query = parseQuery(url)
        var revisionId = query["revId"] ?: query["id"]
        if (urlType == "revision") revisionId = displayId
        var revisionData: JsonObject? = null
        if (revisionId == null) {
            val postQuery = query["sharedKey"]?.let { "?sharedKey=$it" } ?: ""
            val post = callApi(http, "posts", displayId, postQuery)
                ?: throw ExtractionError.Malformed("The BandLab post API returned no data.")
            revisionId = post.obj("revision")?.primitiveText("id") ?: post.primitiveText("revisionId")
            revisionData = post.obj("revision")
            if (revisionData == null && revisionId == null) {
                return when (post.str("type")) {
                    "Video" -> parseVideo(post)
                    "Track" -> parseTrack(post)
                    else -> throw ExtractionError.Unavailable(
                        "Could not extract data for post type '${post.str("type")}'.",
                    )
                }
            }
        }
        val revision = revisionData
            ?: callApi(http, "revisions", revisionId!!, "?edit=false")
            ?: throw ExtractionError.Malformed("The BandLab revision API returned no data.")
        return parseRevision(revision)
    }

    companion object {
        const val IE_KEY: String = "Bandlab"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bandlab\\.com/(?<urlType>track|post|revision|embed)/" +
                "(?:\\?(?:[^#]*&)?id=)?(?<id>[\\da-f_-]+)",
        )
    }
}

/** Upstream `BandlabPlaylistIE`: albums and collections. */
class BandlabPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistType = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val endpoints = when (playlistType) {
            "albums" -> listOf("albums")
            "collections" -> listOf("collections")
            else -> listOf("collections", "albums")
        }
        var playlistData: JsonObject? = null
        for (endpoint in endpoints) {
            val data = callApi(http, endpoint, playlistId, "")
            if (data != null && data.primitiveText("errorCode") == null) {
                playlistData = data
                break
            }
        }
        val data = playlistData
            ?: throw ExtractionError.Unavailable("Could not find BandLab playlist data.")
        val media = mutableListOf<InfoMedia>()
        for (element in data.array("posts").orEmpty()) {
            val post = element as? JsonObject ?: continue
            when (post.str("type")) {
                "Revision" -> post.obj("revision")?.let { revision ->
                    val parsed = parseRevision(revision)
                    media += InfoMedia(
                        mediaId = parsed.id ?: playlistId,
                        title = parsed.title,
                        duration = parsed.duration,
                        thumbnails = parsed.thumbnails,
                        formats = parsed.formats,
                    )
                }

                "Track" -> {
                    val parsed = parseTrack(post)
                    media += InfoMedia(
                        mediaId = parsed.id ?: playlistId,
                        title = parsed.title,
                        duration = parsed.duration,
                        thumbnails = parsed.thumbnails,
                        formats = parsed.formats,
                    )
                }

                "Video" -> {
                    val parsed = parseVideo(post)
                    media += InfoMedia(
                        mediaId = parsed.id ?: playlistId,
                        title = parsed.title,
                        duration = parsed.duration,
                        thumbnails = parsed.thumbnails,
                        formats = parsed.formats,
                    )
                }
            }
        }
        return InfoDict(
            id = playlistId,
            title = data.str("name"),
            description = data.str("description"),
            uploader = data.obj("creator")?.str("name"),
            uploadDate = dateFromIso(data.str("createdOn")),
            thumbnails = listOfNotNull(
                (data.obj("picture")?.obj("original")?.str("url") ?: data.obj("picture")?.str("url"))
                    ?.let { Thumbnail(url = it) },
            ),
            viewCount = data.obj("counters")?.number("plays")?.toLong(),
            media = media,
            webpageUrl = url,
            extractor = "bandlab:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BandlabPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bandlab\\.com/(?:[\\w]+/)?(?<type>albums|collections|embed)/" +
                "(?:collection/)?(?:\\?(?:[^#]*&)?id=)?(?<id>[\\da-f-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun callApi(
    http: ExtractorHttp,
    endpoint: String,
    assetId: String,
    query: String,
): JsonObject? = try {
    http.downloadJson(
        "https://www.bandlab.com/api/v1.3/$endpoint/$assetId$query",
        headers = mapOf(
            "accept" to "application/json",
            "referer" to "https://www.bandlab.com/",
            "x-client-id" to "BandLab-Web",
            "x-client-version" to "10.1.124",
        ),
    ) as? JsonObject
} catch (error: ExtractionError) {
    null
} catch (error: IllegalStateException) {
    null
}

private fun parseRevision(data: JsonObject): InfoDict {
    val mixdown = data.obj("mixdown")
    val song = data.obj("song")
    val url = mixdown?.str("file")
    return InfoDict(
        id = data.primitiveText("revisionId") ?: data.primitiveText("id"),
        title = song?.str("name"),
        description = data.str("description"),
        duration = mixdown?.number("duration"),
        uploader = data.obj("creator")?.str("name"),
        uploadDate = dateFromIso(data.str("createdOn")),
        viewCount = data.obj("counters")?.number("plays")?.toLong(),
        thumbnails = listOfNotNull(song?.obj("picture")?.str("url")?.let { Thumbnail(url = it) }),
        formats = listOfNotNull(url?.let { audioFormat(it) }),
        extractor = "bandlab",
        extractorKey = "Bandlab",
    )
}

private fun parseTrack(data: JsonObject): InfoDict {
    val track = data.obj("track")
    val url = track?.obj("sample")?.str("audioUrl")
    return InfoDict(
        id = data.primitiveText("revisionId") ?: data.primitiveText("id"),
        title = track?.str("name"),
        description = data.str("caption"),
        duration = track?.obj("sample")?.number("duration"),
        uploader = data.obj("creator")?.str("name"),
        uploadDate = dateFromIso(data.str("createdOn")),
        viewCount = data.obj("counters")?.number("plays")?.toLong(),
        thumbnails = listOfNotNull(
            track?.obj("picture")?.obj("original")?.str("url")?.let { Thumbnail(url = it) },
        ),
        formats = listOfNotNull(url?.let { audioFormat(it) }),
        extractor = "bandlab",
        extractorKey = "Bandlab",
    )
}

private fun parseVideo(data: JsonObject): InfoDict {
    val video = data.obj("video")
    val url = video?.str("url")
    val caption = data.str("caption")
    return InfoDict(
        id = data.primitiveText("id"),
        title = caption?.replace("\n", " ")?.take(72),
        description = caption,
        duration = video?.number("duration"),
        uploader = data.obj("creator")?.str("name"),
        uploadDate = dateFromIso(data.str("createdOn")),
        viewCount = video?.obj("counters")?.number("plays")?.toLong(),
        thumbnails = listOfNotNull(video?.obj("picture")?.str("url")?.let { Thumbnail(url = it) }),
        formats = listOfNotNull(
            url?.let {
                MediaFormat(url = it, ext = ExtractorUtils.determineExt(it))
            },
        ),
        extractor = "bandlab",
        extractorKey = "Bandlab",
    )
}

private fun audioFormat(url: String): MediaFormat = MediaFormat(
    url = url,
    ext = ExtractorUtils.determineExt(url),
    vcodec = MediaFormat.CODEC_NONE,
)

private fun parseQuery(url: String): Map<String, String> {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=')
        if (key.isEmpty()) null else key to pair.substringAfter('=', "")
    }.toMap()
}

private fun dateFromIso(value: String?): String? {
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(value ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
