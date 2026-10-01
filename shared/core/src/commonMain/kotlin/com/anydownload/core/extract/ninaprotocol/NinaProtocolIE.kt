/*
 * Nina Protocol extractor — AnyDownload
 *
 * Kotlin translation of `ninaprotocol.py` from
 * `yt_dlp/extractor/ninaprotocol.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ninaprotocol.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `v1/releases` JSON, the `publicKey` override of the URL id, the
 * metadata/publisher/hub fields the info dict models, and each
 * `metadata.properties.files` entry as one selectable `media` item with its
 * direct audio row (`vcodec: none`). Limitations: the upstream playlist
 * result maps to the port's selectable media items (the TapTap/Vidyard
 * model); the `album`, `album_artist`, `tags`, `uploader_id`, `display_id`,
 * and `track`/`track_number` fields are not modeled and are dropped; an
 * `m3u8` file would still become one row. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.ninaprotocol

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `NinaProtocolIE`: one release page. */
class NinaProtocolIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val urlId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson("https://api.ninaprotocol.com/v1/releases/$urlId") as? JsonObject
            ?: throw ExtractionError.Malformed("The Nina Protocol API was not an object.")
        val release = response.obj("release")
            ?: throw ExtractionError.Malformed("The Nina Protocol API returned no release.")
        val videoId = release.str("publicKey") ?: urlId

        val properties = release.obj("metadata")?.obj("properties")
        val media = mutableListOf<InfoMedia>()
        var trackNumber = 0
        for (element in properties?.array("files").orEmpty()) {
            val track = element as? JsonObject ?: continue
            val uri = track.str("uri")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            trackNumber++
            media += InfoMedia(
                mediaId = "${videoId}_$trackNumber",
                title = track.str("track_title"),
                duration = track.number("duration"),
                formats = listOf(
                    MediaFormat(
                        url = uri,
                        ext = ExtractorUtils.mimetype2ext(track.str("type")),
                        vcodec = "none",
                    ),
                ),
            )
        }

        return InfoDict(
            id = videoId,
            title = release.obj("metadata")?.str("name"),
            description = release.obj("metadata")?.str("description"),
            uploader = release.obj("publisherAccount")?.str("handle"),
            channel = release.obj("hub")?.str("handle"),
            channelId = release.obj("hub")?.str("publicKey"),
            uploadDate = release.str("datetime")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = release.obj("metadata")?.str("image")
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { listOf(Thumbnail(url = it)) }
                .orEmpty(),
            media = media,
            webpageUrl = url,
            extractor = "ninaprotocol",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "NinaProtocol"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?ninaprotocol\\.com/releases/(?<id>[^/#?]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
