/*
 * ivi.ru extractors — AnyDownload
 *
 * Kotlin translation of `ivi.py` from `yt_dlp/extractor/ivi.py` at upstream
 * tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ivi.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `api.ivi.ru/light` `da.content.get` call, the file rows with
 * the known-format quality index and the DRM/FPS skip, the compilation
 * title/series split, the preview thumbnails, the season/episode page
 * markup, and the compilation/season playlist walk. Limitations: upstream
 * tries site 353 first, which signs the request with a CMAC-Blowfish key
 * (`_LIGHT_KEY`) and pycryptodomex; the port does not embed that key or add
 * a crypto helper (the naver/zingmp3/abc rule) and uses the unsigned site
 * 183 call that upstream falls back to; the `series`/`season`/
 * `season_number`/`episode`/`episode_number` fields are not modeled and are
 * dropped; the generic `_EMBED_REGEX` discovery is not carried. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.ivi

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val LIGHT_URL = "https://api.ivi.ru/light/"

/** Upstream `_KNOWN_FORMATS`, sorted by quality. */
private val KNOWN_FORMATS = listOf(
    "MP4-low-mobile", "MP4-mobile", "FLV-lo", "MP4-lo", "FLV-hi", "MP4-hi",
    "MP4-SHQ", "MP4-HD720", "MP4-HD1080",
)

/** Upstream `IviIE`: one movie or episode. */
class IviIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()

        // Upstream tries site 353 (CMAC-Blowfish signed) first; the port uses
        // the unsigned site 183 fallback it falls back to.
        val body = buildJsonObject {
            put("method", "da.content.get")
            putJsonArray("params") {
                add(JsonPrimitive(videoId))
                add(
                    buildJsonObject {
                        put("site", "s183")
                        put("referrer", "http://www.ivi.ru/watch/$videoId")
                        put("contentid", videoId)
                    },
                )
            }
        }.toString().encodeToByteArray()
        val videoJson = http.downloadJson(LIGHT_URL, method = "POST", body = body) as? JsonObject
            ?: throw ExtractionError.Malformed("The ivi API was not an object.")

        videoJson.obj("error")?.let { error ->
            val origin = error.str("origin")
            val message = error.str("message") ?: error.str("user_message")
            when {
                origin == "NotAllowedForLocation" ->
                    throw ExtractionError.GeoRestricted(countries = listOf("RU"))
                origin == "NoRedisValidData" ->
                    throw ExtractionError.Unavailable("Video $videoId does not exist.")
                message != null ->
                    throw ExtractionError.Unavailable("Unable to download video $videoId: $message")
                else ->
                    throw ExtractionError.Unavailable("Unable to download video $videoId.")
            }
        }

        val result = videoJson.obj("result")
            ?: throw ExtractionError.Malformed("The ivi API returned no result.")
        val rawTitle = result.str("title")
        val compilation = result.str("compilation")

        val formats = mutableListOf<MediaFormat>()
        for (element in result.array("files").orEmpty()) {
            val file = element as? JsonObject ?: continue
            val fileUrl = file.str("url") ?: continue
            val contentFormat = file.str("content_format") ?: ""
            if ("-MDRM-" in contentFormat || "-FPS-" in contentFormat) continue
            formats += MediaFormat(
                url = fileUrl,
                formatId = contentFormat,
                quality = KNOWN_FORMATS.indexOf(contentFormat).takeIf { it >= 0 }?.toString(),
                filesize = file.number("size_in_bytes")?.toLong(),
            )
        }

        val thumbnails = result.array("preview").orEmpty().mapNotNull { element ->
            val preview = element as? JsonObject ?: return@mapNotNull null
            val previewUrl = preview.str("url") ?: return@mapNotNull null
            Thumbnail(url = previewUrl, id = preview.str("content_format"))
        }

        val webpage = http.downloadWebpage(url)
        val description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
            ?: ExtractorUtils.htmlSearchMeta(webpage, "description")

        return InfoDict(
            id = videoId,
            title = if (compilation != null) "$compilation - $rawTitle" else rawTitle,
            description = description,
            duration = result.number("duration"),
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "ivi",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Ivi"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?ivi\\.(?:ru|tv)/" +
                "(?:watch/(?:[^/]+/)?|video/player\\?.*?videoId=)(?<id>\\d+)",
        )
    }
}

/** Upstream `IviCompilationIE`: a compilation or one season of it. */
class IviCompilationIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val compilationId = match.groups["compilationid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["seasonid"]?.value

        val entries = mutableListOf<InfoEntry>()
        val playlistId: String
        val playlistTitle: String?
        if (seasonId != null) {
            val seasonPage = http.downloadWebpage(url)
            playlistId = "$compilationId/season$seasonId"
            playlistTitle = ExtractorUtils.htmlSearchMeta(seasonPage, "title")
            entries += extractEntries(seasonPage, compilationId)
        } else {
            val compilationPage = http.downloadWebpage(url)
            playlistId = compilationId
            playlistTitle = ExtractorUtils.htmlSearchMeta(compilationPage, "title")
            val seasons = SEASON_LINK.findAll(compilationPage).map { it.groupValues[2] }.toList()
            if (seasons.isEmpty()) {
                entries += extractEntries(compilationPage, compilationId)
            } else {
                for (season in seasons) {
                    val seasonPage = http.downloadWebpage(
                        "http://www.ivi.ru/watch/$compilationId/season$season",
                    )
                    entries += extractEntries(seasonPage, compilationId)
                }
            }
        }

        return InfoDict(
            id = playlistId,
            title = playlistTitle,
            entries = entries,
            webpageUrl = url,
            extractor = "ivi:compilation",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_extract_entries`: the episode links as child URLs. */
    private fun extractEntries(html: String, compilationId: String): List<InfoEntry> =
        Regex("<a\\b[^>]+\\bhref=[\"']/watch/$compilationId/(\\d+)[\"']")
            .findAll(html)
            .map { InfoEntry(id = it.groupValues[1], url = "http://www.ivi.ru/watch/$compilationId/${it.groupValues[1]}") }
            .toList()

    companion object {
        const val IE_KEY: String = "IviCompilation"

        private val SEASON_LINK = Regex("<a href=\"/watch/(?<compilationid>[a-z\\d_-]+)/season(\\d+)")

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?ivi\\.ru/watch/(?!\\d+)(?<compilationid>[a-z\\d_-]+)" +
                "(?:/season(?<seasonid>\\d+))?$",
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
