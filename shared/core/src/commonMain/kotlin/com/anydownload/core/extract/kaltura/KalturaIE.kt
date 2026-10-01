/*
 * Kaltura extractor — AnyDownload
 *
 * Kotlin translation of the public player subset of `kaltura.py` from
 * `yt_dlp/extractor/kaltura.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `kaltura.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `kaltura:<partner>:<entry>[:<player>]` keyword, the index.php /
 * mwEmbedFrame URL forms, the multirequest API (widget session, baseentry,
 * flavor assets, captions), the iframe package data path, the playlist
 * hand-off, and the per-video format/subtitle mapping. The widget session key
 * (`ks`) is fetched per extraction and used only in runtime URLs; no key,
 * cookie, or signed media URL is stored. The `isOriginal` URL validity probe,
 * WVM DRM assets, F4M/ISM, and the smuggled `service_url` are not translated.
 */
package com.anydownload.core.extract.kaltura

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Upstream `KalturaIE`: the public player forms. */
class KalturaIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var partnerId = match.groups["partnerId"]?.value
        var entryId = match.groups["id"]?.value
        var playerType = match.groups["playerType"]?.value
        var ks: String? = null
        var captions: JsonObject? = null
        var info: JsonObject
        var flavorAssets: JsonArray

        if (partnerId != null && entryId != null) {
            val result = getVideoInfo(entryId, partnerId, playerType ?: "html5")
            info = result.info
            flavorAssets = result.flavorAssets
            captions = result.captions
        } else {
            val path = match.groups["path"]?.value
            val query = match.groups["query"]?.value
            if (path == null && query == null) {
                throw ExtractionError.UnsupportedUrl("Invalid Kaltura URL.")
            }
            val params = mutableMapOf<String, String>()
            if (query != null) {
                for (pair in query.split('&')) {
                    if (pair.isEmpty()) continue
                    params[percentDecode(pair.substringBefore('='))] = percentDecode(pair.substringAfter('=', ""))
                }
            }
            if (path != null) {
                val segments = path.split('/')
                var index = 0
                while (index + 1 < segments.size) {
                    params[segments[index]] = segments[index + 1]
                    index += 2
                }
            }
            partnerId = params["wid"]?.removePrefix("_") ?: params["p"] ?: params["partner_id"]
                ?: throw ExtractionError.UnsupportedUrl("Invalid Kaltura URL.")
            if (params["entry_id"] != null) {
                entryId = params["entry_id"]
                val result = getVideoInfo(entryId!!, partnerId!!, playerType ?: "html5")
                info = result.info
                flavorAssets = result.flavorAssets
                captions = result.captions
            } else if (params["uiconf_id"] != null && params["flashvars[referenceId]"] != null) {
                val referenceId = params["flashvars[referenceId]"]!!
                val webpage = http.downloadWebpage(url)
                val entryData = (extractJsonElement(webpage, IFRAME_PACKAGE_DATA) as? JsonObject)
                    ?.obj("entryResult")
                    ?: throw ExtractionError.Malformed("The Kaltura iframe package data was not found.")
                info = entryData.obj("meta")
                    ?: throw ExtractionError.Malformed("The Kaltura iframe data had no meta.")
                flavorAssets = entryData.obj("contextData")?.array("flavorAssets") ?: JsonArray(emptyList())
                entryId = info.str("id")
                if (entryId != null) {
                    runCatching { getVideoInfo(entryId, partnerId, playerType ?: "html5") }
                        .getOrNull()?.let { refreshed ->
                            info = refreshed.info
                            flavorAssets = refreshed.flavorAssets
                            captions = refreshed.captions
                        }
                }
            } else if (params["uiconf_id"] != null && params["flashvars[playlistAPI.kpl0Id]"] != null) {
                val playlistId = params["flashvars[playlistAPI.kpl0Id]"]!!
                val webpage = http.downloadWebpage(url)
                val playlistData = (extractJsonElement(webpage, IFRAME_PACKAGE_DATA) as? JsonObject)
                    ?.obj("playlistResult")
                    ?: throw ExtractionError.Malformed("The Kaltura playlist data was not found.")
                val playlist = playlistData.obj(playlistId)
                val entries = playlist?.array("items").orEmpty().mapNotNull { element ->
                    val id = (element as? JsonObject)?.str("id") ?: return@mapNotNull null
                    InfoEntry(id = id, url = "kaltura:$partnerId:$id:${playerType ?: "html5"}")
                }
                return InfoDict(
                    id = playlistId,
                    title = playlist?.str("name"),
                    entries = entries,
                    webpageUrl = url,
                    extractor = "kaltura",
                    extractorKey = IE_KEY,
                )
            } else {
                throw ExtractionError.UnsupportedUrl("Invalid Kaltura URL.")
            }
            ks = params["flashvars[ks]"]
        }
        return perVideoExtract(entryId!!, info, ks, flavorAssets, captions)
    }

    /** Upstream `_kaltura_api_call`. */
    private suspend fun kalturaApiCall(
        videoId: String,
        actions: List<JsonObject>,
        serviceUrl: String = SERVICE_URL,
    ): JsonArray {
        val body = buildJsonObject {
            actions.first().forEach { (key, value) -> put(key, value) }
            actions.drop(1).forEachIndexed { index, action ->
                put((index + 1).toString(), action)
            }
        }.toString().encodeToByteArray()
        val json = http.downloadJson(
            serviceUrl + SERVICE_BASE,
            method = com.anydownload.core.platform.HttpMethods.POST,
            headers = mapOf("content-type" to "application/json"),
            body = body,
        )
        val data = json as? JsonArray
            ?: throw ExtractionError.Malformed("The Kaltura API returned no array.")
        for ((index, status) in data.withIndex()) {
            val statusObject = status as? JsonObject ?: continue
            if (statusObject.str("objectType") == "KalturaAPIException") {
                throw ExtractionError.Unavailable(
                    "Kaltura said: ${statusObject.str("message") ?: "unknown error"} ($index)",
                )
            }
        }
        return data
    }

    /** Upstream `_get_video_info_html5` / `_get_video_info_kwidget`. */
    private suspend fun getVideoInfo(
        videoId: String,
        partnerId: String,
        playerType: String,
        serviceUrl: String = SERVICE_URL,
    ): KalturaVideoInfo {
        val widgetId = if ('_' in partnerId) partnerId else "_$partnerId"
        val header = buildJsonObject {
            put("apiVersion", "3.3.0")
            put("clientTag", "html5:v3.1.0")
            put("format", 1)
            put("ks", "")
            put("partnerId", partnerId)
        }
        val session = buildJsonObject {
            put("expiry", 86400)
            put("service", "session")
            put("action", "startWidgetSession")
            put("widgetId", widgetId)
        }
        val infoAction = buildJsonObject {
            put("action", "list")
            put("service", "baseentry")
            put("ks", "{1:result:ks}")
            put("filter", buildJsonObject { put("redirectFromEntryId", videoId) })
            put(
                "responseProfile",
                buildJsonObject {
                    put("type", 1)
                    put("fields", "createdAt,dataUrl,duration,name,plays,thumbnailUrl,userId")
                },
            )
        }
        val flavors = buildJsonObject {
            put("action", "getbyentryid")
            put("entryId", videoId)
            put("service", "flavorAsset")
            put("ks", "{1:result:ks}")
        }
        val captions = buildJsonObject {
            put("action", "list")
            put("filter:entryIdEqual", videoId)
            put("service", "caption_captionasset")
            put("ks", "{1:result:ks}")
        }
        val data = kalturaApiCall(videoId, listOf(header, session, infoAction, flavors, captions), serviceUrl)
        val info = (data.getOrNull(1) as? JsonObject)?.array("objects")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Kaltura API returned no entry info.")
        val flavorAssets = data.getOrNull(2) as? JsonArray
            ?: data.getOrNull(2)?.let { (it as? JsonObject)?.array("objects") } ?: JsonArray(emptyList())
        val captionData = data.getOrNull(3) as? JsonObject
            ?: data.getOrNull(3)?.let { (it as? JsonArray)?.firstOrNull() as? JsonObject }
        return KalturaVideoInfo(info, flavorAssets, captionData)
    }

    /** Upstream `_per_video_extract` without the smuggled referrer. */
    private fun perVideoExtract(
        entryId: String,
        info: JsonObject,
        ks: String?,
        flavorAssets: JsonArray,
        captions: JsonObject?,
    ): InfoDict {
        var dataUrl = info.str("dataUrl") ?: throw ExtractionError.Malformed("The Kaltura entry had no dataUrl.")
        if ("/flvclipper/" in dataUrl) {
            dataUrl = dataUrl.replace(Regex("/flvclipper/.*"), "/serveFlavor")
        }

        fun signUrl(unsignedUrl: String): String =
            if (ks != null) "$unsignedUrl/ks/$ks" else unsignedUrl

        val formats = mutableListOf<MediaFormat>()
        for (element in flavorAssets) {
            val flavor = element as? JsonObject ?: continue
            if (flavor.number("status")?.toInt() != 2) continue
            if (flavor.str("fileExt") == "chun") continue
            if (flavor.str("fileExt") == "wvm") continue // DRM; not translated.
            val ext = flavor.str("fileExt")
                ?: if (flavor.str("containerFormat") == "qt") "mov" else "mp4"
            val flavorId = flavor.str("id") ?: continue
            val bitrate = flavor.number("bitrate")?.toLong()
            formats += MediaFormat(
                formatId = "$ext-$bitrate",
                url = signUrl("$dataUrl/flavorId/$flavorId"),
                ext = ext,
                tbr = bitrate?.toDouble(),
                fps = flavor.number("frameRate"),
                filesizeApprox = flavor.number("size")?.div(1024)?.toLong(),
                container = flavor.str("containerFormat"),
                vcodec = if (flavor["videoCodecId"] == null && flavor.number("frameRate") == 0.0) {
                    MediaFormat.CODEC_NONE
                } else {
                    flavor.str("videoCodecId")
                },
                height = flavor.number("height")?.toLong(),
                width = flavor.number("width")?.toLong(),
            )
        }
        if ("/playManifest/" in dataUrl) {
            val m3u8Url = signUrl(dataUrl.replace("format/url", "format/applehttp"))
            formats += MediaFormat(
                formatId = "hls",
                url = m3u8Url,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in captions?.array("objects").orEmpty()) {
            val caption = element as? JsonObject ?: continue
            if (caption.number("status")?.toInt() != 2) continue
            val captionId = caption.str("id") ?: continue
            val language = caption.str("languageCode") ?: caption.str("language") ?: "und"
            val captionFormat = caption.number("format")?.toInt()
            val ext = caption.str("fileExt") ?: CAPTION_TYPES[captionFormat] ?: "ttml"
            subtitles += SubtitleTrack(
                language = language,
                formats = listOf(
                    SubtitleFormat(
                        ext = ext,
                        url = "$SERVICE_URL/api_v3/service/caption_captionasset/action/serve/captionAssetId/$captionId",
                    ),
                ),
            )
        }

        return InfoDict(
            id = entryId,
            title = info.str("name"),
            description = cleanHtml(info.str("description")),
            duration = info.number("duration"),
            uploadDate = info.number("createdAt")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            uploader = info.str("userId"),
            viewCount = info.number("plays")?.toLong(),
            thumbnails = ExtractorUtils.urlOrNone(info.str("thumbnailUrl"))
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = null,
            extractor = "kaltura",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Kaltura"
        const val SERVICE_URL: String = "https://cdnapisec.kaltura.com"
        const val SERVICE_BASE: String = "/api_v3/service/multirequest"

        private val CAPTION_TYPES = mapOf(1 to "srt", 2 to "ttml", 3 to "vtt")
        private val IFRAME_PACKAGE_DATA = Regex("window\\.kalturaIframePackageData\\s*=")

        val VALID_URL: Regex = Regex(
            "(?:kaltura:(?<partnerId>\\w+):(?<id>\\w+)(?::(?<playerType>\\w+))?|" +
                "https?://(?:(?:www|cdnapi(?:sec)?)\\.)?kaltura\\.com(?::\\d+)?/" +
                "(?:(?:index\\.php/(?:kwidget|extwidget/preview)|" +
                "html5/html5lib/[^/]+/mwEmbedFrame\\.php))" +
                "(?:/(?<path>[^?]+))?(?:\\?(?<query>.*))?)",
        )
    }
}

/** The mapped multirequest response. */
private data class KalturaVideoInfo(
    val info: JsonObject,
    val flavorAssets: JsonArray,
    val captions: JsonObject?,
)

// ------------------------------------------------------------------ helpers

/** Upstream `_search_json` subset: balanced JSON after [marker]. */
private fun extractJsonElement(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    val opening = html.getOrNull(index) ?: return null
    if (opening != '{' && opening != '[') return null
    var depth = 0
    var inString = false
    var quote = ' '
    var escaped = false
    for (position in index until html.length) {
        val character = html[position]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> inString = false
            }
            continue
        }
        when (character) {
            '"', '\'' -> {
                inString = true
                quote = character
            }

            '{', '[' -> depth++
            '}', ']' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(html.substring(index, position + 1))
                }
            }
        }
    }
    return null
}

private fun percentDecode(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                append(code.toChar())
                index += 3
                continue
            }
        }
        append(character)
        index++
    }
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    val withoutTags = text.replace(Regex("<[^>]*>"), " ")
    return ExtractorUtils.unescapeHtml(withoutTags)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
