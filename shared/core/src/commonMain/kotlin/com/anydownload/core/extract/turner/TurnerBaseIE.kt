/*
 * Turner base — AnyDownload
 *
 * Kotlin translation of `TurnerBaseIE` from `yt_dlp/extractor/turner.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `turner.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the NGTV `medium.ngtv.io` JSON (`unprotected`/`bulkaes` m3u8 rows,
 * duration, chapters), the public Akamai SPE tokenizer call (`?hdnea=`), and
 * the CVP timestamp helper. The later `adultswim.py` and `tbs.py` files are
 * the only users, and both call `_extract_ngtv_info`; the CVP XML walk
 * (`_extract_cvp_info`), the SMIL/f4m branches, and the MVPD-authenticated
 * tokenizer path stay out because no registered class reaches them and the
 * port has no f4m/SMIL helper. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.turner

import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.adobepass.AdobePassIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ap_data` for the NGTV/tokenizer calls. */
data class TurnerApData(
    val isLive: Boolean = false,
    val authRequired: Boolean = false,
    val url: String? = null,
    val siteName: String? = null,
)

/** The fields upstream `_extract_ngtv_info` returns. */
data class NgtvInfo(
    val formats: List<MediaFormat> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val duration: Double? = null,
)

/** Upstream `TurnerBaseIE`: the NGTV/SPE base for AdultSwim and TBS. */
abstract class TurnerBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : AdobePassIE(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_timestamp`: `dateCreated/@uts`. */
    protected fun extractTimestamp(videoData: String): Long? {
        val tag = Regex("<dateCreated\\b[^>]*>").find(videoData)?.value ?: return null
        return Regex("\\buts\\s*=\\s*[\"'](\\d+)[\"']").find(tag)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Upstream `_extract_ngtv_info`: the `medium.ngtv.io/media/{id}/tv` JSON
     * into m3u8 rows, duration, and chapters. An SPE-protected playlist goes
     * through the public tokenizer; an authenticated tokenizer call is the
     * typed Adobe Pass wall.
     */
    protected suspend fun extractNgtvInfo(
        mediaId: String,
        tokenizerQuery: Map<String, String> = emptyMap(),
        apData: TurnerApData? = null,
    ): NgtvInfo {
        val data = apData ?: TurnerApData()
        val response = http.downloadJson("https://medium.ngtv.io/media/$mediaId/tv") as? JsonObject
            ?: throw ExtractionError.Malformed("The NGTV API was not an object.")
        val tv = response.obj("media")?.obj("tv")
            ?: throw ExtractionError.Malformed("The NGTV API had no media/tv object.")
        val formats = mutableListOf<MediaFormat>()
        val chapters = mutableListOf<Chapter>()
        var duration: Double? = null
        for (supportedType in listOf("unprotected", "bulkaes")) {
            val streamData = tv.obj(supportedType) ?: continue
            var m3u8Url = streamData.str("secureUrl") ?: streamData.str("url") ?: continue
            if (streamData.str("playlistProtection") == "spe") {
                m3u8Url = addAkamaiSpeToken(
                    tokenizerSrc = "https://token.ngtv.io/token/token_spe",
                    videoUrl = m3u8Url,
                    contentId = mediaId,
                    apData = data,
                    customTokenizerQuery = tokenizerQuery,
                )
            }
            formats += MediaFormat(
                formatId = "hls",
                url = m3u8Url,
                ext = "mp4",
                protocol = "m3u8_native",
            )
            duration = streamData.number("totalRuntime")
            if (chapters.isEmpty() && !data.isLive) {
                for (element in streamData.array("contentSegments").orEmpty()) {
                    val chapter = element as? JsonObject ?: continue
                    val start = chapter.number("start") ?: continue
                    val chapterDuration = chapter.number("duration") ?: continue
                    chapters += Chapter(startTime = start, endTime = start + chapterDuration)
                }
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The NGTV API returned no playable stream.")
        }
        return NgtvInfo(formats = formats, chapters = chapters, duration = duration)
    }

    /**
     * Upstream `_add_akamai_spe_token`: the public `token.ngtv.io` tokenizer
     * for a `/secure/` path, appended as `?hdnea=`. An authenticated call is
     * the typed Adobe Pass wall; a tokenizer error message fails typed.
     */
    protected suspend fun addAkamaiSpeToken(
        tokenizerSrc: String,
        videoUrl: String,
        contentId: String,
        apData: TurnerApData,
        customTokenizerQuery: Map<String, String> = emptyMap(),
    ): String {
        val securePath = Regex("https?://[^/]+(.+/)").find(videoUrl)?.groupValues?.get(1) + "*"
        SPE_TOKEN_CACHE[securePath]?.let { return "$videoUrl?hdnea=$it" }
        if (apData.authRequired) {
            // Upstream `_extract_mvpd_auth` asks for TV provider credentials.
            mvpdAuthRequired()
        }
        val query = linkedMapOf("path" to securePath)
        if (customTokenizerQuery.isEmpty()) {
            query["videoId"] = contentId
        } else {
            query.putAll(customTokenizerQuery)
        }
        val auth = http.downloadWebpage(
            tokenizerSrc + "?" + query.entries.joinToString("&") { (key, value) ->
                "${percentEncode(key)}=${percentEncode(value)}"
            },
        )
        val error = ExtractorUtils.searchRegex("<error>\\s*<msg>([^<]*)</msg>", auth)
        if (!error.isNullOrEmpty()) throw ExtractionError.Unavailable(error)
        val token = ExtractorUtils.searchRegex("<token>([^<]*)</token>", auth)
        if (token.isNullOrEmpty()) return videoUrl
        SPE_TOKEN_CACHE[securePath] = token
        return "$videoUrl?hdnea=$token"
    }

    companion object {
        /** Upstream `_AKAMAI_SPE_TOKEN_CACHE`. */
        private val SPE_TOKEN_CACHE = mutableMapOf<String, String>()
    }
}

// ------------------------------------------------------------------ helpers

private const val HEX_DIGITS = "0123456789ABCDEF"

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

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
