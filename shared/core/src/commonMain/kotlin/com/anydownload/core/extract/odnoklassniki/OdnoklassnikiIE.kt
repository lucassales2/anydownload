/*
 * Odnoklassniki extractor — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `odnoklassniki.py`
 * from `yt_dlp/extractor/odnoklassniki.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `odnoklassniki.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the desktop `data-options` player walk (video/hls/dash/rtmp
 * formats, metadata, subtitles) and the mobile fallback. Restricted videos
 * fail typed; the paid-video notice is a typed failure; the embedded DASH
 * manifest parse and the USER_YOUTUBE transparent dispatch are simplified.
 * No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.odnoklassniki

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

/** Upstream `OdnoklassnikiIE`: the video pages. */
class OdnoklassnikiIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val desktopError = try {
            return extractDesktop(http, url)
        } catch (error: ExtractionError) {
            error
        }
        try {
            return extractMobile(http, url)
        } catch (error: Exception) {
            throw desktopError
        }
    }

    companion object {
        const val IE_KEY: String = "Odnoklassniki"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?(?:odnoklassniki|ok)\\.ru/" +
                "(?:video(?:embed)?/|webcam/(?:[^/]+/)?)(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractDesktop(http: ExtractorHttp, url: String): InfoDict {
    val match = OdnoklassnikiIE.VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
    val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
    val isEmbed = url.contains("videoembed")
    val mode = if (isEmbed) "videoembed" else "video"
    val webpage = http.downloadWebpage("https://ok.ru/$mode/$videoId")
    if (">Access to this video is restricted</div>" in webpage) {
        throw ExtractionError.LoginRequired("Access to this Odnoklassniki video is restricted.")
    }
    val playerRaw = ExtractorUtils.searchRegex(
        "data-options=([\"'])(\\{.+?$videoId.+?\\})\\1",
        webpage,
        flags = setOf(RegexOption.DOT_MATCHES_ALL),
        group = 2,
        default = null,
    ) ?: throw ExtractionError.Malformed("The Odnoklassniki page had no player options.")
    val cleaned = ExtractorUtils.unescapeHtml(playerRaw) ?: playerRaw
    val player = ExtractorUtils.parseJson(cleaned) as? JsonObject
        ?: throw ExtractionError.Malformed("The player options were not an object.")
    if (player.bool("isExternalPlayer") == true) {
        player.str("url")?.let {
            return InfoDict(
                id = videoId,
                redirectUrl = it,
                webpageUrl = url,
                extractor = "odnoklassniki",
                extractorKey = "Odnoklassniki",
            )
        }
    }
    val flashvars = player.obj("flashvars")
        ?: throw ExtractionError.Malformed("The player options had no flashvars.")
    val metadata = flashvars.str("metadata")?.let { ExtractorUtils.parseJson(it) as? JsonObject }
        ?: flashvars.str("metadataUrl")?.let { metadataUrl ->
            http.downloadJson(
                metadataUrl,
                method = "POST",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = flashvars.str("location")?.let { "st.location=$it" }?.encodeToByteArray(),
            ) as? JsonObject
        }
        ?: throw ExtractionError.Malformed("The player had no metadata.")
    val movie = metadata.obj("movie")
        ?: throw ExtractionError.Malformed("The metadata had no movie.")
    val provider = metadata.str("provider")
    val title = if (provider == "UPLOADED_ODKL") movie.str("title") else movie.str("title")
    if (provider == "USER_YOUTUBE") {
        movie.str("contentId")?.let {
            return InfoDict(
                id = videoId,
                title = title,
                redirectUrl = it,
                webpageUrl = url,
                extractor = "odnoklassniki",
                extractorKey = "Odnoklassniki",
            )
        }
    }
    val formats = mutableListOf<MediaFormat>()
    for (element in metadata.array("videos").orEmpty()) {
        val video = element as? JsonObject ?: continue
        val videoUrl = video.str("url") ?: continue
        formats += MediaFormat(
            formatId = video.str("name"),
            url = videoUrl,
            ext = "mp4",
            preference = formatTypeQuality(videoUrl),
        )
    }
    metadata.str("hlsManifestUrl")?.let { hlsUrl ->
        formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
    }
    metadata.str("ondemandHls")?.let { hlsUrl ->
        formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
    }
    metadata.str("ondemandDash")?.let { mpdUrl ->
        formats += MediaFormat(formatId = "dash", url = mpdUrl, ext = "mp4", protocol = "mpd")
    }
    metadata.str("metadataWebmUrl")?.let { webmUrl ->
        formats += MediaFormat(formatId = "webm", url = webmUrl, ext = "webm", protocol = "mpd")
    }
    metadata.str("hlsMasterPlaylistUrl")?.let { hlsUrl ->
        formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
    }
    metadata.str("rtmpUrl")?.let { rtmpUrl ->
        formats += MediaFormat(formatId = "rtmp", url = rtmpUrl, ext = "flv")
    }
    if (formats.isEmpty()) {
        if (metadata.obj("paymentInfo") != null) {
            throw ExtractionError.Unavailable("This video is paid; subscribe to download it.")
        }
        throw ExtractionError.NoFormats("The Odnoklassniki metadata returned no playable format.")
    }
    val subtitles = mutableListOf<SubtitleTrack>()
    for (element in movie.array("subtitleTracks").orEmpty()) {
        val sub = element as? JsonObject ?: continue
        val subUrl = sub.str("url") ?: continue
        subtitles += SubtitleTrack(
            language = sub.str("language") ?: "en",
            formats = listOf(SubtitleFormat(ext = "vtt", url = subUrl)),
        )
    }
    val author = metadata.obj("author")
    return InfoDict(
        id = videoId,
        title = title,
        duration = movie.number("duration"),
        uploader = author?.str("name"),
        uploadDate = ExtractorUtils.unifiedStrdate(
            ExtractorUtils.htmlSearchMeta(webpage, "ya:ovs:upload_date"),
        ),
        ageLimit = ExtractorUtils.htmlSearchMeta(webpage, "ya:ovs:adult")?.let { if (it == "true") 18 else 0 },
        thumbnails = listOfNotNull(movie.str("poster")?.let { Thumbnail(url = it) }),
        formats = formats,
        subtitles = subtitles,
        webpageUrl = url,
        extractor = "odnoklassniki",
        extractorKey = "Odnoklassniki",
    )
}

private suspend fun extractMobile(http: ExtractorHttp, url: String): InfoDict {
    val videoId = OdnoklassnikiIE.VALID_URL.find(url)?.groups?.get("id")?.value
        ?: throw ExtractionError.UnsupportedUrl()
    val webpage = http.downloadWebpage("https://m.ok.ru/video/$videoId")
    val jsonRaw = ExtractorUtils.searchRegex(
        "data-video=\"(.+?)\"",
        webpage,
        flags = setOf(RegexOption.DOT_MATCHES_ALL),
        default = null,
    )
        ?: throw ExtractionError.Malformed("The mobile page had no video data.")
    val data = ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(jsonRaw)) as? JsonObject
        ?: throw ExtractionError.Malformed("The mobile video data was not an object.")
    val videoUrl = data.str("videoSrc")
        ?: throw ExtractionError.NoFormats("The mobile video data had no source.")
    return InfoDict(
        id = videoId,
        title = data.str("videoName"),
        duration = data.number("videoDuration")?.div(1000),
        thumbnails = listOfNotNull(data.str("videoPosterSrc")?.let { Thumbnail(url = it) }),
        formats = listOf(MediaFormat(formatId = "mobile", url = videoUrl, ext = "mp4")),
        webpageUrl = url,
        extractor = "odnoklassniki",
        extractorKey = "Odnoklassniki",
    )
}

private val QUALITY_ORDER = listOf("4", "0", "1", "2", "3", "5", "6", "7")

private fun formatTypeQuality(url: String): Int? {
    val match = Regex("\\btype[/=](\\d)").find(url) ?: return null
    val index = QUALITY_ORDER.indexOf(match.groupValues[1])
    return if (index >= 0) index + 1 else null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let {
        when (it.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
