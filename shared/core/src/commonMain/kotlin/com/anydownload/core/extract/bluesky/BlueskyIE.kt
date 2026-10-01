/*
 * Bluesky extractor — AnyDownload
 *
 * Kotlin translation of the public API subset of `bluesky.py` from
 * `yt_dlp/extractor/bluesky.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `bluesky.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `public.api.bsky.app` post-thread call, the embed video
 * walk (HLS playlist + blob formats), the DID service endpoint lookup, and
 * the caption blob tracks. Multi-video posts become media items; like/
 * repost counters and the nested record-with-media variants the port does
 * not carry are simplified. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.bluesky

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `BlueskyIE`: a post. */
class BlueskyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val handle = match.groups["handle"]?.value ?: match.groups["handle2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val postId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(
            "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread" +
                "?uri=at://$handle/app.bsky.feed.post/$postId&depth=0&parentHeight=0",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The post thread API was not an object.")
        val post = response.obj("thread")?.obj("post")
            ?: throw ExtractionError.Malformed("The post thread had no post.")
        val items = mutableListOf<InfoDict>()
        items += extractVideos(http, post, postId, nested = false)
        if (post.obj("embed")?.obj("record") != null) {
            items += extractVideos(http, post, postId, nested = true)
        }
        if (items.isEmpty()) {
            throw ExtractionError.NoFormats("No video could be found in this post.")
        }
        if (items.size == 1) return items.single()
        return InfoDict(
            id = postId,
            media = items.mapNotNull { item ->
                item.id?.let { id ->
                    InfoMedia(
                        mediaId = id,
                        title = item.title,
                        duration = item.duration,
                        thumbnails = item.thumbnails,
                        formats = item.formats,
                    )
                }
            },
            webpageUrl = url,
            extractor = "bluesky",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Bluesky"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:bsky\\.app|main\\.bsky\\.dev)/profile/(?<handle>[\\w.:%-]+)/post/(?<id>\\w+)|" +
                "at://(?<handle2>[\\w.:%-]+)/app\\.bsky\\.feed\\.post/(?<id2>\\w+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractVideos(
    http: ExtractorHttp,
    post: JsonObject,
    videoId: String,
    nested: Boolean,
): List<InfoDict> {
    val recordPrefix = if (nested) post.obj("embed")?.obj("record") else null
    val root = if (nested) {
        recordPrefix?.obj("record") ?: recordPrefix?.get("value")?.let { it as? JsonObject } ?: post
    } else {
        post.obj("record") ?: post
    }
    val embed = if (nested) {
        post.obj("embed")?.obj("media") ?: root.obj("embed")
    } else {
        post.obj("embed")
    } ?: return emptyList()
    val entries = mutableListOf<InfoDict>()
    embed.obj("external")?.str("uri")?.let { externalUri ->
        entries += InfoDict(
            id = videoId,
            redirectUrl = externalUri,
            webpageUrl = "https://bsky.app/profile/$videoId",
            extractor = "bluesky",
            extractorKey = "Bluesky",
        )
    }
    val playlist = embed.str("playlist") ?: return entries
    val formats = mutableListOf<MediaFormat>(
        MediaFormat(formatId = "hls", url = playlist, ext = "mp4", protocol = "m3u8_native"),
    )
    val subtitles = mutableListOf<SubtitleTrack>()
    val videoCid = embed.str("cid")
        ?: root.obj("embed")?.obj("video")?.obj("ref")?.str("\$link")
        ?: root.obj("video")?.obj("ref")?.str("\$link")
    val did = post.obj("author")?.str("did")
    if (did != null && videoCid != null) {
        val endpoint = serviceEndpoint(http, did, videoId)
        formats += MediaFormat(
            formatId = "blob",
            url = "$endpoint/xrpc/com.atproto.sync.getBlob?did=$did&cid=$videoCid",
            ext = null,
            width = embed.obj("aspectRatio")?.number("width")?.toLong(),
            height = embed.obj("aspectRatio")?.number("height")?.toLong(),
            preference = 1,
        )
        for (element in root.array("captions").orEmpty()) {
            val caption = element as? JsonObject ?: continue
            val cid = caption.obj("file")?.obj("ref")?.str("\$link") ?: continue
            subtitles += SubtitleTrack(
                language = caption.str("lang") ?: "und",
                formats = listOf(
                    SubtitleFormat(
                        ext = ExtractorUtils.mimetype2ext(caption.obj("file")?.str("mimeType")) ?: "vtt",
                        url = "$endpoint/xrpc/com.atproto.sync.getBlob?did=$did&cid=$cid",
                    ),
                ),
            )
        }
    }
    val text = root.str("text")
    entries += InfoDict(
        id = root.str("uri")?.substringAfterLast('/') ?: videoId,
        title = text?.replace("\n", " ")?.take(72),
        description = text,
        uploader = post.obj("author")?.str("displayName"),
        channelId = did,
        uploadDate = ExtractorUtils.unifiedStrdate(post.str("indexedAt")),
        ageLimit = if (post.array("labels").orEmpty().any {
                (it as? JsonObject)?.str("val") in listOf("sexual", "porn", "graphic-media")
            }
        ) 18 else null,
        thumbnails = listOfNotNull(embed.str("thumbnail")?.let { Thumbnail(url = it) }),
        formats = formats,
        subtitles = subtitles,
        webpageUrl = "https://bsky.app/profile/$videoId",
        extractor = "bluesky",
        extractorKey = "Bluesky",
    )
    return entries
}

private suspend fun serviceEndpoint(http: ExtractorHttp, did: String, videoId: String): String {
    val lookupUrl = if (did.startsWith("did:web:")) {
        "https://${did.removePrefix("did:web:")}/.well-known/did.json"
    } else {
        "https://plc.directory/$did"
    }
    val services = try {
        http.downloadJson(lookupUrl) as? JsonObject
    } catch (error: ExtractionError) {
        null
    }
    for (element in services?.array("service").orEmpty()) {
        val service = element as? JsonObject ?: continue
        if (service.str("type") == "AtprotoPersonalDataServer") {
            service.str("serviceEndpoint")?.let { return it }
        }
    }
    return "https://bsky.social"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
