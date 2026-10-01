/*
 * ZDF extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `zdf.py` from
 * `yt_dlp/extractor/zdf.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `zdf.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the API token, the `VideoByCanonical` GraphQL call, the
 * `mediathekV2/document` fallback, the PTMD format walk (HLS and
 * progressive MP4/WebM with aspect-ratio widths, codecs, and the language
 * preferences), captions, thumbnails, chapters, and the channel smart
 * collection with its persisted-query season pages. HLS masters are recorded
 * as `m3u8_native` and parsed at download time. The smuggled DGS variant flag
 * is not carried (all variants are treated as non-DGS), the token cache is
 * per instance rather than on disk, and `series`/`series_id`,
 * `episode_number`, `season_number`, and the `_old_archive_ids` are not
 * modeled on the port's InfoDict. No cookie, account token, or signed media
 * URL is stored here; the API token is fetched per extraction and never
 * persisted.
 */
package com.anydownload.core.extract.zdf

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
import com.anydownload.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Upstream `ZDFBaseIE`: the token, PTMD, and GraphQL helpers. */
abstract class ZDFBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    private var cachedTokenType: String? = null
    private var cachedTokenValue: String? = null

    /** Upstream `_get_api_token`; the cache is per instance. */
    protected suspend fun apiToken(): String {
        cachedTokenValue?.let { return "${cachedTokenType ?: "Bearer"} $it" }
        val json = http.downloadJson("https://zdf-prod-futura.zdf.de/mediathekV2/token") as? JsonObject
            ?: throw ExtractionError.Malformed("The ZDF token response was not an object.")
        val type = json.str("type") ?: "Bearer"
        val token = json.str("token")
            ?: throw ExtractionError.Malformed("The ZDF token response had no token.")
        cachedTokenType = type
        cachedTokenValue = token
        return "$type $token"
    }

    /** Upstream `_call_api`. */
    private suspend fun callApi(
        url: String,
        item: String,
        apiToken: String?,
    ): JsonObject {
        val headers = if (apiToken != null) mapOf("api-auth" to apiToken) else emptyMap()
        return http.downloadJson(url, headers = headers) as? JsonObject
            ?: throw ExtractionError.Malformed("The ZDF $item response was not an object.")
    }

    /** Upstream `_download_graphql`; a body makes it a POST, else a GET. */
    protected suspend fun downloadGraphql(
        itemId: String,
        dataDesc: String,
        query: Map<String, String>? = null,
        body: String? = null,
    ): JsonObject {
        val headers = mutableMapOf(
            "api-auth" to apiToken(),
            "apollo-require-preflight" to "true",
        )
        val url: String
        val payload: ByteArray?
        if (body != null) {
            url = "https://api.zdf.de/graphql"
            headers["content-type"] = "application/json"
            payload = body.encodeToByteArray()
        } else {
            val queryString = query.orEmpty().entries.joinToString("&") { (key, value) ->
                encodeQuery(key) + "=" + encodeQuery(value)
            }
            url = "https://api.zdf.de/graphql?$queryString"
            payload = null
        }
        return http.downloadJson(
            url,
            method = if (payload != null) HttpMethods.POST else HttpMethods.GET,
            headers = headers,
            body = payload,
        ) as? JsonObject
            ?: throw ExtractionError.Malformed("The ZDF $dataDesc response was not an object.")
    }

    /** Upstream `_extract_ptmd` without the smuggled DGS flag. */
    protected suspend fun extractPtmd(
        ptmdUrls: List<String>,
        videoId: String,
        apiToken: String?,
        aspectRatio: Double?,
    ): InfoDict {
        var contentId: String? = null
        var duration: Double? = null
        val formats = mutableListOf<MediaFormat>()
        val captions = mutableListOf<JsonObject>()
        val seenUrls = mutableSetOf<String>()

        for (ptmdUrl in ptmdUrls) {
            val ptmd = callApi(ptmdUrl, "PTMD data", apiToken)
            val basename = ptmd.str("basename")
                ?: ExtractorUtils.searchRegex("/vod/ptmd/[^/?#]+/(\\w+)", ptmdUrl, default = null)
            if (contentId == null) contentId = basename
            if (duration == null) {
                duration = ptmd.obj("attributes")?.obj("duration")?.number("value")?.div(1000.0)
            }
            captions += ptmd.array("captions").orEmpty().mapNotNull { it as? JsonObject }

            for (streamElement in ptmd.array("priorityList").orEmpty()) {
                val stream = streamElement as? JsonObject ?: continue
                for (formatElement in stream.array("formitaeten").orEmpty()) {
                    val format = formatElement as? JsonObject ?: continue
                    for (qualityElement in format.array("qualities").orEmpty()) {
                        val quality = qualityElement as? JsonObject ?: continue
                        for (variantElement in quality.obj("audio")?.array("tracks").orEmpty()) {
                            val variant = variantElement as? JsonObject ?: continue
                            val formatUrl = ExtractorUtils.urlOrNone(variant.str("uri")) ?: continue
                            if (!seenUrls.add(formatUrl)) continue
                            val ext = ExtractorUtils.determineExt(formatUrl, defaultExt = "")
                            val built: List<MediaFormat> = when (ext) {
                                "m3u8" -> listOf(
                                    MediaFormat(
                                        formatId = joinNonEmpty("hls", stream.str("type")),
                                        url = formatUrl,
                                        ext = "mp4",
                                        protocol = "m3u8_native",
                                        language = languageCode(
                                            variant.str("language") ?: format.str("language"),
                                        ),
                                        filesize = variant.number("filesize")?.toLong(),
                                    ),
                                )

                                "mp4", "webm" -> {
                                    val height = quality.number("highestVerticalResolution")?.toLong()
                                    val codecs = ExtractorUtils.parseCodecs(quality.str("mimeCodec"))
                                    listOf(
                                        MediaFormat(
                                            formatId = joinNonEmpty("http", stream.str("type")),
                                            url = formatUrl,
                                            ext = ext,
                                            vcodec = codecs.vcodec,
                                            acodec = codecs.acodec,
                                            width = if (aspectRatio != null && height != null) {
                                                (aspectRatio * height).toLong()
                                            } else {
                                                null
                                            },
                                            height = height,
                                            filesize = variant.number("filesize")?.toLong(),
                                            tbr = ExtractorUtils.searchRegex(
                                                "_(\\d+)k_",
                                                formatUrl,
                                                default = null,
                                            )?.toDoubleOrNull(),
                                            language = languageCode(
                                                variant.str("language") ?: format.str("language"),
                                            ),
                                        ),
                                    )
                                }

                                else -> emptyList()
                            }
                            val formatClass = variant.str("class")
                            for (item in built) {
                                val isAudioOnly = item.vcodec == MediaFormat.CODEC_NONE
                                formats += item.copy(
                                    formatNote = joinNonEmpty(
                                        if (!isAudioOnly) formatClass else null,
                                        item.formatNote,
                                        delim = ", ",
                                    ),
                                    preference = -1,
                                    languagePreference = languagePreference(
                                        isAudioOnly = isAudioOnly,
                                        formatNote = item.formatNote,
                                        formatClass = formatClass,
                                        language = item.language,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        return InfoDict(
            id = contentId ?: videoId,
            duration = duration,
            formats = formats,
            subtitles = extractSubtitles(captions),
            extractor = "zdf",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_extract_subtitles`. */
    private fun extractSubtitles(captions: List<JsonObject>): List<SubtitleTrack> {
        val seen = mutableSetOf<String>()
        val byLanguage = linkedMapOf<String, MutableList<SubtitleFormat>>()
        for (caption in captions) {
            val url = ExtractorUtils.urlOrNone(caption.str("uri")) ?: continue
            if (!seen.add(url)) continue
            val language = caption.str("language") ?: "deu"
            byLanguage.getOrPut(language) { mutableListOf() } += SubtitleFormat(ext = "vtt", url = url)
        }
        return byLanguage.map { (language, formats) -> SubtitleTrack(language = language, formats = formats) }
    }

    /** Upstream `_extract_thumbnails`. */
    protected fun extractThumbnails(source: JsonObject?): List<Thumbnail> {
        val list = source ?: return emptyList()
        return list.mapNotNull { (formatId, value) ->
            val url = ExtractorUtils.urlOrNone((value as? JsonPrimitive)?.content) ?: return@mapNotNull null
            val resolution = Regex("(\\d+|auto)[Xx](\\d+|auto)").find(formatId)
            Thumbnail(
                id = formatId,
                url = url,
                preference = if (formatId == "original") 1 else 0,
                width = resolution?.groupValues?.get(1)?.toLongOrNull(),
                height = resolution?.groupValues?.get(2)?.toLongOrNull(),
            )
        }
    }

    /** Upstream `_parse_aspect_ratio`. */
    protected fun parseAspectRatio(aspectRatio: String?): Double? {
        if (aspectRatio.isNullOrEmpty()) return null
        val match = Regex("(?<width>\\d+):(?<height>\\d+)").find(aspectRatio) ?: return null
        val width = match.groups["width"]?.value?.toDoubleOrNull() ?: return null
        val height = match.groups["height"]?.value?.toDoubleOrNull() ?: return null
        return if (height == 0.0) null else width / height
    }

    /** Upstream `_extract_chapters`. */
    protected fun extractChapters(data: JsonArray?): List<Chapter> =
        data.orEmpty().mapNotNull { element ->
            val chapter = element as? JsonObject ?: return@mapNotNull null
            val start = chapter.number("anchorOffset") ?: return@mapNotNull null
            Chapter(startTime = start, title = chapter.str("anchorLabel"))
        }
}

/** Upstream `ZDFIE`: the zdf.de video pages and sister sites. */
class ZDFIE(
    http: ExtractorHttp,
) : ZDFBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = null,
) {
    override fun suitable(url: String): Boolean = VOD_PATTERNS.any { it.containsMatchIn(url) }

    override suspend fun extract(url: String): InfoDict {
        val match = VOD_PATTERNS.firstNotNullOfOrNull { it.find(url) }
            ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val body = buildJsonObject {
            put("operationName", "VideoByCanonical")
            put("query", GRAPHQL_QUERY)
            put(
                "variables",
                buildJsonObject { put("canonical", videoId) },
            )
        }.toString()
        val graphql = downloadGraphql(videoId, "video metadata", body = body)
        val videoData = graphql.obj("data")?.obj("videoByCanonical")
        if (videoData == null) {
            return extractFallback(videoId, url)
        }

        var aspectRatio: Double? = null
        var isLive = false
        val ptmdUrls = mutableListOf<String>()
        for (element in videoData.obj("currentMedia")?.array("nodes").orEmpty()) {
            val node = element as? JsonObject ?: continue
            val template = node.str("ptmdTemplate") ?: continue
            ptmdUrls += urlJoin("https://api.zdf.de", template.replace("{playerId}", "android_native_6"))
            if (node["liveMediaType"] != null) isLive = true
            if (aspectRatio == null) aspectRatio = parseAspectRatio(node.str("aspectRatio"))
        }

        val ptmd = extractPtmd(ptmdUrls, videoId, apiToken(), aspectRatio)
        val chapters = extractChapters(
            videoData.obj("currentMedia")?.array("nodes")?.firstOrNull()?.let { it as? JsonObject }
                ?.obj("streamAnchorTags")?.array("nodes"),
        )
        return ptmd.copy(
            id = videoId,
            title = videoData.str("title"),
            description = videoData.str("leadParagraph")
                ?: videoData.obj("teaser")?.str("description"),
            uploadDate = videoData.str("editorialDate")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = extractThumbnails(videoData.obj("teaser")?.obj("image")?.obj("list")),
            chapters = chapters,
            isLive = isLive,
            webpageUrl = url,
        )
    }

    /** Upstream `_extract_fallback` for the sister-site documents. */
    private suspend fun extractFallback(documentId: String, url: String): InfoDict {
        val video = http.downloadJson(
            "https://zdf-prod-futura.zdf.de/mediathekV2/document/$documentId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ZDF fallback response was not an object.")
        val document = video.obj("document")
            ?: throw ExtractionError.Malformed("The ZDF fallback response had no document.")
        val ptmdUrl = ExtractorUtils.urlOrNone(document.str("streamApiUrlAndroid"))
            ?: document.array("streams")?.firstOrNull()?.let { element ->
                ExtractorUtils.urlOrNone((element as? JsonObject)?.str("streamApiUrlAndroid"))
            }
            ?: throw ExtractionError.Unavailable("The ZDF document had no PTMD URL.")

        val thumbnails = mutableListOf<Thumbnail>()
        for ((key, value) in document.obj("teaserBild").orEmpty()) {
            val thumbnail = value as? JsonObject ?: continue
            val thumbnailUrl = ExtractorUtils.urlOrNone(thumbnail.str("url")) ?: continue
            thumbnails += Thumbnail(
                url = thumbnailUrl,
                id = key,
                width = thumbnail.number("width")?.toLong(),
                height = thumbnail.number("height")?.toLong(),
            )
        }

        val ptmd = extractPtmd(listOf(ptmdUrl), documentId, apiToken(), null)
        return ptmd.copy(
            id = documentId,
            title = document.str("titel"),
            description = document.str("beschreibung"),
            uploadDate = (document.str("date") ?: video.obj("meta")?.str("editorialDate"))
                ?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = thumbnails,
            subtitles = extractFallbackSubtitles(document),
            webpageUrl = url,
        )
    }

    /** Upstream `document.captions` through `_extract_subtitles`. */
    private fun extractFallbackSubtitles(document: JsonObject): List<SubtitleTrack> {
        val captions = document.array("captions").orEmpty().mapNotNull { it as? JsonObject }
        val seen = mutableSetOf<String>()
        val byLanguage = linkedMapOf<String, MutableList<SubtitleFormat>>()
        for (caption in captions) {
            val captionUrl = ExtractorUtils.urlOrNone(caption.str("uri")) ?: continue
            if (!seen.add(captionUrl)) continue
            byLanguage.getOrPut(caption.str("language") ?: "deu") { mutableListOf() } +=
                SubtitleFormat(ext = "vtt", url = captionUrl)
        }
        return byLanguage.map { (language, formats) -> SubtitleTrack(language = language, formats = formats) }
    }

    companion object {
        const val IE_KEY: String = "ZDF"

        val VOD_PATTERNS: List<Regex> = listOf(
            Regex("https?://(?:www\\.)?zdf\\.de/(?:video|play)/(?:[^/?#]+/)*(?<id>[^/?#]+)"),
            Regex("https?://(?:www\\.)?zdf\\.de/(?:[^/?#]+/)*(?<id>[^/?#]+)\\.html"),
            Regex("https?://(?:www\\.)?(?:zdfheute|logo)\\.de/(?:[^/?#]+/)*(?<id>[^/?#]+)\\.html"),
        )

        /** Upstream `_GRAPHQL_QUERY`. */
        private val GRAPHQL_QUERY = """
            query VideoByCanonical(${'$'}canonical: String!) {
              videoByCanonical(canonical: ${'$'}canonical) {
                canonical
                title
                leadParagraph
                editorialDate
                teaser {
                  description
                  image {
                    list
                  }
                }
                episodeInfo {
                  episodeNumber
                  seasonNumber
                }
                smartCollection {
                  canonical
                  title
                }
                currentMedia {
                  nodes {
                    ptmdTemplate
                    ... on VodMedia {
                      duration
                      aspectRatio
                      streamAnchorTags {
                        nodes {
                          anchorOffset
                          anchorLabel
                        }
                      }
                      vodMediaType
                      label
                    }
                    ... on LiveMedia {
                      start
                      stop
                      encryption
                      liveMediaType
                      label
                    }
                    id
                  }
                }
              }
            }
        """.trimIndent()
    }
}

/** Upstream `ZDFChannelIE`: the zdf.de smart-collection pages. */
class ZDFChannelIE(
    http: ExtractorHttp,
) : ZDFBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override fun suitable(url: String): Boolean =
        !ZDFIE.VOD_PATTERNS.any { it.containsMatchIn(url) } && super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        var canonicalId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        canonicalId = runCatching { http.followRedirects(url) }
            .getOrDefault(url)
            .let { finalUrl -> VALID_URL.find(finalUrl)?.groups?.get("id")?.value ?: canonicalId }
        val seasonNumber = Regex("[?&]staffel=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
        val playlistId = joinNonEmpty(
            canonicalId,
            seasonNumber?.let { "s$it" },
        ) ?: canonicalId

        val collectionVariables = buildJsonObject {
            put("canonical", canonicalId)
            put("videoPageSize", 100)
        }.toString()
        val extensions = buildJsonObject {
            put(
                "persistedQuery",
                buildJsonObject {
                    put("version", 1)
                    put("sha256Hash", "cb49420e133bd668ad895a8cea0e65cba6aa11ac1cacb02341ff5cf32a17cd02")
                },
            )
        }.toString()
        val collection = downloadGraphql(
            playlistId,
            "smart collection data",
            query = mapOf(
                "operationName" to "GetSmartCollectionByCanonical",
                "variables" to collectionVariables,
                "extensions" to extensions,
            ),
        ).obj("data")?.obj("smartCollectionByCanonical")
            ?: throw ExtractionError.Malformed("The ZDF collection response had no collection.")
        val videoData = collection.obj("video")
        val seasonNumbers = collection.obj("seasons")?.array("seasons").orEmpty()
            .mapNotNull { (it as? JsonObject)?.number("number")?.toInt() }

        val videoUrl = videoData?.str("sharingUrl")
        if (videoData != null && videoUrl != null) {
            return InfoDict(
                id = videoData.str("canonical") ?: playlistId,
                webpageUrl = url,
                redirectUrl = videoUrl,
                extractor = "zdf",
                extractorKey = IE_KEY,
            )
        }
        if (seasonNumber != null && seasonNumber !in seasonNumbers) {
            throw ExtractionError.Unavailable("Season $seasonNumber was not found in the collection.")
        }

        val entries = mutableListOf<InfoEntry>()
        for ((seasonIndex, number) in seasonNumbers.withIndex()) {
            if (seasonNumber != null && seasonNumber != number) continue
            var cursor: String? = null
            var pageNumber = 1
            while (true) {
                val variables = buildJsonObject {
                    put("seasonIndex", seasonIndex)
                    put("canonical", canonicalId)
                    put("episodesPageSize", PAGE_SIZE)
                    if (cursor != null) put("episodesAfter", cursor)
                }.toString()
                val seasonExtensions = buildJsonObject {
                    put(
                        "persistedQuery",
                        buildJsonObject {
                            put("version", 1)
                            put(
                                "sha256Hash",
                                "9412a0f4ac55dc37d46975d461ec64bfd14380d815df843a1492348f77b5c99a",
                            )
                        },
                    )
                }.toString()
                val page = downloadGraphql(
                    playlistId,
                    "season $number page $pageNumber JSON",
                    query = mapOf(
                        "operationName" to "seasonByCanonical",
                        "variables" to variables,
                        "extensions" to seasonExtensions,
                    ),
                ).obj("data")?.obj("smartCollectionByCanonical")
                val nodes = page?.obj("seasons")?.array("nodes").orEmpty()
                for (node in nodes) {
                    val nodeObject = node as? JsonObject ?: continue
                    for (episodeElement in nodeObject.obj("episodes")?.array("nodes").orEmpty()) {
                        val episode = episodeElement as? JsonObject ?: continue
                        val sharingUrl = ExtractorUtils.urlOrNone(episode.str("sharingUrl")) ?: continue
                        entries += InfoEntry(
                            id = episode.str("canonical"),
                            title = episode.obj("teaser")?.str("title"),
                            url = sharingUrl,
                        )
                    }
                }
                val pageInfo = nodes.lastOrNull()?.let { it as? JsonObject }
                    ?.obj("episodes")?.obj("pageInfo")
                if (pageInfo?.bool("hasNextPage") != true) break
                cursor = pageInfo.str("endCursor") ?: break
                pageNumber++
            }
        }

        return InfoDict(
            id = playlistId,
            title = joinNonEmpty(
                collection.str("title"),
                seasonNumber?.let { "Season $it" },
                delim = " - ",
            ),
            description = collection.str("infoText"),
            entries = entries,
            webpageUrl = url,
            extractor = "zdf",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ZDFChannel"
        private const val PAGE_SIZE = 24

        val VALID_URL: Regex = Regex("https?://www\\.zdf\\.de/(?:[^/?#]+/)*(?<id>[^/?#]+)")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `join_nonempty` with a configurable delimiter. */
private fun joinNonEmpty(vararg values: String?, delim: String = "-"): String? =
    values.filterNotNull().filter { it.isNotEmpty() }.joinToString(delim).ifEmpty { null }

/**
 * Upstream `ISO639Utils.short2long` for the common codes; 3-letter values
 * pass through, 2-letter values map to their ISO 639-2 form.
 */
private fun languageCode(value: String?): String? {
    val text = value?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.length == 3) return text
    return SHORT_TO_LONG[text] ?: text
}

private val SHORT_TO_LONG = mapOf(
    "de" to "deu",
    "en" to "eng",
    "fr" to "fra",
    "es" to "spa",
    "it" to "ita",
    "pt" to "por",
    "nl" to "nld",
    "pl" to "pol",
    "tr" to "tur",
    "ru" to "rus",
    "ar" to "ara",
)

/** Upstream `_language_preference` for the format class and language. */
private fun languagePreference(
    isAudioOnly: Boolean,
    formatNote: String?,
    formatClass: String?,
    language: String?,
): Double = when {
    (isAudioOnly && formatNote == "Audiodeskription") || (!isAudioOnly && formatClass == "ad") -> -10.0
    language == "deu" && formatClass == "main" -> 10.0
    language == "deu" -> 5.0
    formatClass == "main" -> 1.0
    else -> -1.0
}

/** `urllib.parse.urljoin` for the ZDF template URLs. */
private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("/") -> base.trimEnd('/') + value
    else -> base.trimEnd('/') + "/" + value
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
private const val HEX_DIGITS = "0123456789ABCDEF"

/** `urllib.parse.urlencode` component encoding, `+` for space. */
private fun encodeQuery(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val character = byte.toInt().toChar()
        when {
            character in UNRESERVED -> append(character)
            character == ' ' -> append('+')
            else -> append('%')
                .append(HEX_DIGITS[(byte.toInt() shr 4) and 0xF])
                .append(HEX_DIGITS[byte.toInt() and 0xF])
        }
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
