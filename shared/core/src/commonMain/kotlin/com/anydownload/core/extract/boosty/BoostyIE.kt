/*
 * Boosty extractor — AnyDownload
 *
 * Kotlin translation of the public API subset of `boosty.py` from
 * `yt_dlp/extractor/boosty.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `boosty.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `api.boosty.to/v1/blog/{user}/post/{post}` JSON, the ok_video
 * playerUrls into HLS/DASH/direct rows with the upstream quality order, the
 * title fallback page, the subscription wall, and the external-video
 * redirect. A single item returns its info; several items become URL entries
 * (an ok_video entry points at its best direct player URL). The upstream
 * `auth` cookie Bearer extraction is not translated (the active cookie jar
 * attaches the cookie itself); alt_title, tags, likes, and the
 * release/modified timestamps are not carried. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.boosty

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

private val MP4_TYPES = listOf("tiny", "lowest", "low", "medium", "high", "full_hd", "quad_hd", "ultra_hd")

/** Upstream `BoostyIE`: a Boosty post. */
class BoostyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val user = match.groups["user"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val postId = match.groups["postid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val post = http.downloadJson("https://api.boosty.to/v1/blog/$user/post/$postId") as? JsonObject
            ?: throw ExtractionError.Malformed("The Boosty post API was not an object.")

        var postTitle = post.str("title")
        if (postTitle == null) {
            val webpage = http.downloadWebpage(url)
            postTitle = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
                ?: ExtractorUtils.searchRegex(
                    "<title[^>]*>(.*?)</title>",
                    webpage,
                    setOf(RegexOption.DOT_MATCHES_ALL),
                )?.trim()
        }
        val common = PostMetadata(
            channel = post.obj("user")?.str("name"),
            channelId = post.obj("user")?.primitive("id"),
            uploadDate = post.number("createdAt")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
        )

        val entries = mutableListOf<InfoDict>()
        for (element in post.array("data").orEmpty()) {
            val item = element as? JsonObject ?: continue
            when (item.str("type")) {
                "video" -> {
                    val videoUrl = item.str("url")
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: continue
                    entries += InfoDict(id = postId, redirectUrl = videoUrl, webpageUrl = url)
                }

                "ok_video" -> {
                    val videoId = item.primitive("id") ?: postId
                    entries += InfoDict(
                        id = videoId,
                        title = item.str("title") ?: postTitle,
                        channel = common.channel,
                        channelId = common.channelId,
                        duration = item.number("duration"),
                        viewCount = item.number("viewsCounter")?.toLong(),
                        uploadDate = common.uploadDate,
                        thumbnails = listOfNotNull(
                            (item.str("preview") ?: item.str("defaultPreview"))?.let { Thumbnail(url = it) },
                        ),
                        formats = extractFormats(item.array("playerUrls").orEmpty(), videoId),
                        webpageUrl = url,
                        extractor = "boosty",
                        extractorKey = IE_KEY,
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            if (post.bool("hasAccess") != true) {
                throw ExtractionError.LoginRequired("This post requires a subscription.")
            }
            throw ExtractionError.Unavailable("No videos found.")
        }
        if (entries.size == 1) return entries[0]
        return InfoDict(
            id = postId,
            title = postTitle,
            channel = common.channel,
            channelId = common.channelId,
            uploadDate = common.uploadDate,
            entries = entries.map { entry ->
                InfoEntry(
                    id = entry.id,
                    title = entry.title,
                    url = entry.redirectUrl ?: bestDirectUrl(entry),
                )
            },
            webpageUrl = url,
            extractor = "boosty",
            extractorKey = IE_KEY,
        )
    }

    private fun extractFormats(playerUrls: List<JsonElement>, videoId: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in playerUrls) {
            val player = element as? JsonObject ?: continue
            val formatUrl = player.str("url")
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            when (val formatType = player.str("type")) {
                "hls", "hls_live", "live_ondemand_hls", "live_playback_hls" -> formats += MediaFormat(
                    formatId = "hls",
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "dash", "dash_live", "live_playback_dash" -> formats += MediaFormat(
                    formatId = "dash",
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "mpd",
                )

                in MP4_TYPES -> formats += MediaFormat(
                    formatId = formatType,
                    url = formatUrl,
                    ext = "mp4",
                    preference = MP4_TYPES.indexOf(formatType),
                )

                else -> Unit
            }
        }
        return formats
    }

    /** The best direct mp4 URL of a single ok_video entry, for the entry list. */
    private fun bestDirectUrl(entry: InfoDict): String? =
        entry.formats.lastOrNull { it.formatId in MP4_TYPES }?.url ?: entry.formats.lastOrNull()?.url

    private data class PostMetadata(
        val channel: String?,
        val channelId: String?,
        val uploadDate: String?,
    )

    companion object {
        const val IE_KEY: String = "Boosty"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?boosty\\.to/(?<user>[^/#?]+)/posts/(?<postid>[^/#?]+)",
        )
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

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
