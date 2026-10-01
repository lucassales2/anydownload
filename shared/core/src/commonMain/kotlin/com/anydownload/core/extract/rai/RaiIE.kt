/*
 * RAI extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `rai.py` from
 * `yt_dlp/extractor/rai.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rai.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the relinker XML (license/DRM, live flag, duration, the content
 * URL, geo flag, bitrate), the RaiPlay and RaiPlaySound JSON metadata, the
 * program/set playlists, the rai.tv ContentItem API, the RaiNews/RaiCultura
 * player JSON, and the RaiSudtirol page regexes. HLS manifests are recorded
 * as `m3u8_native` and parsed at download time, so the chunklist audio/video
 * codec fixes and the HEAD-probed https-MP4 variants are not available; F4M
 * relinkers are not translated. Geo-protected relinkers without formats fail
 * typed, and the DRM flag fails typed. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.rai

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

/** The relinker fields the port models. */
data class RelinkerInfo(
    val isLive: Boolean? = null,
    val duration: Double? = null,
    val formats: List<MediaFormat> = emptyList(),
)

/** Upstream `RaiBaseIE`: the relinker XML and the shared helpers. */
abstract class RaiBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_extract_relinker_info`; the HEAD-probed http URLs are out. */
    protected suspend fun extractRelinkerInfo(
        relinkerUrl: String,
        videoId: String,
        audioOnly: Boolean = false,
    ): RelinkerInfo {
        if (!relinkerUrl.startsWith("http://") && !relinkerUrl.startsWith("https://")) {
            return RelinkerInfo(formats = listOf(MediaFormat(url = relinkerUrl)))
        }
        val separator = if (relinkerUrl.contains('?')) '&' else '?'
        val xml = http.downloadWebpage(
            "$relinkerUrl${separator}output=64",
            headers = mapOf("user-agent" to "Rai"),
        )

        val licenseUrl = xmlText(xml, "license_url")
        if (licenseUrl != null && licenseUrl != "{}") {
            throw ExtractionError.NoFormats("This video is DRM protected.")
        }
        val isLive = xmlText(xml, "is_live") == "Y"
        val duration = ExtractorUtils.parseDuration(xmlText(xml, "duration"))
        val mediaUrl = xmlUrlContent(xml)
        if (mediaUrl == null) {
            throw ExtractionError.NoFormats("The RAI relinker returned no media URL.")
        }
        val geoProtection = xmlText(xml, "geoprotection") == "Y"
        val ext = ExtractorUtils.determineExt(mediaUrl, defaultExt = "").lowercase()
        val formats = mutableListOf<MediaFormat>()
        when {
            ext == "mp3" -> formats += MediaFormat(
                formatId = "https-mp3",
                url = mediaUrl,
                ext = "mp3",
                vcodec = MediaFormat.CODEC_NONE,
                acodec = "mp3",
            )

            ext == "m3u8" || mediaUrl.contains("format=m3u8") -> formats += MediaFormat(
                formatId = "hls",
                url = mediaUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )

            ext == "mp4" -> {
                val bitrate = xmlText(xml, "bitrate")?.toLongOrNull()
                formats += MediaFormat(
                    formatId = joinNonEmpty("https", bitrate?.takeIf { it > 0 }?.toString()),
                    url = mediaUrl,
                    ext = "mp4",
                    tbr = bitrate?.takeIf { it > 0 }?.toDouble(),
                )
            }

            else -> throw ExtractionError.Unavailable("Unrecognized RAI media extension \"$ext\".")
        }

        if (formats.isEmpty() && geoProtection || mediaUrl.contains("/video_no_available.mp4")) {
            throw ExtractionError.GeoRestricted(listOf("IT"))
        }
        return RelinkerInfo(isLive = isLive, duration = duration, formats = formats)
    }

    /** Upstream `_get_thumbnails_list`. */
    protected fun thumbnailsList(images: JsonObject?, url: String): List<Thumbnail> =
        images.orEmpty().mapNotNull { (_, value) ->
            val thumbUrl = (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            Thumbnail(url = urlJoin(url, thumbUrl))
        }

    /** Upstream `_extract_subtitles`. */
    protected fun extractSubtitles(url: String, videoData: JsonObject): List<SubtitleTrack> {
        val array = (videoData.array("subtitlesArray") ?: videoData.array("subtitleList")
            ?: JsonArray(emptyList())).toMutableList()
        for (key in listOf("subtitles", "subtitlesUrl")) {
            videoData.str(key)?.let { array += JsonObject(mapOf("url" to JsonPrimitive(it))) }
        }
        val byLanguage = linkedMapOf<String, MutableList<SubtitleFormat>>()
        for (element in array) {
            val subtitle = element as? JsonObject ?: continue
            val rawUrl = subtitle.str("url") ?: continue
            val subUrl = urlJoin(url, rawUrl)
            val language = subtitle.str("language") ?: "it"
            val ext = ExtractorUtils.determineExt(subUrl, defaultExt = "srt")
            byLanguage.getOrPut(language) { mutableListOf() } += SubtitleFormat(ext = ext, url = subUrl)
            if (ext == "stl") {
                byLanguage[language]!! += SubtitleFormat(
                    ext = "srt",
                    url = subUrl.removeSuffix("stl") + "srt",
                )
            }
        }
        return byLanguage.map { (language, formats) -> SubtitleTrack(language = language, formats = formats) }
    }

    companion object {
        const val UUID_RE: String = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}"
    }
}

/** Upstream `RaiPlayIE`: the raiplay.it video pages. */
open class RaiPlayIE(
    http: ExtractorHttp,
) : RaiBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val base = match.groups["base"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val media = http.downloadJson("$base.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The RaiPlay response was not an object.")
        if (media.obj("rights_management")?.obj("rights")?.get("drm") != null) {
            throw ExtractionError.NoFormats("This video is DRM protected.")
        }
        val video = media.obj("video")
            ?: throw ExtractionError.Malformed("The RaiPlay response had no video.")
        val contentUrl = video.str("content_url")
            ?: throw ExtractionError.Unavailable("The RaiPlay video had no content URL.")
        val relinker = extractRelinkerInfo(contentUrl, videoId)
        val datePublished = joinNonEmpty(media.str("date_published"), media.str("time_published"), delim = " ")

        return InfoDict(
            id = media.str("id")?.removePrefix("ContentItem-") ?: videoId,
            title = media.str("name"),
            description = media.str("description"),
            uploader = stripOrNull(media.obj("program_info")?.str("channel") ?: media.str("channel")),
            duration = relinker.duration ?: ExtractorUtils.parseDuration(video.str("duration")),
            uploadDate = datePublished?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = thumbnailsList(media.obj("images"), url),
            subtitles = extractSubtitles(url, video),
            formats = relinker.formats,
            isLive = relinker.isLive,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiPlay"

        val VALID_URL: Regex = Regex(
            "(?<base>https?://(?:www\\.)?raiplay\\.it/.+?-(?<id>${RaiBaseIE.UUID_RE}))\\.(?:html|json)",
        )
    }
}

/** Upstream `RaiPlayLiveIE`: the raiplay.it/dirette/<slug> live pages. */
class RaiPlayLiveIE(
    http: ExtractorHttp,
) : RaiPlayIE(http) {
    override fun suitable(url: String): Boolean = VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val base = match.groups["base"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val media = http.downloadJson("$base.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The RaiPlay live response was not an object.")
        val video = media.obj("video")
            ?: throw ExtractionError.Malformed("The RaiPlay live response had no video.")
        val contentUrl = video.str("content_url")
            ?: throw ExtractionError.Unavailable("The RaiPlay live video had no content URL.")
        val relinker = extractRelinkerInfo(contentUrl, base)
        return InfoDict(
            id = media.str("id")?.removePrefix("ContentItem-") ?: base,
            title = media.str("name"),
            description = media.str("description"),
            uploader = stripOrNull(media.obj("program_info")?.str("channel") ?: media.str("channel")),
            thumbnails = thumbnailsList(media.obj("images"), url),
            formats = relinker.formats,
            isLive = true,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiPlayLive"

        val VALID_URL: Regex = Regex(
            "(?<base>https?://(?:www\\.)?raiplay\\.it/dirette/(?<id>[^/?#&]+))",
        )
    }
}

/** Upstream `RaiPlayPlaylistIE`: the raiplay.it/programmi pages. */
class RaiPlayPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val base = match.groups["base"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val extraId = match.groups["extraId"]?.value?.uppercase()?.trimEnd('/')
        val program = http.downloadJson("$base.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The RaiPlay program response was not an object.")
        var playlistTitle = program.str("name")
        val entries = mutableListOf<InfoEntry>()
        for (blockElement in program.array("blocks").orEmpty()) {
            val block = blockElement as? JsonObject ?: continue
            for (setElement in block.array("sets").orEmpty()) {
                val set = setElement as? JsonObject ?: continue
                if (extraId != null) {
                    val setName = joinNonEmpty(block.str("name"), set.str("name"), delim = "/")
                        ?.replace(" ", "-")?.uppercase()
                    if (extraId != setName) continue
                    playlistTitle = joinNonEmpty(playlistTitle, set.str("name"), delim = " - ")
                }
                val setId = set.str("id") ?: continue
                val medias = try {
                    http.downloadJson("$base/$setId.json") as? JsonObject
                } catch (error: ExtractionError) {
                    null
                } ?: continue
                for (itemElement in medias.array("items").orEmpty()) {
                    val pathId = (itemElement as? JsonObject)?.str("path_id") ?: continue
                    val videoUrl = urlJoin(url, pathId)
                    val videoId = RaiPlayIE.VALID_URL.find(videoUrl)?.groups?.get("id")?.value
                    entries += InfoEntry(id = videoId, url = videoUrl)
                }
            }
        }
        return InfoDict(
            id = playlistId,
            title = playlistTitle,
            description = program.obj("program_info")?.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiPlayPlaylist"

        val VALID_URL: Regex = Regex(
            "(?<base>https?://(?:www\\.)?raiplay\\.it/programmi/(?<id>[^/?#&]+))" +
                "(?:/(?<extraId>[^?#&]+))?",
        )
    }
}

/** Upstream `RaiPlaySoundIE`: the raiplaysound.it audio pages. */
open class RaiPlaySoundIE(
    http: ExtractorHttp,
) : RaiBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val base = match.groups["base"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val audioId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val media = http.downloadJson("$base.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The RaiPlaySound response was not an object.")
        val uid = media.str("uniquename")
            ?.removePrefix("ContentItem-")?.removePrefix("Page-")

        val relinkers = linkedSetOf<String>()
        media.obj("downloadable_audio")?.str("url")?.let { relinkers += it }
        media.obj("audio")?.str("url")?.let { relinkers += it }
        media.obj("live")?.array("cards")?.firstOrNull()?.let { card ->
            (card as? JsonObject)?.obj("audio")?.str("url")?.let { relinkers += it }
        }

        var relinker = RelinkerInfo()
        val formats = mutableListOf<MediaFormat>()
        for (relinkerUrl in relinkers) {
            relinker = extractRelinkerInfo(relinkerUrl, audioId, audioOnly = true)
            formats += relinker.formats
        }
        val podcastInfo = media.obj("podcast_info")
            ?: media.obj("live")?.array("cards")?.firstOrNull()?.let { it as? JsonObject }
            ?: JsonObject(emptyMap())
        val datePublished = media.str("create_date")?.let { date ->
            listOf(date, media.str("create_time")).filterNotNull().joinToString(" ")
        } ?: media.obj("live")?.str("create_date")

        return InfoDict(
            id = uid ?: audioId,
            title = media.str("title") ?: media.str("episode_title"),
            description = media.str("description"),
            uploader = stripOrNull(media.obj("track_info")?.str("channel")),
            uploadDate = datePublished?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = thumbnailsList(podcastInfo.obj("images"), url),
            formats = formats,
            isLive = relinker.isLive,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiPlaySound"

        val VALID_URL: Regex = Regex(
            "(?<base>https?://(?:www\\.)?raiplaysound\\.it/.+?-(?<id>${RaiBaseIE.UUID_RE}))" +
                "\\.(?:html|json)",
        )
    }
}

/** Upstream `RaiPlaySoundLiveIE`: the raiplaysound.it/<station> live pages. */
class RaiPlaySoundLiveIE(
    http: ExtractorHttp,
) : RaiPlaySoundIE(http) {
    override fun suitable(url: String): Boolean = VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val base = match.groups["base"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val media = http.downloadJson("$base.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The RaiPlaySound live response was not an object.")
        val card = media.obj("live")?.array("cards")?.firstOrNull() as? JsonObject
        val contentUrl = card?.obj("audio")?.str("url")
            ?: throw ExtractionError.Unavailable("The RaiPlaySound live response had no audio URL.")
        val relinker = extractRelinkerInfo(contentUrl, base, audioOnly = true)
        return InfoDict(
            id = media.str("uniquename")?.removePrefix("ContentItem-") ?: base,
            title = media.str("title") ?: card?.str("title"),
            description = media.str("description"),
            uploader = stripOrNull(card?.str("channel") ?: media.str("channel")),
            thumbnails = thumbnailsList(card?.obj("images") ?: media.obj("images"), url),
            formats = relinker.formats,
            isLive = true,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiPlaySoundLive"

        val VALID_URL: Regex = Regex(
            "(?<base>https?://(?:www\\.)?raiplaysound\\.it/(?<id>[^/?#&]+)$)",
        )
    }
}

/** Upstream `RaiPlaySoundPlaylistIE`: the RaiPlaySound program lists. */
class RaiPlaySoundPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val base = match.groups["base"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var extraId = match.groups["extraId"]?.value
        val jsonUrl = "$base.json"
        var program = http.downloadJson(jsonUrl) as? JsonObject
            ?: throw ExtractionError.Malformed("The RaiPlaySound program response was not an object.")

        if (extraId != null) {
            extraId = extraId.trimEnd('/')
            playlistId += "_" + extraId.replace('/', '_')
            val path = program.array("filters").orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { extraId in (it.str("weblink") ?: "") }
                ?.str("path_id")
                ?: throw ExtractionError.Unavailable("The RaiPlaySound program had no matching filter.")
            program = http.downloadJson(urlJoin("https://www.raiplaysound.it", path)) as? JsonObject
                ?: throw ExtractionError.Malformed("The RaiPlaySound secondary response was not an object.")
        }

        val entries = mutableListOf<InfoEntry>()
        val cards = mutableListOf<JsonObject>()
        program.array("cards")?.let { array ->
            cards += array.mapNotNull { it as? JsonObject }
        }
        program.obj("block")?.array("cards")?.let { array ->
            cards += array.mapNotNull { it as? JsonObject }
        }
        for (card in cards) {
            val pathId = card.str("path_id") ?: continue
            entries += InfoEntry(url = urlJoin(base, pathId))
        }
        return InfoDict(
            id = playlistId,
            title = program.str("title"),
            description = program.obj("podcast_info")?.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiPlaySoundPlaylist"

        val VALID_URL: Regex = Regex(
            "(?<base>https?://(?:www\\.)?raiplaysound\\.it/(?:programmi|playlist|audiolibri)/" +
                "(?<id>[^/?#&]+))(?:/(?<extraId>[^?#&]+))?",
        )
    }
}

/** Upstream `RaiIE`: the rai.tv ContentItem JSON. */
class RaiIE(
    http: ExtractorHttp,
) : RaiBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val contentId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val media = try {
            http.downloadJson(
                "https://www.rai.tv/dl/RaiTV/programmi/media/ContentItem-$contentId.html?json",
            ) as? JsonObject
        } catch (error: ExtractionError) {
            throw ExtractionError.Unavailable("The RAI ContentItem is not available.")
        } ?: throw ExtractionError.Malformed("The RAI ContentItem response was not an object.")

        val mediaType = media.str("type") ?: ""
        val relinker: RelinkerInfo
        if ("Audio" in mediaType) {
            val audioUrl = ExtractorUtils.urlOrNone(media.str("audioUrl"))
                ?: throw ExtractionError.Unavailable("The RAI audio item had no URL.")
            relinker = RelinkerInfo(
                formats = listOf(
                    MediaFormat(
                        formatId = joinNonEmpty("https", media.str("formatoAudio")),
                        url = audioUrl,
                        ext = media.str("formatoAudio"),
                        vcodec = MediaFormat.CODEC_NONE,
                        acodec = media.str("formatoAudio"),
                    ),
                ),
            )
        } else if ("Video" in mediaType) {
            val mediaUri = media.str("mediaUri")
                ?: throw ExtractionError.Unavailable("The RAI video item had no media URI.")
            relinker = extractRelinkerInfo(mediaUri, contentId)
        } else {
            throw ExtractionError.Unavailable("The RAI item is not a media file.")
        }

        val images = JsonObject(
            listOf("image", "image_medium", "image_300")
                .mapNotNull { key -> media.str(key)?.let { key to JsonPrimitive(it) } }
                .toMap(),
        )
        return InfoDict(
            id = contentId,
            title = stripOrNull(media.str("name") ?: media.str("title")),
            description = stripOrNull(media.str("desc")),
            thumbnails = thumbnailsList(images, url),
            uploader = stripOrNull(media.str("author")),
            uploadDate = media.str("date")?.let(ExtractorUtils::unifiedStrdate),
            duration = ExtractorUtils.parseDuration(media.str("length")),
            subtitles = extractSubtitles(url, media),
            formats = relinker.formats,
            isLive = relinker.isLive,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Rai"

        val VALID_URL: Regex = Regex(
            "https?://[^/]+\\.(?:rai\\.(?:it|tv))/.+?-(?<id>${RaiBaseIE.UUID_RE})(?:-.+?)?\\.html",
        )
    }
}

/** Upstream `RaiNewsIE`: the rainews.it player pages. */
open class RaiNewsIE(
    http: ExtractorHttp,
) : RaiBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playerData = extractJsonElement(
            webpage,
            Regex("<rai${playerTag}-player\\s*data='"),
        ) as? JsonObject
        val trackInfo = playerData?.obj("track_info")
        val relinkerUrl = playerData?.obj("mediapolis")?.str("content_url")
        if (relinkerUrl == null) {
            try {
                return RaiIE(http).extract(url)
            } catch (error: ExtractionError.GeoRestricted) {
                throw error
            } catch (error: ExtractionError) {
                throw ExtractionError.Unavailable("The RAI relinker URL was not found.")
            }
        }
        val relinker = extractRelinkerInfo(urlJoin(url, relinkerUrl), videoId)
        return InfoDict(
            id = videoId,
            title = playerData.str("title") ?: trackInfo?.str("title")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            uploadDate = trackInfo?.str("date")?.let(ExtractorUtils::unifiedStrdate),
            uploader = stripOrNull(trackInfo?.str("editor")),
            formats = relinker.formats,
            isLive = relinker.isLive,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    protected open val playerTag: String = "news"

    companion object {
        const val IE_KEY: String = "RaiNews"

        val VALID_URL: Regex = Regex(
            "https?://(www\\.)?rainews\\.it/(?!articoli)[^?#]+-(?<id>${RaiBaseIE.UUID_RE})" +
                "(?:-[^/?#]+)?\\.html",
        )
    }
}

/** Upstream `RaiCulturaIE`: the raicultura.it player pages. */
class RaiCulturaIE(
    http: ExtractorHttp,
) : RaiNewsIE(http) {
    override val playerTag: String = "cultura"

    override fun suitable(url: String): Boolean = VALID_URL.containsMatchIn(url)

    override fun matchId(url: String): String? = VALID_URL.find(url)?.groups?.get("id")?.value

    companion object {
        const val IE_KEY: String = "RaiCultura"

        val VALID_URL: Regex = Regex(
            "https?://(www\\.)?raicultura\\.it/(?!articoli)[^?#]+-(?<id>${RaiBaseIE.UUID_RE})" +
                "(?:-[^/?#]+)?\\.html",
        )
    }
}

/** Upstream `RaiSudtirolIE`: the rai.bz / rai.sudtirol media pages. */
class RaiSudtirolIE(
    http: ExtractorHttp,
) : RaiBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val videoDate = ExtractorUtils.searchRegex(
            "<span class=\"med_data\">(.+?)</span>",
            webpage,
            default = null,
        )
        val videoTitle = ExtractorUtils.searchRegex(
            "<span class=\"med_title\">(.+?)</span>",
            webpage,
            default = null,
        ) ?: ExtractorUtils.searchRegex("title: '(.+?)',", webpage, default = null)
        val videoUrl = ExtractorUtils.searchRegex(
            "sources:\\s*\\[\\{file:\\s*\"(.+?)\"\\}\\]",
            webpage,
            default = null,
        ) ?: ExtractorUtils.searchRegex(
            "<source\\s+src=\"(.+?)\"\\s+type=\"application/x-mpegURL\"",
            webpage,
            default = null,
        )
        val ext = videoUrl?.let { ExtractorUtils.determineExt(it, defaultExt = "") } ?: ""
        val formats = when (ext) {
            "m3u8" -> listOf(
                MediaFormat(
                    formatId = "hls",
                    url = videoUrl!!,
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            )

            "mp4" -> listOf(
                MediaFormat(
                    formatId = "https-mp4",
                    url = protoRelative(videoUrl!!),
                    ext = "mp4",
                    width = 1024,
                    height = 576,
                    fps = 25.0,
                    vcodec = "avc1",
                    acodec = "mp4a",
                ),
            )

            else -> throw ExtractionError.NoFormats("Unrecognized RAI media file: $videoUrl")
        }
        val thumbnailUrl = ExtractorUtils.searchRegex("image: '(.+?)'", webpage, default = null)
        return InfoDict(
            id = videoId,
            title = joinNonEmpty(videoTitle, videoDate, delim = " - "),
            uploadDate = videoDate?.let(ExtractorUtils::unifiedStrdate),
            uploader = "raisudtirol",
            thumbnails = thumbnailUrl?.let {
                listOf(Thumbnail(url = urlJoin("https://raisudtirol.rai.it/", it)))
            }.orEmpty(),
            formats = formats,
            webpageUrl = url,
            extractor = "rai",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RaiSudtirol"

        val VALID_URL: Regex = Regex(
            "https?://rai(?:bz|sudtirol)\\.rai\\.it/.+media=(?<id>\\w+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `xpath_text` for a simple element. */
private fun xmlText(xml: String, tag: String): String? {
    val match = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)
        ?: return null
    return stripCdata(match.groupValues[1])
}

/** Upstream `xpath_text(relinker, './url[@type="content"]')`. */
private fun xmlUrlContent(xml: String): String? {
    val match = Regex("<url[^>]*type=\"content\"[^>]*>(.*?)</url>", RegexOption.DOT_MATCHES_ALL)
        .find(xml) ?: return null
    return stripCdata(match.groupValues[1])
}

private fun stripCdata(value: String?): String? {
    val text = value?.trim() ?: return null
    return text.removePrefix("<![CDATA[").removeSuffix("]]>").trim().takeIf { it.isNotEmpty() }
}

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
                    val raw = html.substring(index, position + 1)
                    return ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(raw) ?: raw)
                }
            }
        }
    }
    return null
}

private fun joinNonEmpty(vararg values: String?, delim: String = "-"): String? =
    values.filterNotNull().filter { it.isNotEmpty() }.joinToString(delim).ifEmpty { null }

private fun stripOrNull(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

private fun protoRelative(value: String): String =
    if (value.startsWith("//")) "https:$value" else value

private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("/") -> base.substringBefore("://") + "://" +
        base.substringAfter("://").substringBefore('/') + value

    else -> base.substringBefore('?').trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
