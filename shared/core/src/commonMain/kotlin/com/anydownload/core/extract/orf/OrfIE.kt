/*
 * ORF extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `orf.py` from
 * `yt_dlp/extractor/orf.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `orf.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the radio broadcast JSON and its loopstream entries, the podcast
 * JSON, the iptv/fm4 story JSON with the load-balancer rendition map
 * (m3u8/rtmp; f4m is skipped), and the ORF ON episode/segment API with its
 * HLS/DASH sources and subtitle tracks. `_old_archive_ids`, series/creator
 * labels, and the interactive segment-prompt are not carried. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.orf

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ORFRadioIE`: the radio broadcast pages. */
class ORFRadioIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val station = match.groups["station"]?.value ?: match.groups["station2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val showDate = match.groups["date"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val showId = match.groups["show"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val info = STATION_INFO[station] ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson(
            "http://audioapi.orf.at/${info.apiStation}/api/json/current/broadcast/$showId/$showDate",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The broadcast API was not an object.")
        val entries = data.array("streams").orEmpty().mapNotNull { element ->
            val stream = element as? JsonObject ?: return@mapNotNull null
            val itemId = stream.str("loopStreamId") ?: return@mapNotNull null
            InfoEntry(
                id = itemId.replace(".mp3", ""),
                url = "https://loopstream01.apa.at/?channel=${info.loopStation}&id=$itemId",
                title = data.str("title"),
            )
        }
        return InfoDict(
            id = showId,
            title = data.str("title"),
            description = cleanHtml(data.str("subtitle")),
            entries = entries,
            webpageUrl = url,
            extractor = "orf:radio",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ORFRadio"

        private val STATION_RE =
            "fm4|noe|wien|burgenland|ooe|steiermark|kaernten|salzburg|tirol|vorarlberg|oe3|oe1"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?<station>$STATION_RE)\\.orf\\.at/player|" +
                "radiothek\\.orf\\.at/(?<station2>$STATION_RE))/(?<date>[0-9]+)/(?<show>\\w+)",
        )
    }
}

private class StationInfo(val apiStation: String, val loopStation: String)

private val STATION_INFO: Map<String, StationInfo> = mapOf(
    "fm4" to StationInfo("fm4", "orffm4"),
    "noe" to StationInfo("noe", "oe2n"),
    "wien" to StationInfo("wie", "oe2w"),
    "burgenland" to StationInfo("bgl", "oe2b"),
    "ooe" to StationInfo("ooe", "oe2o"),
    "steiermark" to StationInfo("stm", "oe2st"),
    "kaernten" to StationInfo("ktn", "oe2k"),
    "salzburg" to StationInfo("sbg", "oe2s"),
    "tirol" to StationInfo("tir", "oe2t"),
    "vorarlberg" to StationInfo("vbg", "oe2v"),
    "oe3" to StationInfo("oe3", "oe3"),
    "oe1" to StationInfo("oe1", "oe1"),
)

/** Upstream `ORFPodcastIE`: the sound.orf.at podcast episodes. */
class ORFPodcastIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val station = match.groups["station"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val show = match.groups["show"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val showId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson(
            "https://audioapi.orf.at/radiothek/api/2.0/podcast/$station/$show/$showId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The podcast API was not an object.")
        val payload = data.obj("payload") ?: throw ExtractionError.Malformed("The podcast had no payload.")
        val enclosure = payload.array("enclosures")?.firstOrNull() as? JsonObject
        val streamUrl = enclosure?.str("url")
            ?: throw ExtractionError.NoFormats("The podcast had no enclosure URL.")
        return InfoDict(
            id = showId,
            title = payload.str("title"),
            description = cleanHtml(payload.str("description")),
            duration = payload.number("duration")?.div(1000),
            url = streamUrl,
            ext = ExtractorUtils.determineExt(streamUrl, "mp3"),
            webpageUrl = url,
            extractor = "orf:podcast",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ORFPodcast"

        private val STATION_RE = "bgl|fm4|ktn|noe|oe1|oe3|ooe|sbg|stm|tir|tv|vbg|wie"

        val VALID_URL: Regex = Regex(
            "https?://sound\\.orf\\.at/podcast/(?<station>$STATION_RE)/(?<show>[\\w-]+)/(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `ORFIPTVIE`: the iptv.orf.at stories. */
class ORFIPTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val storyId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage("http://iptv.orf.at/stories/$storyId")
        val videoId = ExtractorUtils.searchRegex("data-video(?:id)?=\"(\\d+)\"", webpage, default = null)
            ?: throw ExtractionError.Malformed("The story page had no video id.")
        val data = (http.downloadJson(
            "http://bits.orf.at/filehandler/static-api/json/current/data.json?file=$videoId",
        ) as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The video data was empty.")
        val duration = data.number("duration")?.div(1000)
        val video = data.obj("sources")?.obj("default")
            ?: throw ExtractionError.Malformed("The video had no default source.")
        val formats = loadBalancerFormats(http, video)
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The rendition map had no playable format.")
        }
        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?.removeSuffix(" - iptv.ORF.at")?.trim()
        val uploadDate = ExtractorUtils.unifiedStrdate(
            ExtractorUtils.htmlSearchMeta(webpage, "dc.date"),
        )
        return InfoDict(
            id = videoId,
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            duration = duration,
            thumbnails = listOfNotNull(video.str("preview")?.let { Thumbnail(url = it) }),
            uploadDate = uploadDate,
            formats = formats,
            webpageUrl = url,
            extractor = "orf:iptv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ORFIPTV"

        val VALID_URL: Regex = Regex("https?://iptv\\.orf\\.at/(?:#/)?stories/(?<id>\\d+)")
    }
}

/** Upstream `ORFFM4StoryIE`: fm4.orf.at stories with several videos. */
class ORFFM4StoryIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val storyId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val allIds = Regex("data-video(?:id)?=\"(\\d+)\"").findAll(webpage)
            .map { it.groupValues[1] }.distinct().toList()
        if (allIds.isEmpty()) {
            throw ExtractionError.Malformed("The story page had no video ids.")
        }
        val baseTitle = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?.removeSuffix(" - fm4.ORF.at")?.trim()
        val media = mutableListOf<InfoMedia>()
        allIds.forEachIndexed { index, videoId ->
            val data = (http.downloadJson(
                "http://bits.orf.at/filehandler/static-api/json/current/data.json?file=$videoId",
            ) as? JsonArray)?.firstOrNull() as? JsonObject ?: return@forEachIndexed
            val video = data.obj("sources")?.obj("q8c") ?: return@forEachIndexed
            val formats = loadBalancerFormats(http, video)
            media += InfoMedia(
                mediaId = videoId,
                title = if (index == 0) baseTitle else "${baseTitle.orEmpty()} (${index + 1})",
                duration = data.number("duration")?.div(1000),
                thumbnails = listOfNotNull(video.str("preview")?.let { Thumbnail(url = it) }),
                formats = formats,
            )
        }
        return InfoDict(
            id = storyId,
            media = media,
            webpageUrl = url,
            extractor = "orf:fm4:story",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ORFFM4Story"

        val VALID_URL: Regex = Regex("https?://fm4\\.orf\\.at/stories/(?<id>\\d+)")
    }
}

/** Upstream `ORFONIE`: the ORF ON episode/segment API. */
class ORFONIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val segmentId = match.groups["segment"]?.value
        val encryptedId = base64Encode("3dSlfek03nsLKdj4Jsd$videoId")
        val api = http.downloadJson(
            "https://api-tvthek.orf.at/api/v4.3/public/episode/encrypted/$encryptedId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The episode API was not an object.")
        if (api["is_drm_protected"] == JsonPrimitive(true)) {
            throw ExtractionError.Unavailable("This ORF ON video is DRM protected.")
        }
        val segments = api.obj("_embedded")?.array("segments").orEmpty()
            .mapNotNull { it as? JsonObject }.filter { it.primitiveText("id") != null }
        val selected = segments.firstOrNull { it.primitiveText("id") == segmentId }
        if (selected != null) {
            return videoInfo(segmentId!!, selected, url)
        }
        val rootHasSources = api.obj("sources")?.entries?.any { (_, value) ->
            value is JsonArray && value.any { (it as? JsonObject)?.str("src") != null }
        } == true
        if (!rootHasSources && segments.isNotEmpty()) {
            return InfoDict(
                id = videoId,
                title = api.str("title") ?: api.str("headline"),
                description = api.str("description") ?: api.str("teaser_text"),
                duration = api.number("exact_duration")?.div(1000),
                media = segments.map { segment ->
                    InfoMedia(
                        mediaId = segment.primitiveText("id")!!,
                        title = segment.str("title"),
                        duration = segment.number("exact_duration")?.div(1000),
                        formats = sourceFormats(segment),
                    )
                },
                webpageUrl = url,
                extractor = "orf:on",
                extractorKey = IE_KEY,
            )
        }
        return videoInfo(videoId, api, url)
    }

    private fun videoInfo(videoId: String, api: JsonObject, url: String): InfoDict {
        val thumbnails = api.obj("_embedded")?.obj("image")?.obj("public_urls")
            ?.obj("highlight_teaser")?.str("url")?.let { listOf(Thumbnail(url = it)) }.orEmpty()
        val subtitle = api.obj("_embedded")?.obj("subtitle")
        val subtitleFormats = subtitle?.let { element ->
            listOf("xml_url", "sami_url", "stl_url", "ttml_url", "srt_url", "vtt_url")
                .mapNotNull { key -> element.str(key)?.let { SubtitleFormat(ext = key.substringBefore("_url"), url = it) } }
        }.orEmpty()
        val uploadDate = dateOnly(api.str("date") ?: api.str("episode_date"))
        return InfoDict(
            id = videoId,
            title = api.str("title") ?: api.str("headline"),
            description = api.str("description") ?: api.str("teaser_text"),
            duration = api.number("exact_duration")?.div(1000),
            ageLimit = ExtractorUtils.parseAgeLimit(api.number("age_classification")?.toLong()?.toString()),
            thumbnails = thumbnails,
            uploadDate = uploadDate,
            formats = sourceFormats(api),
            subtitles = if (subtitleFormats.isEmpty()) emptyList() else listOf(
                SubtitleTrack(language = "de", formats = subtitleFormats),
            ),
            webpageUrl = url,
            extractor = "orf:on",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ORFON"

        val VALID_URL: Regex = Regex("https?://on\\.orf\\.at/video/(?<id>\\d+)(?:/(?<segment>\\d+))?")
    }
}

// ------------------------------------------------------------------ helpers

private fun sourceFormats(api: JsonObject): List<MediaFormat> {
    val out = mutableListOf<MediaFormat>()
    val sources = api.obj("sources") ?: return out
    for ((type, value) in sources) {
        val entries = value as? JsonArray ?: continue
        for (element in entries) {
            val src = (element as? JsonObject)?.str("src") ?: continue
            when (type) {
                "hls" -> out += MediaFormat(
                    formatId = "hls",
                    url = src,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "dash" -> out += MediaFormat(
                    formatId = "dash",
                    url = src,
                    ext = "mp4",
                    protocol = "mpd",
                )
            }
        }
    }
    return out
}

/** Upstream `_extract_video_info`/`_real_extract` in iptv/fm4: the rendition map. */
private suspend fun loadBalancerFormats(http: ExtractorHttp, video: JsonObject): List<MediaFormat> {
    val loadBalancerUrl = video.str("loadBalancerUrl") ?: return emptyList()
    val rendition = http.downloadJson(loadBalancerUrl) as? JsonObject ?: return emptyList()
    val redirect = rendition.obj("redirect") ?: return emptyList()
    val out = mutableListOf<MediaFormat>()
    for ((formatId, value) in redirect) {
        val formatUrl = (value as? JsonPrimitive)?.content ?: continue
        when {
            formatId == "rtmp" -> out += MediaFormat(
                formatId = "rtmp",
                url = formatUrl,
                abr = video.number("audioBitrate"),
                tbr = video.number("bitrate")?.toDouble(),
                fps = video.number("videoFps"),
                width = video.number("videoWidth")?.toLong(),
                height = video.number("videoHeight")?.toLong(),
            )

            ExtractorUtils.determineExt(formatUrl) == "m3u8" -> out += MediaFormat(
                formatId = formatId,
                url = formatUrl,
                ext = "mp4",
                protocol = "m3u8_native",
                abr = video.number("audioBitrate"),
                tbr = video.number("bitrate")?.toDouble(),
                fps = video.number("videoFps"),
                width = video.number("videoWidth")?.toLong(),
                height = video.number("videoHeight")?.toLong(),
            )
            // f4m renditions are skipped: the port has no f4m helper.
        }
    }
    return out
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun dateOnly(value: String?): String? {
    val text = value ?: return null
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(text) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun base64Encode(value: String): String =
    kotlin.io.encoding.Base64.Default.encode(value.encodeToByteArray())

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
