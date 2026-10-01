/*
 * Nexx extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `nexx.py` from
 * `yt_dlp/extractor/nexx.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nexx.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `arc.nexx.cloud/api/video/<id>.json` metadata plus the
 * azure/free/3q format URL builders (HLS/DASH/progressive) and the caption
 * URLs. Videos absent from the arc endpoint need a session-init request
 * token, which the port does not translate: they fail typed. Inline caption
 * `data` blocks, ISM, alt-title/season fields, and the CDN-shield
 * preferences are not carried. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.nexx

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

/** Upstream `NexxIE`: the video API. */
class NexxIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = try {
            http.downloadJson("https://arc.nexx.cloud/api/video/$videoId.json") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val video = findVideo(response?.get("result"), videoId)
            ?: throw ExtractionError.Unavailable(
                "This Nexx video is not on the public arc endpoint and needs a session-init " +
                    "request token; the port does not translate that flow.",
            )
        val general = video.obj("general") ?: throw ExtractionError.Malformed("The video had no general data.")
        val streamData = video.obj("streamdata") ?: throw ExtractionError.Malformed("The video had no stream data.")
        val cdn = streamData.str("cdnType")
        val formats = when (cdn) {
            "azure" -> azureFormats(streamData, videoId)
            "free" -> freeFormats(streamData, videoId)
            "3q" -> threeQFormats(streamData, videoId)
            else -> emptyList()
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Nexx CDN type '${cdn ?: "unknown"}' produced no format.")
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in video.array("captiondata").orEmpty()) {
            val caption = element as? JsonObject ?: continue
            val url = caption.str("url") ?: continue
            subtitles += SubtitleTrack(
                language = caption.str("language") ?: "en",
                name = caption.str("language_long") ?: caption.str("title"),
                formats = listOf(SubtitleFormat(ext = caption.str("format") ?: "srt", url = url)),
            )
        }
        return InfoDict(
            id = videoId,
            title = general.str("title"),
            description = general.str("description"),
            duration = ExtractorUtils.parseDuration(general.str("runtime"))?.toDouble(),
            uploader = general.str("studio") ?: general.str("studio_adref"),
            uploadDate = general.number("uploaded")?.let {
                ExtractorUtils.epochSecondsToDate(it.toLong())
            },
            thumbnails = listOfNotNull(
                video.obj("imagedata")?.str("thumb")?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "nexx",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Nexx"

        val VALID_URL: Regex = Regex(
            "(?:(?:https?://api\\.nexx(?:\\.cloud|cdn\\.com)/v3(?:\\.\\d)?/(?<domainId>\\d+)/videos/byid/|" +
                "nexx:(?:(?<domainIdS>\\d+):)?|https?://arc\\.nexx\\.cloud/api/video/)" +
                ")(?<id>\\d+)",
        )
    }
}

/** Upstream `NexxEmbedIE`: an embed page that points at a Nexx API URL. */
class NexxEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val embedId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val domainId = ExtractorUtils.searchRegex(
            "<script\\b[^>]+\\bsrc=[\"'](?:https?:)?//(?:require|arc)\\.nexx(?:\\.cloud|cdn\\.com)/(?:sdk/)?(\\d+)",
            webpage,
            default = null,
        )
        val videoId = ExtractorUtils.searchRegex(
            "(?is)onPLAYReady.+?_play\\.(?:init|(?:control\\.)?addPlayer)\\s*\\(.+?\\s*,\\s*[\"']?(\\d+)",
            webpage,
            default = null,
        )
        if (domainId == null || videoId == null) {
            throw ExtractionError.Unavailable("The Nexx embed page had no player configuration.")
        }
        return InfoDict(
            id = embedId,
            redirectUrl = "https://api.nexx.cloud/v3/$domainId/videos/byid/$videoId",
            webpageUrl = url,
            extractor = "nexx:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NexxEmbed"

        val VALID_URL: Regex = Regex(
            "https?://embed\\.nexx(?:\\.cloud|cdn\\.com)/\\d+/(?:video/)?(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun findVideo(result: kotlinx.serialization.json.JsonElement?, videoId: String): JsonObject? {
    return when (result) {
        is JsonObject -> result
        is JsonArray -> result.mapNotNull { it as? JsonObject }
            .firstOrNull { it.obj("general")?.primitiveText("ID") == videoId }
        else -> null
    }
}

private fun azureFormats(streamData: JsonObject, videoId: String): List<MediaFormat> {
    val locator = streamData.str("azureLocator") ?: return emptyList()
    val base = cdnShieldBase(streamData) ?: "http://nx-p00.akamaized.net/"
    val manifest = "${base}${locator}/${videoId}_src.ism/Manifest"
    val formats = mutableListOf(
        MediaFormat(formatId = "azure-hls", url = "$manifest(format=m3u8-aapl)", ext = "mp4", protocol = "m3u8_native"),
        MediaFormat(formatId = "azure-dash", url = "$manifest(format=mpd-time-csf)", ext = "mp4", protocol = "mpd"),
    )
    val progressiveBase = cdnShieldBase(streamData, "Prog", static = true) ?: base
    for (entry in (streamData.str("azureFileDistribution") ?: "").split(',')) {
        val parts = entry.split(':')
        if (parts.size != 2) continue
        val tbr = parts[0].toDoubleOrNull() ?: continue
        val widthHeight = parts[1].split('x')
        formats += MediaFormat(
            formatId = "azure-http-${parts[0]}",
            url = "${progressiveBase}${locator}/${videoId}_src_${parts[1]}_${parts[0]}.mp4",
            tbr = tbr,
            width = widthHeight.getOrNull(0)?.toLongOrNull(),
            height = widthHeight.getOrNull(1)?.toLongOrNull(),
        )
    }
    return formats
}

private fun freeFormats(streamData: JsonObject, videoId: String): List<MediaFormat> {
    val hash = streamData.str("hash") ?: return emptyList()
    val provider = streamData.str("cdnProvider") ?: return emptyList()
    val formats = mutableListOf<MediaFormat>()
    if (provider == "ak") {
        var prefix = "http://${streamData.str("originalDomain") ?: ""}"
        if (streamData.number("applyFolderHierarchy")?.toInt() == 1) {
            val reversed = videoId.padStart(4, '0').reversed()
            prefix += "/${reversed.substring(0, 2)}/${reversed.substring(2, 4)}"
        }
        prefix += "/$videoId/${hash}_"
        val distribution = streamData.str("azureFileDistribution") ?: ""
        var master = "$prefix,"
        for (entry in distribution.split(',')) {
            val parts = entry.split(':')
            if (parts.size < 2) continue
            val structure = if (streamData.number("applyAzureStructure")?.toInt() == 1) "_${parts[0]}" else ""
            master += parts[1] + structure + ","
        }
        master += ".mp4.csmil/master.m3u8"
        formats += MediaFormat(formatId = "free-hls", url = master, ext = "mp4", protocol = "m3u8_native")
    } else if (provider == "ce") {
        val parts = (streamData.str("azureFileDistribution") ?: "").split(',')
        for (entry in parts) {
            val pieces = entry.split(':')
            if (pieces.size < 2) continue
            val tbr = pieces[0].toDoubleOrNull() ?: continue
            val structure = if (streamData.number("applyAzureStructure")?.toInt() == 1) "_${pieces[0]}" else ""
            val widthHeight = pieces[1].split('x')
            formats += MediaFormat(
                formatId = "free-http-${pieces[0]}",
                url = "http://${streamData.str("cdnPathHTTP") ?: ""}/$videoId/${hash}$structure.mp4",
                tbr = tbr,
                width = widthHeight.getOrNull(0)?.toLongOrNull(),
                height = widthHeight.getOrNull(1)?.toLongOrNull(),
            )
        }
    }
    return formats
}

private fun threeQFormats(streamData: JsonObject, videoId: String): List<MediaFormat> {
    val account = streamData.str("qAccount") ?: return emptyList()
    val prefix = streamData.str("qPrefix") ?: ""
    val locator = streamData.str("qLocator") ?: ""
    val hash = streamData.str("qHash") ?: return emptyList()
    val base = "http://sdn-global-streaming-cache.3qsdn.com/"
    return listOf(
        MediaFormat(
            formatId = "3q-hls",
            url = "${base}${account}/files/${prefix}/${locator}/${account}-${hash}.ism/manifest.m3u8",
            ext = "mp4",
            protocol = "m3u8_native",
        ),
        MediaFormat(
            formatId = "3q-dash",
            url = "${base}${account}/files/${prefix}/${locator}/${account}-${hash}.ism/manifest.mpd",
            ext = "mp4",
            protocol = "mpd",
        ),
    )
}

private fun cdnShieldBase(streamData: JsonObject, shieldType: String = "", static: Boolean = false): String? {
    for (secure in listOf("", "s")) {
        val key = "cdnShield${shieldType}HTTP${secure.uppercase()}"
        val shield = streamData.str(key) ?: continue
        return "http${secure}://$shield"
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
