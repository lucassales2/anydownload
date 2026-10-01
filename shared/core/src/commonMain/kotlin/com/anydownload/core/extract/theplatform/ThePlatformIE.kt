/*
 * ThePlatform extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `theplatform.py` from
 * `yt_dlp/extractor/theplatform.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `theplatform.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the direct `link.theplatform.com/s/<path>` SMIL + metadata path and
 * the public feed API entries (formats from `plfile$url`, thumbnails,
 * duration, metadata). The config/guid page paths, the `_sign_url` HMAC
 * helper (only used with smuggled key/secret data), the HLS probe, and the
 * f4m/rtmp transforms are not translated. No key, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.theplatform

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ThePlatformIE`: the player and link URLs. */
class ThePlatformIE(
    http: ExtractorHttp,
) : ThePlatformBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val providerId = match.groups["providerId"]?.value ?: "dJ5BDC"
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val media = match.groups["media"]?.value ?: ""
        val path = providerId + "/" + media + videoId
        val smilUrl = "http://link.theplatform.com/s/$path?mbr=true"
        val xml = downloadSmil(smilUrl, mapOf("format" to "SMIL"))
        val exception = SmilManifest.exceptionValue(xml)
        if (exception == "GeoLocationBlocked") {
            throw ExtractionError.GeoRestricted()
        }
        if (exception != null) {
            throw ExtractionError.Unavailable(
                SmilManifest.refAbstract(xml) ?: "The ThePlatform video is unavailable.",
            )
        }
        val formats = SmilManifest.videos(xml).map { video ->
            val ext = ExtractorUtils.determineExt(video.src)
            MediaFormat(
                url = video.src,
                ext = if (ext == "m3u8") "mp4" else ext,
                protocol = if (ext == "m3u8") "m3u8_native" else null,
                width = video.width,
                height = video.height,
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The ThePlatform SMIL manifest had no video sources.")
        }
        val subtitles = SmilManifest.textStreams(xml).map { (src, lang, type) ->
            SubtitleTrack(
                language = lang ?: "en",
                formats = listOf(
                    SubtitleFormat(
                        ext = ExtractorUtils.mimetype2ext(type) ?: "vtt",
                        url = src,
                    ),
                ),
            )
        }
        val metadata = parseTheplatformMetadata(downloadTheplatformMetadata(path, videoId))
        return InfoDict(
            id = videoId,
            title = metadata.title,
            description = metadata.description,
            duration = metadata.durationSeconds,
            uploadDate = metadata.uploadDate,
            uploader = metadata.uploader,
            ageLimit = metadata.ageLimit,
            chapters = metadata.chapters,
            thumbnails = listOfNotNull(metadata.thumbnailUrl?.let { Thumbnail(url = it) }),
            formats = formats,
            subtitles = subtitles + metadata.subtitles,
            webpageUrl = url,
            extractor = "theplatform",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ThePlatform"

        val VALID_URL: Regex = Regex(
            "(?:https?://(?:link|player)\\.theplatform\\.com/[sp]/(?<providerId>[^/]+)/" +
                "(?:(?:(?:[^/]+/)+select/)?(?<media>media/(?:guid/\\d+/)?)?|" +
                "(?<config>(?:[^/?]+/(?:swf|config)|onsite)/select/))?" +
                "|theplatform:)(?<id>[^/?&]+)",
        )
    }
}

/** Upstream `ThePlatformFeedIE`: the public feed API. */
class ThePlatformFeedIE(
    http: ExtractorHttp,
) : ThePlatformBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val providerId = match.groups["providerId"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val feedId = match.groups["feedId"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val filter = match.groups["filter"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(
            "http://feed.theplatform.com/f/$providerId/$feedId?form=json&$filter",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The feed API was not an object.")
        val entry = response.array("entries")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The feed API returned no entries.")
        val formats = mutableListOf<MediaFormat>()
        var firstVideoId: String? = null
        var duration: Double? = null
        for (element in entry.array("media\$content").orEmpty()) {
            val item = element as? JsonObject ?: continue
            val fileUrl = item.str("plfile\$url") ?: continue
            if (firstVideoId == null) {
                firstVideoId = ThePlatformIE.VALID_URL.find(fileUrl)?.groups?.get("id")?.value
                duration = item.number("plfile\$duration")
            }
            val smilXml = try {
                downloadSmil(
                    fileUrl,
                    mapOf("mbr" to "true", "formats" to (item.str("plfile\$format") ?: "")),
                )
            } catch (error: ExtractionError) {
                null
            }
            if (smilXml != null) {
                for (video in SmilManifest.videos(smilXml)) {
                    val ext = ExtractorUtils.determineExt(video.src)
                    formats += MediaFormat(
                        url = video.src,
                        ext = if (ext == "m3u8") "mp4" else ext,
                        protocol = if (ext == "m3u8") "m3u8_native" else null,
                        width = video.width,
                        height = video.height,
                    )
                }
            } else {
                val ext = ExtractorUtils.determineExt(fileUrl)
                formats += MediaFormat(
                    url = fileUrl,
                    ext = if (ext == "m3u8") "mp4" else ext,
                    protocol = if (ext == "m3u8") "m3u8_native" else null,
                )
            }
        }
        val thumbnails = entry.array("media\$thumbnails").orEmpty().mapNotNull { element ->
            val thumbnail = element as? JsonObject ?: return@mapNotNull null
            val thumbnailUrl = thumbnail.str("plfile\$url") ?: return@mapNotNull null
            Thumbnail(
                url = thumbnailUrl,
                width = thumbnail.number("plfile\$width")?.toLong(),
                height = thumbnail.number("plfile\$height")?.toLong(),
            )
        }
        val metadata = firstVideoId?.let { parseTheplatformMetadata(downloadTheplatformMetadata("$providerId/$it", videoId)) }
            ?: ThePlatformMetadata()
        return InfoDict(
            id = videoId,
            title = metadata.title,
            description = metadata.description,
            duration = duration ?: metadata.durationSeconds,
            uploadDate = entry.number("media\$availableDate")?.let {
                ExtractorUtils.epochSecondsToDate((it / 1000).toLong())
            } ?: metadata.uploadDate,
            uploader = metadata.uploader,
            ageLimit = metadata.ageLimit,
            thumbnails = thumbnails.ifEmpty {
                listOfNotNull(metadata.thumbnailUrl?.let { Thumbnail(url = it) })
            },
            formats = formats,
            subtitles = metadata.subtitles,
            webpageUrl = url,
            extractor = "theplatform:feed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ThePlatformFeed"

        val VALID_URL: Regex = Regex(
            "https?://feed\\.theplatform\\.com/f/(?<providerId>[^/]+)/(?<feedId>[^?/]+)\\?" +
                "(?:[^&]+&)*(?<filter>by(?:Gui|I)d=(?<id>[^&]+))",
        )
    }
}

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
