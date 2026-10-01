/*
 * The New York Times extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `nytimes.py` from
 * `yt_dlp/extractor/nytimes.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nytimes.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `NYTimesCookingRecipeIE` is translated from the recipe page's
 * Next.js data (videoSrc m3u8, metadata, thumbnails). The video, article,
 * and cooking-guide classes match and fail typed: their video renditions
 * come from the Samizdat GraphQL API, which needs an embedded `Nyt-Token`
 * header the port does not carry. No token or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.nytimes

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val WALL =
    "The NYTimes video renditions come from the Samizdat GraphQL API, which needs an embedded " +
        "Nyt-Token header the port does not carry."

/** Upstream `NYTimesIE`: the video and embed pages. */
class NYTimesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NYTimes"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.)?nytimes\\.com/video/(?:[^/]+/)+?" +
                "|graphics8\\.nytimes\\.com/bcvideo/\\d+(?:\\.\\d+)?/iframe/embed\\.html\\?videoId=)" +
                "(?<id>\\d+)",
        )
    }
}

/** Upstream `NYTimesArticleIE`: articles with embedded videos. */
class NYTimesArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NYTimesArticle"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?nytimes\\.com/\\d{4}/\\d{2}/\\d{2}/(?!books|podcasts)[^/?#]+/" +
                "(?:\\w+/)?(?<id>[^./?#]+)(?:\\.html)?",
        )
    }
}

/** Upstream `NYTimesCookingIE`: the cooking guides. */
class NYTimesCookingIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "NYTimesCooking"

        val VALID_URL: Regex = Regex("https?://cooking\\.nytimes\\.com/guides/(?<id>[\\w-]+)")
    }
}

/** Upstream `NYTimesCookingRecipeIE`: the recipe pages with a video. */
class NYTimesCookingRecipeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val pageId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val recipe = nextJsPageProps(webpage)?.obj("recipe")
            ?: throw ExtractionError.Malformed("The recipe page had no Next.js recipe data.")
        val videoSrc = recipe.str("videoSrc")
            ?: throw ExtractionError.NoFormats("The recipe had no video source.")
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in recipe.obj("image")?.obj("crops")?.array("recipe").orEmpty()) {
            val url2 = (element as? JsonPrimitive)?.content ?: continue
            thumbnails += Thumbnail(url = url2)
        }
        return InfoDict(
            id = recipe.primitiveText("id") ?: pageId,
            title = recipe.str("title"),
            description = cleanHtml(recipe.str("topnote")),
            duration = null,
            uploadDate = recipe.number("publishedAt")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            uploader = recipe.obj("contentAttribution")?.str("cardByline"),
            thumbnails = thumbnails,
            formats = listOf(
                MediaFormat(formatId = "hls", url = videoSrc, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "nytimes:cooking:recipe",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NYTimesCookingRecipe"

        val VALID_URL: Regex = Regex("https?://cooking\\.nytimes\\.com/recipes/(?<id>\\d+)")
    }
}

// ------------------------------------------------------------------ helpers

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

private fun nextJsPageProps(html: String): JsonObject? {
    val raw = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
    val root = ExtractorUtils.parseJson(raw) as? JsonObject ?: return null
    return root.obj("props")?.obj("pageProps")
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
