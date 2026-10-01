/*
 * Ximalaya extractors — AnyDownload
 *
 * Kotlin translation of `ximalaya.py` from
 * `yt_dlp/extractor/ximalaya.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ximalaya.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `m.ximalaya.com/tracks` JSON with the public `play_path_32`/
 * `play_path_64` rows (`vcodec: none`), the cover thumbnails, the intro
 * newline cleanup, and the album `getTracksList` paging as child entries.
 * Limitations: the VIP `mpay` path (the seeded filename decrypt and the RC4
 * `ep` URL params, both needing the embedded key) is not carried — the port
 * does not add a crypto helper with a key (the naver/zingmp3/abc rule); a
 * VIP-only track fails typed, and a track that does carry public play paths
 * still yields them. `uploader_id`/`uploader_url`/`categories`/`like_count`
 * are not modeled and are dropped; the album paging is capped at 100 pages.
 * No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.ximalaya

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val VIP_WALL =
    "This Ximalaya track needs the VIP mpay path (the seeded filename decrypt and the RC4 " +
        "URL params), which the port does not carry."

/** Upstream `XimalayaIE`: one sound page. */
class XimalayaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val scheme = if (url.startsWith("https")) "https" else "http"
        val audioId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val audioInfo = http.downloadJson("$scheme://m.ximalaya.com/tracks/$audioId.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The Ximalaya track API was not an object.")

        val isPaid = audioInfo.boolean("is_paid") == true
        val formats = mutableListOf<MediaFormat>()
        for ((bps, key) in listOf(24L to "play_path_32", 64L to "play_path_64")) {
            val audioUrl = audioInfo.str(key) ?: continue
            formats += MediaFormat(
                formatId = "${bps}k",
                url = audioUrl,
                abr = bps.toDouble(),
                vcodec = "none",
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats(
                if (isPaid) VIP_WALL else "No downloadable formats were found.",
            )
        }

        val thumbnails = mutableListOf<Thumbnail>()
        for ((key, value) in audioInfo) {
            if (!key.startsWith("cover_url")) continue
            val coverUrl = (value as? JsonPrimitive)?.content ?: continue
            thumbnails += Thumbnail(
                url = coverUrl,
                id = key,
                width = if (key == "cover_url_142") 180L else null,
                height = if (key == "cover_url_142") 180L else null,
            )
        }

        val description = audioInfo.str("intro")
            ?.replace("\r\n\r\n\r\n ", "\n")
            ?.replace("\r\n", "\n")

        return InfoDict(
            id = audioId,
            title = audioInfo.str("title"),
            description = description,
            uploader = audioInfo.str("nickname"),
            duration = audioInfo.number("duration"),
            viewCount = audioInfo.number("play_count")?.toLong(),
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "ximalaya",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Ximalaya"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?ximalaya\\.com/(?:(?<uid>\\d+)/)?sound/(?<id>[0-9]+)",
        )
    }
}

/** Upstream `XimalayaAlbumIE`: an album playlist. */
class XimalayaAlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()

        val firstPage = fetchPage(playlistId, 1)
        val trackTotalCount = firstPage.number("trackTotalCount") ?: 0.0
        val pageSize = firstPage.number("pageSize")?.takeIf { it > 0 } ?: 30.0
        val pageCount = kotlin.math.ceil(trackTotalCount / pageSize).toInt().coerceAtLeast(1)

        val entries = mutableListOf<InfoEntry>()
        entries += getEntries(firstPage)
        var page = 2
        while (page <= pageCount && page <= MAX_PAGES) {
            entries += getEntries(fetchPage(playlistId, page))
            page++
        }

        val title = (firstPage.array("tracks")?.firstOrNull() as? JsonObject)?.str("albumTitle")
        return InfoDict(
            id = playlistId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "ximalaya:album",
            extractorKey = ieKey,
        )
    }

    private suspend fun fetchPage(playlistId: String, page: Int): JsonObject {
        val response = http.downloadJson(
            "https://www.ximalaya.com/revision/album/v1/getTracksList" +
                "?albumId=$playlistId&pageNum=$page",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Ximalaya album API was not an object.")
        return response.obj("data")
            ?: throw ExtractionError.Malformed("The Ximalaya album API returned no data.")
    }

    /** Upstream `_get_entries`: the proto-relative track URLs as child entries. */
    private fun getEntries(pageData: JsonObject): List<InfoEntry> =
        pageData.array("tracks").orEmpty().mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            val trackUrl = track.str("url") ?: return@mapNotNull null
            val url = if (trackUrl.startsWith("//")) "https:$trackUrl"
            else "https://www.ximalaya.com$trackUrl"
            InfoEntry(id = track.str("trackId"), title = track.str("title"), url = url)
        }

    companion object {
        const val IE_KEY: String = "XimalayaAlbum"

        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|m\\.)?ximalaya\\.com/(?:\\d+/)?album/(?<id>[0-9]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

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

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
