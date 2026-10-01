/*
 * MTV extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `mtv.py` from
 * `yt_dlp/extractor/mtv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mtv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `?json=true` page tree (MainContainer -> AviaWrapper ->
 * FlexWrapper -> Player `props.videoDetail`, with the TVE fallback node), the
 * `videoServiceUrl` stitched-stream JSON, and the HLS or DASH row plus the
 * video metadata. Auth-required video detail is the typed TV-provider login
 * wall; the upstream Adobe Pass MSO flow, JWT cache, and media-token calls
 * are not translated. The port does not carry series/season/episode or
 * release fields, and an m3u8/mpd URL becomes one row. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.mtv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `MTVServicesBaseIE`: the public MTV Services page extraction. */
abstract class MTVServicesBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `update_url(url, query=None)` then `query={'json': 'true'}`.
        val pageUrl = url.substringBefore('?').substringBefore('#') + "?json=true"
        val data = http.downloadJson(pageUrl) as? JsonObject
            ?: throw ExtractionError.Malformed("The MTV page response was not an object.")
        val videoDetail = findVideoDetail(data)
            ?: throw ExtractionError.Malformed("The MTV page had no video detail.")
        val mgid = videoDetail.str("mgid")
            ?: throw ExtractionError.Malformed("The MTV video detail had no mgid.")
        val videoId = mgid.substringAfterLast(':')
        val serviceUrl = videoDetail.str("videoServiceUrl")?.substringBefore('?')
            ?: throw ExtractionError.Unavailable("This content is no longer available.")
        if (videoDetail.bool("authRequired") == true) {
            // Upstream `_get_media_token` runs the Adobe Pass MSO flow.
            throw ExtractionError.LoginRequired(
                "This video is only available for users of participating TV providers.",
            )
        }

        val stitched = (http.downloadJson("$serviceUrl?clientPlatform=desktop") as? JsonObject)
            ?.obj("stitchedstream")
            ?: throw ExtractionError.Malformed("The MTV Services response had no stitched stream.")
        val manifestType = stitched.str("manifesttype")
            ?: throw ExtractionError.NoFormats("The MTV stream had no manifest type.")
        val source = stitched.str("source")
            ?: throw ExtractionError.NoFormats("The MTV stream had no source.")
        val formats = when (manifestType) {
            "hls" -> listOf(
                MediaFormat(
                    formatId = "hls",
                    url = source,
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            )

            "dash" -> listOf(
                MediaFormat(
                    formatId = "dash",
                    url = source,
                    ext = "mp4",
                    protocol = "mpd",
                ),
            )

            else -> throw ExtractionError.NoFormats("Unsupported manifest type \"$manifestType\"")
        }

        val thumbnails = videoDetail.array("images").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.str("url")?.let { Thumbnail(url = it) }
        }
        val uploadDate = videoDetail.str("originalPublishDate")?.let(ExtractorUtils::unifiedStrdate)
            ?: videoDetail.obj("publishDate")?.number("timestamp")?.toLong()
                ?.let(ExtractorUtils::epochSecondsToDate)
        return InfoDict(
            id = videoId,
            title = videoDetail.str("title"),
            description = videoDetail.str("fullDescription") ?: videoDetail.str("description"),
            channel = videoDetail.obj("channel")?.str("name"),
            duration = videoDetail.obj("duration")?.number("milliseconds")?.div(1000.0),
            uploadDate = uploadDate,
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "mtv",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `MTVIE`: an MTV video clip or episode page. */
class MTVIE(
    http: ExtractorHttp,
) : MTVServicesBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    companion object {
        const val IE_KEY: String = "MTV"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mtv\\.com/(?:video-clips|episodes)/(?<id>[\\da-z]{6})",
        )
    }
}

// ------------------------------------------------------------------ helpers

/**
 * Upstream traversal: the first `Player` `props.videoDetail` under the
 * MainContainer -> AviaWrapper -> FlexWrapper tree, then the
 * `handleTVEAuthRedirection` fallback node.
 */
private fun findVideoDetail(root: JsonObject): JsonObject? {
    val main = childByType(root, "MainContainer")
    val avia = childByType(main, "AviaWrapper")
    val flex = childByType(avia, "FlexWrapper")
    val player = childByType(flex, "Player") ?: childByType(childByType(flex, "AuthSuiteWrapper"), "Player")
    player?.obj("props")?.obj("videoDetail")?.let { return it }
    return findFirstObject(root) { node ->
        node.str("type") == "handleTVEAuthRedirection" && node.obj("videoDetail") != null
    }?.obj("videoDetail")
}

private fun childByType(node: JsonObject?, type: String): JsonObject? =
    node?.array("children")?.firstOrNull { (it as? JsonObject)?.str("type") == type } as? JsonObject

private fun findFirstObject(root: JsonElement, predicate: (JsonObject) -> Boolean): JsonObject? {
    when (root) {
        is JsonObject -> {
            if (predicate(root)) return root
            for ((_, value) in root) {
                findFirstObject(value, predicate)?.let { return it }
            }
        }

        is JsonArray -> {
            for (element in root) {
                findFirstObject(element, predicate)?.let { return it }
            }
        }

        else -> Unit
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
