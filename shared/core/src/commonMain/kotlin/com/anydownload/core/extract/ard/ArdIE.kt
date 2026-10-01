/*
 * ARD extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `ard.py` from
 * `yt_dlp/extractor/ard.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ard.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the legacy media JSON parser, the page-gateway item API with its
 * player streams/subtitles/chapters, the collection/compilation pagination,
 * and the ARD Audiothek GraphQL item/show queries. HLS masters are recorded
 * as `m3u8_native` and parsed at download time; RTMP-only legacy streams and
 * F4M are not translated. The optional SSO age-verification token needs the
 * `ams` cookie, which the port cannot read, so a `blockedByFsk` item fails
 * typed as a login wall; `series`, `episode`, `episode_number`, and
 * `display_id` are not modeled on the port's InfoDict. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.ard

import com.anydownload.core.extract.Chapter
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Upstream `ARDMediathekBaseIE`: the legacy media JSON parser. */
abstract class ARDMediathekBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_extract_media_info` / `_parse_media_info`. */
    protected suspend fun parseMediaInfo(
        mediaInfoUrl: String,
        videoId: String,
        fsk: Boolean,
    ): InfoDict {
        val mediaInfo = http.downloadJson(mediaInfoUrl) as? JsonObject
            ?: throw ExtractionError.Malformed("The ARD media JSON was not an object.")
        val formats = extractFormats(mediaInfo, videoId)
        if (formats.isEmpty()) {
            if (fsk) {
                throw ExtractionError.NoFormats("This video is only available after 20:00.")
            }
            if (mediaInfo.bool("_geoblocked") == true) {
                throw ExtractionError.GeoRestricted(listOf("DE"))
            }
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        val subtitleUrl = ExtractorUtils.urlOrNone(mediaInfo.str("_subtitleUrl"))
        if (subtitleUrl != null) {
            subtitles += SubtitleTrack(
                language = "de",
                formats = listOf(
                    SubtitleFormat(ext = "ttml", url = subtitleUrl),
                    SubtitleFormat(
                        ext = "vtt",
                        url = subtitleUrl.replace("/ebutt/", "/webvtt/") + ".vtt",
                    ),
                ),
            )
        }

        return InfoDict(
            id = videoId,
            duration = mediaInfo.number("_duration"),
            thumbnails = ExtractorUtils.urlOrNone(mediaInfo.str("_previewImage"))
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            isLive = mediaInfo.bool("_isLive") == true,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = mediaInfoUrl,
            extractor = "ard",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_extract_formats`. */
    private fun extractFormats(mediaInfo: JsonObject, videoId: String): List<MediaFormat> {
        val type = mediaInfo.str("_type")
        val formats = mutableListOf<MediaFormat>()
        for ((num, mediaElement) in mediaInfo.array("_mediaArray").orEmpty().withIndex()) {
            val media = mediaElement as? JsonObject ?: continue
            for (streamElement in media.array("_mediaStreamArray").orEmpty()) {
                val stream = streamElement as? JsonObject ?: continue
                val rawUrls = when (val value = stream["_stream"]) {
                    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }
                    is JsonPrimitive -> listOf(value.content)
                    else -> emptyList()
                }
                val quality = stream.str("_quality")
                val server = stream.str("_server")
                for (streamUrl in rawUrls) {
                    val url = ExtractorUtils.urlOrNone(streamUrl) ?: continue
                    val ext = ExtractorUtils.determineExt(url, defaultExt = "")
                    if (quality != "auto" && ext in listOf("f4m", "m3u8")) continue
                    when (ext) {
                        "f4m" -> Unit // F4M is not translated.
                        "m3u8" -> formats += MediaFormat(
                            formatId = "hls",
                            url = url,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        )

                        else -> {
                            if (server != null && server.startsWith("rtmp")) continue
                            val resolution = Regex("_(?<width>\\d+)x(?<height>\\d+)\\.mp4$").find(url)
                            formats += MediaFormat(
                                formatId = "a$num-$ext-$quality",
                                url = url,
                                ext = ext.takeIf { it.isNotEmpty() },
                                width = resolution?.groups?.get("width")?.value?.toLongOrNull(),
                                height = resolution?.groups?.get("height")?.value?.toLongOrNull(),
                                vcodec = if (type == "audio") MediaFormat.CODEC_NONE else null,
                            )
                        }
                    }
                }
            }
        }
        return formats
    }
}

/** Upstream `ARDBetaMediathekIE`: the page-gateway item pages. */
class ARDBetaMediathekIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val pageData = http.downloadJson(
            "https://api.ardmediathek.de/page-gateway/pages/ard/item/$displayId" +
                "?embedded=false&mcV6=true",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ARD page-gateway response was not an object.")

        val videoId = pageData.obj("tracking")?.obj("atiCustomVars")?.number("contentId")?.toLong()?.toString()
            ?: displayId
        val playerData = pageData.array("widgets").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("type") in listOf("player_ondemand", "player_live") }
            ?: throw ExtractionError.Unavailable("The ARD page had no player widget.")
        val isLive = playerData.str("type") == "player_live"
        val mediaData = playerData.obj("mediaCollection")?.obj("embedded")
            ?: throw ExtractionError.Malformed("The ARD player had no media collection.")
        if (playerData.bool("blockedByFsk") == true) {
            throw ExtractionError.LoginRequired(
                "This video is only available for age verified users or after 22:00.",
            )
        }

        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        for (streamElement in mediaData.array("streams").orEmpty()) {
            val stream = streamElement as? JsonObject ?: continue
            val kind = stream.str("kind")
            val preference = if (kind == "main") 1 else null
            for (mediaElement in stream.array("media").orEmpty()) {
                val media = mediaElement as? JsonObject ?: continue
                val mediaUrl = ExtractorUtils.urlOrNone(media.str("url")) ?: continue
                val audio = media.array("audios")?.firstOrNull() as? JsonObject
                val audioKind = (audio?.str("kind") ?: "").replace("standard", "")
                val languageCode = audio?.str("languageCode") ?: "deu"
                val language = joinNonEmpty(languageCode, audioKind) ?: languageCode
                val languagePreference = if (language == "deu") 10.0 else -10.0
                if (ExtractorUtils.determineExt(mediaUrl, defaultExt = "") == "m3u8") {
                    formats += MediaFormat(
                        formatId = "hls-${kind ?: "main"}",
                        url = mediaUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                        language = language,
                        languagePreference = languagePreference,
                        preference = preference,
                    )
                } else {
                    formats += MediaFormat(
                        formatId = "http-${kind ?: "main"}",
                        url = mediaUrl,
                        ext = ExtractorUtils.determineExt(mediaUrl, defaultExt = "").takeIf { it.isNotEmpty() },
                        formatNote = media.str("forcedLabel"),
                        width = media.number("maxHResolutionPx")?.toLong(),
                        height = media.number("maxVResolutionPx")?.toLong(),
                        vcodec = media.str("videoCodec"),
                        language = language,
                        languagePreference = languagePreference,
                        preference = preference,
                    )
                }
            }
        }
        for (subElement in mediaData.array("subtitles").orEmpty()) {
            val sub = subElement as? JsonObject ?: continue
            val language = sub.str("languageCode") ?: "deu"
            for (sourceElement in sub.array("sources").orEmpty()) {
                val source = sourceElement as? JsonObject ?: continue
                val subUrl = ExtractorUtils.urlOrNone(source.str("url")) ?: continue
                val ext = when (source.str("kind")) {
                    "webvtt" -> "vtt"
                    "ebutt" -> "ttml"
                    else -> ExtractorUtils.determineExt(subUrl, defaultExt = "vtt")
                }
                subtitles += SubtitleTrack(
                    language = language,
                    formats = listOf(SubtitleFormat(ext = ext, url = subUrl)),
                )
            }
        }

        val chapters = mutableListOf<Chapter>()
        val chapterArray = mediaData.obj("pluginData")?.obj("jumpmarks@all")?.array("chapterArray")
        for (element in chapterArray.orEmpty()) {
            val chapter = element as? JsonObject ?: continue
            val start = chapter.number("chapterTime")?.toDouble() ?: continue
            chapters += Chapter(startTime = start, title = chapter.str("chapterTitle"))
        }

        val meta = mediaData.obj("meta") ?: JsonObject(emptyMap())
        val fskRating = pageData.str("fskRating")
        return InfoDict(
            id = videoId,
            title = meta.str("title"),
            description = meta.str("synopsis"),
            duration = meta.number("durationSeconds"),
            uploadDate = meta.str("broadcastedOnDateTime")?.let(ExtractorUtils::unifiedStrdate),
            channel = meta.str("clipSourceName"),
            ageLimit = fskRating?.removePrefix("FSK")?.let(ExtractorUtils::parseAgeLimit),
            isLive = isLive,
            thumbnails = meta.array("images")?.firstOrNull()?.let { element ->
                ExtractorUtils.urlOrNone((element as? JsonObject)?.str("url"))
            }?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            subtitles = subtitles,
            chapters = chapters,
            webpageUrl = url,
            extractor = "ard",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ARDBetaMediathek"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:beta|www)\\.)?ardmediathek\\.de/(?:[^/]+/)?" +
                "(?:player|live|video)/(?:[^?#]+/)?(?<id>[a-zA-Z0-9]+)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `ARDMediathekCollectionIE`: the sendung/serie/sammlung pages. */
class ARDMediathekCollectionIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayId"]?.value
        val playlistType = match.groups["playlist"]?.value
        val seasonNumber = match.groups["season"]?.value
        val version = match.groups["version"]?.value

        suspend fun callApi(pageNumber: Int): JsonObject {
            val apiPath = if (playlistType == "sammlung") "compilations/ard" else "widgets/ard/asset"
            var query = "?pageNumber=$pageNumber&pageSize=$PAGE_SIZE"
            if (seasonNumber != null) {
                query += "&seasoned=true&seasonNumber=$seasonNumber" +
                    "&withOriginalversion=${version == "OV"}&withAudiodescription=${version == "AD"}"
            }
            return http.downloadJson("https://api.ardmediathek.de/page-gateway/$apiPath/$playlistId$query")
                as? JsonObject ?: throw ExtractionError.Malformed("The ARD playlist response was not an object.")
        }

        val entries = mutableListOf<InfoEntry>()
        var pageNumber = 0
        var iterations = 0
        while (iterations < MAX_PAGES) {
            iterations++
            val page = callApi(pageNumber)
            val teasers = page.array("teasers").orEmpty().mapNotNull { it as? JsonObject }
            if (teasers.isEmpty()) break
            for (item in teasers) {
                val itemId = item.obj("links")?.obj("target")?.str("urlId")
                    ?: item.obj("links")?.obj("target")?.str("id")
                    ?: item.str("id")
                    ?: continue
                if (itemId == playlistId) continue
                val mode = if (item.str("type") == "compilation") "sammlung" else "video"
                entries += InfoEntry(
                    id = item.str("id"),
                    title = item.str("longTitle"),
                    url = "https://www.ardmediathek.de/$mode/$itemId",
                )
            }
            pageNumber++
        }

        val pageData = callApi(0)
        val fullId = joinNonEmpty(playlistId, seasonNumber, version, delim = "_") ?: playlistId
        return InfoDict(
            id = fullId,
            title = pageData.str("title") ?: displayId,
            description = pageData.str("synopsis"),
            entries = entries,
            webpageUrl = url,
            extractor = "ard",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ARDMediathekCollection"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:beta|www)\\.)?ardmediathek\\.de/(?:[^/?#]+/)?" +
                "(?<playlist>sendung|serie|sammlung)/(?:(?<displayId>[^?#]+?)/)?" +
                "(?<id>[a-zA-Z0-9]+)(?:/(?<season>\\d+)(?:/(?<version>OV|AD))?)?/?(?:[?#]|$)",
        )
    }
}

/** Upstream `ARDAudiothekBaseIE`: the GraphQL query helper. */
abstract class ARDAudiothekBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_graphql_query`. */
    protected suspend fun graphqlQuery(urn: String, query: String): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject { put("id", urn) })
        }.toString().encodeToByteArray()
        val json = http.downloadJson(
            "https://api.ardaudiothek.de/graphql",
            method = com.anydownload.core.platform.HttpMethods.POST,
            headers = mapOf("content-type" to "application/json"),
            body = body,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ARD Audiothek response was not an object.")
        return json.obj("data") ?: throw ExtractionError.Malformed("The ARD Audiothek response had no data.")
    }
}

/** Upstream `ARDAudiothekIE`: one episode/section/extra. */
class ARDAudiothekIE(
    http: ExtractorHttp,
) : ARDAudiothekBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val urn = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val item = graphqlQuery(urn, QUERY_ITEM).obj("item")
            ?: throw ExtractionError.Unavailable("The ARD Audiothek item was not found.")
        val formats = item.array("audioList").orEmpty().mapNotNull { element ->
            val audio = element as? JsonObject ?: return@mapNotNull null
            val audioUrl = ExtractorUtils.urlOrNone(audio.str("href")) ?: return@mapNotNull null
            MediaFormat(
                formatId = audio.str("distributionType"),
                url = audioUrl,
                ext = ExtractorUtils.determineExt(audioUrl, defaultExt = "mp3"),
                abr = audio.number("audioBitrate"),
                acodec = audio.str("audioCodec"),
                vcodec = MediaFormat.CODEC_NONE,
            )
        }
        val thumbnail = item.obj("image")?.str("url1X1")
            ?.substringBefore('?')?.substringBefore('#')
        return InfoDict(
            id = urn,
            title = item.str("title"),
            description = item.str("description"),
            duration = item.number("duration"),
            uploadDate = item.str("startDate")?.let(ExtractorUtils::unifiedStrdate),
            channel = item.obj("programSet")?.obj("publicationService")?.str("organizationName"),
            thumbnails = ExtractorUtils.urlOrNone(thumbnail)?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            webpageUrl = url,
            extractor = "ard",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ARDAudiothek"

        val VALID_URL: Regex = Regex(
            "https:?//(?:www\\.)?ard(?:audiothek|sounds)\\.de/episode/" +
                "(?<id>urn:ard:(?:episode|section|extra):[a-f0-9]{16})",
        )

        private val QUERY_ITEM = """
            query(${'$'}id: ID!) {
                item(id: ${'$'}id) {
                    audioList { href distributionType audioBitrate audioCodec }
                    show { title }
                    image { url1X1 }
                    programSet { publicationService { organizationName } }
                    description
                    title
                    duration
                    startDate
                    episodeNumber
                }
            }
        """.trimIndent()
    }
}

/** Upstream `ARDAudiothekPlaylistIE`: the show listing. */
class ARDAudiothekPlaylistIE(
    http: ExtractorHttp,
) : ARDAudiothekBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val urn = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val show = graphqlQuery(urn, QUERY_PLAYLIST).obj("show")
            ?: throw ExtractionError.Unavailable("The ARD Audiothek show was not found.")
        val entries = mutableListOf<InfoEntry>()
        for (element in show.obj("items")?.array("nodes").orEmpty()) {
            val itemUrl = ExtractorUtils.urlOrNone((element as? JsonObject)?.str("url")) ?: continue
            val id = ARDAudiothekIE.VALID_URL.find(itemUrl)?.groups?.get("id")?.value
            entries += InfoEntry(id = id, url = itemUrl)
        }
        return InfoDict(
            id = urn,
            title = show.str("title"),
            description = show.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "ard",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ARDAudiothekPlaylist"

        val VALID_URL: Regex = Regex(
            "https:?//(?:www\\.)?ard(?:audiothek|sounds)\\.de/sendung/" +
                "(?<playlist>[\\w-]+)/(?<id>urn:ard:show:[a-f0-9]{16})",
        )

        private val QUERY_PLAYLIST = """
            query(${'$'}id: ID!) {
                show(id: ${'$'}id) {
                    title
                    description
                    items(filter: { isPublished: { equalTo: true } }) {
                        nodes { url }
                    }
                }
            }
        """.trimIndent()
    }
}

// ------------------------------------------------------------------ helpers

private fun joinNonEmpty(vararg values: String?, delim: String = "-"): String? =
    values.filterNotNull().filter { it.isNotEmpty() }.joinToString(delim).ifEmpty { null }

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
