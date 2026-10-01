/*
 * SRG SSR extractors — AnyDownload
 *
 * Kotlin translation of `srgssr.py` from `yt_dlp/extractor/srgssr.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `srgssr.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `mediaComposition` JSON with the block-reason map, the
 * `tp.srgssr.ch/akahd/token` authparams append, the resource rows (one HLS
 * row for HLS/AKAMAI, direct rows for HTTP(S), the podcast rows), the
 * subtitle list with the per-bu default language, and the `srgssr:` and play
 * URL forms. Limitations: an HDS resource is skipped (the port has no f4m
 * helper); m3u8 subtitle tracks are not parsed; the AKAMAI rows become one
 * HLS row instead of the upstream akamai manifest walk; `timestamp` folds
 * into `uploadDate`. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.srgssr

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val TOKEN_URL = "http://tp.srgssr.ch/akahd/token?acl=*"

private val ERRORS = linkedMapOf(
    "AGERATING12" to "To protect children under the age of 12, this video is only available " +
        "between 8 p.m. and 6 a.m.",
    "AGERATING18" to "To protect children under the age of 18, this video is only available " +
        "between 11 p.m. and 5 a.m.",
    "GEOBLOCK" to "For legal reasons, this video is only available in Switzerland.",
    "LEGAL" to "The video cannot be transmitted for legal reasons.",
    "STARTDATE" to "This video is not yet available. Please try again later.",
)

private val DEFAULT_LANGUAGE_CODES = linkedMapOf(
    "srf" to "de",
    "rts" to "fr",
    "rsi" to "it",
    "rtr" to "rm",
    "swi" to "en",
)

/** Upstream `SRGSSRIE`: one `srgssr:` media id (open for the RTS subclass). */
open class SRGSSRIE(
    http: ExtractorHttp,
    ieKeyName: String = IE_KEY,
    validUrl: Regex = VALID_URL,
) : InfoExtractor(ieKey = ieKeyName, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val bu = match.groups["bu"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val mediaType = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val mediaId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val mediaData = getMediaData(bu, mediaType, mediaId)

        val formats = mutableListOf<MediaFormat>()
        for (element in mediaData.array("resourceList").orEmpty()) {
            val source = element as? JsonObject ?: continue
            val formatUrl = source.str("url") ?: continue
            val protocol = source.str("protocol") ?: ""
            val quality = source.str("quality")
            val formatId = listOf(protocol, source.str("encoding"), quality)
                .filter { !it.isNullOrBlank() }.joinToString("-")
            when {
                protocol == "HDS" -> Unit // The port has no f4m helper.
                protocol in setOf("HLS") && source.str("tokenType") == "AKAMAI" -> {
                    val tokenized = tokenize(formatUrl)
                    formats += MediaFormat(
                        formatId = formatId,
                        url = tokenized,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                }
                protocol == "HLS" -> formats += MediaFormat(
                    formatId = formatId,
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                protocol in setOf("HTTP", "HTTPS") -> formats += MediaFormat(
                    formatId = formatId,
                    url = formatUrl,
                    quality = qualityIndex(quality)?.toString(),
                )
            }
        }

        // The podcast URLs are usually present for audio; only position 0 is the full episode.
        if (mediaData.number("position")?.toLong() == 0L) {
            for ((field, quality) in listOf("podcastSdUrl" to "SD", "podcastHdUrl" to "HD")) {
                val podcastUrl = mediaData.str(field) ?: continue
                formats += MediaFormat(
                    formatId = "PODCAST-$quality",
                    url = podcastUrl,
                    quality = qualityIndex(quality)?.toString(),
                )
            }
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        if (mediaType == "video") {
            for (element in mediaData.array("subtitleList").orEmpty()) {
                val sub = element as? JsonObject ?: continue
                val subUrl = sub.str("url") ?: continue
                val language = sub.str("locale") ?: DEFAULT_LANGUAGE_CODES[bu] ?: "und"
                subtitles += SubtitleTrack(
                    language = language,
                    formats = listOf(SubtitleFormat(ext = "vtt", url = subUrl)),
                )
            }
        }

        return InfoDict(
            id = mediaId,
            title = mediaData.str("title"),
            description = mediaData.str("description"),
            uploadDate = mediaData.str("date")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = mediaData.str("imageUrl")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = mediaData.number("duration")?.let { it / 1000.0 },
            subtitles = subtitles,
            formats = formats,
            webpageUrl = url,
            extractor = "srgssr",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_get_media_data`; protected for the RTS subclass. */
    protected suspend fun getMediaData(bu: String, mediaType: String, mediaId: String): JsonObject {
        val query = if (mediaType == "video") "?onlyChapters=true" else ""
        val response = http.downloadJson(
            "https://il.srgssr.ch/integrationlayer/2.0/$bu/mediaComposition/$mediaType/$mediaId.json$query",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The SRG SSR API was not an object.")
        val chapterList = response.array("chapterList")
            ?: throw ExtractionError.Malformed("The SRG SSR API returned no chapter list.")
        val mediaData = chapterList.filterIsInstance<JsonObject>()
            .firstOrNull { it.str("id") == mediaId }
            ?: throw ExtractionError.Malformed("No media information found.")
        mediaData.str("blockReason")?.let { blockReason ->
            val message = ERRORS[blockReason]
            if (blockReason == "GEOBLOCK") {
                throw ExtractionError.GeoRestricted(countries = listOf("CH"))
            }
            if (message != null) throw ExtractionError.Unavailable("srgssr said: $message")
        }
        return mediaData
    }

    /** Upstream `_get_tokenized_src`; protected for the RTS subclass. */
    protected suspend fun tokenize(url: String): String {
        val token = runCatching { http.downloadJson(TOKEN_URL) as? JsonObject }.getOrNull()
        val authParams = token?.obj("token")?.str("authparams") ?: return url
        val separator = if ('?' in url) '&' else '?'
        return url + separator + authParams
    }

    companion object {
        const val IE_KEY: String = "SRGSSR"

        val VALID_URL: Regex = Regex(
            "(?:(?:https?://tp\\.srgssr\\.ch/p(?:/[^/]+)+\\?urn=urn)|srgssr):" +
                "(?<bu>srf|rts|rsi|rtr|swi):(?:[^:]+:)?" +
                "(?<type>video|audio):(?<id>[0-9a-f-]{36}|\\d+)",
        )
    }
}

/** Upstream `SRGSSRPlayIE`: the srf/rts/rsi/rtr/swissinfo play sites. */
class SRGSSRPlayIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val bu = match.groups["bu"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val mediaType = match.groups["type"]?.value ?: match.groups["type2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val mediaId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `url_result(f'srgssr:{bu[:3]}:{media_type}:{media_id}')`.
        val info = SRGSSRIE(http).extract("srgssr:${bu.take(3)}:$mediaType:$mediaId")
        return info.copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "SRGSSRPlay"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|play)\\.)?(?<bu>srf|rts|rsi|rtr|swissinfo)\\.ch/play/(?:tv|radio)/" +
                "(?:[^/]+/(?<type>video|audio)/[^?]+|popup(?<type2>video|audio)player)" +
                "\\?.*?\\b(?:id=|urn=urn:[^:]+:video:)(?<id>[0-9a-f-]{36}|\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `qualities(['SD', 'HD'])`. */
private fun qualityIndex(quality: String?): Int? = when (quality) {
    "SD" -> 0
    "HD" -> 1
    else -> null
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
