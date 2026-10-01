/*
 * TapTap extractors — AnyDownload
 *
 * Kotlin translation of `taptap.py` from `yt_dlp/extractor/taptap.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `taptap.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `webapiv2` moment/app/post detail JSON, the `video-resource`
 * multi-get JSON, the per-call random `X-UA` value, and the four URL forms.
 * Limitations: the upstream playlist result maps to the port's selectable
 * `media` items (each video id becomes one item; the page metadata merges
 * into each item's title only), an m3u8 URL becomes one HLS row (so the
 * upstream h265 format-id rename is not carried), and the
 * `modified_timestamp` field is dropped. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.taptap

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

private const val X_UA_CN =
    "V=1&PN=WebApp&LANG=zh_CN&VN_CODE=102&LOC=CN&PLT=PC&DS=Android&UID={uuid}" +
        "&OS=Windows&OSV=10&DT=PC"
private const val X_UA_INTL =
    "V=1&PN=WebAppIntl2&LANG=zh_TW&VN_CODE=115&VN=0.1.0&LOC=CN&PLT=PC&DS=Android&UID={uuid}" +
        "&CURR=&DT=PC&OS=Windows&OSV=NT%208.0.0"

private const val VIDEO_API_CN = "https://www.taptap.cn/webapiv2/video-resource/v1/multi-get"
private const val VIDEO_API_INTL = "https://www.taptap.io/webapiv2/video-resource/v1/multi-get"

/** The page metadata upstream merges into the playlist result. */
data class TapTapMeta(
    val title: String? = null,
    val description: String? = null,
    val uploader: String? = null,
    val uploadDate: String? = null,
)

/** Upstream `TapTapBaseIE`: the API calls and the per-page playlist. */
abstract class TapTapBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    protected val xUaTemplate: String,
    protected val videoApi: String,
    protected val infoApi: String,
    protected val infoQueryKey: String = "id",
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {

    /** Upstream `_get_api`: adds the random `X-UA` value and returns `data`. */
    protected suspend fun getApi(url: String, videoId: String, query: Map<String, String>): JsonObject {
        val params = query + ("X-UA" to xUaTemplate.replace("{uuid}", randomUuid()))
        val queryString = params.entries.joinToString("&") {
            "${percentEncode(it.key)}=${percentEncode(it.value)}"
        }
        val response = http.downloadJson("$url?$queryString") as? JsonObject
            ?: throw ExtractionError.Malformed("The TapTap API was not an object.")
        return response["data"] as? JsonObject
            ?: throw ExtractionError.Malformed("The TapTap API returned no data.")
    }

    /** Upstream `_extract_video`: one video-resource entry. */
    protected suspend fun extractVideo(videoId: String, title: String?): InfoMedia {
        val videoData = (getApi(videoApi, videoId, mapOf("video_ids" to videoId))["list"] as? JsonArray)
            ?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The TapTap video list was empty.")
        val playUrl = videoData.obj("play_url")
        val videoUrl = playUrl?.str("url_h265") ?: playUrl?.str("url")
        val thumbnail = videoData.obj("thumbnail")
        return InfoMedia(
            mediaId = videoId,
            title = title,
            duration = videoData.obj("info")?.number("duration"),
            thumbnails = (thumbnail?.str("original_url") ?: thumbnail?.str("url"))
                ?.let { listOf(Thumbnail(url = it)) }
                .orEmpty(),
            formats = videoUrl?.let {
                listOf(MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native"))
            }.orEmpty(),
        )
    }

    /** Upstream `_real_extract`: the playlist result as selectable media. */
    protected suspend fun playlist(url: String, dataPath: String?): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = getApi(infoApi, videoId, mapOf(infoQueryKey to videoId))
        val root = dataPath?.let { data.obj(it) } ?: data
        val meta = metainfo(root)
        val media = videoIds(root).distinct().map { extractVideo(it, meta.title) }
        return InfoDict(
            id = videoId,
            title = meta.title,
            description = meta.description,
            uploader = meta.uploader,
            uploadDate = meta.uploadDate,
            media = media,
            webpageUrl = url,
            extractor = extractorName,
            extractorKey = ieKey,
        )
    }

    /** Upstream `_ID_PATH`. */
    protected abstract fun videoIds(root: JsonObject): List<String>

    /** Upstream `_META_PATH`. */
    protected abstract fun metainfo(root: JsonObject): TapTapMeta

    protected abstract val extractorName: String
}

/** Upstream `TapTapMomentIE`: a www.taptap.cn moment. */
class TapTapMomentIE(
    http: ExtractorHttp,
) : TapTapBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    xUaTemplate = X_UA_CN,
    videoApi = VIDEO_API_CN,
    infoApi = "https://www.taptap.cn/webapiv2/moment/v3/detail",
) {
    override suspend fun extract(url: String): InfoDict = playlist(url, dataPath = "moment")

    override fun videoIds(root: JsonObject): List<String> {
        val topic = root.obj("topic") ?: return emptyList()
        val ids = mutableListOf<String>()
        for (element in topic.array("videos").orEmpty()) {
            (element as? JsonObject)?.str("video_id")?.let { ids += it }
        }
        topic.obj("pin_video")?.str("video_id")?.let { ids += it }
        return ids
    }

    override fun metainfo(root: JsonObject): TapTapMeta = TapTapMeta(
        title = root.obj("topic")?.str("title"),
        description = root.obj("topic")?.str("summary"),
        uploader = root.obj("author")?.obj("user")?.str("name"),
        uploadDate = root.number("created_time")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
    )

    override val extractorName: String = "taptap:moment"

    companion object {
        const val IE_KEY: String = "TapTapMoment"

        val VALID_URL: Regex = Regex("https?://www\\.taptap\\.cn/moment/(?<id>\\d+)")
    }
}

/** Upstream `TapTapAppIE`: a www.taptap.cn app page. */
class TapTapAppIE(
    http: ExtractorHttp,
) : TapTapBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    xUaTemplate = X_UA_CN,
    videoApi = VIDEO_API_CN,
    infoApi = "https://www.taptap.cn/webapiv2/app/v4/detail",
) {
    override suspend fun extract(url: String): InfoDict = playlist(url, dataPath = null)

    override fun videoIds(root: JsonObject): List<String> = appVideoIds(root)

    override fun metainfo(root: JsonObject): TapTapMeta = TapTapMeta(
        title = root.str("title"),
        description = cleanHtml(root.obj("description")?.str("text")),
    )

    override val extractorName: String = "taptap:app"

    companion object {
        const val IE_KEY: String = "TapTapApp"

        val VALID_URL: Regex = Regex("https?://www\\.taptap\\.cn/app/(?<id>\\d+)")
    }
}

/** Upstream `TapTapAppIntlIE`: a www.taptap.io app page. */
class TapTapAppIntlIE(
    http: ExtractorHttp,
) : TapTapBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    xUaTemplate = X_UA_INTL,
    videoApi = VIDEO_API_INTL,
    infoApi = "https://www.taptap.io/webapiv2/i/app/v5/detail",
) {
    override suspend fun extract(url: String): InfoDict = playlist(url, dataPath = "app")

    override fun videoIds(root: JsonObject): List<String> = appVideoIds(root)

    override fun metainfo(root: JsonObject): TapTapMeta = TapTapMeta(
        title = root.str("title"),
        description = cleanHtml(root.obj("description")?.str("text")),
    )

    override val extractorName: String = "taptap:app:intl"

    companion object {
        const val IE_KEY: String = "TapTapAppIntl"

        val VALID_URL: Regex = Regex("https?://www\\.taptap\\.io/app/(?<id>\\d+)")
    }
}

/** Upstream `TapTapPostIntlIE`: a www.taptap.io post page. */
class TapTapPostIntlIE(
    http: ExtractorHttp,
) : TapTapBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
    xUaTemplate = X_UA_INTL,
    videoApi = VIDEO_API_INTL,
    infoApi = "https://www.taptap.io/webapiv2/creation/post/v1/detail",
    infoQueryKey = "id_str",
) {
    override suspend fun extract(url: String): InfoDict = playlist(url, dataPath = "post")

    override fun videoIds(root: JsonObject): List<String> {
        val ids = mutableListOf<String>()
        for (element in root.array("videos").orEmpty()) {
            (element as? JsonObject)?.str("video_id")?.let { ids += it }
        }
        root.obj("pin_video")?.str("video_id")?.let { ids += it }
        return ids
    }

    override fun metainfo(root: JsonObject): TapTapMeta = TapTapMeta(
        title = root.str("title"),
        description = root.obj("list_fields")?.str("summary"),
        uploader = root.obj("user")?.str("name"),
        uploadDate = root.number("published_time")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
    )

    override val extractorName: String = "taptap:post:intl"

    companion object {
        const val IE_KEY: String = "TapTapPostIntl"

        val VALID_URL: Regex = Regex("https?://www\\.taptap\\.io/post/(?<id>\\d+)")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_ID_PATH` of the app classes: `(('app_videos', 'videos'), ..., 'video_id')`. */
private fun appVideoIds(root: JsonObject): List<String> {
    val ids = mutableListOf<String>()
    for (name in listOf("app_videos", "videos")) {
        for (element in root.array(name).orEmpty()) {
            (element as? JsonObject)?.str("video_id")?.let { ids += it }
        }
    }
    return ids
}

/** Upstream `uuid.uuid4()` for the `X-UA` value. */
private fun randomUuid(): String {
    val hex = "0123456789abcdef"
    fun segment(length: Int): String = buildString(length) {
        repeat(length) { append(hex[Random.nextInt(hex.length)]) }
    }
    return "${segment(8)}-${segment(4)}-${segment(4)}-${segment(4)}-${segment(12)}"
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

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
