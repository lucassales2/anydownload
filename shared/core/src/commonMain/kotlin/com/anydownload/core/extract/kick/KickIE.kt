/*
 * Kick extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `kick.py` from
 * `yt_dlp/extractor/kick.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `kick.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the anonymous `v2/channels`, `v1/video`, and `v2/clips/play` JSON
 * with one HLS row (or one direct row for a non-m3u8 clip) and the metadata
 * the info dict models. Limitations: upstream reads the `session_token`
 * cookie and sends `Authorization: Bearer` plus `impersonate=True`; the
 * platform allowlist refuses `Authorization` and the port does not
 * impersonate, so only public resources resolve; `uploader_id`,
 * `concurrent_view_count`, `release_timestamp`, `categories`, and
 * `like_count` are not modeled and are dropped; a channel without a
 * `livestream` object fails typed NotYetAvailable. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.kick

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `KickBaseIE`: the anonymous API call. */
abstract class KickBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun callApi(path: String): JsonObject {
        // Upstream adds the session_token Authorization header and
        // `impersonate=True`; the allowlist refuses Authorization and the port
        // does not impersonate, so the request is anonymous.
        return http.downloadJson("https://kick.com/api/$path") as? JsonObject
            ?: throw ExtractionError.Malformed("The Kick API was not an object.")
    }
}

/** Upstream `KickIE`: a live channel page. */
class KickIE(
    http: ExtractorHttp,
) : KickBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        !KickVODIE.VALID_URL.containsMatchIn(url) &&
            !KickClipIE.VALID_URL.containsMatchIn(url) &&
            super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val channel = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi("v2/channels/$channel")
        val livestream = response.obj("livestream")
            ?: throw ExtractionError.NotYetAvailable("This Kick channel is not live.")
        val playbackUrl = livestream.str("playback_url")
            ?: throw ExtractionError.Malformed("The Kick channel carried no playback URL.")

        return InfoDict(
            id = livestream.str("slug"),
            title = livestream.str("session_title"),
            description = response.obj("user")?.str("bio"),
            channel = channel,
            channelId = response.number("id")?.toLong()?.toString()
                ?: livestream.number("channel_id")?.toLong()?.toString(),
            uploader = response.str("name") ?: response.obj("user")?.str("username"),
            uploadDate = livestream.str("created_at")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = livestream.obj("thumbnail")?.str("url")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            isLive = true,
            ageLimit = if (livestream.boolean("is_mature") == true) 18 else 0,
            formats = listOf(
                MediaFormat(formatId = "hls", url = playbackUrl, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "kick:live",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Kick"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kick\\.com/" +
                "(?!(?:video|categories|search|auth)(?:[/?#]|$))(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `KickVODIE`: a channel VOD page. */
class KickVODIE(
    http: ExtractorHttp,
) : KickBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi("v1/video/$videoId")
        val source = response.str("source")
            ?: throw ExtractionError.Malformed("The Kick VOD carried no source URL.")
        val livestream = response.obj("livestream")
        val kickChannel = livestream?.obj("channel")

        return InfoDict(
            id = videoId,
            title = livestream?.str("session_title") ?: livestream?.str("slug"),
            description = kickChannel?.obj("user")?.str("bio"),
            channel = kickChannel?.str("slug"),
            channelId = kickChannel?.number("id")?.toLong()?.toString(),
            uploader = kickChannel?.obj("user")?.str("username"),
            uploadDate = response.str("created_at")?.let(ExtractorUtils::unifiedStrdate),
            duration = livestream?.number("duration")?.let { it / 1000.0 },
            thumbnails = livestream?.str("thumbnail")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            viewCount = response.number("views")?.toLong(),
            isLive = livestream?.boolean("is_live"),
            ageLimit = if (livestream?.boolean("is_mature") == true) 18 else 0,
            formats = listOf(
                MediaFormat(formatId = "hls", url = source, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "kick:vod",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "KickVOD"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kick\\.com/[\\w-]+/videos/" +
                "(?<id>[\\da-f]{8}-(?:[\\da-f]{4}-){3}[\\da-f]{12})",
        )
    }
}

/** Upstream `KickClipIE`: a clip page or `?clip=` link. */
class KickClipIE(
    http: ExtractorHttp,
) : KickBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val clipId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val clip = (callApi("v2/clips/$clipId/play"))["clip"] as? JsonObject
            ?: throw ExtractionError.Malformed("The Kick clip response carried no clip.")
        val clipUrl = clip.str("clip_url")
            ?: throw ExtractionError.Malformed("The Kick clip carried no URL.")
        val formats = if (ExtractorUtils.determineExt(clipUrl) == "m3u8") {
            listOf(MediaFormat(formatId = "hls", url = clipUrl, ext = "mp4", protocol = "m3u8_native"))
        } else {
            listOf(MediaFormat(url = clipUrl))
        }

        return InfoDict(
            id = clipId,
            title = clip.str("title"),
            channel = clip.obj("channel")?.str("slug"),
            channelId = clip.obj("channel")?.number("id")?.toLong()?.toString(),
            uploader = clip.obj("creator")?.str("username"),
            uploadDate = clip.str("created_at")?.let(ExtractorUtils::unifiedStrdate),
            duration = clip.number("duration"),
            thumbnails = clip.str("thumbnail_url")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            viewCount = clip.number("views")?.toLong(),
            ageLimit = if (clip.boolean("is_mature") == true) 18 else 0,
            formats = formats,
            webpageUrl = url,
            extractor = "kick:clips",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "KickClip"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kick\\.com/[\\w-]+" +
                "(?:/clips/|/?\\?(?:[^#]+&)?clip=)(?<id>clip_[\\w-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
