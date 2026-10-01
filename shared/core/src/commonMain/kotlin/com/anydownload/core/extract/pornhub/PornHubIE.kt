/*
 * PornHub extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `pornhub.py` from
 * `yt_dlp/extractor/pornhub.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `pornhub.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the video page (flashvars mediaDefinitions, the `media_`/`quality_`/
 * `qualityItems_` JS variables, download-button links, `/video/get_media`,
 * title/uploader/counts/thumbnail/duration, captions) and the user/channel/
 * pornstar/playlist listings with their page walks. The port cannot set the
 * `age_verified` cookies, log in, or run the PhantomJS cookie gate, so a page
 * that returns the JS age/consent gate fails typed as a login wall; requests
 * are never impersonated. JSON-LD, the `impersonate=True` paths, and the
 * premium login are not translated. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.pornhub

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

/** Upstream `PornHubBaseIE`: the host regex, headers, and the JS gate. */
abstract class PornHubBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_get_headers`; the manifest requests need these. */
    protected fun siteHeaders(host: String): Map<String, String> = mapOf(
        "origin" to "https://www.$host",
        "referer" to "https://www.$host/",
    )

    /**
     * Upstream `_download_webpage_handle`'s PhantomJS cookie gate. The port
     * cannot run the JS or set the `age_verified` cookies, so the gate fails
     * typed instead.
     */
    protected fun checkGate(webpage: String) {
        val gated = JS_GATE_PATTERNS.any { it.containsMatchIn(webpage) } ||
            webpage.contains("id=\"ageDisclaimer\"") ||
            webpage.contains("ageDisclaimerBanner")
        if (gated) {
            throw ExtractionError.LoginRequired(
                "PornHub requires age verification; the port cannot set the age cookies or run " +
                    "the JavaScript gate.",
            )
        }
    }

    companion object {
        /** Upstream `_PORNHUB_HOST_RE`. */
        const val HOST_RE: String =
            "(?:(?<host>pornhub(?:premium)?\\.(?:com|net|org))|" +
                "pornhubvybmsymdol4iibwgwtkpwmeyd6luq2gxajgjzfjvotyt5zhyd\\.onion)"

        private val JS_GATE_PATTERNS = listOf(
            Regex("<body\\b[^>]+\\bonload=[\"']go\\(\\)"),
            Regex("document\\.cookie\\s*=\\s*[\"']RNKEY="),
            Regex("document\\.location\\.reload\\(true\\)"),
        )
    }
}

/** Upstream `PornHubIE`: the view_video pages and Thumbzilla. */
class PornHubIE(
    http: ExtractorHttp,
) : PornHubBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: "pornhub.com"
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(
            "https://www.$host/view_video.php?viewkey=$videoId",
            headers = siteHeaders(host),
        )
        checkGate(webpage)

        val errorMessage = ExtractorUtils.searchRegex(
            "(?s)<div[^>]+class=([\"'])(?:(?!\\1).)*\\b(?:removed|userMessageSection)\\b" +
                "(?:(?!\\1).)*\\1[^>]*>(?<error>.+?)</div>",
            webpage,
            group = 2,
            default = null,
        ) ?: ExtractorUtils.searchRegex(
            "(?s)<section[^>]+class=[\"']noVideo[\"'][^>]*>(?<error>.+?)</section>",
            webpage,
            group = 2,
            default = null,
        )
        if (errorMessage != null) {
            throw ExtractionError.Unavailable("PornHub said: ${errorMessage.replace(Regex("\\s+"), " ")}")
        }
        if (webpage.contains("class=\"geoBlocked\"") ||
            webpage.contains("This content is unavailable in your country")
        ) {
            throw ExtractionError.GeoRestricted()
        }

        val title = ExtractorUtils.htmlSearchMeta(webpage, "twitter:title")
            ?: ExtractorUtils.searchRegex(
                "(?s)<h1[^>]+class=[\"']title[\"'][^>]*>(?<title>.+?)</h1>",
                webpage,
                group = 2,
                default = null,
            )
            ?: ExtractorUtils.searchRegex(
                "<div[^>]+data-video-title=([\"'])(?<title>(?:(?!\\1).)+)\\1",
                webpage,
                group = 2,
                default = null,
            )

        val videoUrls = linkedMapOf<String, Long?>()
        val subtitles = mutableListOf<SubtitleTrack>()
        val flashvars = extractJsonElement(webpage, Regex("var\\s+flashvars_\\d+\\s*=\\s*")) as? JsonObject
        var thumbnail: String? = null
        var duration: Double? = null
        if (flashvars != null) {
            ExtractorUtils.urlOrNone(flashvars.str("closedCaptionsFile"))?.let { captionUrl ->
                subtitles += SubtitleTrack(
                    language = "en",
                    formats = listOf(SubtitleFormat(ext = "srt", url = captionUrl)),
                )
            }
            thumbnail = ExtractorUtils.urlOrNone(flashvars.str("image_url"))
            duration = flashvars.double("video_duration")
            for (element in flashvars.array("mediaDefinitions").orEmpty()) {
                val definition = element as? JsonObject ?: continue
                val videoUrl = ExtractorUtils.urlOrNone(definition.str("videoUrl")) ?: continue
                if (!videoUrls.containsKey(videoUrl)) videoUrls[videoUrl] = definition.long("quality")
            }
        }

        if (videoUrls.isEmpty()) {
            val jsVars = extractJsVars(webpage, Regex("(var\\s+(?:media|quality|qualityItems)_.+)"))
            for ((key, value) in jsVars) {
                when {
                    key.startsWith("qualityItems") -> {
                        val items = ExtractorUtils.parseJson(value) as? JsonArray
                        for (item in items.orEmpty()) {
                            val url = ExtractorUtils.urlOrNone((item as? JsonObject)?.str("url"))
                                ?: continue
                            if (!videoUrls.containsKey(url)) videoUrls[url] = null
                        }
                    }

                    key.startsWith("media") || key.startsWith("quality") -> {
                        ExtractorUtils.urlOrNone(value)?.let { url ->
                            if (!videoUrls.containsKey(url)) videoUrls[url] = null
                        }
                    }
                }
            }
            if (videoUrls.isEmpty() && webpage.contains("id=\"lockedPlayer")) {
                throw ExtractionError.Unavailable("The PornHub video is locked.")
            }
        }

        for (match in Regex(
            "<a[^>]+\\bclass=[\"']downloadBtn\\b[^>]+\\bhref=([\"'])(?<url>(?:(?!\\1).)+)\\1",
        ).findAll(webpage)) {
            ExtractorUtils.urlOrNone(match.groups["url"]?.value)?.let { url ->
                if (!videoUrls.containsKey(url)) videoUrls[url] = null
            }
        }

        var uploadDate: String? = null
        val formats = mutableListOf<MediaFormat>()
        for ((videoUrl, heightHint) in videoUrls) {
            if (uploadDate == null) {
                uploadDate = ExtractorUtils.searchRegex("/(\\d{6}/\\d{2})/", videoUrl, default = null)
                    ?.replace("/", "")
            }
            if (videoUrl.contains("/video/get_media")) {
                val medias = try {
                    http.downloadJson(videoUrl, headers = siteHeaders(host)) as? JsonArray
                } catch (error: ExtractionError) {
                    null
                }
                for (element in medias.orEmpty()) {
                    val media = element as? JsonObject ?: continue
                    val mediaUrl = ExtractorUtils.urlOrNone(media.str("videoUrl")) ?: continue
                    addFormat(
                        formats,
                        mediaUrl,
                        media.long("quality"),
                        host,
                        videoId,
                    )
                }
                continue
            }
            addFormat(formats, videoUrl, heightHint, host, videoId)
        }

        val modelProfile = extractJsonElement(webpage, Regex("var\\s+MODEL_PROFILE\\s*=")) as? JsonObject
        val uploader = ExtractorUtils.searchRegex(
            "(?s)From:&nbsp;.+?<(?:a\\b[^>]+\\bhref=[\"']/(?:(?:user|channel)s|model|pornstar)/|" +
                "span\\b[^>]+\\bclass=[\"']username)[^>]+>(.+?)<",
            webpage,
            default = null,
        ) ?: modelProfile?.str("username")

        val viewCount = count(webpage, "<span class=\"count\">([\\d,\\.]+)</span> [Vv]iews")
        val commentCount = count(webpage, "All Comments\\s*<span>\\(([\\d,.]+)\\)")

        return InfoDict(
            id = videoId,
            title = title,
            uploader = uploader,
            uploadDate = uploadDate,
            thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = duration,
            viewCount = viewCount,
            ageLimit = 18,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "pornhub",
            extractorKey = IE_KEY,
        )
    }

    private fun count(webpage: String, pattern: String): Long? =
        ExtractorUtils.searchRegex(pattern, webpage, default = null)?.let(::strToInt)

    private fun addFormat(
        formats: MutableList<MediaFormat>,
        formatUrl: String,
        heightHint: Long?,
        host: String,
        videoId: String,
    ) {
        val headers = siteHeaders(host)
        when (ExtractorUtils.determineExt(formatUrl, defaultExt = "")) {
            "mpd" -> formats += MediaFormat(
                formatId = "dash",
                url = formatUrl,
                ext = "mp4",
                protocol = "http_dash_segments",
                httpHeaders = headers,
            )

            "m3u8" -> formats += MediaFormat(
                formatId = "hls",
                url = formatUrl,
                ext = "mp4",
                protocol = "m3u8_native",
                httpHeaders = headers,
            )

            else -> {
                val height = heightHint
                    ?: ExtractorUtils.searchRegex("(?<height>\\d+)[pP]?_\\d+[kK]", formatUrl, group = 1, default = null)
                        ?.toLongOrNull()
                formats += MediaFormat(
                    url = formatUrl,
                    formatId = height?.let { "${it}p" },
                    height = height,
                    httpHeaders = headers,
                )
            }
        }
    }

    companion object {
        const val IE_KEY: String = "PornHub"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:[a-zA-Z0-9.-]+\\.)?${PornHubBaseIE.HOST_RE}/" +
                "(?:(?:view_video\\.php|video/show)\\?viewkey=|embed/)|" +
                "(?:www\\.)?thumbzilla\\.com/video/)(?<id>[\\da-z]+)",
        )
    }
}

/** Upstream `PornHubPlaylistBaseIE`: the page scan helper. */
abstract class PornHubPlaylistBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : PornHubBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_extract_page`. */
    protected fun extractPage(url: String): Int? =
        ExtractorUtils.searchRegex("\\bpage=(\\d+)", url, default = null)?.toIntOrNull()

    /** Upstream `_extract_entries`. */
    protected fun extractEntries(webpage: String, host: String): List<InfoEntry> {
        val container = ExtractorUtils.searchRegex(
            "(?s)(<div[^>]+class=[\"']container.+)",
            webpage,
            default = webpage,
        ) ?: webpage
        return Regex(
            "href=\"/?(view_video\\.php\\?.*\\bviewkey=[\\da-z]+[^\"]*)\"[^>]*\\s+title=\"([^\"]+)\"",
        ).findAll(container).map { match ->
            val path = match.groupValues[1]
            val viewKey = Regex("viewkey=([\\da-z]+)").find(path)?.groupValues?.get(1)
            InfoEntry(
                id = viewKey,
                title = match.groupValues[2],
                url = "https://www.$host/$path",
            )
        }.distinctBy { it.url }.toList()
    }

    /** Upstream `_has_more`. */
    protected fun hasMore(webpage: String): Boolean =
        Regex("<li[^>]+\\bclass=[\"']page_next|<link[^>]+\\brel=[\"']next|<button[^>]+\\bid=[\"']moreDataBtn")
            .containsMatchIn(webpage)
}

/** Upstream `PornHubUserIE`: the model/pornstar/user/channel pages. */
class PornHubUserIE(
    http: ExtractorHttp,
) : PornHubPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val userUrl = match.groups["url"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val userId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var videosUrl = "$userUrl/videos"
        extractPage(url)?.let { videosUrl += "?page=$it" }
        return InfoDict(
            id = userId,
            webpageUrl = url,
            redirectUrl = videosUrl,
            extractor = "pornhub",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PornHubUser"

        val VALID_URL: Regex = Regex(
            "(?<url>https?://(?:[a-zA-Z0-9.-]+\\.)?${PornHubBaseIE.HOST_RE}/" +
                "(?:(?:user|channel)s|model|pornstar)/(?<id>[^/?#&]+))(?:[?#&]|/(?!videos)|$)",
        )
    }
}

/** Upstream `PornHubPagedPlaylistBaseIE`: the paged listing walk. */
abstract class PornHubPagedPlaylistBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    protected val pageUrl: Regex,
) : PornHubPlaylistBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = pageUrl,
) {
    /** Upstream `_entries` with the `/videos` 404 fallback and the page cap. */
    protected suspend fun listEntries(url: String, host: String, itemId: String): List<InfoEntry> {
        val requestedPage = extractPage(url)
        val hasPage = requestedPage != null
        val firstPage = requestedPage ?: 1
        var baseUrl = url
        var pageNumber = firstPage
        val entries = mutableListOf<InfoEntry>()
        var iterations = 0
        while (iterations < MAX_PAGES) {
            iterations++
            val webpage = try {
                http.downloadWebpage(appendQuery(baseUrl, "page", "$pageNumber"), headers = siteHeaders(host))
            } catch (error: ExtractionError) {
                if (pageNumber == firstPage && baseUrl.contains("/videos")) {
                    baseUrl = baseUrl.replace("/videos", "")
                    http.downloadWebpage(appendQuery(baseUrl, "page", "$pageNumber"), headers = siteHeaders(host))
                } else if (pageNumber != firstPage) {
                    break
                } else {
                    throw error
                }
            }
            val pageEntries = extractEntries(webpage, host)
            if (pageEntries.isEmpty()) break
            entries += pageEntries
            if (!hasMore(webpage)) break
            if (hasPage) break
            pageNumber++
        }
        return entries
    }

    override suspend fun extract(url: String): InfoDict {
        val match = pageUrl.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: "pornhub.com"
        val itemId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = itemId,
            entries = listEntries(url, host, itemId),
            webpageUrl = url,
            extractor = "pornhub",
            extractorKey = ieKey,
        )
    }

    companion object {
        private const val MAX_PAGES = 200
    }
}

/** Upstream `PornHubPagedVideoListIE`: the model/channel/category lists. */
class PornHubPagedVideoListIE(
    http: ExtractorHttp,
) : PornHubPagedPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    pageUrl = VALID_URL,
) {
    override fun suitable(url: String): Boolean =
        !PornHubIE.VALID_URL.containsMatchIn(url) &&
            !PornHubUserIE.VALID_URL.containsMatchIn(url) &&
            !PornHubUserVideosUploadIE.VALID_URL.containsMatchIn(url) &&
            super.suitable(url)

    companion object {
        const val IE_KEY: String = "PornHubPagedVideoList"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?${PornHubBaseIE.HOST_RE}/(?!playlist/)(?<id>(?:[^/]+/)*[^/?#&]+)",
        )
    }
}

/** Upstream `PornHubUserVideosUploadIE`: the `/videos/upload` lists. */
class PornHubUserVideosUploadIE(
    http: ExtractorHttp,
) : PornHubPagedPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    pageUrl = VALID_URL,
) {
    companion object {
        const val IE_KEY: String = "PornHubUserVideosUpload"

        val VALID_URL: Regex = Regex(
            "(?<url>https?://(?:[^/]+\\.)?${PornHubBaseIE.HOST_RE}/" +
                "(?:(?:user|channel)s|model|pornstar)/(?<id>[^/]+)/videos/upload)",
        )
    }
}

/** Upstream `PornHubPlaylistIE`: the `/playlist/<id>` pages. */
class PornHubPlaylistIE(
    http: ExtractorHttp,
) : PornHubPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: "pornhub.com"
        val itemId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistUrl = match.groups["url"]?.value ?: url
        val webpage = http.downloadWebpage(playlistUrl, headers = siteHeaders(host))
        val playlistId = ExtractorUtils.searchRegex("var\\s+playlistId\\s*=\\s*\"([^\"]+)\"", webpage, default = null)
            ?: throw ExtractionError.Malformed("The PornHub playlist had no id.")
        val videoCount = ExtractorUtils.searchRegex(
            "var\\s+itemsCount\\s*=\\s*([0-9]+)\\s*\\|\\|",
            webpage,
            default = null,
        )?.toIntOrNull() ?: 0
        val token = ExtractorUtils.searchRegex("var\\s+token\\s*=\\s*\"([^\"]+)\"", webpage, default = null)
            ?: throw ExtractionError.Malformed("The PornHub playlist had no token.")

        val entries = mutableListOf<InfoEntry>()
        var pageEntries = extractEntries(webpage, host)
        val pageCount = if (videoCount > 36) ((videoCount - 36 + 39) / 40) + 1 else 1
        for (pageNumber in 1..pageCount) {
            if (pageNumber > 1) {
                val pageUrl = "https://www.$host/playlist/viewChunked?id=$playlistId&page=$pageNumber&token=$token"
                val page = try {
                    http.downloadWebpage(pageUrl, headers = siteHeaders(host))
                } catch (error: ExtractionError) {
                    break
                }
                pageEntries = extractEntries(page, host)
            }
            if (pageEntries.isEmpty()) break
            entries += pageEntries
        }
        return InfoDict(
            id = itemId,
            entries = entries,
            webpageUrl = url,
            extractor = "pornhub",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PornHubPlaylist"

        val VALID_URL: Regex = Regex(
            "(?<url>https?://(?:[^/]+\\.)?${PornHubBaseIE.HOST_RE}/playlist/(?<id>[^/?#&]+))",
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

/** Upstream `extract_js_vars` for the `media_`/`quality_`/`qualityItems_` vars. */
private fun extractJsVars(webpage: String, pattern: Regex): Map<String, String> {
    val assignments = pattern.find(webpage)?.groupValues?.get(1) ?: return emptyMap()
    val vars = linkedMapOf<String, String>()

    fun parseValue(input: String): String {
        val withoutComments = input.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        if ('+' in withoutComments) {
            return withoutComments.split('+').joinToString("") { parseValue(it) }
        }
        val trimmed = withoutComments.trim()
        return vars[trimmed] ?: removeQuotes(trimmed)
    }

    for (assignment in assignments.split(';')) {
        val trimmed = assignment.trim().replace(Regex("^var\\s+"), "")
        if (trimmed.isEmpty() || '=' !in trimmed) continue
        val name = trimmed.substringBefore('=').trim()
        val value = trimmed.substringAfter('=', "")
        vars[name] = parseValue(value)
    }
    return vars
}

private fun appendQuery(url: String, key: String, value: String): String {
    val separator = if (url.contains('?')) '&' else '?'
    return "$url$separator$key=$value"
}

private fun removeQuotes(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length >= 2 &&
        (trimmed.first() == '"' || trimmed.first() == '\'') &&
        trimmed.last() == trimmed.first()
    ) {
        return trimmed.substring(1, trimmed.length - 1)
    }
    return trimmed
}

/** Upstream `str_to_int` subset: separators are ignored. */
private fun strToInt(value: String): Long? =
    value.replace(",", "").replace(".", "").replace(" ", "").toLongOrNull()

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.double(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()
