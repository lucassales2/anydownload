/*
 * Niconico extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `niconico.py` from
 * `yt_dlp/extractor/niconico.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `niconico.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the guest watch API (`/api/watch/v3_guest/<id>`), the access-rights
 * HLS call, the mylist/series/user listing APIs, and the search/tag page
 * scans. The port always uses the guest path (no login; the `v3` authed path
 * and cookies are out). The 400/404 API error body (`meta.errorCode` /
 * `reasonCode`) cannot be read through the typed HTTP seam, so scheduled,
 * geo, PPV, premium, and member-only refusals fail typed as unavailable
 * instead of a named reason; a payment flag still maps to `availability`.
 * The per-variant audio/video quality fields and danmaku comment subtitles
 * are not available because the HLS master is parsed at download time and
 * the port's subtitle model carries URLs only. `NiconicoLiveIE` needs the
 * WebSocket `startWatching` handshake and is planned. No cookie, account
 * token, or signed media URL is stored here; the access-right key is fetched
 * per extraction and never persisted.
 */
package com.anydownload.core.extract.niconico

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

/** Upstream `NiconicoBaseIE`: the shared client constants. */
abstract class NiconicoBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    companion object {
        const val API_BASE: String = "https://nvapi.nicovideo.jp"
        const val BASE_URL: String = "https://www.nicovideo.jp"

        /** Upstream `_HEADERS`. */
        val CLIENT_HEADERS: Map<String, String> = mapOf(
            "x-frontend-id" to "6",
            "x-frontend-version" to "0",
        )
    }
}

/** Upstream `NiconicoIE`: one nicovideo.jp watch page through the guest API. */
class NiconicoIE(
    http: ExtractorHttp,
) : NiconicoBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val trackId = "AAAAAAAAAA_" + (Clock.System.now().toEpochMilliseconds())
        val apiResponse = http.downloadJson(
            "$BASE_URL/api/watch/v3_guest/$videoId?actionTrackId=$trackId",
            headers = CLIENT_HEADERS,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Niconico API returned no object.")
        val apiData = apiResponse.obj("data")
            ?: throw ExtractionError.Unavailable("The Niconico video is not available through the guest API.")

        val video = apiData.obj("video")
        val availability = availability(apiData)
        val format = extractHlsFormat(apiData, videoId)
        if (format == null) {
            when (availability) {
                "needs_auth" -> throw ExtractionError.LoginRequired(
                    "PPV video, payment information required.",
                )

                "premium_only" -> throw ExtractionError.LoginRequired("Premium members only.")
                "subscriber_only" -> throw ExtractionError.LoginRequired("Channel members only.")
            }
        }

        val owner = apiData.obj("channel") ?: apiData.obj("owner")
        return InfoDict(
            id = video?.str("id") ?: videoId,
            title = video?.str("title"),
            description = cleanHtml(video?.str("description")),
            duration = video?.number("duration"),
            uploadDate = video?.str("registeredAt")?.let(ExtractorUtils::unifiedStrdate),
            viewCount = video?.obj("count")?.number("view")?.toLong(),
            channel = owner?.str("name") ?: owner?.str("nickname"),
            channelId = owner?.str("id"),
            uploader = owner?.str("name") ?: owner?.str("nickname"),
            thumbnails = thumbnails(video),
            formats = format?.let { listOf(it) }.orEmpty(),
            availability = availability,
            webpageUrl = url,
            extractor = "niconico",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_formats`: the access-rights call for the HLS master. */
    private suspend fun extractHlsFormat(apiData: JsonObject, videoId: String): MediaFormat? {
        val domand = apiData.obj("media")?.obj("domand") ?: return null
        val videos = domand.array("videos").orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.bool("isAvailable") == true && it.str("id") != null }
        val audios = domand.array("audios").orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.bool("isAvailable") == true && it.str("id") != null }
        val accessKey = domand.str("accessRightKey") ?: return null
        val trackId = apiData.obj("client")?.str("watchTrackId") ?: return null
        if (videos.isEmpty() || audios.isEmpty()) return null

        val outputs = buildJsonArray {
            for (video in videos) {
                for (audio in audios) {
                    add(
                        buildJsonArray {
                            add(video.str("id")!!)
                            add(audio.str("id")!!)
                        },
                    )
                }
            }
        }
        val body = buildJsonObject { put("outputs", outputs) }.toString().encodeToByteArray()
        val json = http.downloadJson(
            "$API_BASE/v1/watch/$videoId/access-rights/hls?actionTrackId=$trackId",
            method = HttpMethods.POST,
            headers = mapOf(
                "accept" to "application/json;charset=utf-8",
                "content-type" to "application/json",
                "x-access-right-key" to accessKey,
                "x-request-with" to BASE_URL,
            ) + CLIENT_HEADERS,
            body = body,
        ) as? JsonObject ?: return null
        val contentUrl = json.obj("data")?.str("contentUrl") ?: return null
        return MediaFormat(
            formatId = "hls",
            url = contentUrl,
            ext = "mp4",
            protocol = "m3u8_native",
        )
    }

    /** Upstream `_availability` for the payment flags. */
    private fun availability(apiData: JsonObject): String {
        val payment = apiData.obj("payment")?.obj("video") ?: return "public"
        return when {
            payment.bool("isContinuationBenefit") == true || payment.bool("isPpv") == true -> "needs_auth"
            payment.bool("isAdmission") == true -> "subscriber_only"
            payment.bool("isPremium") == true -> "premium_only"
            else -> "public"
        }
    }

    private fun thumbnails(video: JsonObject?): List<Thumbnail> =
        video?.obj("thumbnail")?.mapNotNull { (key, value) ->
            val url = ExtractorUtils.urlOrNone((value as? JsonPrimitive)?.content) ?: return@mapNotNull null
            Thumbnail(url = url, id = key, width = resolution(url).first, height = resolution(url).second)
        }.orEmpty()

    companion object {
        const val IE_KEY: String = "Niconico"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:embed|sp|www)\\.)?nicovideo\\.jp/(?:shorts|watch)/(?<id>(?:[a-z]{2})?\\d+)",
        )
    }
}

/**
 * Upstream `NiconicoPlaylistBaseIE`: the paged listing helper shared by the
 * mylist and series APIs.
 */
abstract class NiconicoPlaylistBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_api`; returns the page object that carries `items`. */
    protected abstract suspend fun callApi(listId: String, page: Int, pageSize: Int): JsonObject

    /** Upstream `_fetch_page` / `_entries` with the page-size termination. */
    protected suspend fun listingEntries(listId: String): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (true) {
            val pageObject = callApi(listId, page, PAGE_SIZE)
            val items = pageObject.array("items") ?: break
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val video = item.obj("video") ?: item
                val videoId = video.str("id") ?: continue
                entries += InfoEntry(
                    id = videoId,
                    title = video.str("title"),
                    url = "$BASE_URL/watch/$videoId",
                )
            }
            if (items.size < PAGE_SIZE) break
            page++
        }
        return entries
    }

    companion object {
        const val PAGE_SIZE: Int = 100

        /** Upstream `_API_HEADERS`. */
        val API_HEADERS: Map<String, String> = mapOf(
            "x-frontend-id" to "6",
            "x-frontend-version" to "0",
            "x-niconico-language" to "en-us",
        )
    }
}

/** Upstream `NiconicoPlaylistIE`: `mylist/<id>` and its user-scoped forms. */
class NiconicoPlaylistIE(
    http: ExtractorHttp,
) : NiconicoPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun callApi(listId: String, page: Int, pageSize: Int): JsonObject {
        val json = http.downloadJson(
            "https://nvapi.nicovideo.jp/v2/mylists/$listId?page=$page&pageSize=$pageSize",
            headers = API_HEADERS,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Niconico mylist API returned no object.")
        return json.obj("data")?.obj("mylist")
            ?: throw ExtractionError.Malformed("The Niconico mylist API had no list.")
    }

    override suspend fun extract(url: String): InfoDict {
        val listId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val mylist = callApi(listId, 1, 1)
        return InfoDict(
            id = listId,
            title = mylist.str("name"),
            description = mylist.str("description"),
            entries = listingEntries(listId),
            webpageUrl = url,
            extractor = "niconico",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NiconicoPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.|sp\\.)?nicovideo\\.jp|nico\\.ms)/(?:user/\\d+/)?(?:my/)?" +
                "mylist/(?:#/)?(?<id>\\d+)",
        )
    }
}

/** Upstream `NiconicoSeriesIE`: `series/<id>` on the web hosts and nico.ms. */
class NiconicoSeriesIE(
    http: ExtractorHttp,
) : NiconicoPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun callApi(listId: String, page: Int, pageSize: Int): JsonObject {
        val json = http.downloadJson(
            "https://nvapi.nicovideo.jp/v2/series/$listId?page=$page&pageSize=$pageSize",
            headers = API_HEADERS,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Niconico series API returned no object.")
        return json.obj("data") ?: throw ExtractionError.Malformed("The Niconico series API had no data.")
    }

    override suspend fun extract(url: String): InfoDict {
        val listId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val detail = callApi(listId, 1, 1).obj("detail")
        return InfoDict(
            id = listId,
            title = detail?.str("title"),
            description = detail?.str("description"),
            entries = listingEntries(listId),
            webpageUrl = url,
            extractor = "niconico",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NiconicoSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.|sp\\.)?nicovideo\\.jp(?:/user/\\d+)?|nico\\.ms)/series/(?<id>\\d+)",
        )
    }
}

/** Upstream `NicovideoSearchBaseIE`: the `data-video-id` page scan. */
abstract class NicovideoSearchBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_entries`: page by page until a page has no results. */
    protected suspend fun searchEntries(url: String, query: Map<String, String>): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (true) {
            val pageUrl = url + "?" + (query + ("page" to page.toString()))
                .entries.joinToString("&") { "${it.key}=${it.value}" }
            val webpage = http.downloadWebpage(pageUrl)
            val ids = VIDEO_ID.findAll(webpage).map { it.groups["id"]?.value }.filterNotNull().toList()
            for (id in ids) {
                entries += InfoEntry(id = id, url = "$BASE_URL/watch/$id")
            }
            if (ids.isEmpty()) break
            page++
        }
        return entries
    }

    companion object {
        private val VIDEO_ID = Regex("data-video-id=[\"']?(?<id>[^\"']+)")
    }
}

/** Upstream `NicovideoSearchURLIE`: `/search/<query>` pages. */
class NicovideoSearchURLIE(
    http: ExtractorHttp,
) : NicovideoSearchBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val query = matchId(url).orEmpty()
        return InfoDict(
            id = query,
            title = query,
            entries = searchEntries("$BASE_URL/search/$query", emptyMap()),
            webpageUrl = url,
            extractor = "niconico",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NicovideoSearchURL"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?nicovideo\\.jp/search/(?<id>[^?#&]+)?")
    }
}

/** Upstream `NicovideoTagURLIE`: `/tag/<tag>` pages. */
class NicovideoTagURLIE(
    http: ExtractorHttp,
) : NicovideoSearchBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val query = matchId(url).orEmpty()
        return InfoDict(
            id = query,
            title = query,
            entries = searchEntries("$BASE_URL/tag/$query", emptyMap()),
            webpageUrl = url,
            extractor = "niconico",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NicovideoTagURL"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?nicovideo\\.jp/tag/(?<id>[^?#&]+)?")
    }
}

/** Upstream `NiconicoUserIE`: the user's video listing. */
class NiconicoUserIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val listId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        var totalCount = 1
        while (entries.size < totalCount) {
            val json = http.downloadJson(
                "https://nvapi.nicovideo.jp/v2/users/$listId/videos" +
                    "?sortKey=registeredAt&sortOrder=desc&pageSize=$PAGE_SIZE&page=$page",
                headers = USER_HEADERS,
            ) as? JsonObject ?: throw ExtractionError.Malformed("The Niconico user API returned no object.")
            val data = json.obj("data") ?: break
            if (page == 1) totalCount = data.number("totalCount")?.toInt() ?: 1
            for (element in data.array("items").orEmpty()) {
                val id = (element as? JsonObject)?.obj("essential")?.str("id") ?: continue
                entries += InfoEntry(id = id, url = "$BASE_URL/watch/$id")
            }
            page++
        }
        return InfoDict(
            id = listId,
            entries = entries,
            webpageUrl = url,
            extractor = "niconico",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NiconicoUser"
        private const val PAGE_SIZE = 100

        private val USER_HEADERS = mapOf(
            "x-frontend-id" to "6",
            "x-frontend-version" to "0",
        )

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?nicovideo\\.jp/user/(?<id>\\d+)(?:/video)?/?(?:$|[#?])",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_BASE_URL` for classes that do not extend `NiconicoBaseIE`. */
private const val BASE_URL = "https://www.nicovideo.jp"

private val RESOLUTION = Regex("(?<!\\d)(?<width>\\d{2,5})x(?<height>\\d{2,5})(?!\\d)")

/** Upstream `parse_resolution(url, lenient=True)` for one URL. */
private fun resolution(url: String): Pair<Long?, Long?> {
    val match = RESOLUTION.find(url) ?: return null to null
    return match.groups["width"]?.value?.toLongOrNull() to match.groups["height"]?.value?.toLongOrNull()
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
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
