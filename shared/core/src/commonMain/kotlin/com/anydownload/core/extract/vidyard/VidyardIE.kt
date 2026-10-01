/*
 * Vidyard extractor — AnyDownload
 *
 * Kotlin translation of the public API subset of `vidyard.py` from
 * `yt_dlp/extractor/vidyard.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vidyard.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the player JSON (`play.vidyard.com/player/<id>.json`), the HLS
 * master/variant sources plus the http source profiles, the direct VTT
 * captions, and the additional metadata (title, duration, thumbnails,
 * video-section chapters). Multi-chapter players become media items.
 * Display-id and tags fields the port does not carry are dropped. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.vidyard

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `VidyardIE`: the player JSON. */
class VidyardIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val payload = (http.downloadJson("https://play.vidyard.com/player/$videoId.json") as? JsonObject)
            ?.obj("payload")
            ?: throw ExtractionError.Malformed("The Vidyard player API had no payload.")
        val chapters = payload.array("chapters").orEmpty().mapNotNull { it as? JsonObject }
        if (chapters.isEmpty()) {
            throw ExtractionError.Malformed("The Vidyard player had no chapters.")
        }
        if (chapters.size == 1) {
            return processChapter(http, chapters.single())
        }
        return InfoDict(
            id = payload.primitiveText("playerUuid") ?: videoId,
            title = payload.str("name"),
            media = chapters.map { chapter ->
                val processed = processChapter(http, chapter)
                InfoMedia(
                    mediaId = processed.id ?: videoId,
                    title = processed.title,
                    duration = processed.duration,
                    thumbnails = processed.thumbnails,
                    formats = processed.formats,
                )
            },
            webpageUrl = url,
            extractor = "vidyard",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Vidyard"

        val VALID_URL: Regex = Regex(
            "https?://(?:" +
                "[\\w-]+(?:\\.hubs)?\\.vidyard\\.com/watch/|" +
                "(?:embed|share)\\.vidyard\\.com/share/|" +
                "play\\.vidyard\\.com/(?:player/)?" +
                ")(?<id>[\\w-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun processChapter(http: ExtractorHttp, chapter: JsonObject): InfoDict {
    val facadeUuid = chapter.str("facadeUuid")
    val formats = mutableListOf<MediaFormat>()
    val subtitles = mutableListOf<SubtitleTrack>()
    val sources = chapter["sources"]
    if (sources is JsonObject) {
        val hlsList = sources["hls"] as? JsonArray
        val auto = hlsList?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("profile") == "auto" }?.str("url")
        val hlsUrls = if (auto != null) listOf(auto) else {
            hlsList.orEmpty().mapNotNull { (it as? JsonObject)?.str("url") }
        }
        for (hlsUrl in hlsUrls) {
            formats += MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native")
        }
        for ((sourceType, value) in sources) {
            if (sourceType == "hls") continue
            val list = value as? JsonArray ?: continue
            for (element in list) {
                val source = element as? JsonObject ?: continue
                val sourceUrl = source.str("url") ?: continue
                val profile = source.str("profile")
                formats += MediaFormat(
                    formatId = listOfNotNull("http", sourceType, profile).joinToString("-"),
                    url = sourceUrl,
                    ext = mimetypeExt(source.str("mimeType")),
                    width = resolution(profile)?.first,
                    height = resolution(profile)?.second,
                )
            }
        }
    }
    val captions = chapter.array("captions").orEmpty().mapNotNull { it as? JsonObject }
    for (caption in captions) {
        val vttUrl = caption.str("vttUrl") ?: continue
        subtitles += SubtitleTrack(
            language = caption.str("language") ?: "und",
            name = caption.str("name"),
            formats = listOf(SubtitleFormat(ext = "vtt", url = vttUrl)),
        )
    }
    val additional = if (facadeUuid != null) {
        try {
            http.downloadJson("https://play.vidyard.com/video/$facadeUuid") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
    } else {
        null
    }
    val duration = chapter.number("milliseconds")?.div(1000)
        ?: chapter.number("seconds")
        ?: additional?.number("seconds")
    val thumbnails = mutableListOf<Thumbnail>()
    additional?.str("thumbnailUrl")?.let { thumbnails += Thumbnail(url = it) }
    val thumbnailUrls = chapter.obj("thumbnailUrls")
    if (thumbnailUrls != null) {
        for (key in listOf("small", "normal")) {
            thumbnailUrls.obj(key)?.str("url")?.let { thumbnails += Thumbnail(url = it) }
        }
    }
    val chapters = additional?.array("videoSections").orEmpty().mapNotNull { element ->
        val section = element as? JsonObject ?: return@mapNotNull null
        val millis = section.number("milliseconds") ?: return@mapNotNull null
        Chapter(title = section.str("title"), startTime = millis / 1000)
    }
    return InfoDict(
        id = facadeUuid,
        title = chapter.str("name") ?: additional?.str("name"),
        description = chapter.str("description")?.let { ExtractorUtils.unescapeHtml(it) },
        duration = duration,
        thumbnails = thumbnails,
        chapters = chapters,
        formats = formats,
        subtitles = subtitles,
        extractor = "vidyard",
        extractorKey = "Vidyard",
    )
}

private fun resolution(profile: String?): Pair<Long?, Long?>? {
    val match = Regex("^(\\d+)p$").find(profile ?: return null) ?: return null
    val height = match.groupValues[1].toLongOrNull() ?: return null
    return Pair((height * 16 / 9), height)
}

private fun mimetypeExt(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.contains("mp4") -> "mp4"
    mimeType.contains("webm") -> "webm"
    mimeType.contains("mpeg") -> "mp3"
    else -> mimeType.substringAfter('/', "").takeIf { it.isNotBlank() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
