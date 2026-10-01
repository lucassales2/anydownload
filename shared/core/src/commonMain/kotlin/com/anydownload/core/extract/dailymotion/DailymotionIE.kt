/*
 * Dailymotion extractor — AnyDownload
 *
 * Kotlin translation of the public metadata subset of `DailymotionIE` from
 * `yt_dlp/extractor/dailymotion.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `dailymotion.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `dai.ly/<id>`, `dailymotion.<tld>/video|embed/video|swf/video/<id>`,
 * `player.html?video=<id>`, and the `lequipe.fr` host forms. The public
 * `player/metadata/video/<xid>` JSON maps the quality list (HTTP plus
 * `m3u8_native` HLS), subtitles, posters/thumbnails, duration, date, owner,
 * explicit flag, and the `is_live` flag. DM007 fails typed as geo-restricted.
 *
 * Not translated: the authenticated GraphQL media call and its hardcoded
 * OAuth client credentials (deliberately not copied), description, like and
 * view counts, and the playlist, search, and user classes. A login wall or
 * DRM wall is Partial. No OAuth token, client secret, cookie, or signed media
 * URL is stored or committed; fixture hosts are `*.example`.
 */
package com.anydownload.core.extract.dailymotion

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Upstream `DailymotionIE`: one public video from the metadata endpoint. */
class DailymotionIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Dailymotion"

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadata = http.downloadJson(
            "https://www.dailymotion.com/player/metadata/video/$videoId?app=com.dailymotion.neon",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Dailymotion metadata response was empty.")

        metadata.obj("error")?.let { error ->
            val errorCode = error.str("code")
            if (errorCode == "DM007") {
                throw ExtractionError.GeoRestricted()
            }
            throw ExtractionError.Unavailable("Dailymotion says this video is not available.")
        }

        val formats = mutableListOf<MediaFormat>()
        for ((quality, element) in metadata.obj("qualities").orEmpty()) {
            for (mediaElement in (element as? JsonArray).orEmpty()) {
                val media = mediaElement as? JsonObject ?: continue
                val mediaUrl = media.str("url")?.substringBefore('#') ?: continue
                val mediaType = media.str("type")
                if (mediaType == "application/vnd.lumberjack.manifest") continue
                if (mediaType == "application/x-mpegURL") {
                    formats += MediaFormat(
                        formatId = "hls-$quality",
                        url = mediaUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                        formatNote = "HLS",
                    )
                } else {
                    val dimensions = DIMENSIONS.find(mediaUrl)
                    var format = MediaFormat(
                        formatId = "http-$quality",
                        url = mediaUrl,
                        width = dimensions?.groupValues?.get(1)?.toLongOrNull(),
                        height = dimensions?.groupValues?.get(2)?.toLongOrNull(),
                        fps = dimensions?.groupValues?.get(3)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
                    )
                    if (format.fps == null && format.formatId?.endsWith("@60") == true) {
                        format = format.copy(fps = 60.0)
                    }
                    formats += format
                }
            }
        }
        if (formats.isEmpty()) throw ExtractionError.NoFormats("Dailymotion declared no format.")

        val subtitles = mutableListOf<SubtitleTrack>()
        for ((language, element) in metadata.obj("subtitles")?.obj("data").orEmpty()) {
            val subtitle = element as? JsonObject ?: continue
            val formatsForLanguage = subtitle.array("urls").orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
                .filter { it.isNotBlank() }
                .map { SubtitleFormat(ext = subtitleExt(it), url = it) }
            if (formatsForLanguage.isNotEmpty()) {
                subtitles += SubtitleTrack(language = language, formats = formatsForLanguage)
            }
        }

        val thumbnails = mutableListOf<Thumbnail>()
        for (key in listOf("posters", "thumbnails")) {
            for ((size, element) in metadata.obj(key).orEmpty()) {
                val thumbnailUrl = (element as? JsonPrimitive)
                    ?.takeIf { it.isString }?.content
                    ?.takeIf { it.isNotBlank() } ?: continue
                thumbnails += Thumbnail(
                    url = thumbnailUrl,
                    id = size,
                    height = size.toLongOrNull(),
                )
            }
        }

        val owner = metadata.obj("owner")
        return InfoDict(
            id = videoId,
            title = metadata.str("title"),
            duration = metadata.number("duration"),
            uploadDate = metadata.number("created_time")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            uploader = owner?.str("screenname"),
            channelId = owner?.str("id") ?: owner?.number("id")?.toLong()?.toString(),
            ageLimit = if ((metadata["explicit"] as? JsonPrimitive)?.booleanOrNull == true) 18 else 0,
            formats = formats,
            subtitles = subtitles,
            thumbnails = thumbnails,
            isLive = (metadata["is_live"] as? JsonPrimitive)?.booleanOrNull,
            webpageUrl = url,
            extractor = "dailymotion",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Dailymotion"

        /** Upstream `_VALID_URL` without the playlist query capture. */
        val VALID_URL: Regex = Regex(
            "(?:https?:)?//" +
                "(?:" +
                "dai\\.ly/" +
                "|" +
                "(?:(?:(?:www|touch|geo)\\.)?dailymotion\\.[a-z]{2,3}|(?:www\\.)?lequipe\\.fr)/" +
                "(?:" +
                "swf/(?!video)" +
                "|(?:(?:crawler|embed|swf)/)?video/" +
                "|player(?:/[\\da-z]+)?\\.html\\?video=" +
                ")" +
                ")" +
                "(?<id>[^/?_&#]+)",
        )

        private val DIMENSIONS = Regex("/H264-(\\d+)x(\\d+)(?:-(60))?")
    }
}

private fun subtitleExt(url: String): String = when {
    url.substringBefore('?').endsWith(".srt", ignoreCase = true) -> "srt"
    else -> "vtt"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
