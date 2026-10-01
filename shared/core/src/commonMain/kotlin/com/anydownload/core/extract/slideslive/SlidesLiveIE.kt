/*
 * SlidesLive extractor — AnyDownload
 *
 * Kotlin translation of the public page/player subset of `slideslive.py`
 * from `yt_dlp/extractor/slideslive.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `slideslive.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the embed page player token, the custom `#EXT-SL-` player tags,
 * the slides JSON/XML chapters and thumbnails, the subtitles, and the yoda
 * m3u8/mpd formats (recorded as manifests; manifest durations are not
 * parsed). The video-slide playlist and the vimeo/youtube transparent
 * dispatch are simplified: those service URLs become a redirect. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.slideslive

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `SlidesLiveIE`: the SlidesLive presentations. */
class SlidesLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val embedUrl = "https://slideslive.com/embed/presentation/$videoId"
        val webpage = http.downloadWebpage(embedUrl)
        val playerToken = ExtractorUtils.searchRegex("data-player-token=\"([^\"]+)\"", webpage, default = null)
            ?: throw ExtractionError.Malformed("The embed page had no player token.")
        val playerData = http.downloadWebpage(
            "https://slideslive.com/player/$videoId?player_token=$playerToken",
        )
        val playerInfo = extractCustomM3u8Info(playerData)
        val serviceName = playerInfo.str("service_name")?.lowercase()
            ?: throw ExtractionError.Malformed("The player data had no service name.")
        val serviceId = playerInfo.str("service_id")
            ?: throw ExtractionError.Malformed("The player data had no service id.")

        val slidesInfo = mutableListOf<SlideInfo>()
        var slideUrlTemplate = "https://slides.slideslive.com/$videoId/slides/original/%s%s"
        var slideExtDefault = ".png"
        val slidesJsonUrl = playerInfo.str("slides_json_url")
        if (slidesJsonUrl != null) {
            val slides = try {
                http.downloadJson(slidesJsonUrl) as? JsonObject
            } catch (error: ExtractionError) {
                null
            }
            if (slides != null) {
                val quality = (slides.array("slide_qualities")?.firstOrNull() as? JsonPrimitive)?.content
                if (!quality.isNullOrBlank()) {
                    slideExtDefault = ".jpg"
                    slideUrlTemplate = "https://cdn.slideslive.com/data/presentations/$videoId/slides/$quality/%s%s"
                }
                var slideId = 0
                for (element in slides.array("slides").orEmpty()) {
                    val slide = element as? JsonObject ?: continue
                    slideId++
                    val image = slide.obj("image")
                    slidesInfo += SlideInfo(
                        id = slideId,
                        name = image?.str("name"),
                        ext = image?.str("extname") ?: slideExtDefault,
                        time = slide.number("time")?.div(1000),
                    )
                }
            }
        }
        if (slidesInfo.isEmpty() && playerInfo.str("slides_xml_url") != null) {
            val xml = try {
                http.downloadWebpage(playerInfo.str("slides_xml_url")!!)
            } catch (error: ExtractionError) {
                null
            }
            if (xml != null) {
                slideUrlTemplate = "https://cdn.slideslive.com/data/presentations/$videoId/slides/big/%s.jpg"
                var slideId = 0
                for (match in SLIDE_XML.findAll(xml)) {
                    slideId++
                    slidesInfo += SlideInfo(
                        id = slideId,
                        name = match.groupValues[1],
                        ext = ".jpg",
                        time = match.groupValues[2].toDoubleOrNull(),
                    )
                }
            }
        }

        val thumbnails = mutableListOf<Thumbnail>()
        val chapters = mutableListOf<Chapter>()
        playerInfo.str("thumbnail")?.let { thumbnails += Thumbnail(url = it, id = "cover") }
        for (slide in slidesInfo) {
            if (slide.name != null) {
                thumbnails += Thumbnail(
                    id = slide.id.toString().padStart(3, '0'),
                    url = formatTemplate(slideUrlTemplate, slide.name, slide.ext),
                )
            }
            chapters += Chapter(
                title = "Slide ${slide.id.toString().padStart(3, '0')}",
                startTime = slide.time,
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in playerInfo.array("subtitles").orEmpty()) {
            val sub = element as? JsonObject ?: continue
            val vttUrl = sub.str("webvtt_url") ?: continue
            subtitles += SubtitleTrack(
                language = sub.str("language") ?: "en",
                formats = listOf(SubtitleFormat(ext = "vtt", url = vttUrl)),
            )
        }

        val title = playerInfo.str("title")
        val uploadDate = dateFromIso(playerInfo.str("timestamp"))
        val formats = mutableListOf<MediaFormat>()
        var redirectUrl: String? = null
        when (serviceName) {
            "url" -> redirectUrl = serviceId
            "yoda" -> {
                val server = (playerInfo.array("video_servers")?.firstOrNull() as? JsonPrimitive)?.content
                    ?: throw ExtractionError.NoFormats("The player data had no video server.")
                formats += MediaFormat(
                    formatId = "hls",
                    url = "https://$server/$serviceId/master.m3u8",
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                formats += MediaFormat(
                    formatId = "dash",
                    url = "https://$server/$serviceId/master.mpd",
                    ext = "mp4",
                    protocol = "mpd",
                )
            }

            "vimeo" -> redirectUrl = "https://player.vimeo.com/video/$serviceId"
            "youtube" -> redirectUrl = if (serviceId.startsWith("http")) {
                serviceId
            } else {
                "https://www.youtube.com/watch?v=$serviceId"
            }

            else -> throw ExtractionError.Unavailable("The SlidesLive service '$serviceName' is not translated.")
        }
        return InfoDict(
            id = videoId,
            title = title,
            uploadDate = uploadDate,
            isLive = playerInfo.str("playlist_type") != "vod",
            thumbnails = thumbnails,
            chapters = chapters,
            subtitles = subtitles,
            formats = formats,
            redirectUrl = redirectUrl,
            webpageUrl = url,
            extractor = "slideslive",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SlidesLive"

        val VALID_URL: Regex = Regex(
            "https?://slideslive\\.com/(?:embed/(?:presentation/)?)?(?<id>[0-9]+)",
        )

        private val SLIDE_XML = Regex(
            "(?s)<slide>.*?<slideName>([^<]*)</slideName>.*?<timeSec>([^<]*)</timeSec>",
        )
    }
}

private data class SlideInfo(
    val id: Int,
    val name: String?,
    val ext: String,
    val time: Double?,
)

private val TAG_LOOKUP = mapOf(
    "PRESENTATION-TITLE" to "title",
    "PRESENTATION-UPDATED-AT" to "timestamp",
    "PRESENTATION-THUMBNAIL" to "thumbnail",
    "PLAYLIST-TYPE" to "playlist_type",
    "VOD-VIDEO-SERVICE-NAME" to "service_name",
    "VOD-VIDEO-ID" to "service_id",
    "VOD-VIDEO-SERVERS" to "video_servers",
    "VOD-SUBTITLES" to "subtitles",
    "VOD-SLIDES-JSON-URL" to "slides_json_url",
    "VOD-SLIDES-XML-URL" to "slides_xml_url",
)

private fun extractCustomM3u8Info(data: String): JsonObject {
    val out = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
    for (line in data.lineSequence()) {
        if (!line.startsWith("#EXT-SL-")) continue
        val tag = line.substringBefore(':')
        val value = line.substringAfter(':', "")
        val key = TAG_LOOKUP[tag.removePrefix("#EXT-SL-")] ?: continue
        out[key] = when (key) {
            "video_servers", "subtitles" -> ExtractorUtils.parseJson(value) ?: JsonArray(emptyList())
            else -> JsonPrimitive(value)
        }
    }
    return JsonObject(out)
}

private fun formatTemplate(template: String, vararg values: String): String {
    var out = template
    for (value in values) out = out.replaceFirst("%s", value)
    return out
}

private fun dateFromIso(value: String?): String? {
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(value ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
