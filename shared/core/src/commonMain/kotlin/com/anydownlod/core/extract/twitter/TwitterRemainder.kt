/*
 * X / Twitter remainder extractors — AnyDownload
 *
 * Kotlin translation of `TwitterCardIE`, `TwitterAmplifyIE`,
 * `TwitterBroadcastIE`, `TwitterSpacesIE`, and `TwitterShortenerIE` from
 * `yt_dlp/extractor/twitter.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `twitter.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the card URL delegates to the status extractor through the registry
 * `redirectUrl` seam (upstream `url_result`); Amplify parses the
 * `twitter:amplify:vmap` meta and the VMAP `videoVariant`/`MediaFile` subset;
 * the shortener resolves a bounded redirect and re-enters the registry.
 * Broadcasts and Spaces match their URL forms and fail typed because their
 * upstream API calls need the Twitter bearer/guest token, which this app does
 * not ship and never commits. No token, cookie, or signed URL appears here.
 */
package com.anydownlod.core.extract.twitter

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail

/**
 * Upstream `TwitterCardIE`: `/i/cards/tfw/v1/<id>` and `/i/videos/tweet/<id>`
 * are display wrappers around the status extractor. The card id is the status
 * id in every upstream test, so the extractor hands the canonical status URL
 * to the registry instead of duplicating the syndication call.
 */
class TwitterCardIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "X / Twitter card"

    override suspend fun extract(url: String): InfoDict {
        val statusId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream: `url_result('https://twitter.com/statuses/' + status_id,
        // TwitterIE.ie_key(), status_id)`.
        return InfoDict(
            id = statusId,
            webpageUrl = url,
            extractor = "twitter",
            extractorKey = ieKey,
            redirectUrl = "https://twitter.com/statuses/$statusId",
        )
    }

    companion object {
        const val IE_KEY: String = "TwitterCard"

        /** Upstream `_VALID_URL` for the card class. */
        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|m(?:obile)?)\\.)?(?:twitter|x)\\.com/" +
                "i/(?:cards/tfw/v1|videos(?:/tweet)?)/(?<id>\\d+)/?(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )
    }
}

/**
 * Upstream `TwitterAmplifyIE`: one `amp.twimg.com/v/<uuid>` page with a
 * `twitter:amplify:vmap` meta. The VMAP document is parsed for its
 * `videoVariant` elements (URL-decoded `url` attribute, optional `bitrate`)
 * and a `MediaFile` fallback, matching `_extract_formats_from_vmap_url` and
 * `_extract_variant_formats`. The HLS manifest itself is not expanded at
 * extraction time; the engine resolves `m3u8_native` when it downloads.
 */
class TwitterAmplifyIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "X / Twitter Amplify"

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val vmapUrl = ExtractorUtils.htmlSearchMeta(webpage, "twitter:amplify:vmap")
            ?: throw ExtractionError.Malformed("The amplify page has no vmap URL.")
        val xml = http.downloadWebpage(vmapUrl)
        val parsed = vmapFormats(xml)
        if (parsed.isEmpty()) throw ExtractionError.NoFormats("The amplify page has no video.")

        // Upstream applies the player dimensions to the first format.
        val playerWidth = metaLong(webpage, "twitter:player:width")
        val playerHeight = metaLong(webpage, "twitter:player:height")
        val formats = if (playerWidth != null || playerHeight != null) {
            parsed.mapIndexed { index, format ->
                if (index == 0) {
                    format.copy(width = playerWidth ?: format.width, height = playerHeight ?: format.height)
                } else {
                    format
                }
            }
        } else {
            parsed
        }

        val thumbnailUrl = ExtractorUtils.htmlSearchMeta(webpage, "twitter:image:src")
        val thumbnails = if (thumbnailUrl != null) {
            listOf(
                Thumbnail(
                    url = thumbnailUrl,
                    width = metaLong(webpage, "twitter:image:width"),
                    height = metaLong(webpage, "twitter:image:height"),
                ),
            )
        } else {
            emptyList()
        }

        return InfoDict(
            id = videoId,
            title = "Twitter Video",
            formats = formats,
            thumbnails = thumbnails,
            webpageUrl = url,
            extractor = "twitter",
            extractorKey = ieKey,
        )
    }

    /**
     * Upstream `_extract_formats_from_vmap_url`: every `videoVariant` in
     * document order, then the `MediaFile` text when it was not already one
     * of the variants.
     */
    private fun vmapFormats(xml: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        val urls = mutableSetOf<String>()
        for (match in VIDEO_VARIANT.findAll(xml)) {
            val attributes = parseXmlAttributes(match.groupValues[1])
            val url = percentDecode(attributes["url"]?.trim().orEmpty())
            if (url.isEmpty()) continue
            urls += url
            formats += variantFormat(url, attributes["bitrate"] ?: attributes["bit_rate"])
        }
        val mediaFile = MEDIA_FILE.find(xml)?.groupValues?.get(1)?.trim()
        if (!mediaFile.isNullOrEmpty() && mediaFile !in urls) {
            formats += variantFormat(mediaFile, null)
        }
        return formats
    }

    /** Upstream `_extract_variant_formats` without the manifest download. */
    private fun variantFormat(url: String, bitrate: String?): MediaFormat {
        if (url.contains(".m3u8")) {
            return MediaFormat(
                formatId = "hls",
                url = url,
                ext = "mp4",
                protocol = "m3u8_native",
                formatNote = "HLS",
            )
        }
        val tbrKbps = bitrate?.toDoubleOrNull()?.div(1000)
        val dimensions = DIMENSIONS.find(url)
        return MediaFormat(
            formatId = joinNonempty("http", tbrKbps?.toLong()?.toString()),
            url = url,
            ext = "mp4",
            protocol = "https",
            tbr = tbrKbps,
            width = dimensions?.groupValues?.getOrNull(1)?.toLongOrNull(),
            height = dimensions?.groupValues?.getOrNull(2)?.toLongOrNull(),
        )
    }

    companion object {
        const val IE_KEY: String = "TwitterAmplify"

        val VALID_URL: Regex = Regex(
            "https?://amp\\.twimg\\.com/v/(?<id>[0-9a-f\\-]{36})/?(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )

        private val VIDEO_VARIANT = Regex(
            "<(?:[A-Za-z_][\\w.-]*:)?videoVariant\\b([^>]*)>",
            RegexOption.IGNORE_CASE,
        )
        private val MEDIA_FILE = Regex(
            "<(?:[A-Za-z_][\\w.-]*:)?MediaFile\\b[^>]*>([^<]*)</",
            RegexOption.IGNORE_CASE,
        )
        private val XML_ATTRIBUTE = Regex(
            """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""",
        )
        private val DIMENSIONS = Regex("/(\\d+)x(\\d+)/")

        private fun parseXmlAttributes(source: String): Map<String, String> {
            val attributes = linkedMapOf<String, String>()
            for (match in XML_ATTRIBUTE.findAll(source)) {
                val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
                attributes[match.groupValues[1].lowercase()] = value
            }
            return attributes
        }

        private fun joinNonempty(prefix: String, suffix: String?): String =
            if (suffix.isNullOrBlank()) prefix else "$prefix-$suffix"

        /** `urllib.parse.unquote` for the VMAP attribute subset. */
        private fun percentDecode(value: String): String {
            if ('%' !in value) return value
            val out = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val character = value[index]
                if (character == '%' && index + 2 < value.length) {
                    val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        index += 3
                        continue
                    }
                }
                out.append(character)
                index++
            }
            return out.toString()
        }
    }
}

/**
 * Upstream `TwitterBroadcastIE`: `/i/broadcasts/<id>` and `/i/events/<id>`
 * match, but extraction needs `broadcasts/show.json` and
 * `live_video_stream/status/<media_key>` with the Twitter API bearer and
 * guest tokens. This app does not ship those credentials, so the URL routes
 * to Kotlin and fails typed instead of silently falling through to the CLI.
 */
class TwitterBroadcastIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "X / Twitter broadcast"

    override suspend fun extract(url: String): InfoDict =
        throw ExtractionError.Unavailable(
            "Broadcast extraction needs the Twitter API token, which this app does not ship.",
        )

    companion object {
        const val IE_KEY: String = "TwitterBroadcast"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|m(?:obile)?)\\.)?(?:twitter|x)\\.com/" +
                "i/(?:broadcasts|events)/(?<id>\\w+)/?(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )
    }
}

/**
 * Upstream `TwitterSpacesIE`: `/i/spaces/<13-char id>` matches, but
 * extraction needs the `AudioSpaceById` GraphQL query and
 * `live_video_stream/status/<media_key>` with the Twitter API bearer token.
 * Not shipped; the URL routes to Kotlin and fails typed.
 */
class TwitterSpacesIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "X / Twitter Spaces"

    override suspend fun extract(url: String): InfoDict =
        throw ExtractionError.Unavailable(
            "Spaces extraction needs the Twitter API token, which this app does not ship.",
        )

    companion object {
        const val IE_KEY: String = "TwitterSpaces"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|m(?:obile)?)\\.)?(?:twitter|x)\\.com/" +
                "i/spaces/(?<id>[0-9a-zA-Z]{13})/?(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )
    }
}

/**
 * Upstream `TwitterShortenerIE`: `t.co/<code>` and the `tco:<code>` keyword
 * resolve one bounded redirect chain with the `curl` user agent, strip the
 * safety-warning prefix, and hand the resolved URL back to the registry
 * through [InfoDict.redirectUrl] (upstream `url_result(new_url)`).
 */
class TwitterShortenerIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "X / Twitter shortener"

    /** Upstream reads `id` for the URL form and `eid` for `tco:`. */
    override fun matchId(url: String): String? {
        val match = VALID_URL.find(url) ?: return null
        return match.groups["id"]?.value ?: match.groups["eid"]?.value
    }

    override suspend fun extract(url: String): InfoDict {
        val shortcode = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val requestUrl = if (url.startsWith("tco:", ignoreCase = true)) BASE_URL + shortcode else url
        val resolved = http.followRedirects(requestUrl, headers = mapOf("user-agent" to "curl"))
        val target = resolved.removePrefix(UNSAFE_LINK_PREFIX)
        return InfoDict(
            id = shortcode,
            webpageUrl = target,
            extractor = "twitter",
            extractorKey = ieKey,
            redirectUrl = target,
        )
    }

    companion object {
        const val IE_KEY: String = "TwitterShortener"

        val VALID_URL: Regex = Regex(
            "https?://t\\.co/(?<id>[^?#]+)|tco:(?<eid>[^?#]+)",
            RegexOption.IGNORE_CASE,
        )

        private const val BASE_URL: String = "https://t.co/"
        private const val UNSAFE_LINK_PREFIX: String =
            "https://twitter.com/safety/unsafe_link_warning?unsafe_link="
    }
}

private fun metaLong(html: String, name: String): Long? =
    ExtractorUtils.htmlSearchMeta(html, name)?.toLongOrNull()
