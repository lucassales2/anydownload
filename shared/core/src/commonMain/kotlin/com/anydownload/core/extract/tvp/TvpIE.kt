/*
 * TVP extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `tvp.py` from
 * `yt_dlp/extractor/tvp.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tvp.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the vue data pages (`window.__videoData`/`__newsData`/`__websiteData`/
 * `__directoryData`), the classic iframe/object hand-off, the `__channels`
 * stream lookup, the TVPlayer2 JSONP config (HLS/DASH/direct formats, posters,
 * age limit, subtitles, geo/payment typed failures), and the vod.tvp.pl VOD
 * API with its playlist and season listings. HLS/DASH masters are recorded for
 * the download-time parse. F4M/ISM manifests, `_old_archive_ids`, `series`/
 * `episode` fields, and the `season` metadata are not modeled on the port's
 * InfoDict where noted in the scopes. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.tvp

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

/** Upstream `TVPIE`: the regional/info/world pages. */
class TVPIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val pageId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = runCatching { http.followRedirects(url) }.getOrDefault(url)
        for (vod in listOf(TVPVODSeriesIE.VALID_URL, TVPVODVideoIE.VALID_URL)) {
            if (vod.containsMatchIn(finalUrl)) {
                return InfoDict(
                    id = pageId,
                    webpageUrl = url,
                    redirectUrl = finalUrl,
                    extractor = "tvp",
                    extractorKey = IE_KEY,
                )
            }
        }

        val webpage = http.downloadWebpage(url)
        if (VUE_DATA.containsMatchIn(webpage)) {
            return handleVuePage(url, webpage, pageId)
        }

        // Classic server-side rendered sites.
        var videoId: String? = null
        for (pattern in CLASSIC_ID_PATTERNS) {
            videoId = ExtractorUtils.searchRegex(pattern, webpage, default = null)
            if (videoId != null) break
        }
        val resolved = videoId ?: pageId
        return InfoDict(
            id = resolved,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description", "description"),
            thumbnails = ExtractorUtils.htmlSearchMeta(webpage, "og:image")
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            webpageUrl = url,
            redirectUrl = "tvp:$resolved",
            extractor = "tvp",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_handle_vuejs_page`. */
    private suspend fun handleVuePage(url: String, webpage: String, pageId: String): InfoDict {
        val videoData = extractJsonElement(webpage, VUE_VIDEO_DATA)
        if (videoData != null) {
            return vueVideo(videoData, pageId, url)
        }
        val websiteData = extractJsonElement(webpage, VUE_WEBSITE_DATA) as? JsonObject
            ?: throw ExtractionError.Malformed("Could not extract the TVP video or website data.")
        val entries = mutableListOf<InfoEntry>()
        entries += vueEntries(websiteData)
        val totalCount = websiteData.number("items_total_count")?.toInt() ?: 0
        val perPage = websiteData.number("items_per_page")?.toInt() ?: 0
        if (perPage > 0 && totalCount > perPage) {
            var page = 2
            while (page <= MAX_VUE_PAGES) {
                val pageWebpage = try {
                    http.downloadWebpage("$url?page=$page")
                } catch (error: ExtractionError) {
                    break
                }
                val pageData = extractJsonElement(pageWebpage, VUE_WEBSITE_DATA) as? JsonObject ?: break
                if (pageData.array("videos").orEmpty().isEmpty() &&
                    pageData.array("items").orEmpty().isEmpty()
                ) {
                    break
                }
                entries += vueEntries(pageData)
                page++
            }
        }
        return InfoDict(
            id = pageId,
            title = websiteData.str("title"),
            description = websiteData.str("lead"),
            entries = entries,
            webpageUrl = url,
            extractor = "tvp",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_vuejs_entries`. */
    private fun vueEntries(websiteData: JsonObject): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        fun add(video: JsonObject) {
            val info = vueVideo(video, null, null)
            entries += InfoEntry(id = info.id, title = info.title, url = info.redirectUrl)
        }
        websiteData.obj("latestVideo")?.let(::add)
        for (element in websiteData.array("videos").orEmpty()) {
            (element as? JsonObject)?.let(::add)
        }
        for (element in websiteData.array("items").orEmpty()) {
            (element as? JsonObject)?.let(::add)
        }
        return entries
    }

    /** Upstream `_extract_vue_video`. */
    private fun vueVideo(videoData: JsonElement, pageId: String?, pageUrl: String?): InfoDict {
        val video = when (videoData) {
            is JsonObject -> videoData
            is JsonPrimitive -> ExtractorUtils.parseJson(
                ExtractorUtils.jsToJson(videoData.content),
            ) as? JsonObject ?: JsonObject(emptyMap())

            else -> JsonObject(emptyMap())
        }
        val thumbnails = mutableListOf<Thumbnail>()
        when (val image = video["image"]) {
            is JsonObject -> image.str("url")?.let { thumbnails += Thumbnail(url = it) }
            is JsonArray -> image.forEach { element ->
                (element as? JsonObject)?.str("url")?.let { thumbnails += Thumbnail(url = it) }
            }

            else -> Unit
        }
        val isWebsite = video.str("type") == "website"
        val id = video.str("_id") ?: pageId
        return InfoDict(
            id = id,
            title = video.str("title"),
            description = video.str("lead"),
            duration = video.number("duration"),
            uploadDate = video.number("release_date_long")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            thumbnails = thumbnails,
            webpageUrl = pageUrl,
            redirectUrl = if (isWebsite) {
                ExtractorUtils.urlOrNone(video.str("url"))
            } else {
                id?.let { "tvp:$it" }
            },
            extractor = "tvp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TVP"
        private const val MAX_VUE_PAGES = 100

        private val VUE_DATA = Regex("window\\.__(?:video|news|website|directory)Data\\s*=")
        private val VUE_VIDEO_DATA = Regex("window\\.__(?:news|video)Data\\s*=")
        private val VUE_WEBSITE_DATA = Regex("window\\.__(?:website|directory)Data\\s*=")

        private val CLASSIC_ID_PATTERNS = listOf(
            "<iframe[^>]+src=\"[^\"]*?embed\\.php\\?(?:[^&]+&)*ID=(\\d+)",
            "<iframe[^>]+src=\"[^\"]*?object_id=(\\d+)",
            "object_id\\s*:\\s*'(\\d+)'",
            "data-video-id=\"(\\d+)\"",
            "<script>\\s*tvpabc\\.video\\.init\\(\\s*\\d+,\\s*(\\d+)\\s*\\)\\s*</script>",
        )

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?(?:tvp(?:parlament)?\\.(?:pl|info)|tvpworld\\.com|swipeto\\.pl)/" +
                "(?:(?!\\d+/)[^/]+/)*(?<id>\\d+)(?:[/?#]|$)",
        )
    }
}

/** Upstream `TVPStreamIE`: the stream.tvp.pl channels. */
class TVPStreamIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: ""
        val channelUrl = "https://stream.tvp.pl/?channel_id=$channelId"
        val webpage = http.downloadWebpage(channelUrl)
        val channels = extractJsonElement(webpage, Regex("window\\.__channels\\s*=")) as? JsonArray
            ?: throw ExtractionError.Malformed("The TVP stream page had no channel list.")
        val channel = if (channelId.isNotEmpty()) {
            channels.mapNotNull { it as? JsonObject }
                .firstOrNull { (it["id"] as? JsonPrimitive)?.content == channelId }
        } else {
            channels.firstOrNull() as? JsonObject
        } ?: throw ExtractionError.Unavailable("The TVP channel was not found.")
        val audition = channel.array("items").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.bool("is_live") == true }
            ?: throw ExtractionError.Unavailable("The TVP channel has no live stream.")
        val videoId = (audition["video_id"] as? JsonPrimitive)?.content
            ?: throw ExtractionError.Unavailable("The TVP stream had no video id.")
        return InfoDict(
            id = channelId.ifEmpty { channel.str("id") },
            title = audition.str("title") ?: channel.str("title"),
            isLive = true,
            webpageUrl = url,
            redirectUrl = "tvp:$videoId",
            extractor = "tvp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TVPStream"

        val VALID_URL: Regex = Regex(
            "(?:tvpstream:|https?://(?:tvpstream\\.vod|stream)\\.tvp\\.pl/" +
                "(?:\\?(?:[^&]+[&;])*channel_id=)?)(?<id>\\d*)",
        )
    }
}

/** Upstream `TVPEmbedIE`: the TVPlayer2 JSONP config. */
class TVPEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(
            "https://www.tvp.pl/sess/TVPlayer2/api.php?id=$videoId" +
                "&@method=getTvpConfig&@callback=tvp_embed_callback",
        )
        val payload = jsonpPayload(webpage)
            ?: throw ExtractionError.Malformed("The TVP config response was not JSONP.")
        if (payload.startsWith("null,")) {
            val error = ExtractorUtils.parseJson(payload.substring(5))
            val errorDesc = (error as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("desc") }
            if (errorDesc == "Obiekt wymaga płatności") {
                throw ExtractionError.LoginRequired(
                    "The TVP video requires payment and a sign-in.",
                )
            }
            throw ExtractionError.Unavailable(errorDesc ?: "Unexpected TVP config error.")
        }
        val content = (ExtractorUtils.parseJson(payload) as? JsonObject)?.obj("content")
            ?: throw ExtractionError.Malformed("The TVP config had no content.")
        val info = content.obj("info") ?: JsonObject(emptyMap())
        val isLive = info.bool("isLive") == true
        if (info.bool("isGeoBlocked") == true) {
            throw ExtractionError.GeoRestricted(listOf("PL"))
        }

        val formats = mutableListOf<MediaFormat>()
        for (element in content.array("files").orEmpty()) {
            val file = element as? JsonObject ?: continue
            val videoUrl = ExtractorUtils.urlOrNone(file.str("url")) ?: continue
            val quality = file.obj("quality")
            when {
                ExtractorUtils.determineExt(videoUrl, defaultExt = "") == "m3u8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                ExtractorUtils.determineExt(videoUrl, defaultExt = "") == "mpd" && !isLive -> formats += MediaFormat(
                    formatId = "dash",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "http_dash_segments",
                )

                videoUrl.endsWith(".ism/manifest") -> Unit // ISM is not translated.

                ExtractorUtils.determineExt(videoUrl, defaultExt = "").isNotEmpty() -> formats += MediaFormat(
                    formatId = "direct",
                    url = videoUrl,
                    ext = ExtractorUtils.determineExt(videoUrl, defaultExt = ""),
                    fps = quality?.number("fps"),
                    tbr = quality?.number("bitrate")?.div(1000.0),
                    width = quality?.number("width")?.toLong(),
                    height = quality?.number("height")?.toLong(),
                )
            }
        }

        val thumbnails = mutableListOf<Thumbnail>()
        for (element in content.array("posters").orEmpty()) {
            val poster = element as? JsonObject ?: continue
            val posterUrl = poster.str("src") ?: continue
            if ("{width}" in posterUrl || "{height}" in posterUrl) continue
            thumbnails += Thumbnail(
                url = posterUrl,
                width = poster.number("width")?.toLong(),
                height = poster.number("height")?.toLong(),
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in content.array("subtitles").orEmpty()) {
            val sub = element as? JsonObject ?: continue
            val subUrl = ExtractorUtils.urlOrNone(sub.str("url")) ?: continue
            subtitles += SubtitleTrack(
                language = sub.str("lang") ?: "und",
                formats = listOf(SubtitleFormat(ext = sub.str("type") ?: "vtt", url = subUrl)),
            )
        }

        val rawAge = info.obj("ageGroup")?.number("minAge")?.toInt()
        return InfoDict(
            id = videoId,
            title = info.str("subtitle") ?: info.str("title") ?: info.str("seoTitle"),
            description = info.str("description") ?: info.str("seoDescription"),
            duration = if (isLive) null else info.number("duration"),
            ageLimit = if (rawAge == 1) 0 else rawAge,
            isLive = isLive,
            thumbnails = thumbnails,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "tvp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TVPEmbed"

        val VALID_URL: Regex = Regex(
            "(?:tvp:|https?://(?:[^/]+\\.)?(?:tvp(?:parlament)?\\.pl|tvp\\.info|tvpworld\\.com|" +
                "swipeto\\.pl)/(?:sess/(?:tvplayer\\.php\\?.*?object_id|" +
                "TVPlayer2/(?:embed|api)\\.php\\?.*[Ii][Dd])|shared/details\\.php\\?.*?object_id)=)" +
                "(?<id>\\d+)",
        )
    }
}

/** Upstream `TVPVODBaseIE`: the vod.tvp.pl API helper. */
abstract class TVPVODBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_api`; a 4xx body becomes the typed error. */
    protected suspend fun callVodApi(
        resource: String,
        videoId: String,
        query: Map<String, String> = emptyMap(),
    ): JsonObject {
        val queryString = (mapOf("lang" to "pl", "platform" to "BROWSER") + query)
            .entries.joinToString("&") { (key, value) -> "$key=" + encodeQuery(value) }
        val json = try {
            http.downloadJson("https://vod.tvp.pl/api/products/$resource?$queryString") as? JsonObject
        } catch (error: ExtractionError) {
            throw ExtractionError.Unavailable("Woronicza said: the TVP VOD API rejected the request.")
        }
        return json ?: throw ExtractionError.Malformed("The TVP VOD API returned no object.")
    }

    /** Upstream `_parse_video`. */
    protected fun parseVodVideo(video: JsonObject, withUrl: Boolean = true): InfoDict {
        val thumbnails = mutableListOf<Thumbnail>()
        fun addImage(element: JsonElement?) {
            when (element) {
                is JsonObject -> element.str("url")?.let { thumbnails += Thumbnail(url = it) }
                is JsonArray -> element.forEach(::addImage)
                else -> Unit
            }
        }
        addImage(video["images"])
        return InfoDict(
            id = video.number("id")?.toLong()?.toString(),
            title = video.str("title"),
            description = cleanHtml(video.str("lead") ?: video.str("description")),
            ageLimit = video.number("rating")?.toInt(),
            duration = video.number("duration"),
            thumbnails = thumbnails,
            redirectUrl = if (withUrl) ExtractorUtils.urlOrNone(video.str("webUrl")) else null,
            webpageUrl = null,
            extractor = "tvp",
            extractorKey = ieKey,
        )
    }

    companion object {
        internal fun encodeQuery(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val character = byte.toInt().toChar()
                if (character in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~") {
                    append(character)
                } else {
                    append('%')
                        .append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0xF])
                        .append("0123456789ABCDEF"[byte.toInt() and 0xF])
                }
            }
        }
    }
}

/** Upstream `TVPVODVideoIE`: the VOD and live video pages. */
class TVPVODVideoIE(
    http: ExtractorHttp,
) : TVPVODBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val category = match.groups["category"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val isLive = category == "live,1"
        val entity = if (isLive) "lives" else "vods"
        val info = parseVodVideo(callVodApi("$entity/$videoId", videoId), withUrl = false)
        val playlist = callVodApi("$videoId/videos/playlist", videoId, mapOf("videoType" to "MOVIE"))

        val formats = mutableListOf<MediaFormat>()
        for (element in playlist.obj("sources")?.array("HLS").orEmpty()) {
            val url = ExtractorUtils.urlOrNone((element as? JsonObject)?.str("src")) ?: continue
            formats += MediaFormat(formatId = "hls", url = url, ext = "mp4", protocol = "m3u8_native")
        }
        for (element in playlist.obj("sources")?.array("DASH").orEmpty()) {
            val url = ExtractorUtils.urlOrNone((element as? JsonObject)?.str("src")) ?: continue
            formats += MediaFormat(formatId = "dash", url = url, ext = "mp4", protocol = "http_dash_segments")
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in playlist.array("subtitles").orEmpty()) {
            val sub = element as? JsonObject ?: continue
            val subUrl = ExtractorUtils.urlOrNone(sub.str("url")) ?: continue
            subtitles += SubtitleTrack(
                language = sub.str("language") ?: "und",
                formats = listOf(SubtitleFormat(ext = "ttml", url = subUrl)),
            )
        }
        return info.copy(
            formats = formats,
            subtitles = subtitles,
            isLive = isLive,
            webpageUrl = url,
        )
    }

    companion object {
        const val IE_KEY: String = "TVPVODVideo"

        val VALID_URL: Regex = Regex(
            "https?://vod\\.tvp\\.pl/(?<category>[a-z\\d-]+,\\d+)/[a-z\\d-]+(?<!-odcinki)" +
                "(?:-odcinki,\\d+/odcinek--?\\d+,S-?\\d+E-?\\d+)?,(?<id>\\d+)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `TVPVODSeriesIE`: the series pages. */
class TVPVODSeriesIE(
    http: ExtractorHttp,
) : TVPVODBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadata = callVodApi("vods/serials/$playlistId", playlistId)
        val seasons = callVodApi("vods/serials/$playlistId/seasons", playlistId)
        val entries = mutableListOf<InfoEntry>()
        for (element in seasons.array("items").orEmpty().ifEmpty { seasons.array("seasons").orEmpty() }) {
            val season = element as? JsonObject ?: continue
            val seasonId = season.number("id")?.toLong()?.toString() ?: season.str("id") ?: continue
            val episodes = try {
                callVodApi("vods/serials/$playlistId/seasons/$seasonId/episodes", playlistId)
            } catch (error: ExtractionError) {
                continue
            }
            val items = episodes.array("items").orEmpty().ifEmpty { episodes.array("episodes").orEmpty() }
            for (episodeElement in items) {
                val episode = episodeElement as? JsonObject ?: continue
                val info = parseVodVideo(episode)
                entries += InfoEntry(id = info.id, title = info.title, url = info.redirectUrl)
            }
        }
        return InfoDict(
            id = playlistId,
            title = metadata.str("title"),
            description = cleanHtml(metadata.obj("description")?.str("lead") ?: metadata.str("lead")),
            ageLimit = metadata.number("rating")?.toInt(),
            entries = entries,
            webpageUrl = url,
            extractor = "tvp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TVPVODSeries"

        val VALID_URL: Regex = Regex(
            "https?://vod\\.tvp\\.pl/[a-z\\d-]+,\\d+/[a-z\\d-]+-odcinki,(?<id>\\d+)(?:\\?[^#]+)?(?:#.+)?$",
        )
    }
}

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
                    val raw = html.substring(index, position + 1)
                    return ExtractorUtils.parseJson(ExtractorUtils.jsToJson(raw))
                }
            }
        }
    }
    return null
}

/** The JSONP payload between the first `(` and the last `)`. */
private fun jsonpPayload(webpage: String): String? {
    val start = webpage.indexOf('(')
    val end = webpage.lastIndexOf(')')
    if (start < 0 || end <= start) return null
    return webpage.substring(start + 1, end)
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

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
