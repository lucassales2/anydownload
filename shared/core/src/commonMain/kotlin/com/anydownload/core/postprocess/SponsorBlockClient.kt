/*
 * SponsorBlock client — AnyDownload (T-016)
 *
 * Translation of the SponsorBlock API call and segment parsing in
 * `yt_dlp/postprocessor/sponsorblock.py` (`SponsorBlockPP._get_sponsor_segments`)
 * at upstream tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-09-30. Unlicense; see shared/core/NOTICE.md.
 *
 * The call is the app's one extra external service use. It only runs when the
 * user opts in; the result is typed so the engine can say whether segments
 * were removed, none were found, or the service was unreachable. No token or
 * personal data is sent.
 */
package com.anydownload.core.postprocess

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One SponsorBlock skip segment; times are milliseconds. */
data class SponsorSegment(
    val category: String,
    val startMillis: Long,
    val endMillis: Long,
)

/** The typed outcome of one SponsorBlock lookup. */
sealed interface SponsorBlockResult {
    /** At least one removable segment was returned. */
    data class Segments(val segments: List<SponsorSegment>) : SponsorBlockResult

    /** The service answered with no segments for this video. */
    data object NoSegments : SponsorBlockResult

    /** No video id to query. */
    data object NotApplicable : SponsorBlockResult

    /** The service could not be reached or answered unusably; the media is kept. */
    data class Unavailable(val reason: String) : SponsorBlockResult
}

class SponsorBlockClient(
    private val http: ExtractorHttp,
    private val apiBase: String = DEFAULT_API,
) {

    /**
     * Fetches the removable segments for [videoId] in [categories]. A 404
     * (no segments) and any transport/parse failure both become typed
     * outcomes; the caller decides whether to keep the media.
     */
    suspend fun fetch(
        videoId: String,
        categories: List<String> = DEFAULT_CATEGORIES,
    ): SponsorBlockResult {
        if (videoId.isBlank()) return SponsorBlockResult.NotApplicable
        val url = buildUrl(videoId, categories)
        return try {
            val json = http.downloadJson(url, maxBytes = MAX_BYTES)
            val array = json as? JsonArray
                ?: return SponsorBlockResult.Unavailable("The SponsorBlock response was not a list.")
            val segments = array.mapNotNull { element -> segmentOf(element) }
            if (segments.isEmpty()) SponsorBlockResult.NoSegments else SponsorBlockResult.Segments(segments)
        } catch (error: ExtractionError) {
            SponsorBlockResult.Unavailable(error.message ?: "The SponsorBlock service could not be reached.")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SponsorBlockResult.Unavailable("The SponsorBlock service could not be reached.")
        }
    }

    private fun segmentOf(element: kotlinx.serialization.json.JsonElement): SponsorSegment? {
        val object0 = element as? JsonObject ?: return null
        if ((object0["actionType"] as? JsonPrimitive)?.content != "skip") return null
        val category = (object0["category"] as? JsonPrimitive)?.content ?: return null
        val segment = object0["segment"] as? JsonArray ?: return null
        val start = (segment.getOrNull(0) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
        val end = (segment.getOrNull(1) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
        if (end <= start) return null
        return SponsorSegment(
            category = category,
            startMillis = (start * 1000.0).toLong(),
            endMillis = (end * 1000.0).toLong(),
        )
    }

    private fun buildUrl(videoId: String, categories: List<String>): String {
        val encodedCategories = percentEncode("[" + categories.joinToString(",") { "\"$it\"" } + "]")
        return "$apiBase?videoID=${percentEncode(videoId)}&categories=$encodedCategories"
    }

    private fun percentEncode(value: String): String = buildString {
        for (character in value) {
            when {
                character.isLetterOrDigit() || character == '-' || character == '_' || character == '.' -> append(character)
                else -> append('%').append(character.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    companion object {
        const val DEFAULT_API: String = "https://sponsor.ajay.app/api/skipSegments"

        /** The reviewed MeTube default set (`YtDlpArguments.SPONSORBLOCK_CATEGORIES`). */
        val DEFAULT_CATEGORIES: List<String> = listOf(
            "sponsor",
            "intro",
            "outro",
            "selfpromo",
            "preview",
            "filler",
            "interaction",
            "music_offtopic",
        )

        const val MAX_BYTES: Int = 512 * 1024
    }
}
