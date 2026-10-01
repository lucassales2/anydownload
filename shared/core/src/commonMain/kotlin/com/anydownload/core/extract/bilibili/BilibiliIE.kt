/*
 * Bilibili extractors — AnyDownload
 *
 * Kotlin translation of the single-video subset of `BiliBiliIE` and of
 * `BiliBiliPlayerIE` from `yt_dlp/extractor/bilibili.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `bilibili.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `/video/BV...`, `/video/av...`, and the festival `?bvid=` form;
 * `window.__INITIAL_STATE__` metadata; the anthology `?p=` handling through
 * registry entries; WBI-signed `/x/player/wbi/playurl` with the dash
 * audio/video and legacy `durl` format mapping; and the player iframe
 * delegation. Bangumi, cheese, intl, space, lists, search, category, audio,
 * dynamic, and live classes are recorded as planned rows, and the interactive
 * chapters, danmaku/CC subtitles, comments, tags, and multi-FLV workaround
 * stay out. No cookie, bearer token, or signed media URL is stored or
 * committed; media addresses in fixtures are `*.example`.
 */
package com.anydownload.core.extract.bilibili

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.MediaFragment
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.md5Hex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * Upstream `BiliBiliIE` subset: one video or one part of an anthology.
 *
 * The extractor reads the page's `window.__INITIAL_STATE__`, calls the
 * pagelist API for anthologies, and signs the playurl request with WBI. A
 * multi-part video without `?p=` becomes registry entries (`?p=<n>` URLs), so
 * the engine expands it into bounded child jobs; a `?p=<n>` URL extracts that
 * part only. Playurl dash audio/video formats are merged-capable; legacy
 * `durl` fragments become one `http_dash_segments` format.
 */
class BiliBiliIE(
    http: ExtractorHttp,
    private val nowSeconds: () -> Long = { Clock.System.now().toEpochMilliseconds() / 1000 },
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Bilibili"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val prefix = match.groups["prefix"]?.value?.uppercase() ?: throw ExtractionError.UnsupportedUrl()
        val rawId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val webpage = http.downloadWebpage(url)
        val initial = initialState(webpage)
            ?: throw ExtractionError.Malformed("Unable to extract the Bilibili initial state.")
        when (initial.obj("error")?.number("trueCode")?.toLong()) {
            -403L -> throw ExtractionError.LoginRequired()
            -404L -> throw ExtractionError.Unavailable("This video may be deleted or geo-restricted.")
        }

        val isFestival = initial["videoData"] !is JsonObject
        val videoData = (if (isFestival) initial.obj("videoInfo") else initial.obj("videoData"))
            ?: throw ExtractionError.Malformed("Unable to extract the Bilibili video data.")
        val videoId = videoData.str("bvid") ?: (prefix + rawId)
        val baseTitle = videoData.str("title")

        val pageList = if (isFestival) {
            emptyList()
        } else {
            runCatching { fetchPageList(videoId) }.getOrElse { throw ExtractionError.Malformed("pagelist debug: ${it::class.simpleName}: ${it.message}") }
        }
        val isAnthology = pageList.size > 1
        val partId = partOf(url)
        if (isAnthology && partId == null) {
            return InfoDict(
                id = videoId,
                title = baseTitle,
                entries = pageList.mapNotNull { page ->
                    val pageNumber = page.number("page")?.toLong() ?: return@mapNotNull null
                    InfoEntry(
                        id = "${videoId}_p$pageNumber",
                        title = page.str("part"),
                        url = "https://www.bilibili.com/video/$videoId?p=$pageNumber",
                    )
                },
                webpageUrl = url,
                extractor = "bilibili",
                extractorKey = ieKey,
            )
        }

        var title = baseTitle
        if (isAnthology && partId != null) {
            val partName = pageList.getOrNull(partId - 1)?.str("part").orEmpty()
            title = "${baseTitle.orEmpty()} p${partId.toString().padStart(2, '0')} $partName".trim()
        }

        val cid = if (partId != null) {
            pageList.getOrNull(partId - 1)?.number("cid")?.toLong()
        } else {
            videoData.number("cid")?.toLong()
        } ?: throw ExtractionError.Malformed("The video has no cid.")

        val playInfo = downloadPlayInfo(videoId, cid)
        val formats = extractFormats(playInfo, url)
        if (formats.isEmpty()) throw ExtractionError.NoFormats()

        val upData = initial.obj("upData")
        val stat = videoData.obj("stat")
        val festivalThumbnail = if (isFestival) {
            initial.array("sectionEpisodes").orEmpty()
                .filterIsInstance<JsonObject>()
                .firstOrNull { it.str("bvid") == videoId }
                ?.str("cover")
        } else {
            null
        }

        return InfoDict(
            id = if (partId != null) "${videoId}_p$partId" else videoId,
            title = title,
            formats = formats,
            thumbnails = listOfNotNull(
                (videoData.str("pic") ?: festivalThumbnail)?.let { Thumbnail(url = it) },
            ),
            uploader = if (isFestival) videoData.str("upName") else upData?.str("name"),
            channelId = if (isFestival) {
                videoData.number("upMid")?.toLong()?.toString()
            } else {
                upData?.number("mid")?.toLong()?.toString()
            },
            uploadDate = videoData.number("pubdate")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = videoData.number("viewCount")?.toLong() ?: stat?.number("view")?.toLong(),
            description = videoData.str("desc"),
            webpageUrl = url,
            extractor = "bilibili",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_download_playinfo`: the WBI-signed playurl call. */
    private suspend fun downloadPlayInfo(bvid: String, cid: Long): JsonObject {
        val key = wbiKey(fetchWbiKey())
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "fnval" to "4048",
            "try_look" to "1",
        )
        val signed = wbiSign(params, key, nowSeconds())
        val query = signed.entries.joinToString("&") { (name, value) ->
            "${percentEncode(name)}=${percentEncode(value)}"
        }
        val json = http.downloadJson(
            "$PLAYURL_URL?$query",
            headers = mapOf("referer" to "https://www.bilibili.com/"),
        )
        return (json as? JsonObject)?.obj("data")
            ?: throw ExtractionError.Malformed("The playurl response was empty.")
    }

    private suspend fun fetchPageList(bvid: String): List<JsonObject> {
        val json = http.downloadJson(
            "$PAGELIST_URL?bvid=$bvid&jsonp=jsonp",
            headers = mapOf("referer" to "https://www.bilibili.com/"),
        )
        return ((json as? JsonObject)?.array("data"))?.filterIsInstance<JsonObject>().orEmpty()
    }

    /** Upstream `_get_wbi_key`: the nav response's two image names joined. */
    private suspend fun fetchWbiKey(): String {
        val json = http.downloadJson(NAV_URL)
        val wbiImg = (json as? JsonObject)?.obj("data")?.obj("wbi_img")
            ?: throw ExtractionError.Malformed("The WBI key response was empty.")
        return basename(wbiImg.str("img_url")) + basename(wbiImg.str("sub_url"))
    }

    /** Upstream `extract_formats`: dash audio/video plus the `durl` fallback. */
    private fun extractFormats(playInfo: JsonObject, referer: String): List<MediaFormat> {
        val formatNames = linkedMapOf<Long, String>()
        for (support in playInfo.array("support_formats").orEmpty()) {
            val quality = (support as? JsonObject)?.number("quality")?.toLong() ?: continue
            formatNames[quality] = (support.str("new_description") ?: support.str("display_desc")).orEmpty()
        }

        val dash = playInfo.obj("dash")
        val audios = buildList {
            dash?.array("audio")?.filterIsInstance<JsonObject>()?.let { addAll(it) }
            dash?.obj("dolby")?.array("audio")?.filterIsInstance<JsonObject>()?.let { addAll(it) }
            dash?.obj("flac")?.obj("audio")?.let { add(it) }
        }

        val formats = mutableListOf<MediaFormat>()
        for (audio in audios) {
            val audioUrl = audio.str("baseUrl") ?: audio.str("base_url") ?: audio.str("url") ?: continue
            formats += MediaFormat(
                formatId = audio.number("id")?.toLong()?.toString() ?: audio.str("id"),
                url = audioUrl,
                ext = ExtractorUtils.mimetype2ext(audio.str("mimeType") ?: audio.str("mime_type")),
                vcodec = MediaFormat.CODEC_NONE,
                acodec = audio.str("codecs")?.lowercase(),
                tbr = audio.number("bandwidth")?.div(1000),
                filesize = audio.number("size")?.toLong(),
                httpHeaders = mapOf("referer" to referer),
            )
        }
        for (video in dash?.array("video").orEmpty()) {
            val element = video as? JsonObject ?: continue
            val videoUrl = element.str("baseUrl") ?: element.str("base_url") ?: element.str("url") ?: continue
            val quality = element.number("id")?.toLong()
            val videoId = FORMAT_ID.find(videoUrl)?.groupValues?.get(1)
            formats += MediaFormat(
                formatId = videoId ?: quality?.toString(),
                url = videoUrl,
                ext = ExtractorUtils.mimetype2ext(element.str("mimeType") ?: element.str("mime_type")),
                vcodec = element.str("codecs"),
                acodec = if (audios.isEmpty()) null else MediaFormat.CODEC_NONE,
                width = element.number("width")?.toLong(),
                height = element.number("height")?.toLong(),
                fps = element.number("frameRate") ?: element.number("frame_rate"),
                tbr = element.number("bandwidth")?.div(1000),
                filesize = element.number("size")?.toLong(),
                quality = quality?.toString(),
                dynamicRange = when (quality) {
                    126L -> "DV"
                    125L -> "HDR10"
                    else -> null
                },
                formatNote = formatNames[quality]?.ifEmpty { null },
                httpHeaders = mapOf("referer" to referer),
            )
        }

        val fragments = playInfo.array("durl").orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val fragmentUrl = entry.str("url") ?: return@mapNotNull null
            MediaFragment(
                url = fragmentUrl,
                rangeEnd = null,
            ) to entry.number("size")?.toLong()
        }
        if (fragments.isNotEmpty()) {
            val quality = playInfo.number("quality")?.toLong()
            val note = formatNames[quality]
            val resolution = parseResolution(note)
            formats += MediaFormat(
                formatId = quality?.toString(),
                url = fragments.first().first.url,
                ext = "mp4",
                protocol = if (fragments.size > 1) "http_dash_segments" else "https",
                fragments = if (fragments.size > 1) fragments.map { it.first } else null,
                filesize = fragments.mapNotNull { it.second }.sum(),
                quality = quality?.toString(),
                width = resolution?.first,
                height = resolution?.second,
                formatNote = note?.ifEmpty { null },
                httpHeaders = mapOf("referer" to referer),
            )
        }
        return formats
    }

    private fun partOf(url: String): Int? {
        val query = url.substringAfter('?', "")
        for (pair in query.split('&')) {
            val name = pair.substringBefore('=')
            if (name != "p") continue
            return pair.substringAfter('=', "").toIntOrNull()?.takeIf { it > 0 }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "BiliBili"

        /** Upstream `_VALID_URL` for the video and festival forms. */
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bilibili\\.com/" +
                "(?:video/|festival/[^/?#]+\\?(?:[^#]*&)?bvid=)(?<prefix>[aAbB][vV])(?<id>[^/?#&]+)",
        )

        private const val NAV_URL: String = "https://api.bilibili.com/x/web-interface/nav"
        private const val PAGELIST_URL: String = "https://api.bilibili.com/x/player/pagelist"
        private const val PLAYURL_URL: String = "https://api.bilibili.com/x/player/wbi/playurl"
        private val FORMAT_ID: Regex = Regex("-(\\d+)\\.m4s\\?")

        /** `getMixinKey()` from the vendor JS, as upstream uses it. */
        private val MIXIN_KEY_ENC_TAB: IntArray = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52,
        )

        /**
         * Upstream `_get_wbi_key` mix: the two nav image names are joined and
         * permuted by [MIXIN_KEY_ENC_TAB], then cut to 32 characters. Exposed
         * for the WBI tests; the extractor never logs the key.
         */
        internal fun wbiKey(lookup: String): String =
            MIXIN_KEY_ENC_TAB.toList().mapNotNull { lookup.getOrNull(it) }.joinToString("").take(32)

        /**
         * Upstream `_sign_wbi`: `wts` is added, values are stripped of
         * `!'()*`, keys are sorted, the query is URL-encoded, and `w_rid` is
         * the MD5 of `query + key`. The result is per-request and never stored.
         */
        internal fun wbiSign(
            params: Map<String, String>,
            key: String,
            nowSeconds: Long,
        ): Map<String, String> {
            val withWts = params + ("wts" to nowSeconds.toString())
            val query = withWts.entries.sortedBy { it.key }.joinToString("&") { (name, value) ->
                val filtered = value.filterNot { it in "!'()*" }
                "${percentEncode(name)}=${percentEncode(filtered)}"
            }
            val rid = md5Hex((query + key).encodeToByteArray())
            return withWts + ("w_rid" to rid)
        }

        private fun basename(value: String?): String =
            value?.substringAfterLast('/')?.substringBefore('.') ?: ""

        private fun parseResolution(name: String?): Pair<Long?, Long?>? {
            if (name.isNullOrBlank()) return null
            val height = Regex("(\\d{3,4})[pP]").find(name)?.groupValues?.get(1)?.toLongOrNull()
                ?: return null
            return null to height
        }

        /** `urllib.parse.urlencode`'s quote_plus for the WBI values. */
        private fun percentEncode(value: String): String {
            val out = StringBuilder(value.length)
            for (byte in value.encodeToByteArray()) {
                val code = byte.toInt() and 0xff
                val character = code.toChar()
                when {
                    character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' -> out.append(character)
                    character in "-_.~" -> out.append(character)
                    character == ' ' -> out.append('+')
                    else -> {
                        val hex = "0123456789ABCDEF"
                        out.append('%').append(hex[code ushr 4]).append(hex[code and 0x0f])
                    }
                }
            }
            return out.toString()
        }
    }
}

/**
 * Upstream `BiliBiliPlayerIE`: the embed URL carries only an `aid`, so the
 * extractor canonicalizes it to the `av` watch URL and re-enters the registry
 * through [InfoDict.redirectUrl].
 */
class BiliBiliPlayerIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Bilibili player"

    override suspend fun extract(url: String): InfoDict {
        val aid = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = aid,
            webpageUrl = url,
            extractor = "bilibili",
            extractorKey = ieKey,
            redirectUrl = "https://www.bilibili.com/video/av$aid",
        )
    }

    companion object {
        const val IE_KEY: String = "BiliBiliPlayer"

        val VALID_URL: Regex = Regex(
            "https?://player\\.bilibili\\.com/player\\.html\\?.*?\\baid=(?<id>\\d+)",
        )
    }
}

/** Upstream `_search_json(r'window\.__INITIAL_STATE__\s*=', ...)` subset. */
private fun initialState(html: String): JsonObject? {
    val marker = Regex("window\\.__INITIAL_STATE__\\s*=")
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    val open = html.getOrNull(index) ?: return null
    if (open != '{' && open != '[') return null
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
                    return ExtractorUtils.parseJson(html.substring(index, position + 1)) as? JsonObject
                }
            }
        }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
