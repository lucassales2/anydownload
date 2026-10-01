/*
 * TV2 / Katsomo / MTV Uutiset extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `tv2.py` from
 * `yt_dlp/extractor/tv2.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tv2.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public TV2 sumo assets/play API (HLS and direct rows), the
 * TV2 article asset scan, and the MTV Uutiset article video list. Katsomo
 * matches and fails typed (upstream marks it `_WORKING = False`; the play
 * endpoint needs a session). F4M/ISM/MPD manifests are skipped and an m3u8
 * URL becomes one HLS row; DRM playback fails typed. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.tv2

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

/** Upstream `TV2IE`: a tv2.no video. */
class TV2IE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val asset = http.downloadJson("https://sumo.tv2.no/rest/assets/$videoId") as? JsonObject
            ?: throw ExtractionError.Malformed("The TV2 asset API returned no object.")
        val isLive = asset.boolean("live") == true
        val formats = mutableListOf<MediaFormat>()
        var drmProtected = false
        for (protocol in listOf("HLS", "DASH")) {
            val data = try {
                http.downloadJson(
                    "https://api.sumo.tv2.no/play/$videoId?stream=$protocol",
                    method = "POST",
                    headers = mapOf("content-type" to "application/json"),
                    body = """{"device":{"id":"1-1-1","name":"Nettleser (HTML)"}}""".encodeToByteArray(),
                ) as? JsonObject
            } catch (error: Exception) {
                continue
            } ?: continue
            val playback = data.obj("playback") ?: continue
            drmProtected = playback.boolean("drmProtected") == true
            for (element in playback.array("streams").orEmpty()) {
                val item = element as? JsonObject ?: continue
                val streamUrl = item.str("url") ?: continue
                val formatId = "${protocol.lowercase()}-${item.str("type") ?: "na"}"
                when (ExtractorUtils.determineExt(streamUrl)) {
                    "m3u8" -> if (!drmProtected) {
                        formats += MediaFormat(
                            formatId = formatId,
                            url = streamUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        )
                    }

                    "mpd", "f4m", "ism" -> Unit // MPD/F4M/ISM manifests are not translated.

                    else -> formats += MediaFormat(
                        formatId = formatId,
                        url = streamUrl,
                        ext = ExtractorUtils.determineExt(streamUrl),
                    )
                }
            }
        }
        if (formats.isEmpty() && drmProtected) {
            throw ExtractionError.Unavailable("This video is DRM protected.")
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The TV2 play API returned no playable format.")
        }
        val thumbnails = mutableListOf<Thumbnail>()
        for ((_, value) in asset.obj("images").orEmpty()) {
            val thumbUrl = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            thumbnails += Thumbnail(url = thumbUrl)
        }
        return InfoDict(
            id = videoId,
            title = asset.str("title"),
            description = asset.str("description")?.trim(),
            duration = asset.number("accurateDuration") ?: asset.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(
                asset.str("live_broadcast_time") ?: asset.str("update_time"),
            ),
            viewCount = asset.number("views")?.toLong(),
            isLive = isLive,
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "tv2",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TV2"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv2\\.no/v(?:ideo)?\\d*/(?:[^?#]+/)*(?<id>\\d+)")
    }
}

/** Upstream `TV2ArticleIE`: a tv2.no article. */
class TV2ArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val assets = mutableListOf<String>()
        for (match in Regex("data-assetid=[\"'](\\d+)").findAll(webpage)) {
            assets += match.groupValues[1]
        }
        if (assets.isEmpty()) {
            for (match in Regex("(?s)(?:TV2ContentboxVideo|TV2\\.TV2Video)\\((.+?)\\)").findAll(webpage)) {
                val video = ExtractorUtils.parseJson(match.groupValues[1]) as? JsonObject ?: continue
                video.str("assetId")?.let { assets += it }
            }
        }
        val title = metaContent(webpage, "og:title")?.removeSuffix(" - TV2.no")
        val description = metaContent(webpage, "og:description")?.removeSuffix(" - TV2.no")
        return InfoDict(
            id = playlistId,
            title = title,
            description = description,
            entries = assets.map { InfoEntry(url = "http://www.tv2.no/v/$it") },
            webpageUrl = url,
            extractor = "tv2:article",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TV2Article"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv2\\.no/(?!v(?:ideo)?\\d*/)[^?#]+/(?<id>\\d+)")
    }
}

/** Upstream `KatsomoIE`: a katsomo/mtv.fi video (typed wall). */
class KatsomoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The Katsomo play API needs an authenticated session, and upstream marks this extractor _WORKING = False.",
        )
    }

    companion object {
        const val IE_KEY: String = "Katsomo"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:katsomo|mtv(uutiset)?)\\.fi/" +
                "(?:sarja/[0-9a-z-]+-\\d+/[0-9a-z-]+-|(?:#!/)?jakso/(?:\\d+/[^/]+/)?|video/prog)(?<id>\\d+)",
        )
    }
}

/** Upstream `MTVUutisetArticleIE`: an MTV Uutiset article. */
class MTVUutisetArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val articleId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val article = http.downloadJson("http://api.mtvuutiset.fi/mtvuutiset/api/json/$articleId")
            as? JsonObject ?: throw ExtractionError.Malformed("The MTV Uutiset API returned no object.")
        val entries = mutableListOf<InfoEntry>()
        for (element in article.array("videos").orEmpty()) {
            val video = element as? JsonObject ?: continue
            val videoType = video.str("videotype")
            val videoUrl = video.str("url") ?: continue
            if (videoType != "katsomo" && videoType != "youtube") continue
            entries += InfoEntry(id = video.primitive("video_id"), url = videoUrl)
        }
        return InfoDict(
            id = articleId,
            entries = entries,
            webpageUrl = url,
            extractor = "mtvuutiset:article",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MTVUutisetArticle"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)mtvuutiset\\.fi/artikkeli/[^/]+/(?<id>\\d+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1].ifBlank { null } }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
