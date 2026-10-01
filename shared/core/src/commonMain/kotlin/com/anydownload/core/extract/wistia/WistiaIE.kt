/*
 * Wistia extractors — AnyDownload
 *
 * Kotlin translation of the public embed API subset of `wistia.py` from
 * `yt_dlp/extractor/wistia.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `wistia.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `fast.wistia.net/embed` config APIs for medias,
 * playlists, and channels (assets to formats/thumbnails, captions,
 * metadata), including the channel base64 JSONP fallback. The password
 * option is a typed failure and the HEAD extension probe is simplified. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.wistia

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

private const val EMBED_BASE_URL = "http://fast.wistia.net/embed/"

private fun validUrlBase(): String = "https?://(?:\\w+\\.)?wistia\\.(?:net|com)/(?:embed/)?"

/** Upstream `WistiaIE`: a single media. */
class WistiaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val embedConfig = downloadEmbedConfig(http, "medias", videoId, url)
        return extractMedia(embedConfig)
    }

    companion object {
        const val IE_KEY: String = "Wistia"

        val VALID_URL: Regex = Regex(
            "(?:wistia:|${validUrlBase()}(?:iframe|medias)/)(?<id>[a-z0-9]{10})",
        )
    }
}

/** Upstream `WistiaPlaylistIE`: a playlist. */
class WistiaPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlist = downloadEmbedConfig(http, "playlists", playlistId, url) as? JsonArray
            ?: throw ExtractionError.Malformed("The playlist config was not a list.")
        val entries = mutableListOf<InfoEntry>()
        for (element in (playlist.firstOrNull() as? JsonObject)?.array("medias").orEmpty()) {
            val media = element as? JsonObject ?: continue
            val embedConfig = media.obj("embed_config") ?: continue
            val mediaInfo = extractMedia(embedConfig)
            mediaInfo.id?.let { entries += InfoEntry(id = it, title = mediaInfo.title, url = "wistia:$it") }
        }
        return InfoDict(
            id = playlistId,
            entries = entries,
            webpageUrl = url,
            extractor = "wistia:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "WistiaPlaylist"

        val VALID_URL: Regex = Regex("${validUrlBase()}playlists/(?<id>[a-z0-9]{10})")
    }
}

/** Upstream `WistiaChannelIE`: a channel. */
class WistiaChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = try {
            downloadEmbedConfig(http, "channel", channelId, url)
        } catch (error: ExtractionError) {
            val webpage = http.downloadWebpage("https://fast.wistia.net/embed/channel/$channelId")
            val raw = ExtractorUtils.searchRegex(
                "wchanneljsonp-$channelId'\\]\\s*=[^\"]*\"([A-Za-z0-9=/]*)",
                webpage,
                default = null,
            ) ?: throw ExtractionError.Malformed("The channel page had no JSONP data.")
            ExtractorUtils.parseJson(decodeUrlSafeBase64(raw)) as? JsonObject
                ?: throw ExtractionError.Malformed("The channel JSONP was not an object.")
        }
        val dataObj = data as? JsonObject
            ?: throw ExtractionError.Malformed("The channel config was not an object.")
        val series = dataObj.array("series")?.firstOrNull() as? JsonObject
        val entries = mutableListOf<InfoEntry>()
        for (section in series?.array("sections").orEmpty()) {
            val sectionObj = section as? JsonObject ?: continue
            for (listKey in listOf("videos", "episodes")) {
                for (element in sectionObj.array(listKey).orEmpty()) {
                    val video = element as? JsonObject ?: continue
                    val hashedId = video.str("hashedId") ?: continue
                    entries += InfoEntry(id = hashedId, title = video.str("name"), url = "wistia:$hashedId")
                }
            }
        }
        return InfoDict(
            id = channelId,
            title = series?.str("title"),
            description = series?.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "wistia:channel",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "WistiaChannel"

        val VALID_URL: Regex = Regex("(?:wistiachannel:|${validUrlBase()}channel/)(?<id>[a-z0-9]{10})")
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun downloadEmbedConfig(
    http: ExtractorHttp,
    configType: String,
    configId: String,
    referer: String,
): JsonElement {
    val baseUrl = "$EMBED_BASE_URL$configType/$configId"
    val headers = if (referer.startsWith("http")) mapOf("Referer" to referer) else emptyMap()
    val embedConfig = try {
        http.downloadJson("$baseUrl.json", headers = headers)
    } catch (error: ExtractionError) {
        throw error
    }
    if (embedConfig is JsonObject) {
        embedConfig.str("error")?.let {
            throw ExtractionError.Unavailable("Error while getting the playlist: $it")
        }
        val passwordProtected = embedConfig.obj("media")
            ?.obj("embed_options")?.obj("plugin")?.obj("passwordProtectedVideo")?.str("on")
            ?: embedConfig.obj("media")?.obj("embedOptions")?.obj("plugin")
                ?.obj("passwordProtectedVideo")?.str("on")
        if (passwordProtected == "true") {
            throw ExtractionError.Unavailable(
                "This Wistia content is password-protected and the port has no video-password option.",
            )
        }
    }
    return embedConfig
}

private fun extractMedia(embedConfig: JsonElement): InfoDict {
    val data = (embedConfig as? JsonObject)?.obj("media")
        ?: throw ExtractionError.Malformed("The embed config had no media.")
    val videoId = data.str("hashedId")
        ?: throw ExtractionError.Malformed("The media had no hashed id.")
    val formats = mutableListOf<MediaFormat>()
    val thumbnails = mutableListOf<Thumbnail>()
    for (element in data.array("assets").orEmpty()) {
        val asset = element as? JsonObject ?: continue
        val assetUrl = asset.str("url") ?: continue
        val status = asset.number("status")
        val type = asset.str("type")
        if ((status != null && status != 2.0) || type == "preview" || type == "storyboard") continue
        if (type == "still" || type == "still_image") {
            thumbnails += Thumbnail(
                url = assetUrl.replace(".bin", "." + getRealExt(assetUrl)),
                width = asset.number("width")?.toLong(),
                height = asset.number("height")?.toLong(),
            )
            continue
        }
        val assetExt = asset.str("ext") ?: getRealExt(assetUrl)
        val displayName = asset.str("display_name")
        var formatId = type
        if (type != null && type.endsWith("_video") && displayName != null) {
            formatId = "${type.dropLast(6)}-$displayName"
        }
        val base = MediaFormat(
            formatId = formatId,
            url = assetUrl,
            tbr = asset.number("bitrate"),
            preference = if (type == "original") 1 else null,
        )
        if (displayName == "Audio") {
            formats += base.copy(vcodec = MediaFormat.CODEC_NONE)
        } else {
            val withSize = base.copy(
                width = asset.number("width")?.toLong(),
                height = asset.number("height")?.toLong(),
                vcodec = asset.str("codec"),
            )
            if (asset.str("container") == "m3u8" || assetExt == "m3u8") {
                formats += withSize.copy(
                    formatId = withSize.formatId?.replace("hls-", "ts-"),
                    url = withSize.url?.replace(".bin", ".ts"),
                    ext = "ts",
                )
                formats += withSize.copy(ext = "mp4", protocol = "m3u8_native")
            } else {
                formats += withSize.copy(ext = assetExt, container = asset.str("container"))
            }
        }
    }
    val subtitles = mutableListOf<SubtitleTrack>()
    for (element in data.array("captions").orEmpty()) {
        val caption = element as? JsonObject ?: continue
        val language = caption.str("language") ?: continue
        subtitles += SubtitleTrack(
            language = language,
            formats = listOf(
                SubtitleFormat(
                    ext = "vtt",
                    url = "${EMBED_BASE_URL}captions/$videoId.vtt?language=$language",
                ),
            ),
        )
    }
    return InfoDict(
        id = videoId,
        title = data.str("name"),
        description = data.str("seoDescription"),
        duration = data.number("duration"),
        uploadDate = data.number("createdAt")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
        thumbnails = thumbnails,
        formats = formats,
        subtitles = subtitles,
        webpageUrl = "$EMBED_BASE_URL$videoId",
        extractor = "wistia",
        extractorKey = "Wistia",
    )
}

private fun getRealExt(url: String): String {
    val ext = ExtractorUtils.determineExt(url, "bin")
    return if (ext == "mov") "mp4" else ext
}

private fun decodeUrlSafeBase64(value: String): String {
    val padded = value + "=".repeat((4 - value.length % 4) % 4)
    val decoded = runCatching {
        kotlin.io.encoding.Base64.UrlSafe.decode(padded).decodeToString()
    }.getOrDefault(padded)
    return decoded.replace("+", " ").replace("%2B", "+")
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
