/*
 * RTVC Play extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `rtvcplay.py` from
 * `yt_dlp/extractor/rtvcplay.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rtvcplay.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `window.__RTVCPLAY_STATE__` hydration (live HLS, asset-id HLS
 * template, season/podcast playlists, metadata), the `config` player object
 * (HLS/direct rows), and the two CMS metadata calls. An m3u8 URL becomes one
 * HLS row, so manifest subtitles are not parsed; season/episode numbers are
 * not carried on entries; no cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.rtvcplay

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `RTVCPlayBaseIE`: the player-config helpers. */
abstract class RTVCPlayBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_player_config`: the `config` object in a script. */
    protected fun extractPlayerConfig(webpage: String, videoId: String): JsonObject {
        val cleaned = webpage.replace(Regex("\"\\s*\\+\\s*\""), "")
        val marker = Regex("<script\\b[^>]*>[^<]*(?:var|let|const)\\s+config\\s*=").find(cleaned)
            ?: throw ExtractionError.Malformed("The RTVC player config marker was not found.")
        val body = balancedObject(cleaned, marker.range.last + 1)
            ?: throw ExtractionError.Malformed("The RTVC player config was not an object.")
        return ExtractorUtils.parseJson(
            ExtractorUtils.jsToJson(cleaned.substring(body.first, body.last + 1)),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The RTVC player config was not JSON.")
    }

    /** Upstream `_extract_formats_and_subtitles_player_config`. */
    protected fun formatsFromPlayerConfig(
        playerConfig: JsonObject,
        videoId: String,
    ): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in playerConfig.array("sources").orEmpty()) {
            val source = element as? JsonObject ?: continue
            val sourceUrl = source.str("url")
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: continue
            val ext = ExtractorUtils.mimetype2ext(source.str("mimetype"))
                ?: ExtractorUtils.determineExt(sourceUrl)
            if (ext == "m3u8") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = sourceUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            } else {
                formats += MediaFormat(url = sourceUrl, ext = ext)
            }
        }
        return formats
    }
}

/** Upstream `RTVCPlayIE`: a rtvcplay.co page. */
class RTVCPlayIE(
    http: ExtractorHttp,
) : RTVCPlayBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val category = match.groups["category"]?.value
        val webpage = http.downloadWebpage(url)
        val marker = Regex("window\\.__RTVCPLAY_STATE__\\s*=").find(webpage)
            ?: throw ExtractionError.Malformed("The RTVC hydration state was not found.")
        val range = balancedObject(webpage, marker.range.last + 1)
            ?: throw ExtractionError.Malformed("The RTVC hydration state was not an object.")
        val state = ExtractorUtils.parseJson(
            ExtractorUtils.jsToJson(webpage.substring(range.first, range.last + 1)),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The RTVC hydration state was not JSON.")
        val hydration = state.obj("content")?.obj("currentContent")
            ?: throw ExtractionError.Malformed("The RTVC hydration state had no current content.")

        val assetId = hydration.obj("video")?.primitive("assetid")
        val hlsUrl = if (assetId != null) {
            hydration.str("base_url_hls")?.replace("[node:field_asset_id]", assetId)
        } else {
            hydration.obj("channel")?.str("hls")
        }
        val title = hydration.str("title")
        val description = hydration.str("description")
        val thumbnail = hydration.obj("channel")?.obj("image")?.obj("logo")?.str("path")
            ?: hydration.obj("resource")?.obj("image")?.obj("cover_desktop")?.str("path")

        if (hlsUrl.isNullOrBlank()) {
            val seasons = hydration.array("widgets").orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.str("type") == "seasonList" }
                ?.array("contents")
            if (seasons.isNullOrEmpty()) {
                val podcastEpisodes = hydration.array("audios")
                if (podcastEpisodes.isNullOrEmpty()) {
                    throw ExtractionError.Malformed(
                        "Could not find an asset id, program playlist, or podcast episodes.",
                    )
                }
                val entries = podcastEpisodes.mapNotNull { element ->
                    val episode = element as? JsonObject ?: return@mapNotNull null
                    val file = episode.str("file") ?: return@mapNotNull null
                    InfoEntry(
                        title = episode.str("title"),
                        url = file,
                    )
                }
                return InfoDict(
                    id = videoId,
                    title = title,
                    description = description,
                    thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
                    entries = entries,
                    webpageUrl = url,
                    extractor = "rtvcplay",
                    extractorKey = IE_KEY,
                )
            }
            val entries = mutableListOf<InfoEntry>()
            for (seasonElement in seasons) {
                val season = seasonElement as? JsonObject ?: continue
                for (episodeElement in season.array("contents").orEmpty()) {
                    val episode = episodeElement as? JsonObject ?: continue
                    val slug = episode.str("slug") ?: continue
                    entries += InfoEntry(
                        title = episode.str("title"),
                        url = urlJoin(url, slug),
                    )
                }
            }
            return InfoDict(
                id = videoId,
                title = title,
                description = description,
                thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
                entries = entries,
                webpageUrl = url,
                extractor = "rtvcplay",
                extractorKey = IE_KEY,
            )
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            isLive = category == "en-vivo",
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = listOf(
                MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "rtvcplay",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVCPlay"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtvcplay\\.co/(?<category>(?!embed)[^/]+)/(?:[^?#]+/)?(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `RTVCPlayEmbedIE`: the rtvcplay.co embed player. */
class RTVCPlayEmbedIE(
    http: ExtractorHttp,
) : RTVCPlayBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playerConfig = extractPlayerConfig(webpage, videoId)
        val formats = formatsFromPlayerConfig(playerConfig, videoId)
        val assetId = playerConfig.obj("rtvcplay")?.primitive("assetid")
        val metadata = if (assetId == null) {
            null
        } else {
            try {
                http.downloadJson("https://cms.rtvcplay.co/api/v1/video/asset-id/$assetId") as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
        }
        val thumbnail = metadata?.array("image").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstNotNullOfOrNull { it.obj("thumbnail")?.str("path") }
        return InfoDict(
            id = videoId,
            title = metadata?.str("title"),
            description = metadata?.str("description"),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "rtvcplay:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVCPlayEmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtvcplay\\.co/embed/(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `RTVCKalturaIE`: the media.rtvc.gov.co Kaltura page. */
class RTVCKalturaIE(
    http: ExtractorHttp,
) : RTVCPlayBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playerConfig = extractPlayerConfig(webpage, videoId)
        val formats = formatsFromPlayerConfig(playerConfig, videoId).toMutableList()
        val channelId = playerConfig.obj("rtvcplay")?.primitive("channelId")
        val metadata = if (channelId == null) {
            null
        } else {
            try {
                http.downloadJson("https://cms.rtvcplay.co/api/v1/taxonomy_term/streaming/$channelId")
                    as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
        }
        metadata?.obj("channel")?.str("hls")?.let { channelHls ->
            formats += MediaFormat(
                formatId = "hls",
                url = channelHls,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }
        return InfoDict(
            id = videoId,
            title = metadata?.str("title"),
            description = metadata?.str("description"),
            isLive = true,
            thumbnails = listOfNotNull(
                metadata?.obj("channel")?.obj("image")?.obj("logo")?.str("path")
                    ?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "rtvc:kaltura",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVCKaltura"

        val VALID_URL: Regex = Regex(
            "https?://media\\.rtvc\\.gov\\.co/kalturartvc/(?<id>[\\w-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** The first `{...}` object after [start], with string/escape awareness. */
private fun balancedObject(value: String, start: Int): IntRange? {
    val open = value.indexOf('{', start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var index = open
    while (index < value.length) {
        val character = value[index]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
        } else {
            when (character) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return open..index
                }
            }
        }
        index++
    }
    return null
}

/** Upstream `urllib.parse.urljoin` for an absolute base and a path. */
private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("//") -> "https:$value"
    value.startsWith("/") -> Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) + value
    else -> base.substringBefore('?').trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
