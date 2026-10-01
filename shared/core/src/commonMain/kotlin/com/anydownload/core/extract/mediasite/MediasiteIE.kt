/*
 * Mediasite extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `mediasite.py` from
 * `yt_dlp/extractor/mediasite.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mediasite.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the PlayerService GetPlayerOptions presentation walk (m3u8/mpd/
 * plain video URLs, thumbnails, metadata), the catalog folder POST with the
 * optional anti-forgery header, and the named-catalog redirect. The MHTML
 * slide streams, ISM (SS) media, and the query-string smuggling are not
 * translated. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.mediasite

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `MediasiteIE`: a presentation. */
class MediasiteIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val resourceId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val query = match.groups["query"]?.value ?: ""
        val webpage = http.downloadWebpage(url)
        val servicePath = ExtractorUtils.searchRegex(
            "<div[^>]+\\bid=[\"']ServicePath[^>]+>(.+?)</div>",
            webpage,
            default = null,
        ) ?: "/Mediasite/PlayerService/PlayerService.svc/json"
        val optionsBody = """
            {"getPlayerOptionsRequest": {"ResourceId": "$resourceId", "QueryString": "${escapeJson(query)}",
             "UrlReferrer": "", "UseScreenReader": false}}
        """.trimIndent()
        val response = http.downloadJson(
            "$servicePath/GetPlayerOptions",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "X-Requested-With" to "XMLHttpRequest",
            ),
            body = optionsBody.encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The player options API was not an object.")
        val playerOptions = response.obj("d")
            ?: throw ExtractionError.Malformed("The player options response had no d.")
        val presentation = playerOptions.obj("Presentation")
            ?: throw ExtractionError.Unavailable(
                playerOptions.str("PlayerPresentationStatusMessage") ?: "Mediasite returned no presentation.",
            )
        val formats = mutableListOf<MediaFormat>()
        val thumbnails = mutableListOf<Thumbnail>()
        for ((snum, element) in presentation.array("Streams").orEmpty().withIndex()) {
            val stream = element as? JsonObject ?: continue
            val streamType = stream.number("StreamType")?.toInt() ?: continue
            val streamId = STREAM_TYPES[streamType] ?: "type$streamType"
            for ((unum, videoElement) in stream.array("VideoUrls").orEmpty().withIndex()) {
                val video = videoElement as? JsonObject ?: continue
                val videoUrl = video.str("Location") ?: continue
                val mediaType = video.str("MediaType")
                val ext = ExtractorUtils.mimetype2ext(video.str("MimeType"))
                val formatId = "$streamId-$snum.$unum"
                when {
                    mediaType == "SS" -> Unit // ISM is skipped: the port has no ISM helper.
                    mediaType == "Dash" -> formats += MediaFormat(
                        formatId = formatId,
                        url = videoUrl,
                        ext = "mp4",
                        protocol = "mpd",
                        preference = if (streamType != 0) -10 else null,
                    )

                    ext == "m3u" || ext == "m3u8" -> formats += MediaFormat(
                        formatId = formatId,
                        url = videoUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                        preference = if (streamType != 0) -10 else null,
                    )

                    else -> formats += MediaFormat(
                        formatId = formatId,
                        url = videoUrl,
                        ext = ext,
                        preference = if (streamType != 0) -10 else null,
                    )
                }
            }
            stream.str("ThumbnailUrl")?.let {
                thumbnails += Thumbnail(
                    id = "$streamId-$snum",
                    url = if (it.startsWith("http")) it else urlJoin(url, it),
                    preference = if (streamType != 0) -1 else 0,
                )
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The presentation returned no playable stream.")
        }
        return InfoDict(
            id = resourceId,
            title = presentation.str("Title"),
            description = presentation.str("Description"),
            duration = presentation.number("Duration")?.div(1000),
            uploadDate = presentation.number("UnixTime")?.toLong()?.div(1000)
                ?.let(ExtractorUtils::epochSecondsToDate),
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "mediasite",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Mediasite"

        private const val ID_RE =
            "(?:[0-9a-f]{32,34}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12,14})"

        val VALID_URL: Regex = Regex(
            "(?i)https?://[^/]+/Mediasite/(?:Play|Showcase/[^/#?]+/Presentation)/" +
                "(?<id>$ID_RE)(?<query>\\?[^#]+|)",
        )

        private val STREAM_TYPES = mapOf(
            0 to "video1",
            2 to "slide",
            3 to "presentation",
            4 to "video2",
            5 to "video3",
        )
    }
}

/** Upstream `MediasiteCatalogIE`: a catalog folder. */
class MediasiteCatalogIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val mediasiteUrl = match.groups["url"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val catalogId = match.groups["catalogId"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val currentFolderId = match.groups["currentFolderId"]?.value ?: catalogId
        val rootDynamicFolderId = match.groups["rootDynamicFolderId"]?.value
        val webpage = http.downloadWebpage(url)
        val antiForgeryToken = ExtractorUtils.searchRegex(
            "AntiForgeryToken\\s*:\\s*([\"'])([^\"']+)\\1",
            webpage,
            group = 2,
            default = null,
        )
        val antiForgeryHeader = if (antiForgeryToken != null) {
            ExtractorUtils.searchRegex(
                "AntiForgeryHeaderName\\s*:\\s*([\"'])([^\"']+)\\1",
                webpage,
                group = 2,
                default = null,
            ) ?: "X-SOFO-AntiForgeryHeader"
        } else {
            null
        }
        val body = """
            {"IsViewPage": true, "IsNewFolder": true, "AuthTicket": null, "CatalogId": "$catalogId",
             "CurrentFolderId": "$currentFolderId",
             "RootDynamicFolderId": ${rootDynamicFolderId?.let { "\"$it\"" } ?: "null"},
             "ItemsPerPage": 1000, "PageIndex": 0, "PermissionMask": "Execute",
             "CatalogSearchType": "SearchInFolder", "SortBy": "Date", "SortDirection": "Descending",
             "StartDate": null, "EndDate": null, "StatusFilterList": null, "PreviewKey": null, "Tags": []}
        """.trimIndent()
        val headers = mutableMapOf(
            "Content-Type" to "application/json; charset=UTF-8",
            "Referer" to url,
            "X-Requested-With" to "XMLHttpRequest",
        )
        if (antiForgeryToken != null && antiForgeryHeader != null) {
            headers[antiForgeryHeader] = antiForgeryToken
        }
        val catalog = http.downloadJson(
            "$mediasiteUrl/Catalog/Data/GetPresentationsForFolder",
            method = "POST",
            headers = headers,
            body = body.encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The catalog API was not an object.")
        val entries = mutableListOf<InfoEntry>()
        for (element in catalog.array("PresentationDetailsList").orEmpty()) {
            val video = element as? JsonObject ?: continue
            val videoId = video.primitiveText("Id") ?: continue
            entries += InfoEntry(id = videoId, url = "$mediasiteUrl/Play/$videoId")
        }
        return InfoDict(
            id = catalogId,
            title = catalog.obj("CurrentFolder")?.str("Name"),
            entries = entries,
            webpageUrl = url,
            extractor = "mediasite:catalog",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MediasiteCatalog"

        private const val ID_RE =
            "(?:[0-9a-f]{32,34}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12,14})"

        val VALID_URL: Regex = Regex(
            "(?i)(?<url>https?://[^/]+/Mediasite)/Catalog/Full/(?<catalogId>$ID_RE)" +
                "(?:/(?<currentFolderId>$ID_RE))?(?:/(?<rootDynamicFolderId>$ID_RE))?",
        )
    }
}

/** Upstream `MediasiteNamedCatalogIE`: the named catalog pages. */
class MediasiteNamedCatalogIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val mediasiteUrl = match.groups["url"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val catalogId = ExtractorUtils.searchRegex(
            "CatalogId\\s*:\\s*[\"']([\\da-f-]{32,36})",
            webpage,
            default = null,
        ) ?: throw ExtractionError.Malformed("The named catalog page had no catalog id.")
        return InfoDict(
            id = catalogId,
            redirectUrl = "$mediasiteUrl/Catalog/Full/$catalogId",
            webpageUrl = url,
            extractor = "mediasite:named-catalog",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MediasiteNamedCatalog"

        val VALID_URL: Regex = Regex(
            "(?i)(?<url>https?://[^/]+/Mediasite)/Catalog/catalogs/(?<catalogName>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun escapeJson(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun urlJoin(base: String, path: String): String {
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return path
    return if (path.startsWith("/")) origin + path else "$origin/$path"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
