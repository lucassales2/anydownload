/*
 * X / Twitter status extractor — AnyDownload
 *
 * Kotlin translation of a subset of `yt_dlp/extractor/twitter.py`
 * (`TwitterIE._VALID_URL`, `_call_syndication_api`, `_generate_syndication_token`,
 * `_extract_status` through the syndication selection only, the `_real_extract`
 * metadata, and the `_extract_variant_formats`/`extract_from_video_info` media
 * mapping), read at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf) on 2026-09-25.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. Not translated: the GraphQL and legacy
 * API selections and their hard-coded bearer values, cookies, guest-token
 * storage, photos, quoted tweets, cards, Spaces, broadcasts, Amplify, the
 * shortener, and every other class in `twitter.py`. `twitter.py` is not
 * vendored; see shared/core/NOTICE.md and port/manifest.json.
 *
 * The only network call is the public syndication lookup. It sends no cookie
 * and no authorization header. The per-request token is derived from the
 * status id, used for that one request, and never stored or logged.
 */
package com.anydownlod.core.extract.twitter

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.InfoMedia
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.platform.HttpMethods
import com.anydownlod.core.platform.HttpRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.math.PI
import kotlin.math.floor

/**
 * Single-status X/Twitter extraction through the public guest lookup.
 *
 * A status with one or more videos becomes [InfoDict.media], one item per
 * stable media id; [InfoDict.formats] stays empty so only an explicit
 * selection can start a download. A photo-only, protected, or deleted status
 * fails typed. Quoted tweets and cards are ignored.
 */
class TwitterIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "X / Twitter"

    override suspend fun extract(url: String): InfoDict {
        val statusId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val status = fetchStatus(statusId)
        return statusToInfo(statusId, url, status)
    }

    /** Upstream `_call_syndication_api`; the only request an extraction makes. */
    private suspend fun fetchStatus(statusId: String): JsonObject {
        val token = syndicationToken(statusId)
            ?: throw ExtractionError.Malformed("The status id is not a number.")
        val bytes = http.downloadBytes(
            HttpRequest(
                url = "$SYNDICATION_URL?id=$statusId&token=$token",
                method = HttpMethods.GET,
                headers = mapOf("user-agent" to GOOGLEBOT_USER_AGENT),
            ),
        )
        val parsed = ExtractorUtils.parseJson(bytes.decodeToString(), fatal = true)
        val status = parsed as? JsonObject
            ?: throw ExtractionError.Unavailable("This post is unavailable.")
        checkAvailability(status)
        return status
    }

    /**
     * Upstream returns the tweet dict with a synthetic `extended_entities`;
     * this port reads the syndication `mediaDetails` directly. A payload that
     * says the post is protected is a typed login requirement, never a cookie
     * prompt.
     */
    private fun checkAvailability(status: JsonObject) {
        val reason = status.str("reason")
        val unavailable = status.str("__typename") == "TweetUnavailable"
        if (reason != null || unavailable) {
            val lower = reason?.lowercase().orEmpty()
            if ("protect" in lower || "login" in lower || "nsfwloggedout" in lower) {
                throw ExtractionError.LoginRequired()
            }
            throw ExtractionError.Unavailable("This post is unavailable.")
        }
        if (status.obj("user")?.flag("protected") == true) throw ExtractionError.LoginRequired()
    }

    private fun statusToInfo(statusId: String, url: String, status: JsonObject): InfoDict {
        val user = status.obj("user")
        val rawText = status.str("text") ?: status.str("full_text") ?: ""
        val description = rawText.replace('\n', ' ').trim()
        val uploader = user?.str("name")
        val title = statusTitle(uploader, description)

        val videos = mutableListOf<InfoMedia>()
        var index = 0
        for (element in status.array("mediaDetails").orEmpty()) {
            val media = element as? JsonObject ?: continue
            index++
            if (media.str("type") == "photo") continue
            val formats = variantFormats(media)
            if (formats.isEmpty()) continue
            videos += InfoMedia(
                mediaId = mediaIdOf(media, statusId, index),
                title = null,
                duration = media.obj("video_info")?.number("duration_millis")?.div(1000),
                thumbnails = mediaThumbnails(media),
                formats = formats,
            )
        }
        if (videos.isEmpty()) throw ExtractionError.NoFormats("This post has no video.")

        // Upstream titles a multi-video status `#1`, `#2`, ... and keeps the
        // single-video title unchanged.
        val named = if (videos.size == 1) {
            videos.map { it.copy(title = title) }
        } else {
            videos.mapIndexed { position, media ->
                media.copy(title = "${title ?: "Video"} #${position + 1}")
            }
        }

        return InfoDict(
            id = statusId,
            title = title,
            description = description.ifEmpty { null },
            uploader = uploader,
            channel = uploader,
            channelId = user?.str("id_str") ?: user?.number("id")?.toLong()?.toString(),
            uploadDate = ExtractorUtils.unifiedStrdate(status.str("created_at")),
            viewCount = status.number("view_count")?.toLong(),
            thumbnails = named.firstOrNull()?.thumbnails.orEmpty(),
            media = named,
            webpageUrl = url,
            extractor = "twitter",
            extractorKey = ieKey,
            ageLimit = if (status.flag("possibly_sensitive") == true) 18 else 0,
        )
    }

    /** Upstream `_extract_variant_formats` without the manifest download. */
    private fun variantFormats(media: JsonObject): List<MediaFormat> {
        val variants = media.obj("video_info")?.array("variants") ?: return emptyList()
        val formats = mutableListOf<MediaFormat>()
        for (element in variants) {
            val variant = element as? JsonObject ?: continue
            val url = ExtractorUtils.urlOrNone(variant.str("url")) ?: continue
            val tbrKbps = (variant.number("bitrate") ?: variant.number("bit_rate"))?.div(1000)
            if (url.contains(".m3u8")) {
                formats += MediaFormat(
                    formatId = joinNonempty("hls", tbrKbps?.toLong()?.toString()),
                    url = url,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    tbr = tbrKbps,
                    formatNote = "HLS",
                )
            } else {
                val dimensions = DIMENSIONS.find(url)
                formats += MediaFormat(
                    formatId = joinNonempty("http", tbrKbps?.toLong()?.toString()),
                    url = url,
                    ext = "mp4",
                    protocol = "https",
                    tbr = tbrKbps,
                    width = dimensions?.groupValues?.getOrNull(1)?.toLongOrNull(),
                    height = dimensions?.groupValues?.getOrNull(2)?.toLongOrNull(),
                )
            }
        }
        return formats
    }

    /**
     * Upstream `_call_syndication_api` rewrites every media entry with
     * `id_str` from the variant URL's `_video/<id>/` segment; the port reads
     * it the same way and falls back to a status-scoped synthetic id so a
     * video without one is still grouped and selectable.
     */
    private fun mediaIdOf(media: JsonObject, statusId: String, index: Int): String {
        media.str("id_str")?.let { return it }
        media.number("id")?.toLong()?.let { return it.toString() }
        for (element in media.obj("video_info")?.array("variants").orEmpty()) {
            val url = (element as? JsonObject)?.str("url") ?: continue
            MEDIA_ID.find(url)?.let { return it.groupValues[1] }
        }
        return "$statusId-$index"
    }

    private fun mediaThumbnails(media: JsonObject): List<Thumbnail> {
        val base = media.str("media_url_https") ?: media.str("media_url") ?: return emptyList()
        val thumbnails = mutableListOf<Thumbnail>()
        media.obj("sizes")?.forEach { (name, element) ->
            val size = element as? JsonObject ?: return@forEach
            thumbnails += Thumbnail(
                url = withQuery(base, "name=$name"),
                id = name,
                width = size.number("w")?.toLong() ?: size.number("width")?.toLong(),
                height = size.number("h")?.toLong() ?: size.number("height")?.toLong(),
            )
        }
        val original = media.obj("original_info")
        thumbnails += Thumbnail(
            url = withQuery(base, "name=orig"),
            id = "orig",
            width = original?.number("width")?.toLong(),
            height = original?.number("height")?.toLong(),
        )
        return thumbnails
    }

    companion object {
        const val IE_KEY: String = "Twitter"

        private const val SYNDICATION_URL: String = "https://cdn.syndication.twimg.com/tweet-result"
        private const val GOOGLEBOT_USER_AGENT: String = "Googlebot"

        /**
         * Upstream `TwitterBaseIE._BASE_REGEX` restricted to the two public
         * domains. `m.`/`mobile.`/`www.` hosts and the `i/web` path form are
         * accepted; the `statuses` spelling is accepted too. A `/video` or
         * `/photo` suffix, a profile path, or a `t.co` link does not match.
         */
        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|m(?:obile)?)\\.)?(?:twitter|x)\\.com/" +
                "(?:(?:i/web|[^/?#]+)/status|statuses)/(?<id>\\d+)/?(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )

        private val MEDIA_ID: Regex = Regex("_video/(\\d+)/")
        private val DIMENSIONS: Regex = Regex("/(\\d+)x(\\d+)/")

        /**
         * Upstream `_generate_syndication_token`: a JS-style base-36 string of
         * `(id / 1e15) * pi` with every `0` and `.` removed. The token is
         * derived for one request and never persisted.
         */
        internal fun syndicationToken(statusId: String): String? {
            val id = statusId.toDoubleOrNull() ?: return null
            val value = (id / 1e15) * PI
            return jsNumberToStringBase36(value).replace("0", "").replace(".", "")
        }

        /**
         * Port of `yt_dlp.jsinterp.js_number_to_string` for radix 36, the
         * helper the upstream token derivation calls. Kept private; only the
         * derived string leaves it.
         */
        private fun jsNumberToStringBase36(value: Double): String {
            if (value.isNaN()) return "NaN"
            if (value == 0.0) return "0"
            if (value.isInfinite()) return if (value < 0) "-Infinity" else "Infinity"

            val digits = ArrayDeque<Int>()
            val negative = value < 0
            val absolute = kotlin.math.abs(value)
            var fraction = absolute - floor(absolute)
            var integer = floor(absolute)
            var delta = maxOf(Double.MIN_VALUE, machineUlp(absolute) / 2)

            if (fraction >= delta) digits.addLast(DOT)
            while (fraction >= delta) {
                delta *= 36
                val scaled = fraction * 36
                fraction = scaled - floor(scaled)
                val digit = floor(scaled).toInt()
                digits.addLast(digit)
                val needsRounding = fraction > 0.5 || (fraction == 0.5 && (digit and 1) == 1)
                if (needsRounding && fraction + delta > 1) {
                    var carried = false
                    var index = digits.size - 1
                    while (index >= 1) {
                        if (digits[index] + 1 < 36) {
                            digits[index] = digits[index] + 1
                            carried = true
                            break
                        }
                        digits.removeLast()
                        index--
                    }
                    if (!carried) integer += 1
                    break
                }
            }

            var whole = integer.toLong()
            digits.addFirst((whole % 36).toInt())
            whole /= 36
            while (whole > 0) {
                digits.addFirst((whole % 36).toInt())
                whole /= 36
            }
            if (negative) digits.addFirst(MINUS)

            val builder = StringBuilder(digits.size)
            for (digit in digits) {
                when (digit) {
                    DOT -> builder.append('.')
                    MINUS -> builder.append('-')
                    else -> builder.append(BASE36_ALPHABET[digit])
                }
            }
            return builder.toString()
        }

        private fun machineUlp(value: Double): Double =
            Double.fromBits(value.toBits() + 1) - value

        private const val BASE36_ALPHABET: String = "0123456789abcdefghijklmnopqrstuvwxyz"
        private const val DOT: Int = -2
        private const val MINUS: Int = -1
    }
}

/** Upstream `truncate_string(..., left=72)` and the trailing-URL cleanup. */
private fun statusTitle(uploader: String?, description: String): String? {
    val withoutUrls = Regex("\\s+https?://\\S+").replace(description, "").trim()
    val joined = if (uploader.isNullOrBlank()) {
        withoutUrls
    } else {
        if (withoutUrls.isEmpty()) "$uploader" else "$uploader - $withoutUrls"
    }
    if (joined.isEmpty()) return null
    return if (joined.length <= 72) joined else "${joined.take(69)}..."
}

private fun joinNonempty(prefix: String, suffix: String?): String =
    if (suffix.isNullOrBlank()) prefix else "$prefix-$suffix"

private fun withQuery(url: String, query: String): String =
    if (url.contains('?')) "$url&$query" else "$url?$query"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.flag(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull
