/*
 * RedGifs extractors — AnyDownload
 *
 * Kotlin translation of `redgifs.py` from `yt_dlp/extractor/redgifs.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `redgifs.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the temporary-token fetch (`v2/auth/temporary`, cached per instance
 * and refreshed once on a 401, the DPlay `discoAuth` shape), the gif JSON
 * (gif/sd/hd rows with the aspect-ratio width and the numeric quality), the
 * browse search and user search pagination with the query-string fields, and
 * the `RedGifs said:` error. Limitations: the `x-customheader` header is
 * refused by the platform allowlist and the `referer`/`origin` headers are
 * the only ones carried; the search/user entries expand as child watch jobs
 * (an extra API call) instead of the upstream inline infos; the
 * `categories`/`tags`/`like_count` fields are not modeled and are dropped;
 * pagination is capped at 100 pages. The temporary bearer token is fetched
 * at runtime and never stored. No cookie or signed media URL is stored here.
 */
package com.anydownload.core.extract.redgifs

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_BASE = "https://api.redgifs.com/v2/"

private val FORMAT_HEIGHTS = linkedMapOf("gif" to 250L, "sd" to 480L, "hd" to null)

/** Upstream `RedGifsBaseIE`: the token, the API call, and the gif parser. */
abstract class RedGifsBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    private var authorization: String? = null

    /** Upstream `_fetch_oauth_token`: the public temporary token. */
    protected suspend fun fetchOAuthToken() {
        val auth = http.downloadJson("${API_BASE}auth/temporary") as? JsonObject
        val token = auth?.str("token")
            ?: throw ExtractionError.Malformed("Unable to get temporary token.")
        authorization = "Bearer $token"
    }

    /** Upstream `_call_api`: one retry on a 401, then the `error` check. */
    protected suspend fun callApi(ep: String, videoId: String, query: Map<String, String?> = emptyMap()): JsonObject {
        var data: JsonObject? = null
        for (firstAttempt in listOf(true, false)) {
            if (authorization == null) fetchOAuthToken()
            try {
                val url = API_BASE + ep + if (query.isEmpty()) "" else "?" + queryString(query)
                data = http.downloadJson(
                    url,
                    headers = mapOf(
                        "referer" to "https://www.redgifs.com/",
                        "origin" to "https://www.redgifs.com",
                        "content-type" to "application/json",
                    ),
                    authorization = authorization,
                ) as? JsonObject
                break
            } catch (error: ExtractionError.LoginRequired) {
                if (firstAttempt) {
                    authorization = null
                    continue
                }
                throw error
            }
        }
        val result = data ?: throw ExtractionError.Malformed("The RedGifs API returned no data.")
        result.str("error")?.let { throw ExtractionError.Unavailable("RedGifs said: $it") }
        return result
    }

    /** Upstream `_parse_gif_data`. */
    protected fun parseGifData(gif: JsonObject): InfoDict {
        val videoId = gif.str("id")
            ?: throw ExtractionError.Malformed("The RedGifs gif carried no id.")
        val origHeight = gif.number("height")
        val width = gif.number("width")
        val aspectRatio = if (origHeight != null && origHeight != 0.0 && width != null) {
            width / origHeight
        } else {
            null
        }

        val formats = mutableListOf<MediaFormat>()
        for ((index, entry) in FORMAT_HEIGHTS.entries.withIndex()) {
            val formatId = entry.key
            val formatHeight = entry.value
            val videoUrl = gif.obj("urls")?.str(formatId) ?: continue
            val height = if (origHeight != null && formatHeight != null) {
                minOf(origHeight, formatHeight.toDouble())
            } else {
                formatHeight?.toDouble() ?: origHeight
            }
            formats += MediaFormat(
                url = videoUrl,
                formatId = formatId,
                width = aspectRatio?.let { (height ?: 0.0) * it }?.toLong(),
                height = height?.toLong(),
                quality = index.toString(),
            )
        }

        val tags = (gif["tags"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.content?.takeIf { content -> content.isNotBlank() }
        }
        return InfoDict(
            id = videoId,
            title = tags.joinToString(" ").takeIf { it.isNotEmpty() } ?: "RedGifs",
            uploader = gif.str("userName"),
            uploadDate = gif.number("createDate")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            duration = gif.number("duration"),
            viewCount = gif.number("views")?.toLong(),
            ageLimit = 18,
            formats = formats,
            webpageUrl = "https://redgifs.com/watch/$videoId",
            extractor = "RedGifs",
            extractorKey = "RedGifs",
        )
    }

    /** Upstream `_prepare_api_query`: the page query merged over the defaults, nulls dropped. */
    protected fun prepareQuery(query: Map<String, String>, fields: Map<String, String?>): Map<String, String?> =
        fields.mapNotNull { (name, default) -> (query[name] ?: default)?.let { name to it } }.toMap()

    /** Upstream `_paged_entries`: one page or the capped page list. */
    protected suspend fun pagedEntries(
        ep: String,
        itemId: String,
        query: Map<String, String>,
        fields: Map<String, String?>,
    ): List<InfoEntry> {
        val apiQuery = prepareQuery(query, fields)
        val requestedPage = query["page"]?.toIntOrNull()
        if (requestedPage != null) {
            return fetchPage(ep, itemId, apiQuery, requestedPage - 1).mapNotNull(::gifEntry)
        }
        val entries = mutableListOf<InfoEntry>()
        var page = 0
        while (page < MAX_PAGES) {
            val gifs = fetchPage(ep, itemId, apiQuery, page)
            if (gifs.isEmpty()) break
            entries += gifs.mapNotNull(::gifEntry)
            page++
        }
        return entries
    }

    /** Upstream `_fetch_page`: page is 1-based in the API query. */
    private suspend fun fetchPage(
        ep: String,
        itemId: String,
        query: Map<String, String?>,
        page: Int,
    ): List<JsonObject> {
        val withPage = query + ("page" to (page + 1).toString())
        val data = callApi(ep, itemId, withPage)
        return (data["gifs"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    /** Upstream `url_result` per search entry: the port expands a child watch job. */
    private fun gifEntry(gif: JsonObject): InfoEntry? {
        val id = gif.str("id") ?: return null
        return InfoEntry(id = id, url = "https://redgifs.com/watch/$id")
    }

    companion object {
        private const val MAX_PAGES = 100
    }
}

/** Upstream `RedGifsIE`: one watch/ifr/thumbs2 page. */
class RedGifsIE(
    http: ExtractorHttp,
) : RedGifsBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url)?.lowercase() ?: throw ExtractionError.UnsupportedUrl()
        val videoInfo = callApi("gifs/$videoId?views=yes", videoId)
        val gif = videoInfo.obj("gif")
            ?: throw ExtractionError.Malformed("The RedGifs video info carried no gif.")
        return parseGifData(gif).copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "RedGifs"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.)?redgifs\\.com/(?:watch|ifr)/|thumbs2\\.redgifs\\.com/)" +
                "(?<id>[^-/?#.]+)",
        )
    }
}

/** Upstream `RedGifsSearchIE`: the browse page. */
class RedGifsSearchIE(
    http: ExtractorHttp,
) : RedGifsBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val queryString = VALID_URL.find(url)?.groups?.get("query")?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val query = parseQuery(queryString)
        val tags = query["tags"]
            ?: throw ExtractionError.Unavailable("Invalid query tags")
        val order = query["order"] ?: "trending"
        val searchQuery = query + ("search_text" to tags)
        val entries = pagedEntries(
            "gifs/search",
            queryString,
            searchQuery,
            mapOf("search_text" to null, "order" to "trending", "type" to null),
        )
        return InfoDict(
            id = queryString,
            title = tags,
            description = "RedGifs search for $tags, ordered by $order",
            entries = entries,
            webpageUrl = url,
            extractor = "RedGifsSearch",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "RedGifsSearch"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?redgifs\\.com/browse\\?(?<query>[^#]+)")
    }
}

/** Upstream `RedGifsUserIE`: a user page. */
class RedGifsUserIE(
    http: ExtractorHttp,
) : RedGifsBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val username = match.groups["username"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val queryString = match.groups["query"]?.value
        val playlistId = if (queryString != null) "$username?$queryString" else username
        val query = queryString?.let(::parseQuery).orEmpty()
        val order = query["order"] ?: "recent"
        val entries = pagedEntries(
            "users/$username/search",
            playlistId,
            query,
            mapOf("order" to "recent", "type" to null),
        )
        return InfoDict(
            id = playlistId,
            title = username,
            description = "RedGifs user $username, ordered by $order",
            entries = entries,
            webpageUrl = url,
            extractor = "RedGifsUser",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "RedGifsUser"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?redgifs\\.com/users/(?<username>[^/?#]+)(?:\\?(?<query>[^#]+))?",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `parse_qs` first values: `a=1&b=2` to a map. */
private fun parseQuery(query: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val name = decodeQueryComponent(pair.substringBefore('='))
        if (name.isEmpty() || name in result) continue
        result[name] = decodeQueryComponent(pair.substringAfter('=', ""))
    }
    return result
}

private fun decodeQueryComponent(value: String): String =
    value.replace('+', ' ').replace(Regex("%([0-9a-fA-F]{2})")) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }

private fun queryString(params: Map<String, String?>): String =
    params.entries.joinToString("&") { (name, value) ->
        "${percentEncode(name)}=${percentEncode(value.orEmpty())}"
    }

private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[code shr 4])
            append(HEX_DIGITS[code and 0x0F])
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
