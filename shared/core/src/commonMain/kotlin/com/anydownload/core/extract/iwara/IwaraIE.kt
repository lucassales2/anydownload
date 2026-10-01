/*
 * Iwara extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `iwara.py` from
 * `yt_dlp/extractor/iwara.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `iwara.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `api.iwara.tv` video JSON and its SHA-1 `X-Version`
 * file-list request (a public site constant, see `sha1Hex`), the profile
 * video listing, and the playlist listing. The upstream `impersonate=True`
 * requests become plain requests, so a Cloudflare challenge fails typed; the
 * netrc user/media token flow is not translated, so a private or
 * login-required video is the typed login wall; tags, like/comment counts,
 * and the modified timestamp are dropped. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.iwara

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.sha1Hex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_BASE = "https://api.iwara.tv/"
private const val PER_PAGE = 32
private const val MAX_PAGES = 5

/** The public site constant upstream joins into the SHA-1 `X-Version` input. */
private const val X_VERSION_SALT = "mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN"

/** Upstream `IwaraBaseIE`: the shared API call. */
abstract class IwaraBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun callApi(
        path: String,
        videoId: String?,
        query: Map<String, String> = emptyMap(),
    ): JsonObject {
        val suffix = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.entries.joinToString("&") { (key, value) -> "$key=$value" }
        }
        return http.downloadJson(API_BASE + path + suffix) as? JsonObject
            ?: throw ExtractionError.Malformed("The Iwara API response was not an object.")
    }
}

/** Upstream `IwaraIE`: one Iwara video. */
class IwaraIE(
    http: ExtractorHttp,
) : IwaraBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoData = callApi("video/$videoId", videoId)
        when (val message = videoData.str("message")) {
            "errors.privateVideo" -> throw ExtractionError.LoginRequired(
                "This is a private video; a login with permissions is needed.",
            )

            "errors.notFound" -> throw ExtractionError.LoginRequired(
                "This video may need a login to view.",
            )

            null -> Unit
            else -> throw ExtractionError.Unavailable("Iwara says: $message")
        }
        val fileUrl = videoData.str("fileUrl")
        if (fileUrl == null) {
            videoData.str("embedUrl")?.let { embedUrl ->
                return InfoDict(
                    redirectUrl = protoRelativeUrl(embedUrl),
                    webpageUrl = url,
                    extractor = "iwara",
                    extractorKey = IE_KEY,
                )
            }
            throw ExtractionError.Unavailable("This video is unplayable.")
        }
        val formats = extractFormats(videoId, fileUrl)
        val file = videoData.obj("file")?.str("id")
        return InfoDict(
            id = videoId,
            title = videoData.str("title"),
            description = videoData.str("body"),
            uploader = videoData.obj("user")?.str("name"),
            viewCount = videoData.number("numViews")?.toLong(),
            uploadDate = videoData.str("createdAt")?.let(ExtractorUtils::unifiedStrdate),
            ageLimit = if (videoData.str("rating") == "ecchi") 18 else 0,
            thumbnails = listOfNotNull(
                file?.let { Thumbnail(url = "https://files.iwara.tv/image/thumbnail/$it/thumbnail-00.jpg") },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "iwara",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun extractFormats(videoId: String, fileUrl: String): List<MediaFormat> {
        val path = fileUrl.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val expires = queryValue(fileUrl, "expires")
        val xVersion = sha1Hex("${path}_${expires}_$X_VERSION_SALT".encodeToByteArray())
        val files = http.downloadJson(fileUrl, headers = mapOf("X-Version" to xVersion)) as? JsonArray
            ?: throw ExtractionError.Malformed("The Iwara file list was not an array.")
        val qualities = listOf("preview", "360", "540", "Source")
        return files.mapNotNull { element ->
            val file = element as? JsonObject ?: return@mapNotNull null
            val name = file.str("name")
            val source = file.obj("src")?.str("view") ?: file.obj("src")?.str("download")
                ?: return@mapNotNull null
            MediaFormat(
                formatId = name,
                url = protoRelativeUrl(source),
                ext = ExtractorUtils.mimetype2ext(file.str("type")),
                height = name?.toLongOrNull(),
                preference = name?.let { qualities.indexOf(it).takeIf { index -> index >= 0 } },
            )
        }
    }

    companion object {
        const val IE_KEY: String = "Iwara"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.|ecchi\\.)?iwara\\.tv/videos?/(?<id>[a-zA-Z0-9]+)",
        )
    }
}

/** Upstream `IwaraUserIE`: a profile video listing. */
class IwaraUserIE(
    http: ExtractorHttp,
) : IwaraBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val userInfo = callApi("profile/$playlistId", playlistId)
        val userId = userInfo.obj("user")?.primitive("id")
        val entries = mutableListOf<InfoEntry>()
        var page = 0
        while (page < MAX_PAGES) {
            val videos = callApi(
                "videos",
                playlistId,
                mapOf(
                    "page" to page.toString(),
                    "sort" to "date",
                    "user" to userId.orEmpty(),
                    "limit" to PER_PAGE.toString(),
                ),
            )
            val results = videos.array("results").orEmpty()
            if (results.isEmpty()) break
            for (element in results) {
                val id = (element as? JsonObject)?.str("id") ?: continue
                entries += InfoEntry(url = "https://iwara.tv/video/$id")
            }
            if (results.size < PER_PAGE) break
            page++
        }
        return InfoDict(
            id = playlistId,
            title = userInfo.obj("user")?.str("name"),
            entries = entries,
            webpageUrl = url,
            extractor = "iwara:user",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "IwaraUser"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?iwara\\.tv/profile/(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `IwaraPlaylistIE`: a playlist listing. */
class IwaraPlaylistIE(
    http: ExtractorHttp,
) : IwaraBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val firstPage = callApi(
            "playlist/$playlistId",
            playlistId,
            mapOf("page" to "0", "limit" to PER_PAGE.toString()),
        )
        val entries = mutableListOf<InfoEntry>()
        appendResults(entries, firstPage)
        var page = 1
        while (page < MAX_PAGES) {
            val videos = callApi(
                "videos",
                playlistId,
                mapOf("page" to page.toString(), "limit" to PER_PAGE.toString()),
            )
            val results = videos.array("results").orEmpty()
            if (results.isEmpty()) break
            appendResults(entries, videos)
            if (results.size < PER_PAGE) break
            page++
        }
        return InfoDict(
            id = playlistId,
            title = firstPage.str("title") ?: firstPage.str("name"),
            entries = entries,
            webpageUrl = url,
            extractor = "iwara:playlist",
            extractorKey = IE_KEY,
        )
    }

    private fun appendResults(entries: MutableList<InfoEntry>, response: JsonObject) {
        for (element in response.array("results").orEmpty()) {
            val id = (element as? JsonObject)?.str("id") ?: continue
            entries += InfoEntry(url = "https://iwara.tv/video/$id")
        }
    }

    companion object {
        const val IE_KEY: String = "IwaraPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?iwara\\.tv/playlist/(?<id>[0-9a-f-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun queryValue(url: String, name: String): String =
    url.substringAfter('?', "").split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        .orEmpty()

/** Upstream `_proto_relative_url`: `//host/...` becomes `https://host/...`. */
private fun protoRelativeUrl(value: String): String =
    if (value.startsWith("//")) "https:$value" else value

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
