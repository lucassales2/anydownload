/*
 * Tumblr extractor — AnyDownload
 *
 * Kotlin translation of the public page subset of `tumblr.py` from
 * `yt_dlp/extractor/tumblr.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tumblr.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public post page (the `WhatsApp/2.0` user agent, the OG video
 * URL, the `/video/<blog>/<id>/` iframe page with its `data-crt-options`
 * hd/sd sources, the title/description/thumbnail). The `API_TOKEN` login
 * page, the OAuth login, the `/api/v2/.../permalink` metadata (reblog chain,
 * tags, counts, NSFW flag), and the external embed re-dispatch need the
 * account token; a dashboard-only/safe-mode redirect fails typed as a login
 * wall. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.tumblr

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonObject

/** Upstream `TumblrIE`: the public post pages. */
class TumblrIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val blog = match.groups["blogName2"]?.value ?: match.groups["blogName1"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val postUrl = "http://$blog.tumblr.com/post/$videoId"
        val headers = mapOf("user-agent" to "WhatsApp/2.0")
        val finalUrl = runCatching { http.followRedirects(postUrl, headers) }.getOrDefault(postUrl)
        val apiOnly = Regex("(tumblr\\.com|^)/(safe-mode|login_required|blog/view)")
            .containsMatchIn(finalUrl)
        if (apiOnly) {
            throw ExtractionError.LoginRequired(
                "This Tumblr post is dashboard-only; the account API token is needed and the login " +
                    "flow is not translated.",
            )
        }

        val webpage = http.downloadWebpage(postUrl, headers = headers)
        val title = ExtractorUtils.searchRegex(
            "(?s)<title>(?<title>.*?)(?: \\| Tumblr)?</title>",
            webpage,
            group = 1,
            default = null,
        )?.trim()?.takeIf { it.isNotEmpty() }
        val description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
        val ogVideoUrl = ExtractorUtils.htmlSearchMeta(webpage, "og:video", "og:video:url")
            ?: ExtractorUtils.searchRegex(
                "<meta[^>]+property=[\"']og:video:secure_url[\"'][^>]+content=[\"']([^\"']+)",
                webpage,
                default = null,
            )

        val formats = mutableListOf<MediaFormat>()
        var duration: Double? = null
        val iframeUrl = ExtractorUtils.searchRegex(
            "src='(https?://www\\.tumblr\\.com/video/$blog/$videoId/[^']+)'",
            webpage,
            default = null,
        )
        if (iframeUrl != null) {
            val iframe = try {
                http.downloadWebpage(iframeUrl, headers = mapOf("referer" to finalUrl))
            } catch (error: ExtractionError) {
                null
            }
            val options = iframe?.let { page ->
                ExtractorUtils.searchRegex(
                    "data-crt-options=([\"'])(?<options>.+?)\\1",
                    page,
                    group = 2,
                    default = null,
                )?.let { raw ->
                    ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(raw) ?: raw) as? JsonObject
                }
            }
            if (options != null) {
                duration = options.number("duration")
                val hdUrl = ExtractorUtils.urlOrNone(options.str("hdUrl"))
                if (hdUrl != null) {
                    val sources = mutableListOf<Pair<String, String>>()
                    val sdUrl = iframe?.let { page ->
                        ExtractorUtils.searchRegex(
                            "<source[^>]+src=([\"'])(?<url>.+?)\\1",
                            page,
                            group = 2,
                            default = null,
                        )
                    }
                    ExtractorUtils.urlOrNone(sdUrl)?.let { sources += it to "sd" }
                    sources += hdUrl to "hd"
                    for ((quality, source) in sources.withIndex()) {
                        val (sourceUrl, formatId) = source
                        formats += MediaFormat(
                            formatId = formatId,
                            url = sourceUrl,
                            height = ExtractorUtils.searchRegex(
                                "_(\\d+)\\.\\w+$",
                                sourceUrl,
                                default = null,
                            )?.toLongOrNull(),
                            preference = quality,
                        )
                    }
                }
            }
        }
        if (formats.isEmpty() && ogVideoUrl != null) {
            formats += MediaFormat(
                url = ogVideoUrl,
                width = ExtractorUtils.htmlSearchMeta(webpage, "og:video:width")?.toLongOrNull(),
                height = ExtractorUtils.htmlSearchMeta(webpage, "og:video:height")?.toLongOrNull(),
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("No video could be found in this Tumblr post.")
        }

        return InfoDict(
            id = videoId,
            title = title ?: blog,
            description = description,
            uploadDate = null,
            ageLimit = null,
            thumbnails = ExtractorUtils.htmlSearchMeta(webpage, "og:image")
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            duration = duration,
            webpageUrl = postUrl,
            extractor = "tumblr",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Tumblr"

        val VALID_URL: Regex = Regex(
            "https?://(?<blogName1>[^/?#&]+)\\.tumblr\\.com/(?:post|video|(?<blogName2>[a-zA-Z\\d-]+))/" +
                "(?<id>[0-9]+)(?:$|[/?#])",
        )
    }
}

private fun JsonObject.str(name: String): String? =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
