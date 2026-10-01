/*
 * NDR / N-JOY extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `ndr.py` from
 * `yt_dlp/extractor/ndr.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ndr.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `-ppjson.json` player data (m3u8 and plain formats, quality
 * preferences, poster thumbnails, TTML tracks, live flag) and the page scans
 * that re-dispatch to `ndr:<id>`. f4m formats are skipped (the port has no
 * f4m helper) and the JSON-LD merge is simplified. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.ndr

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

/** Upstream `NDRIE`: the ndr.de pages. */
class NDRIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val embedUrl = findEmbedUrl(webpage, displayId, url)
            ?: throw ExtractionError.Unavailable("Unable to extract the NDR embed URL.")
        return InfoDict(
            id = displayId,
            redirectUrl = embedUrl,
            webpageUrl = url,
            extractor = "ndr",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NDR"

        val VALID_URL: Regex = Regex("https?://(?:\\w+\\.)*ndr\\.de/(?:[^/]+/)*(?<id>[^/?#]+),[\\da-z]+\\.html")
    }
}

/** Upstream `NJoyIE`: the n-joy.de pages. */
class NJoyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayId"]?.value
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val ndrId = ExtractorUtils.searchRegex(
            "\\bsrc\\s*=\\s*[\"']?(?:/\\w+)+/([a-z]+\\d+)(?!\\.)\\b",
            webpage,
            default = null,
        ) ?: ExtractorUtils.searchRegex(
            "<iframe[^>]+id=\"pp_([\\da-z]+)\"",
            webpage,
            default = null,
        ) ?: throw ExtractionError.Malformed("The N-JOY page had no NDR id.")
        return InfoDict(
            id = videoId,
            title = displayId?.replace('-', ' ')?.trim(),
            description = ExtractorUtils.htmlSearchMeta(webpage, "description"),
            redirectUrl = "ndr:$ndrId",
            webpageUrl = url,
            extractor = "njoy",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NJoy"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?n-joy\\.de/(?:[^/]+/)*(?:(?<displayId>[^/?#]+),)?(?<id>[\\da-z]+)\\.html",
        )
    }
}

/** Upstream `NDREmbedBaseIE`: the `-ppjson.json` player data. */
class NDREmbedBaseIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["idS"]?.value ?: match.groups["id"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        return extractPpjson(http, videoId, url)
    }

    companion object {
        const val IE_KEY: String = "NDREmbedBase"

        val VALID_URL: Regex = Regex(
            "(?:ndr:(?<idS>[\\da-z]+)|https?://www\\.ndr\\.de/(?<id>[\\da-z]+)-ppjson\\.json)",
        )
    }
}

/** Upstream `NDREmbedIE`: the `-player.html` pages. */
class NDREmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractPpjson(http, videoId, url)
    }

    companion object {
        const val IE_KEY: String = "NDREmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:\\w+\\.)*ndr\\.de/(?:[^/]+/)*(?<id>[\\da-z]+)-(?:(?:ard)?player|externalPlayer)\\.html",
        )
    }
}

/** Upstream `NJoyEmbedIE`: the N-JOY `-player_...` pages. */
class NJoyEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractPpjson(http, videoId, url)
    }

    companion object {
        const val IE_KEY: String = "NJoyEmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?n-joy\\.de/(?:[^/]+/)*(?<id>[\\da-z]+)-(?:player|externalPlayer)_[^/]+\\.html",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val QUALITY_ORDER = listOf("xs", "s", "m", "l", "xl")

private fun qualityPreference(quality: String?): Int? =
    quality?.let { QUALITY_ORDER.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }

private suspend fun extractPpjson(http: ExtractorHttp, videoId: String, url: String): InfoDict {
    val ppjson = http.downloadJson("http://www.ndr.de/$videoId-ppjson.json") as? JsonObject
        ?: throw ExtractionError.Malformed("The ppjson API was not an object.")
    val playlist = ppjson.obj("playlist")
        ?: throw ExtractionError.Malformed("The ppjson had no playlist.")
    val formats = mutableListOf<MediaFormat>()
    for ((formatId, value) in playlist) {
        if (formatId == "config") continue
        val entry = value as? JsonObject ?: continue
        val src = entry.str("src") ?: continue
        val ext = ExtractorUtils.determineExt(src)
        when (ext) {
            "m3u8" -> formats += MediaFormat(
                formatId = "hls",
                url = src,
                ext = "mp4",
                protocol = "m3u8_native",
            )

            "f4m" -> Unit // f4m is skipped: the port has no f4m helper.
            else -> {
                val quality = entry.str("quality")
                val type = entry.str("type")
                formats += MediaFormat(
                    formatId = quality ?: formatId,
                    url = src,
                    ext = if (type?.startsWith("audio") == true) (ext.takeIf { it != "unknown_video" } ?: "mp3") else ext,
                    vcodec = if (type?.startsWith("audio") == true) MediaFormat.CODEC_NONE else null,
                    preference = qualityPreference(quality),
                )
            }
        }
    }
    if (formats.isEmpty()) {
        throw ExtractionError.NoFormats("The ppjson playlist had no playable source.")
    }
    val config = playlist.obj("config") ?: JsonObject(emptyMap())
    val live = config.str("streamType") in listOf("httpVideoLive", "httpAudioLive")
    val thumbnails = mutableListOf<Thumbnail>()
    for ((thumbnailId, value) in config.obj("poster").orEmpty()) {
        val poster = value as? JsonObject ?: continue
        val thumbnailUrl = poster.str("src") ?: continue
        thumbnails += Thumbnail(
            url = ExtractorUtils.urlOrNone(thumbnailUrl) ?: "https://www.ndr.de/$thumbnailUrl",
            id = poster.str("quality") ?: thumbnailId,
            preference = qualityPreference(poster.str("quality")),
        )
    }
    val subtitles = mutableListOf<SubtitleTrack>()
    for (element in config.array("tracks").orEmpty()) {
        val track = element as? JsonObject ?: continue
        val trackUrl = track.str("src") ?: continue
        subtitles += SubtitleTrack(
            language = track.str("srclang") ?: "de",
            formats = listOf(
                SubtitleFormat(
                    ext = "ttml",
                    url = if (trackUrl.startsWith("http")) trackUrl else "https://www.ndr.de/$trackUrl",
                ),
            ),
        )
    }
    val uploader = ppjson.obj("config")?.str("branding")?.takeIf { it != "-" }
    val uploadDate = ppjson.obj("config")?.str("publicationDate")?.take(8)
    return InfoDict(
        id = videoId,
        title = config.str("title"),
        isLive = live,
        uploader = uploader,
        uploadDate = uploadDate,
        duration = config.number("duration"),
        thumbnails = thumbnails,
        formats = formats,
        subtitles = subtitles,
        webpageUrl = url,
        extractor = "ndr:embed",
        extractorKey = "NDREmbedBase",
    )
}

/** Upstream `NDRBaseIE._extract_embed` for the ndr.de pages. */
private fun findEmbedUrl(webpage: String, displayId: String, url: String): String? {
    val embedUrl = ExtractorUtils.htmlSearchMeta(webpage, "embedURL")
        ?: ExtractorUtils.searchRegex(
            "\\bembedUrl[\"']\\s*:\\s*([\"'])([^\"']+)\\1",
            webpage,
            group = 2,
            default = null,
        )
        ?: ExtractorUtils.searchRegex(
            "\\bvar\\s*sophoraID\\s*=\\s*([\"'])([^\"']+)\\1",
            webpage,
            group = 2,
            default = null,
        )
        ?: return null
    if (Regex("^[a-z]+\\d+$").matches(embedUrl)) {
        val path = url.substringAfter("://").substringAfter('/', "")
        val prefix = Regex("(.+/)$displayId").find(path)?.groupValues?.get(1)
        val ndrId = prefix?.let {
            ExtractorUtils.searchRegex("$it([a-z]+\\d+)(?!\\.)\\b", webpage, default = null)
        }
        return if (ndrId != null) "ndr:$ndrId" else "https://www.ndr.de/info/$embedUrl-player.html"
    }
    return embedUrl
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
