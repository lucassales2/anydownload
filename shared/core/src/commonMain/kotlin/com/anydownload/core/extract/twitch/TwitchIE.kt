/*
 * Twitch extractors — AnyDownload
 *
 * Kotlin translation of the VOD and live-stream subsets of `TwitchVodIE` and
 * `TwitchStreamIE` from `yt_dlp/extractor/twitch.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `twitch.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public GraphQL metadata and playback-access-token calls for one
 * VOD or one live channel, then the usher HLS URL. A signed token and
 * signature are minted at runtime and carried in the format URL only; they
 * are never stored or committed. The usher manifest is recorded as one
 * `m3u8_native` format and the engine resolves the variants at download time.
 * Storyboards, chapter moments, clips, collections, and the video/clip
 * listings are not translated. `CLIENT_ID` below is Twitch's public web
 * GraphQL client identifier, not a user credential; no OAuth token, cookie,
 * or guest token is stored. A login wall or DRM wall is Partial.
 */
package com.anydownload.core.extract.twitch

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Upstream `TwitchVodIE`: one VOD's metadata and usher HLS URL. */
class TwitchVodIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Twitch VOD"

    override suspend fun extract(url: String): InfoDict {
        val vodId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()

        val responses = gqlBatch(
            http,
            buildJsonArray {
                add(
                    persistedOperation(
                        "VideoMetadata",
                        buildJsonObject {
                            put("channelLogin", "")
                            put("videoID", vodId)
                        },
                        VIDEO_METADATA_HASH,
                    ),
                )
            },
        )
        val video = responses.firstOrNull()?.obj("data")?.obj("video")
            ?: throw ExtractionError.Unavailable("This Twitch VOD does not exist.")

        val token = playbackToken(
            http,
            query = "{ videoPlaybackAccessToken(id: \"$vodId\", params: " +
                "{platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}) " +
                "{ value signature } }",
            field = "videoPlaybackAccessToken",
        )
        val usher = usherUrl("vod", vodId, token)
        val infoId = if (vodId.startsWith("v")) vodId else "v$vodId"
        val owner = video.obj("owner")

        return InfoDict(
            id = infoId,
            title = video.str("title") ?: "Untitled Broadcast",
            description = video.str("description"),
            duration = video.number("lengthSeconds"),
            uploader = owner?.str("displayName"),
            channel = owner?.str("login"),
            uploadDate = video.str("publishedAt")?.let(ExtractorUtils::unifiedStrdate),
            viewCount = video.number("viewCount")?.toLong(),
            thumbnails = thumbnails(video.str("previewThumbnailURL")),
            formats = listOf(hlsFormat(usher)),
            isLive = false,
            webpageUrl = url,
            extractor = "twitch",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TwitchVod"

        /** Upstream `TwitchVodIE._VALID_URL`. */
        val VALID_URL: Regex = Regex(
            "https?://" +
                "(?:" +
                "(?:(?:www|go|m)\\.)?twitch\\.tv/(?:[^/]+/v(?:ideo)?|videos)/" +
                "|player\\.twitch\\.tv/\\?.*?\\bvideo=v?" +
                "|www\\.twitch\\.tv/[^/]+/schedule\\?vodID=" +
                ")(?<id>\\d+)",
        )
    }
}

/** Upstream `TwitchStreamIE`: one live channel's metadata and usher HLS URL. */
class TwitchStreamIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Twitch stream"

    override suspend fun extract(url: String): InfoDict {
        val channel = matchId(url)?.lowercase() ?: throw ExtractionError.UnsupportedUrl()

        val responses = gqlBatch(
            http,
            buildJsonArray {
                add(
                    persistedOperation(
                        "StreamMetadata",
                        buildJsonObject {
                            put("channelLogin", channel)
                            put("includeIsDJ", true)
                        },
                        STREAM_METADATA_HASH,
                    ),
                )
                add(
                    persistedOperation(
                        "ComscoreStreamingQuery",
                        buildJsonObject {
                            put("channel", channel)
                            put("clipSlug", "")
                            put("isClip", false)
                            put("isLive", true)
                            put("isVodOrCollection", false)
                            put("vodID", "")
                        },
                        COMSCORE_HASH,
                    ),
                )
                add(
                    persistedOperation(
                        "VideoPreviewOverlay",
                        buildJsonObject { put("login", channel) },
                        PREVIEW_OVERLAY_HASH,
                    ),
                )
            },
        )

        val user = responses.firstOrNull()?.obj("data")?.obj("user")
            ?: throw ExtractionError.Unavailable("This Twitch channel does not exist.")
        val stream = user.obj("stream")
            ?: throw ExtractionError.NotYetAvailable("This Twitch channel is not live.")
        val scoreUser = responses.getOrNull(1)?.obj("data")?.obj("user")
        val previewStream = responses.getOrNull(2)?.obj("data")?.obj("user")?.obj("stream")

        val token = playbackToken(
            http,
            query = "{ streamPlaybackAccessToken(channelName: \"$channel\", params: " +
                "{platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}) " +
                "{ value signature } }",
            field = "streamPlaybackAccessToken",
        )
        val usher = usherUrl("api/channel/hls", channel, token)

        val uploader = scoreUser?.str("displayName")
        val streamType = stream.str("type")
        val title = (uploader ?: channel) +
            if (streamType == "live" || streamType == "rerun") " ($streamType)" else ""

        return InfoDict(
            id = stream.str("id") ?: channel,
            title = title,
            description = scoreUser?.obj("broadcastSettings")?.str("title"),
            uploader = uploader,
            channelId = channel,
            uploadDate = stream.str("createdAt")?.let(ExtractorUtils::unifiedStrdate),
            viewCount = stream.number("viewers")?.toLong(),
            thumbnails = thumbnails(previewStream?.str("previewImageURL")),
            formats = listOf(hlsFormat(usher)),
            isLive = streamType == "live",
            webpageUrl = url,
            extractor = "twitch",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TwitchStream"

        /** Upstream `TwitchStreamIE._VALID_URL` with the listing paths excluded. */
        val VALID_URL: Regex = Regex(
            "https?://" +
                "(?:(?:(?:www|go|m)\\.)?twitch\\.tv/" +
                "(?!(?:videos|directory|downloads|search|settings|subscriptions|inventory|wallet|drops|friends)(?:[/?#]|$))" +
                "|player\\.twitch\\.tv/\\?.*?\\bchannel=)" +
                "(?<id>[^/#?&]+)(?:[?#].*)?$",
        )
    }
}

/**
 * Twitch's public web GraphQL client identifier. It is not a user credential
 * and no OAuth token or cookie is stored or sent.
 */
private const val CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"
private const val GQL_URL = "https://gql.twitch.tv/gql"
private const val USHER_BASE = "https://usher.ttvnw.net"

private const val VIDEO_METADATA_HASH = "45111672eea2e507f8ba44d101a61862f9c56b11dee09a15634cb75cb9b9084d"
private const val STREAM_METADATA_HASH = "ad022ca32220d5523d03a23cbcb5beaa1e0999889c1f8f78f9f2520dafb5cae6"
private const val COMSCORE_HASH = "e1edae8122517d013405f237ffcc124515dc6ded82480a88daef69c83b53ac01"
private const val PREVIEW_OVERLAY_HASH = "9515480dee68a77e667cb19de634739d33f243572b007e98e67184b1a5d8369f"

private fun persistedOperation(name: String, variables: JsonObject, hash: String): JsonObject =
    buildJsonObject {
        put("operationName", name)
        put("variables", variables)
        putJsonObject("extensions") {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", hash)
            }
        }
    }

/** One POST to the public GraphQL endpoint; returns the array for a batch. */
private suspend fun gqlBatch(
    http: ExtractorHttp,
    ops: JsonArray,
): List<JsonObject> {
    val response = http.downloadJson(
        GQL_URL,
        method = HttpMethods.POST,
        headers = mapOf(
            "client-id" to CLIENT_ID,
            "content-type" to "text/plain;charset=UTF-8",
        ),
        body = ops.toString().encodeToByteArray(),
    )
    return (response as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
}

private suspend fun playbackToken(
    http: ExtractorHttp,
    query: String,
    field: String,
): Pair<String, String> {
    val response = http.downloadJson(
        GQL_URL,
        method = HttpMethods.POST,
        headers = mapOf(
            "client-id" to CLIENT_ID,
            "content-type" to "text/plain;charset=UTF-8",
        ),
        body = buildJsonObject { put("query", query) }.toString().encodeToByteArray(),
    ) as? JsonObject ?: throw ExtractionError.Malformed("The Twitch GraphQL response was not an object.")
    val token = response.obj("data")?.obj(field)
        ?: throw ExtractionError.Malformed("The Twitch playback token was missing.")
    return (token.str("value") ?: "") to (token.str("signature") ?: "")
}

private fun usherUrl(path: String, id: String, token: Pair<String, String>): String {
    val (value, signature) = token
    return "$USHER_BASE/$path/$id.m3u8" +
        "?allow_source=true&allow_audio_only=true&allow_spectre=true" +
        "&platform=web&player=twitchweb&supported_codecs=av1,h265,h264" +
        "&playlist_include_framerate=true" +
        "&sig=" + percentEncode(signature) + "&token=" + percentEncode(value)
}

private fun hlsFormat(usherUrl: String): MediaFormat = MediaFormat(
    formatId = "hls",
    url = usherUrl,
    ext = "mp4",
    protocol = "m3u8_native",
    formatNote = "HLS",
)

/** Upstream `_get_thumbnails`: a `0x0` variant plus the original. */
private fun thumbnails(thumbnail: String?): List<Thumbnail> {
    if (thumbnail.isNullOrBlank()) return emptyList()
    val zero = Regex("\\d+x\\d+(\\.\\w+)(?=$|[?#])").replace(thumbnail, "0x0\$1")
    return listOf(
        Thumbnail(url = zero, preference = 1),
        Thumbnail(url = thumbnail),
    )
}

private fun percentEncode(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xff
        val character = code.toChar()
        when {
            character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' -> out.append(character)
            character in "-_.~" -> out.append(character)
            character == ' ' -> out.append('+')
            else -> {
                val hex = "0123456789ABCDEF"
                out.append('%').append(hex[code ushr 4]).append(hex[code and 0x0f])
            }
        }
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
