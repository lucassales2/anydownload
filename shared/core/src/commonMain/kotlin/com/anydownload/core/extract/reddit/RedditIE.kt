/*
 * Reddit extractor — AnyDownload
 *
 * Kotlin translation of the public JSON subset of `reddit.py` from
 * `yt_dlp/extractor/reddit.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `reddit.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `comments/<id>/.json` walk: preview thumbnails, the
 * reddit-hosted video formats (fallback mp4, HLS, DASH) with the fallback
 * caption track, text-post `media_metadata` items, and the external-link
 * redirect. The `over18`/`_options` opt-in cookies, the shreddit session
 * setup, the comment walk, and the like/dislike/comment counters are not
 * translated; quarantined/private subreddits fail typed. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.reddit

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

/** Upstream `RedditIE`: a comments page. */
class RedditIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val slug = match.groups["slug"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val response = try {
            http.downloadJson("https://www.reddit.com/$slug/.json") as? JsonArray
        } catch (error: ExtractionError) {
            throw ExtractionError.LoginRequired(
                "Reddit refused the public API call (quarantined, private, or gated subreddit); " +
                    "an authenticated session is required.",
            )
        } ?: throw ExtractionError.Malformed("The Reddit API was not a list.")
        val data = response.firstOrNull()?.let { it as? JsonObject }
            ?.obj("data")?.array("children")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Reddit post was not found.")
        val post = data.obj("data") ?: throw ExtractionError.Malformed("The Reddit post had no data.")

        val thumbnails = mutableListOf<Thumbnail>()
        for (element in post.obj("preview")?.array("images").orEmpty()) {
            val image = element as? JsonObject ?: continue
            addThumbnail(thumbnails, image.obj("source"))
            for (resolution in image.array("resolutions").orEmpty()) {
                addThumbnail(thumbnails, resolution as? JsonObject)
            }
        }
        val title = post.str("title")?.take(72)
        val common = InfoDict(
            title = title,
            description = post.str("selftext"),
            uploadDate = post.number("created_utc")?.let {
                ExtractorUtils.epochSecondsToDate(it.toLong())
            },
            uploader = post.str("author"),
            channelId = post.str("subreddit"),
            ageLimit = if (post.bool("over_18") == true) 18 else 0,
            thumbnails = thumbnails,
        )

        val videoUrl = post.str("url") ?: ""
        if (videoUrl.contains("reddit.com") && videoUrl.contains("/$videoId/")) {
            val media = mutableListOf<InfoMedia>()
            for (element in post.obj("media_metadata")?.let { it as? JsonObject }?.values.orEmpty()) {
                val item = element as? JsonObject ?: continue
                if (item.str("id") == null || item.str("e") != "RedditVideo") continue
                val formats = mutableListOf<MediaFormat>()
                item.str("hlsUrl")?.let {
                    formats += MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native")
                }
                item.str("dashUrl")?.let {
                    formats += MediaFormat(formatId = "dash", url = it, ext = "mp4", protocol = "mpd")
                }
                if (formats.isNotEmpty()) {
                    media += InfoMedia(mediaId = item.str("id")!!, title = title, formats = formats)
                }
            }
            if (media.isNotEmpty()) {
                return common.copy(
                    id = videoId,
                    media = media,
                    webpageUrl = url,
                    extractor = "reddit",
                    extractorKey = IE_KEY,
                )
            }
            throw ExtractionError.NoFormats("No media found.")
        }

        val redditVideo = post.obj("secure_media")?.obj("reddit_video")
            ?: post.array("crosspost_parent_list")?.firstOrNull()?.let { it as? JsonObject }
                ?.obj("secure_media")?.obj("reddit_video")
        if (redditVideo != null) {
            val fallbackUrl = redditVideo.str("fallback_url")
                ?: throw ExtractionError.NoFormats("The Reddit video had no fallback URL.")
            val realId = Regex("https?://v\\.redd\\.it/([^/?#&]+)").find(fallbackUrl)
                ?.groupValues?.get(1) ?: videoId
            val formats = mutableListOf<MediaFormat>()
            formats += MediaFormat(
                formatId = "fallback",
                url = fallbackUrl,
                ext = "mp4",
                height = redditVideo.number("height")?.toLong(),
                width = redditVideo.number("width")?.toLong(),
                tbr = redditVideo.number("bitrate_kbps")?.toDouble(),
                vcodec = "h264",
                acodec = MediaFormat.CODEC_NONE,
            )
            val hlsUrl = redditVideo.str("hls_url") ?: "https://v.redd.it/$realId/HLSPlaylist.m3u8"
            val dashUrl = redditVideo.str("dash_url") ?: "https://v.redd.it/$realId/DASHPlaylist.mpd"
            formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
            formats += MediaFormat(formatId = "dash", url = dashUrl, ext = "mp4", protocol = "mpd")
            val subtitles = listOf(
                SubtitleTrack(
                    language = "en",
                    formats = listOf(
                        SubtitleFormat(ext = "vtt", url = "https://v.redd.it/$realId/wh_ben_en.vtt"),
                    ),
                ),
            )
            return common.copy(
                id = realId,
                duration = redditVideo.number("duration"),
                formats = formats,
                subtitles = subtitles,
                webpageUrl = url,
                extractor = "reddit",
                extractorKey = IE_KEY,
            )
        }

        if (videoUrl.startsWith("https://v.redd.it/")) {
            throw ExtractionError.NotYetAvailable("This Reddit video is still processing.")
        }
        return common.copy(
            id = videoId,
            redirectUrl = videoUrl.takeIf { it.startsWith("http") },
            webpageUrl = url,
            extractor = "reddit",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Reddit"

        val VALID_URL: Regex = Regex(
            "https?://(?:\\w+\\.)?reddit(?:media)?\\.com/" +
                "(?<slug>(?:(?:r|user)/[^/]+/)?comments/(?<id>[^/?#&]+))",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun addThumbnail(thumbnails: MutableList<Thumbnail>, source: JsonObject?) {
    val url = source?.str("url") ?: return
    thumbnails += Thumbnail(
        url = ExtractorUtils.unescapeHtml(url) ?: url,
        width = source.number("width")?.toLong(),
        height = source.number("height")?.toLong(),
    )
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let {
        when (it.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
