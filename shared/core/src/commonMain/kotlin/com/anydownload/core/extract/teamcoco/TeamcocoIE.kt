/*
 * Team Coco extractors — AnyDownload
 *
 * Kotlin translation of `teamcoco.py` from `yt_dlp/extractor/teamcoco.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `teamcoco.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Next.js `pageData` blocks merged into one info (meta-tags,
 * video-player, video-info), the `src` rows as one HLS row or direct quality
 * rows with the low/sd/hd/uhd sizes, the thumbnail-derived video id, the
 * Conan Classic legacy GraphQL JSON, and the NGTV media info through the
 * shared Turner base with the `jws` token. Limitations: the
 * `_initialize_geo_bypass(['US'])` call is not carried (the port has no geo
 * bypass); m3u8 subtitles are not parsed (one row per manifest); the
 * `display_id` and `_old_archive_ids` fields are not modeled and are
 * dropped; `timestamp` folds into `uploadDate`. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.teamcoco

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.turner.TurnerBaseIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val QUALITIES = linkedMapOf(
    "low" to (480L to 272L),
    "sd" to (640L to 360L),
    "hd" to (1280L to 720L),
    "uhd" to (1920L to 1080L),
)

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

/** Upstream `TeamcocoBaseIE`: the shared src-row parser. */
abstract class TeamcocoBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : TurnerBaseIE(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_get_formats_and_subtitles`. */
    protected fun getFormatsAndSubtitles(info: JsonObject): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in info.array("src").orEmpty()) {
            val src = element as? JsonObject ?: continue
            val formatId = src.str("label")
            var srcUrl = src.str("src") ?: continue
            if (Regex("https?:/[^/]").containsMatchIn(srcUrl)) {
                srcUrl = srcUrl.replaceFirst(":/", "://")
            }
            val ext = ExtractorUtils.determineExt(srcUrl, ExtractorUtils.mimetype2ext(src.str("type")) ?: "unknown_video")
            if (formatId == null || srcUrl.isBlank()) continue
            if (formatId == "hls" || ext == "m3u8") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = srcUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            } else if (formatId in QUALITIES) {
                if (srcUrl.startsWith("/mp4:protected/")) continue
                val (width, height) = QUALITIES.getValue(formatId)
                formats += MediaFormat(
                    url = srcUrl,
                    ext = ext,
                    formatId = formatId,
                    width = width,
                    height = height,
                )
            }
        }
        return formats
    }

    /** Upstream `_search_nextjs_data`. */
    protected fun searchNextJsData(html: String): JsonObject? =
        NEXT_DATA.find(html)?.groupValues?.get(1)?.let { ExtractorUtils.parseJson(it) as? JsonObject }

    protected fun pageData(webpage: String): JsonObject =
        searchNextJsData(webpage)?.obj("props")?.obj("pageProps")?.obj("pageData")
            ?: throw ExtractionError.Malformed("The Team Coco page carried no Next.js page data.")
}

/** Upstream `TeamcocoIE`: a teamcoco.com video page. */
class TeamcocoIE(
    http: ExtractorHttp,
) : TeamcocoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = (matchId(url) ?: throw ExtractionError.UnsupportedUrl()).replace('/', '_')
        val webpage = http.downloadWebpage(url)
        val data = pageData(webpage)

        val info = linkedMapOf<String, JsonElement>()
        for (element in data.array("blocks").orEmpty()) {
            val block = element as? JsonObject ?: continue
            if (block.str("name") !in setOf("meta-tags", "video-player", "video-info")) continue
            block.obj("props")?.forEach { (key, value) ->
                if (value !is JsonNull) info[key] = value
            }
        }
        val merged = JsonObject(info)

        val thumbnail = (merged.str("image") ?: merged.str("poster"))
            ?.let { urlJoin("https://teamcoco.com/", it) }
        val videoId = thumbnail?.let { queryValue(it, "id") } ?: displayId

        return InfoDict(
            id = videoId,
            title = merged.str("title"),
            description = cleanHtml(merged.str("descriptionHtml") ?: merged.str("description")),
            uploadDate = merged.str("publishedOn")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = getFormatsAndSubtitles(merged),
            webpageUrl = url,
            extractor = "teamcoco",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Teamcoco"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?teamcoco\\.com/(?<id>([^/]+/)*[^/?#]+)")
    }
}

/** Upstream `ConanClassicIE`: a conanclassic.com or conan25 page. */
class ConanClassicIE(
    http: ExtractorHttp,
) : TeamcocoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val data = pageData(webpage)
        val videoId = findVideoId(data)
            ?: throw ExtractionError.NoFormats("Unable to extract video ID from webpage.")

        val response = http.downloadJson(
            "https://conanclassic.com/api/legacy/graphql",
            method = "POST",
            headers = mapOf("content-type" to "application/json"),
            body = buildJsonObject {
                put("query", GRAPHQL_QUERY)
                put("variables", buildJsonObject { put("id", videoId) })
            }.toString().encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Conan Classic API was not an object.")

        val responseData = response.obj("data")
        val findRecord = responseData?.obj("findRecord")
        val metadata = responseData?.obj("findRecordVideoMetadata")

        val mediaId = findRecord?.str("turnerMediaId") ?: metadata?.str("turnerMediaId")
        val formats: List<MediaFormat>
        var duration = ExtractorUtils.parseDuration(findRecord?.str("duration"))
        if (mediaId != null) {
            val token = findRecord?.str("turnerMediaAuthToken")
                ?: metadata?.str("turnerMediaAuthToken")
                ?: throw ExtractionError.Unavailable("No Turner Media auth token found in API response.")
            // Upstream `_initialize_geo_bypass({'countries': ['US']})` is not carried.
            val ngtv = extractNgtvInfo(
                mediaId,
                tokenizerQuery = mapOf("accessToken" to token, "accessTokenType" to "jws"),
            )
            formats = ngtv.formats
            duration = ngtv.duration ?: duration
        } else {
            formats = getFormatsAndSubtitles(metadata ?: JsonObject(emptyMap()))
        }

        return InfoDict(
            id = videoId,
            title = findRecord?.str("title"),
            description = findRecord?.str("teaser"),
            duration = duration,
            uploadDate = findRecord?.str("publishOn")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = findRecord?.obj("thumb")?.str("preview")
                ?.let { listOf(Thumbnail(url = it)) }
                .orEmpty(),
            formats = formats,
            webpageUrl = url,
            extractor = "conanclassic",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_ID_PATH`: the incomingVideoId field or the incomingVideoRecord id. */
    private fun findVideoId(data: JsonObject): String? {
        for (element in data.array("blocks").orEmpty()) {
            val props = (element as? JsonObject)?.obj("props") ?: continue
            for (field in props.array("fieldDefs").orEmpty()) {
                val definition = field as? JsonObject ?: continue
                if (definition.str("name") == "incomingVideoId") {
                    definition.str("value")?.let { return it }
                }
            }
            props.obj("fields")?.obj("incomingVideoRecord")?.str("id")?.let { return it }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "ConanClassic"

        /** Upstream `_GRAPHQL_QUERY`, verbatim. */
        const val GRAPHQL_QUERY: String = """
query find(${'$'}id: ID!) {
  findRecord(id: ${'$'}id) {

... on MetaInterface {
  id
  title
  teaser
  publishOn
  slug
  thumb {

... on FileInterface {
  id
  path
  preview
  mime
}

  }
}

... on Video {
  videoType
  duration
  isLive
  youtubeId
  turnerMediaId
  turnerMediaAuthToken
  airDate
}

... on Episode {
  airDate
  seasonNumber
  episodeNumber
  guestNames
}

  }
  findRecordVideoMetadata(id: ${'$'}id) {
    turnerMediaId
    turnerMediaAuthToken
    duration
    src
  }
}"""

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.)?conanclassic|conan25\\.teamcoco)\\.com/(?<id>([^/]+/)*[^/?#]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `urljoin` for the thumbnail paths the page carries. */
private fun urlJoin(base: String, path: String): String =
    if (path.startsWith("http")) path else base.trimEnd('/') + "/" + path.trimStart('/')

/** Upstream `parse_qs(url)['name'][0]`. */
private fun queryValue(url: String, name: String): String? {
    for (pair in url.substringAfter('?', "").split('&')) {
        if (pair.substringBefore('=') == name) return pair.substringAfter('=', "")
    }
    return null
}

/** Upstream `clean_html`. */
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
