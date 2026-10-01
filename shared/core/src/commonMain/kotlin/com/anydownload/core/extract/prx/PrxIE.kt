/*
 * PRX extractors — AnyDownload
 *
 * Kotlin translation of the public CMS API subset of `prx.py` from
 * `yt_dlp/extractor/prx.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `prx.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `cms.prx.org/api/v1` story/series/account API, the
 * `_embedded` image/audio/account/series relations, the audio-piece
 * formats, and the paged story/series listings (five pages eagerly). The
 * `prxstories:`/`prxseries:` search keys are not routed by the engine, so
 * those two classes are planned. The port does not carry tags, series,
 * season, or episode fields, so they are dropped. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.prx

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_BASE = "https://cms.prx.org/api/v1/"
private const val MAX_PAGES = 5
private const val BASE_URL_RE = "https?://(?:(?:beta|listen)\\.)?prx\\.org/"

/** One audio piece: its format row plus the duration upstream keeps on it. */
private data class AudioPiece(val format: MediaFormat, val duration: Double?)

/** Upstream `PRXStoryIE`: a story. */
class PRXStoryIE(
    http: ExtractorHttp,
) : PrxBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val storyId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi(storyId, "stories/$storyId")
        return extractStory(response, storyId).copy(webpageUrl = url, extractorKey = IE_KEY)
    }

    companion object {
        const val IE_KEY: String = "PRXStory"

        val VALID_URL: Regex = Regex(BASE_URL_RE + "stories/(?<id>\\d+)")
    }
}

/** Upstream `PRXSeriesIE`: a series listing. */
class PRXSeriesIE(
    http: ExtractorHttp,
) : PrxBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val seriesId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi(seriesId, "series/$seriesId")
        val info = extractSeriesInfo(response)
        val id = info.id ?: seriesId
        return info.copy(
            entries = entries(id, "series/$id/stories"),
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PRXSeries"

        val VALID_URL: Regex = Regex(BASE_URL_RE + "series/(?<id>\\d+)")
    }
}

/** Upstream `PRXAccountIE`: an account listing. */
class PRXAccountIE(
    http: ExtractorHttp,
) : PrxBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val accountId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApi(accountId, "accounts/$accountId")
        val info = extractAccountInfo(response)
            ?: throw ExtractionError.Malformed("The PRX account API returned no account.")
        val id = info.id ?: accountId
        return info.copy(
            entries = entries(id, "accounts/$id/series") + entries(id, "accounts/$id/stories"),
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PRXAccount"

        val VALID_URL: Regex = Regex(BASE_URL_RE + "accounts/(?<id>\\d+)")
    }
}

/** Shared upstream `PRXBaseIE` behaviour. */
abstract class PrxBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_call_api`. */
    protected suspend fun callApi(itemId: String, path: String, query: String = ""): JsonObject {
        val suffix = if (query.isEmpty()) "" else "?$query"
        return http.downloadJson("$API_BASE$path$suffix") as? JsonObject
            ?: throw ExtractionError.Malformed("The PRX API returned no object for $itemId.")
    }

    private fun embedElement(response: JsonObject, section: String): JsonElement? =
        response.obj("_embedded")?.get("prx:$section")

    private fun embedObject(response: JsonObject, section: String): JsonObject? =
        embedElement(response, section) as? JsonObject

    private fun fileLink(response: JsonObject): String? =
        response.obj("_links")?.obj("enclosure")?.str("href")

    private fun imageThumbnail(image: JsonObject?): Thumbnail? {
        val url = image?.let { fileLink(it) } ?: return null
        return Thumbnail(
            url = url,
            width = image.number("width")?.toLong(),
            height = image.number("height")?.toLong(),
        )
    }

    /** Upstream `_extract_base_info`. */
    protected fun extractBaseInfo(response: JsonObject): InfoDict {
        val id = response.primitive("id")
            ?: throw ExtractionError.Malformed("The PRX item had no id.")
        val thumbnail = imageThumbnail(embedObject(response, "image"))
        return InfoDict(
            id = id,
            title = response.str("title") ?: id,
            description = cleanHtml(response.str("description")) ?: response.str("shortDescription"),
            duration = response.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(response.str("releasedAt")),
            thumbnails = listOfNotNull(thumbnail),
            extractor = "prx",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_extract_account_info`. */
    protected fun extractAccountInfo(response: JsonObject?): InfoDict? {
        if (response == null) return null
        val base = runCatching { extractBaseInfo(response) }.getOrNull() ?: return null
        val name = response.str("name")
        return base.copy(
            title = name,
            channel = name,
            channelId = base.id,
        )
    }

    /** Upstream `_extract_series_info`. */
    protected fun extractSeriesInfo(response: JsonObject): InfoDict {
        val base = extractBaseInfo(response)
        val account = extractAccountInfo(embedObject(response, "account"))
        return base.copy(
            channel = account?.channel,
            channelId = account?.channelId,
        )
    }

    /** Upstream `_extract_story_info`. */
    protected fun extractStoryInfo(response: JsonObject): InfoDict {
        val base = extractBaseInfo(response)
        val account = extractAccountInfo(embedObject(response, "account"))
        return base.copy(
            channel = account?.channel,
            channelId = account?.channelId,
        )
    }

    /** Upstream `_extract_story`: one format row, or one media item per audio piece. */
    protected fun extractStory(response: JsonObject, storyId: String): InfoDict {
        val info = extractStoryInfo(response)
        val pieces = extractAudioPieces(response)
        if (pieces.size == 1) {
            return info.copy(formats = listOf(pieces.single().format))
        }
        val media = pieces.mapIndexed { index, piece ->
            InfoMedia(
                mediaId = "${info.id ?: storyId}_part${index + 1}",
                title = info.title,
                duration = piece.duration,
                formats = listOf(piece.format),
            )
        }
        return info.copy(media = media)
    }

    /** Upstream `_extract_audio_pieces`. */
    private fun extractAudioPieces(storyResponse: JsonObject): List<AudioPiece> {
        val audio = embedObject(storyResponse, "audio") ?: return emptyList()
        val items = (embedElement(audio, "items") as? JsonArray)
            ?: audio.array("prx:items")
            ?: return emptyList()
        return items.mapNotNull { element ->
            val piece = element as? JsonObject ?: return@mapNotNull null
            val url = fileLink(piece) ?: return@mapNotNull null
            AudioPiece(
                format = MediaFormat(
                    formatId = piece.primitive("id"),
                    formatNote = piece.str("label"),
                    url = url,
                    ext = ExtractorUtils.mimetype2ext(piece.str("contentType")),
                    filesize = piece.number("size")?.toLong(),
                    abr = piece.number("bitRate"),
                    asr = piece.number("frequency")?.let { (it / 1000.0).toLong() },
                    vcodec = MediaFormat.CODEC_NONE,
                ),
                duration = piece.number("duration"),
            )
        }.sortedBy { it.format.formatId?.toDoubleOrNull() ?: 0.0 }
    }

    /** Upstream `_entries`: paged story/series listing, five pages eagerly. */
    protected suspend fun entries(itemId: String, endpoint: String): List<InfoEntry> {
        val out = mutableListOf<InfoEntry>()
        var total = 0
        var page = 1
        while (page <= MAX_PAGES) {
            val response = try {
                callApi(itemId, endpoint, "page=$page&per=100")
            } catch (error: ExtractionError) {
                break
            }
            val items = (embedElement(response, "items") as? JsonArray) ?: break
            if (items.isEmpty()) break
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val id = item.primitive("id") ?: continue
                val path = if (endpoint.contains("/series")) "series" else "stories"
                out += InfoEntry(
                    id = id,
                    title = item.str("title"),
                    url = "https://beta.prx.org/$path/$id",
                )
            }
            total += response.number("count")?.toInt() ?: items.size
            val announced = response.number("total")?.toInt() ?: total
            if (total >= announced) break
            page++
        }
        return out
    }
}

// ------------------------------------------------------------------ helpers

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonElement.str(name: String): String? = (this as? JsonObject)?.str(name)
