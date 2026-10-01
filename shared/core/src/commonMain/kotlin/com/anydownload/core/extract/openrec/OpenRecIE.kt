/*
 * OpenRec / mellow-fan extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `openrec.py` from
 * `yt_dlp/extractor/openrec.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `openrec.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `window.pageStore` state, the mellow-fan v5 API metadata and
 * media detail, the live/capture/movie pages, the user playlist and channel
 * listings, and the public search endpoints. HLS masters are recorded as
 * `m3u8_native` and parsed at download time. The port cannot read the
 * `access_token`/`random`/`token`/`uuid` cookies, so the email login and the
 * authenticated playlist path are not translated (a `users/me` 401 is
 * treated as a guest, as upstream expects); premium/subscription/PPV content
 * fails typed as a login wall. The live comment subtitles (JSON2XML/SRT) are
 * not translated, and `cast`/`categories`/`tags` are not modeled on the
 * port's InfoDict. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.openrec

import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `OpenRecBaseIE`: the pageStore and v5 API helpers. */
abstract class OpenRecBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_api`; a `users/me` 401 is the guest state. */
    protected suspend fun callApi(path: String, itemId: String, expected401: Boolean = false): JsonObject? {
        return try {
            http.downloadJson("$API_BASE/$path", headers = SITE_HEADERS) as? JsonObject
        } catch (error: ExtractionError) {
            if (expected401) null else throw error
        }
    }

    /** Upstream `_extract_pagestore`. */
    protected fun extractPageStore(webpage: String, videoId: String): JsonObject {
        val encoded = Regex(
            "window\\.pageStore\\s*=\\s*JSON\\.parse\\s*\\(\\s*decodeURIComponent\\s*" +
                "\\(\\s*([\"'])(?<json>.*?)\\1\\s*\\)\\s*\\)",
            RegexOption.DOT_MATCHES_ALL,
        ).find(webpage)?.groups?.get("json")?.value
        if (encoded != null) {
            val decoded = percentDecode(encoded)
            return ExtractorUtils.parseJson(decoded) as? JsonObject
                ?: throw ExtractionError.Malformed("The OpenRec page store was not an object.")
        }
        return extractJsonElement(webpage, Regex("window\\.pageStore\\s*=")) as? JsonObject
            ?: throw ExtractionError.Malformed("The OpenRec page had no page store.")
    }

    /** Upstream `_parse_openrec_metadata`; the API checks degrade to guest. */
    protected suspend fun parseOpenRecMetadata(pageStore: JsonObject, videoId: String): OpenRecMetadata {
        val info = pageStore.obj("v8")?.obj("movie")
        val targetMembers = info?.array("targetMembers").orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("type") }
            .firstOrNull()
        val needsSubscription = targetMembers == "subscription"
        val needsAuth = targetMembers == "ppv"
        val needsPremium = info?.str("publicType") == "premium"

        val me = callApi("users/me", videoId, expected401 = true)
        val isPremium = me?.obj("data")?.array("items").orEmpty()
            .any { (it as? JsonObject)?.bool("is_premium") == true }
        val detail = callApi("movies/$videoId/detail", videoId, expected401 = true)
        val items = detail?.obj("data")?.array("items").orEmpty().mapNotNull { it as? JsonObject }
        val isMember = items.any { it.obj("membership")?.bool("is_active") == true }
        val hasPpv = items.any { it["ppv_ticket_products"] != null }

        val need = when {
            needsPremium && !isPremium -> "premium membership"
            needsSubscription && !isMember -> "channel subscription"
            needsAuth && !hasPpv -> "PPV purchase"
            else -> null
        }
        if (need != null) {
            throw ExtractionError.LoginRequired("This content requires a $need.")
        }

        val channel = info?.obj("channel")?.obj("user")
        return OpenRecMetadata(
            id = videoId,
            availability = when {
                needsPremium -> "premium_only"
                needsSubscription -> "subscriber_only"
                needsAuth -> "needs_auth"
                else -> "public"
            },
            title = info?.str("title")?.let(::cleanHtml),
            description = info?.str("introduction")?.let(::cleanHtml),
            duration = info?.obj("playTime")?.number("value")?.div(1000.0),
            thumbnail = ExtractorUtils.urlOrNone(
                info?.str("lThumbnailUrl") ?: info?.str("thumbnailUrl"),
            ),
            uploadDate = info?.obj("startedAt")?.number("time")?.div(1000)?.toLong()
                ?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = info?.number("totalViews")?.toLong(),
            channel = channel?.str("name")?.let(::cleanHtml),
            channelId = channel?.str("id"),
            onAirStatus = info?.str("onAirStatus"),
            liveStatus = LIVE_STATUS[info?.str("onAirStatus")],
            detailItems = items,
        )
    }

    companion object {
        const val API_BASE: String = "https://apiv5.mellow-fan.com/api/v5"
        const val PUBLIC_API_BASE: String = "https://public.mellow-fan.com/external/api/v5"
        const val BASE_URL: String = "https://www.mellow-fan.com"
        val SITE_HEADERS: Map<String, String> = mapOf("referer" to "$BASE_URL/")

        internal val LIVE_STATUS = mapOf(
            "ARCHIVE" to "was_live",
            "COMING_UP" to "is_upcoming",
            "LIVE_STREAMING" to "is_live",
            "UPLOADED" to "not_live",
        )
    }
}

/** The mapped metadata from `_parse_openrec_metadata`. */
data class OpenRecMetadata(
    val id: String,
    val availability: String,
    val title: String? = null,
    val description: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val uploadDate: String? = null,
    val viewCount: Long? = null,
    val channel: String? = null,
    val channelId: String? = null,
    val onAirStatus: String? = null,
    val liveStatus: String? = null,
    val detailItems: List<JsonObject> = emptyList(),
) {
    /** The media URLs the detail response exposes. */
    fun mediaUrls(): List<String> {
        val urls = mutableListOf<String>()
        for (item in detailItems) {
            val media = item.obj("media") ?: continue
            for (key in listOf("url", "url_audio", "url_dvr", "url_dvr_audio")) {
                ExtractorUtils.urlOrNone(media.str(key))?.let { urls += it }
            }
            for (element in media.array("subs_trial_media").orEmpty()) {
                val url = ExtractorUtils.urlOrNone((element as? JsonPrimitive)?.content) ?: continue
                urls += url
            }
        }
        return urls.distinct()
    }

    fun toInfoDict(): InfoDict = InfoDict(
        id = id,
        title = title,
        description = description,
        duration = duration,
        uploadDate = uploadDate,
        viewCount = viewCount,
        channel = channel,
        channelId = channelId,
        availability = availability,
        thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
        isLive = liveStatus == "is_live",
        webpageUrl = null,
        extractor = "openrec",
    )
}

/** Upstream `OpenRecIE`: the live/archive pages. */
class OpenRecIE(
    http: ExtractorHttp,
) : OpenRecBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val pageStore = extractPageStore(webpage, videoId)
        if (pageStore.obj("movieStore")?.bool("notFound") == true) {
            throw ExtractionError.Unavailable("This video is no longer available.")
        }
        val metadata = parseOpenRecMetadata(pageStore, videoId)
        if (metadata.liveStatus == "is_upcoming") {
            throw ExtractionError.NotYetAvailable("This livestream has not yet started.")
        }
        val formats = metadata.mediaUrls().map { mediaUrl ->
            MediaFormat(
                formatId = "hls",
                url = mediaUrl,
                ext = "mp4",
                protocol = "m3u8_native",
                httpHeaders = SITE_HEADERS,
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The OpenRec stream is not available.")
        }
        val startedAt = pageStore.obj("v8")?.obj("movie")?.obj("startedAt")
            ?.number("time")?.div(1000.0)
        val chapters = mutableListOf<Chapter>()
        for (element in pageStore.obj("v8")?.obj("movie")?.array("chapters").orEmpty()) {
            val chapter = element as? JsonObject ?: continue
            val chapterAt = chapter.obj("chapterAt")?.number("time")?.div(1000.0) ?: continue
            chapters += Chapter(
                startTime = if (startedAt != null) chapterAt - startedAt else chapterAt,
                title = chapter.str("title")?.let(::cleanHtml),
            )
        }
        return metadata.toInfoDict().copy(
            formats = formats,
            chapters = chapters,
            webpageUrl = url,
        )
    }

    companion object {
        const val IE_KEY: String = "OpenRec"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:mellow-fan\\.com|openrec\\.tv)/(?:m/)?live/(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `OpenRecCaptureIE`: the capture pages (page store only). */
class OpenRecCaptureIE(
    http: ExtractorHttp,
) : OpenRecBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val pageStore = extractPageStore(webpage, videoId)
        val capture = pageStore.obj("capture")
            ?: throw ExtractionError.Malformed("The OpenRec capture page had no capture.")
        val m3u8Url = ExtractorUtils.urlOrNone(capture.str("source"))
            ?: throw ExtractionError.NoFormats("The OpenRec capture had no source.")
        val channel = pageStore.obj("movie")?.obj("channel")
        val start = capture.number("startTime")
        val end = capture.number("endTime")
        return InfoDict(
            id = videoId,
            title = capture.str("title")?.let(::cleanHtml),
            duration = if (start != null && end != null) end - start else null,
            thumbnails = ExtractorUtils.urlOrNone(capture.str("thumbnailUrl"))
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            uploadDate = capture.str("publishedAt")?.let(ExtractorUtils::unifiedStrdate),
            channel = channel?.str("name")?.let(::cleanHtml),
            channelId = channel?.str("id"),
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = m3u8Url,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    httpHeaders = SITE_HEADERS,
                ),
            ),
            webpageUrl = url,
            extractor = "openrec",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "OpenRecCapture"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:mellow-fan\\.com|openrec\\.tv)/(?:m/)?capture/(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `OpenRecMovieIE`: the uploaded movie pages. */
class OpenRecMovieIE(
    http: ExtractorHttp,
) : OpenRecBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val pageStore = extractPageStore(webpage, videoId)
        if (pageStore.obj("movieStore")?.bool("notFound") == true) {
            throw ExtractionError.Unavailable("This video is no longer available.")
        }
        val metadata = parseOpenRecMetadata(pageStore, videoId)
        val formats = metadata.mediaUrls().map { mediaUrl ->
            MediaFormat(
                formatId = "hls",
                url = mediaUrl,
                ext = "mp4",
                protocol = "m3u8_native",
                httpHeaders = SITE_HEADERS,
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The OpenRec movie stream is not available.")
        }
        return metadata.toInfoDict().copy(formats = formats, webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "OpenRecMovie"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:mellow-fan\\.com|openrec\\.tv)/(?:m/)?movie/(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `OpenRecPlaylistIE`: the user playlists. */
class OpenRecPlaylistIE(
    http: ExtractorHttp,
) : OpenRecBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val me = callApi("users/me/playlists/$playlistId", playlistId, expected401 = true)
        val items = me?.obj("data")?.array("items")?.firstOrNull() as? JsonObject
        val entries = mutableListOf<InfoEntry>()
        if (items != null) {
            for (element in items.array("playlist_movies").orEmpty()) {
                val movie = (element as? JsonObject)?.obj("movie") ?: continue
                val movieId = movie.str("id") ?: continue
                val path = if (movie.bool("is_live") == true) "live" else "movie"
                entries += InfoEntry(id = movieId, url = "$BASE_URL/$path/$movieId")
            }
            for (element in items.array("playlist_captures").orEmpty()) {
                val capture = (element as? JsonObject)?.obj("capture_relation")?.obj("capture")
                    ?: continue
                val captureId = capture.str("id") ?: continue
                entries += InfoEntry(id = captureId, url = "$BASE_URL/capture/$captureId")
            }
            return InfoDict(
                id = playlistId,
                title = items.str("title")?.let(::cleanHtml),
                entries = entries,
                webpageUrl = url,
                extractor = "openrec",
                extractorKey = IE_KEY,
            )
        }

        val webpage = http.downloadWebpage(url)
        for (match in Regex("href=\"([^\"]+)\"").findAll(webpage)) {
            val href = match.groupValues[1]
            if (href.contains("/live/") || href.contains("/movie/") || href.contains("/capture/")) {
                entries += InfoEntry(url = urlJoin(BASE_URL, href))
            }
        }
        return InfoDict(
            id = playlistId,
            entries = entries.distinctBy { it.url },
            webpageUrl = url,
            extractor = "openrec",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "OpenRecPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:mellow-fan\\.com|openrec\\.tv)/(?:m/)?user/[^/?#]+/" +
                "playlist/(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `OpenRecChannelIE`: the user channel listing. */
class OpenRecChannelIE(
    http: ExtractorHttp,
) : OpenRecBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val pageStore = extractPageStore(webpage, channelId)
        val channel = pageStore.obj("state")?.obj("_channel")
        val movieCount = channel?.number("movieCount")?.toInt() ?: 0

        val entries = mutableListOf<InfoEntry>()
        var page = 1
        var iterations = 0
        while (entries.size < movieCount && iterations < MAX_PAGES) {
            iterations++
            val pageUrl = "$PUBLIC_API_BASE/search-movies?channel_ids=$channelId&include_live=true" +
                "&include_upload=true&onair_status=2&include_deleted=true&sort=published_at&page=$page"
            val movies = try {
                http.downloadJson(pageUrl, headers = SITE_HEADERS) as? JsonArray
            } catch (error: ExtractionError) {
                null
            }.orEmpty()
            if (movies.isEmpty()) break
            for (element in movies) {
                val movie = element as? JsonObject ?: continue
                val id = movie.str("id") ?: continue
                val path = if (movie.str("movie_type") == "1") "live" else "movie"
                entries += InfoEntry(id = id, url = "$BASE_URL/$path/$id")
            }
            page++
        }
        return InfoDict(
            id = channelId,
            title = channel?.obj("user")?.str("name")?.let(::cleanHtml),
            entries = entries,
            webpageUrl = url,
            extractor = "openrec",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "OpenRecChannel"
        private const val MAX_PAGES = 200

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:mellow-fan\\.com|openrec\\.tv)/(?:m/)?user/(?<id>[^/?#]+)$",
        )
    }
}

/** Upstream `OpenRecChannelSearchIE`: the channel search listings. */
class OpenRecChannelSearchIE(
    http: ExtractorHttp,
) : OpenRecBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val channelId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val searchType = match.groups["type"]?.value
        val searchQuery = Regex("[?&]search_query=([^&#]+)").find(url)?.groupValues?.get(1)
            ?.let(::percentDecode)
            ?: throw ExtractionError.Unavailable("The OpenRec search query is missing.")
        if (searchType == null) {
            return InfoDict(
                id = channelId,
                title = joinNonEmpty(channelId, searchQuery, delim = ":"),
                entries = listOf("capture", "movie").map { type ->
                    InfoEntry(
                        url = "$BASE_URL/user/$channelId/search/$type?search_query=" + searchQuery,
                    )
                },
                webpageUrl = url,
                extractor = "openrec",
                extractorKey = IE_KEY,
            )
        }
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        var iterations = 0
        while (iterations < MAX_PAGES) {
            iterations++
            val pageUrl = "$PUBLIC_API_BASE/search-${searchType}s?channel_ids=$channelId&page=$page" +
                "&search_query=$searchQuery"
            val items = try {
                http.downloadJson(pageUrl, headers = SITE_HEADERS) as? JsonArray
            } catch (error: ExtractionError) {
                null
            }.orEmpty()
            if (items.isEmpty()) break
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val itemType = if (searchType == "movie" && item.bool("is_live") == true) "live" else searchType
                val id = item.str("id") ?: item.obj("capture")?.str("id") ?: continue
                entries += InfoEntry(id = id, url = "$BASE_URL/$itemType/$id")
            }
            page++
        }
        return InfoDict(
            id = channelId,
            title = joinNonEmpty(channelId, searchQuery, searchType, delim = ":"),
            entries = entries,
            webpageUrl = url,
            extractor = "openrec",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "OpenRecChannelSearch"
        private const val MAX_PAGES = 200

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:mellow-fan\\.com|openrec\\.tv)/(?:m/)?user/(?<id>[^/?#]+)/" +
                "search(?:/(?<type>capture|movie))?(?:[/?#]|$)",
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
                    return ExtractorUtils.parseJson(html.substring(index, position + 1))
                }
            }
        }
    }
    return null
}

/** `urllib.parse.unquote` for the encoded page store and search query. */
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

private fun joinNonEmpty(vararg values: String?, delim: String = "-"): String? =
    values.filterNotNull().filter { it.isNotEmpty() }.joinToString(delim).ifEmpty { null }

private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("/") -> base.trimEnd('/') + value
    else -> base.trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
