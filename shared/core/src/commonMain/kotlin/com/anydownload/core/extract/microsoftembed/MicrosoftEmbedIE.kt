/*
 * Microsoft embed extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `microsoftembed.py` from
 * `yt_dlp/extractor/microsoftembed.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `microsoftembed.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public video CMS stream API, the Learn contentbrowser and
 * video APIs, the Learn session redirect, and the Build session API. Smooth
 * Streaming (ISM) and MPEG-DASH manifests are not translated, so a Medius
 * video with only an ISM manifest fails typed; HLS and plain-URL formats,
 * captions, and thumbnails are translated. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.microsoftembed

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_PAGES = 10

/** Upstream `MicrosoftEmbedIE`: a microsoft.com videoplayer embed. */
class MicrosoftEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadata = http.downloadJson(
            "https://prod-video-cms-rt-microsoft-com.akamaized.net/vhs/api/videos/$videoId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Microsoft video API returned no object.")
        val formats = mutableListOf<MediaFormat>()
        for ((sourceType, value) in metadata.obj("streams").orEmpty()) {
            val source = value as? JsonObject ?: continue
            val sourceUrl = source.str("url") ?: continue
            when (sourceType) {
                "apple_HTTP_Live_Streaming" -> formats += MediaFormat(
                    formatId = sourceType,
                    url = sourceUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "smooth_Streaming", "mPEG_DASH" -> Unit // ISM/MPD manifests are not translated.

                else -> formats += MediaFormat(
                    formatId = sourceType,
                    url = sourceUrl,
                    height = source.number("heightPixels")?.toLong(),
                    width = source.number("widthPixels")?.toLong(),
                )
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Microsoft video API returned no playable format.")
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        for ((language, value) in metadata.obj("captions").orEmpty()) {
            val caption = value as? JsonObject ?: continue
            val captionUrl = caption.str("url") ?: continue
            subtitles += SubtitleTrack(
                language = language,
                formats = listOf(SubtitleFormat(ext = "vtt", url = captionUrl)),
            )
        }
        val snippet = metadata.obj("snippet")
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in snippet?.array("thumbnails").orEmpty()) {
            val thumb = element as? JsonObject ?: continue
            val thumbUrl = thumb.str("url") ?: continue
            thumbnails += Thumbnail(
                url = thumbUrl,
                width = thumb.number("width")?.toLong(),
                height = thumb.number("height")?.toLong(),
            )
        }
        return InfoDict(
            id = videoId,
            title = snippet?.str("title"),
            uploadDate = ExtractorUtils.unifiedStrdate(snippet?.str("activeStartDate")),
            thumbnails = thumbnails.distinctBy { it.url },
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "microsoft:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MicrosoftEmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?microsoft\\.com/(?:[^/]+/)?videoplayer/embed/(?<id>[a-z0-9A-Z]+)",
        )
    }
}

/** Upstream `MicrosoftMediusIE`: a Medius embed page. */
class MicrosoftMediusIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage("https://medius.microsoft.com/Embed/video-nc/$videoId")
        Regex("StreamUrl\\s*=\\s*\"([^\"]+manifest)\"").find(webpage)
            ?: throw ExtractionError.Malformed("The Medius page had no stream URL.")
        throw ExtractionError.NoFormats(
            "The Medius video is served as a Smooth Streaming (ISM) manifest, which the port does not translate.",
        )
    }

    companion object {
        const val IE_KEY: String = "MicrosoftMedius"

        val VALID_URL: Regex = Regex(
            "https?://medius\\.microsoft\\.com/Embed/(?:Video\\?id=|video-nc/|VideoDetails/)" +
                "(?<id>[\\da-f-]+)",
        )
    }
}

/** Upstream `MicrosoftLearnPlaylistIE`: a Learn show or event listing. */
class MicrosoftLearnPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistType = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val subType = if (playlistType == "shows") "episodes" else "sessions"
        val entries = mutableListOf<InfoEntry>()
        var skip = 0
        var page = 0
        while (page < MAX_PAGES) {
            val info = http.downloadJson(
                "https://learn.microsoft.com/api/contentbrowser/search/$playlistType/$playlistId/" +
                    "$subType?locale=en-us&\$skip=$skip",
            ) as? JsonObject ?: break
            val paths = info.array("results").orEmpty().mapNotNull { element ->
                (element as? JsonObject)?.str("url")
            }
            for (path in paths) {
                entries += InfoEntry(url = "https://learn.microsoft.com/en-us$path")
            }
            skip += paths.size
            val count = info.number("count")?.toInt() ?: 0
            if (paths.isEmpty() || skip >= count) break
            page++
        }
        return InfoDict(
            id = playlistId,
            title = metaContent(webpage, "og:title"),
            description = metaContent(webpage, "og:description"),
            entries = entries,
            webpageUrl = url,
            extractor = "microsoft:learn:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MicrosoftLearnPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://learn\\.microsoft\\.com/(?:[\\w-]+/)?(?<type>shows|events)/(?<id>[\\w-]+)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `MicrosoftLearnEpisodeIE`: a Learn show episode. */
class MicrosoftLearnEpisodeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entryId = metaContent(webpage, "entryId")
            ?: throw ExtractionError.Malformed("The Learn page had no entry id.")
        val videoInfo = http.downloadJson(
            "https://learn.microsoft.com/api/video/public/v1/entries/$entryId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Learn video API returned no object.")
        val publicVideo = videoInfo.obj("publicVideo")
        val formats = mutableListOf<MediaFormat>()
        publicVideo?.str("adaptiveVideoHLSUrl")?.let {
            formats += MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native")
        }
        for (key in listOf("low", "medium", "high")) {
            val videoUrl = publicVideo?.str("${key}QualityVideoUrl") ?: continue
            val resolution = parseResolution(videoUrl)
            formats += MediaFormat(
                formatId = "video-http-$key",
                url = videoUrl,
                acodec = MediaFormat.CODEC_NONE,
                width = resolution?.first,
                height = resolution?.second,
            )
        }
        publicVideo?.str("audioUrl")?.let {
            formats += MediaFormat(formatId = "audio-http", url = it, vcodec = MediaFormat.CODEC_NONE)
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in publicVideo?.array("captions").orEmpty()) {
            val caption = element as? JsonObject ?: continue
            val captionUrl = caption.str("url") ?: continue
            subtitles += SubtitleTrack(
                language = caption.str("language") ?: "und",
                formats = listOf(SubtitleFormat(ext = "vtt", url = captionUrl)),
            )
        }
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in publicVideo?.array("thumbnailOtherSizes").orEmpty()) {
            val url = (element as? JsonObject)?.str("url") ?: continue
            thumbnails += Thumbnail(url = url)
        }
        return InfoDict(
            id = entryId,
            title = metaContent(webpage, "og:title"),
            description = metaContent(webpage, "og:description"),
            uploadDate = ExtractorUtils.unifiedStrdate(videoInfo.str("createTime")),
            thumbnails = thumbnails,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "microsoft:learn:episode",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MicrosoftLearnEpisode"

        val VALID_URL: Regex = Regex(
            "https?://learn\\.microsoft\\.com/(?:[\\w-]+/)?shows/[\\w-]+/(?<id>[^?#/]+)",
        )
    }
}

/** Upstream `MicrosoftLearnSessionIE`: a Learn event session. */
class MicrosoftLearnSessionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val videoUrl = metaContent(webpage, "externalVideoUrl")
            ?: throw ExtractionError.Malformed("The Learn session had no video URL.")
        return InfoDict(
            id = videoId,
            title = metaContent(webpage, "og:title"),
            description = metaContent(webpage, "og:description"),
            redirectUrl = videoUrl,
            webpageUrl = url,
            extractor = "microsoft:learn:session",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MicrosoftLearnSession"

        val VALID_URL: Regex = Regex(
            "https?://learn\\.microsoft\\.com/(?:[\\w-]+/)?events/[\\w-]+/(?<id>[^?#/]+)",
        )
    }
}

/** Upstream `MicrosoftBuildIE`: a Build session or the session listing. */
class MicrosoftBuildIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val sessions = http.downloadJson(
            "https://api-v2.build.microsoft.com/api/session/all/en-US",
        )
        val entries = mutableListOf<InfoEntry>()
        for (element in (sessions as? JsonArray).orEmpty()) {
            val session = element as? JsonObject ?: continue
            val onDemand = session.str("onDemand") ?: continue
            entries += InfoEntry(
                id = session.str("sessionId"),
                title = session.str("title"),
                url = onDemand,
            )
        }
        if (videoId == "sessions") {
            return InfoDict(
                id = videoId,
                entries = entries,
                webpageUrl = url,
                extractor = "microsoft:build",
                extractorKey = IE_KEY,
            )
        }
        val sessionEntry = entries.firstOrNull { it.id == videoId }
            ?: throw ExtractionError.Malformed("The Build API had no session with this id.")
        return InfoDict(
            id = videoId,
            redirectUrl = sessionEntry.url,
            webpageUrl = url,
            extractor = "microsoft:build",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MicrosoftBuild"

        val VALID_URL: Regex = Regex(
            "https?://build\\.microsoft\\.com/[\\w-]+/sessions/(?<id>[\\da-f-]+)|" +
                "https?://build\\.microsoft\\.com/[\\w-]+/(?<id2>sessions)/?(?:[?#]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `parse_resolution` subset: an `NNNxNNN` pair in the URL. */
private fun parseResolution(url: String): Pair<Long, Long>? {
    val match = Regex("[^0-9](\\d{2,4})x(\\d{2,4})[^0-9]").find(url) ?: return null
    val width = match.groupValues[1].toLongOrNull() ?: return null
    val height = match.groupValues[2].toLongOrNull() ?: return null
    return width to height
}

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1].ifBlank { null } }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
