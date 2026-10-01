/*
 * ESPN extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `espn.py` from
 * `yt_dlp/extractor/espn.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `espn.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public clip API (m3u8/smil/plain sources), the article video
 * scan, the FiveThirtyEight embed redirect, and the public CricInfo video
 * details API. f4m formats are skipped; `WatchESPNIE` matches and fails
 * typed: the Bamgrid token exchange needs an API key and the MVPD auth
 * flow. No key, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.espn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.theplatform.SmilManifest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ESPNIE`: the video clip pages. */
class ESPNIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson("http://api-app.espn.com/v1/video/clips/$videoId")
            as? JsonObject ?: throw ExtractionError.Malformed("The clip API was not an object.")
        val clip = response.array("videos")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The clip API returned no videos.")
        val formats = mutableListOf<MediaFormat>()
        val seen = mutableSetOf<String>()
        val links = clip.obj("links")
        collectSources(http, links?.obj("source"), null, formats, seen)
        collectSources(http, links?.obj("mobile"), null, formats, seen)
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The clip API returned no playable source.")
        }
        return InfoDict(
            id = videoId,
            title = clip.str("headline"),
            description = clip.str("caption") ?: clip.str("description"),
            duration = clip.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(clip.str("originalPublishDate")),
            thumbnails = listOfNotNull(clip.str("thumbnail")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "espn",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ESPN"

        val VALID_URL: Regex = Regex(
            "https?://(?:" +
                "(?:(?:(?:\\w+\\.)+)?espn\\.go|(?:www\\.)?espn)\\.com/" +
                "(?:(?:video/(?:clip|iframe/twitter))?(?:.*?\\?.*?\\bid=|/_/id/)|[^/]+/video/)" +
                "|(?:www\\.)espnfc\\.(?:com|us)/(?:video/)?[^/]+/\\d+/video/" +
                ")(?<id>\\d+)",
        )
    }
}

/** Upstream `ESPNArticleIE`: articles with a video play button. */
class ESPNArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !ESPNIE.VALID_URL.containsMatchIn(url) &&
            !WatchESPNIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val embedId = ExtractorUtils.searchRegex(
            "class=([\"']).*?video-play-button.*?\\1[^>]+data-id=[\"'](\\d+)",
            webpage,
            group = 2,
            default = null,
        ) ?: throw ExtractionError.Malformed("The article had no video play button.")
        return InfoDict(
            id = videoId,
            redirectUrl = "http://espn.go.com/video/clip?id=$embedId",
            webpageUrl = url,
            extractor = "espn:article",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ESPNArticle"

        val VALID_URL: Regex = Regex("https?://(?:espn\\.go|(?:www\\.)?espn)\\.com/(?:[^/]+/)*(?<id>[^/]+)")
    }
}

/** Upstream `FiveThirtyEightIE`: the feature pages with an ABC embed. */
class FiveThirtyEightIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val pageId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val embedUrl = ExtractorUtils.searchRegex(
            "<iframe[^>]+src=[\"'](https?://fivethirtyeight\\.abcnews\\.go\\.com/video/embed/\\d+/\\d+)",
            webpage,
            default = null,
        ) ?: throw ExtractionError.Malformed("The feature page had no embed URL.")
        return InfoDict(
            id = pageId,
            redirectUrl = embedUrl,
            webpageUrl = url,
            extractor = "fivethirtyeight",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "FiveThirtyEight"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?fivethirtyeight\\.com/features/(?<id>[^/?#]+)")
    }
}

/** Upstream `ESPNCricInfoIE`: the CricInfo video pages. */
class ESPNCricInfoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(
            "https://hs-consumer-api.espncricinfo.com/v1/pages/video/video-details?videoId=$videoId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The CricInfo API was not an object.")
        val data = response.obj("video")
            ?: throw ExtractionError.Malformed("The CricInfo API had no video.")
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in data.array("playbacks").orEmpty()) {
            val item = element as? JsonObject ?: continue
            val playbackUrl = item.str("url") ?: continue
            when (item.str("type")) {
                "HLS" -> formats += MediaFormat(
                    formatId = "hls",
                    url = playbackUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "AUDIO" -> formats += MediaFormat(
                    url = playbackUrl,
                    ext = ExtractorUtils.determineExt(playbackUrl),
                    vcodec = MediaFormat.CODEC_NONE,
                )
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The CricInfo API returned no playable source.")
        }
        return InfoDict(
            id = videoId,
            title = data.str("title"),
            description = data.str("summary"),
            duration = data.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(data.str("publishedAt") ?: data.str("recordedAt")),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "espncricinfo",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ESPNCricInfo"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?espncricinfo\\.com/(?:cricket-)?videos?/[^#$&?/]+-(?<id>\\d+)",
        )
    }
}

/** Upstream `WatchESPNIE`: the watch/ESPN+ player (Bamgrid/MVPD wall). */
class WatchESPNIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(
        "The WatchESPN player needs a Bamgrid token exchange (API key) and MVPD authentication; " +
            "the port excludes both.",
    )

    companion object {
        const val IE_KEY: String = "WatchESPN"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?espn\\.com/(?:watch|espnplus)/player/_/id/" +
                "(?<id>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun collectSources(
    http: ExtractorHttp,
    source: JsonObject?,
    baseId: String?,
    formats: MutableList<MediaFormat>,
    seen: MutableSet<String>,
) {
    if (source == null) return
    for ((sourceId, value) in source) {
        if (sourceId == "alert") continue
        val id = if (baseId != null) "$baseId-$sourceId" else sourceId
        when (value) {
            is JsonPrimitive -> addSource(http, value.content, id, formats, seen)
            is JsonObject -> collectSources(http, value, id, formats, seen)
            else -> Unit
        }
    }
}

private suspend fun addSource(
    http: ExtractorHttp,
    sourceUrl: String,
    sourceId: String,
    formats: MutableList<MediaFormat>,
    seen: MutableSet<String>,
) {
    if (!seen.add(sourceUrl)) return
    when (ExtractorUtils.determineExt(sourceUrl)) {
        "smil" -> {
            val xml = try {
                http.downloadWebpage(sourceUrl)
            } catch (error: ExtractionError) {
                return
            }
            for (video in SmilManifest.videos(xml)) {
                formats += MediaFormat(
                    formatId = sourceId,
                    url = video.src,
                    ext = ExtractorUtils.determineExt(video.src),
                    width = video.width,
                    height = video.height,
                )
            }
        }

        "m3u8" -> formats += MediaFormat(
            formatId = sourceId,
            url = sourceUrl,
            ext = "mp4",
            protocol = "m3u8_native",
        )

        "f4m" -> Unit // f4m is skipped: the port has no f4m helper.
        else -> {
            var height: Long? = null
            var fps: Double? = null
            var tbr: Double? = null
            Regex("(\\d+)p(\\d+)_(\\d+)k\\.").find(sourceUrl)?.let { match ->
                height = match.groupValues[1].toLongOrNull()
                fps = match.groupValues[2].toDoubleOrNull()
                tbr = match.groupValues[3].toDoubleOrNull()
            }
            formats += MediaFormat(
                formatId = sourceId,
                url = sourceUrl,
                ext = ExtractorUtils.determineExt(sourceUrl),
                height = height,
                fps = fps,
                tbr = tbr,
                preference = if (sourceId == "mezzanine") 1 else null,
            )
        }
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
