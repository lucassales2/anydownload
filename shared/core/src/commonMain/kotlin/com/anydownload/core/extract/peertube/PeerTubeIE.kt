/*
 * PeerTube extractor — AnyDownload
 *
 * Kotlin translation of the public video subset of `PeerTubeIE` from
 * `yt_dlp/extractor/peertube.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `peertube.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public REST API path (`https://<host>/api/v1/videos/<id>`),
 * `/videos/watch/<id>`, `/videos/embed/<id>`, `/w/<id>`, and the
 * `peertube:<host>:<id>` keyword for the two UUID shapes. The API maps
 * progressive `files`, `streamingPlaylists` HLS manifests and their files,
 * captions, the long-description call, account/channel names, views,
 * duration, publish date, NSFW flag, and thumbnails.
 *
 * The upstream `_INSTANCES_RE` allowlist (a generated list of known public
 * instances) is deliberately not copied: the URL shape is the match, and the
 * API response is trusted as the PeerTube contract. The playlist class
 * (`PeerTubePlaylistIE`: account, channel, and video-playlist listings) is
 * planned. No cookie, bearer token, or signed media URL is stored or
 * committed; fixture hosts are `*.example`.
 */
package com.anydownload.core.extract.peertube

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Upstream `PeerTubeIE` subset: one public federated video.
 *
 * `https://<host>/a/<account>`, `https://<host>/c/<channel>`, and
 * `https://<host>/w/p/<playlist>` are the playlist class and match no
 * registered extractor, so they fall through to the desktop CLI or Android
 * Chaquopy.
 */
class PeerTubeIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "PeerTube"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: match.groups["schemeHost"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val video = api(host, videoId, "") as? JsonObject
            ?: throw ExtractionError.Malformed("The PeerTube video API returned no object.")
        val title = video.str("name")

        val formats = mutableListOf<MediaFormat>()
        var isLive = false
        val files = mutableListOf<JsonObject>()
        video.array("files")?.filterIsInstance<JsonObject>()?.let { files.addAll(it) }
        for (element in video.array("streamingPlaylists").orEmpty()) {
            val playlist = element as? JsonObject ?: continue
            val playlistUrl = ExtractorUtils.urlOrNone(playlist.str("playlistUrl"))
            if (playlistUrl != null) {
                isLive = true
                formats += MediaFormat(
                    formatId = "hls",
                    url = playlistUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    formatNote = "HLS",
                )
            }
            playlist.array("files")?.filterIsInstance<JsonObject>()?.let { files.addAll(it) }
        }
        for (file in files) {
            val fileUrl = ExtractorUtils.urlOrNone(file.str("fileUrl")) ?: continue
            val label = file.obj("resolution")?.str("label")
            val audioOnly = label == "0p"
            formats += MediaFormat(
                formatId = label,
                url = fileUrl,
                ext = "mp4",
                height = resolutionHeight(label),
                filesize = file.number("size")?.toLong(),
                vcodec = if (audioOnly) MediaFormat.CODEC_NONE else null,
                fps = if (audioOnly) null else file.number("fps"),
            )
            isLive = false
        }

        var description = video.str("description")
        if (description != null && description.length >= TRUNCATED_DESCRIPTION_LENGTH) {
            val full = try {
                api(host, videoId, "description") as? JsonObject
            } catch (_: ExtractionError) {
                null
            }
            description = full?.str("description") ?: description
        }

        val account = video.obj("account")
        val channel = video.obj("channel")
        val ageLimit = (video["nsfw"] as? JsonPrimitive)?.booleanOrNull
            ?.let { if (it) 18 else 0 }
        val webpageUrl = "https://$host/videos/watch/$videoId"
        val thumbnailPath = video.str("thumbnailPath")

        return InfoDict(
            id = videoId,
            title = title,
            formats = formats,
            description = description,
            thumbnails = listOfNotNull(
                thumbnailPath?.let { Thumbnail(url = absoluteUrl(host, it)) },
            ),
            uploadDate = video.str("publishedAt")?.let(ExtractorUtils::unifiedStrdate),
            uploader = account?.str("displayName"),
            channel = channel?.str("displayName"),
            channelId = channel?.number("id")?.toLong()?.toString(),
            duration = video.number("duration"),
            viewCount = video.number("views")?.toLong(),
            ageLimit = ageLimit,
            isLive = isLive,
            subtitles = fetchSubtitles(host, videoId),
            webpageUrl = webpageUrl,
            extractor = "peertube",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_call_api`: `https://<host>/api/v1/videos/<id>/<path>`. */
    private suspend fun api(host: String, videoId: String, path: String): JsonElement =
        http.downloadJson("https://$host/api/v1/videos/$videoId/$path")

    /** Upstream `_get_subtitles`: the captions API, fatal=false upstream. */
    private suspend fun fetchSubtitles(host: String, videoId: String): List<SubtitleTrack> {
        val json = try {
            api(host, videoId, "captions") as? JsonObject
        } catch (_: ExtractionError) {
            return emptyList()
        }
        val data = json?.array("data") ?: return emptyList()
        val tracks = mutableListOf<SubtitleTrack>()
        for (element in data) {
            val caption = element as? JsonObject ?: continue
            val language = caption.obj("language")?.str("id") ?: "en"
            val path = caption.str("captionPath") ?: continue
            tracks += SubtitleTrack(
                language = language,
                formats = listOf(SubtitleFormat(ext = "vtt", url = absoluteUrl(host, path))),
            )
        }
        return tracks
    }

    companion object {
        const val IE_KEY: String = "PeerTube"

        /** Upstream `_TRUNCATED_DESCRIPTION_LENGTH`. */
        private const val TRUNCATED_DESCRIPTION_LENGTH = 250

        private const val UUID = "[\\da-zA-Z]{22}|[\\da-fA-F]{8}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{12}"

        /**
         * Upstream `_VALID_URL` without the generated instance allowlist:
         * `peertube:<host>:<id>`, `/videos/watch/<id>`, `/videos/embed/<id>`,
         * `/api/v<n>/videos/<id>`, and `/w/<id>`.
         */
        val VALID_URL: Regex = Regex(
            "(?:peertube:(?<schemeHost>[^:]+):|" +
                "https?://(?<host>[^/?#]+)/(?:videos/(?:watch|embed)|api/v\\d+/videos|w)/)" +
                "(?<id>$UUID)",
        )
    }
}

private fun resolutionHeight(label: String?): Long? =
    label?.let { Regex("(\\d+)p").find(it)?.groupValues?.get(1)?.toLongOrNull() }

/** Upstream `urljoin` for a host-rooted path. */
private fun absoluteUrl(host: String, value: String): String =
    if (value.startsWith("http")) value else "https://$host/" + value.trimStart('/')

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
