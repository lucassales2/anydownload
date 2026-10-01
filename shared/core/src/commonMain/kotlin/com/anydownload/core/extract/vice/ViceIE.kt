/*
 * Vice extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `vice.py` from
 * `yt_dlp/extractor/vice.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vice.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the GraphQL videos/shows/articles calls, the signed preplay request
 * (`sha512("{video_id}:GET:{exp}")` over public fields), the HLS play URL and
 * subtitle tracks, and the show/article listings and embed redirects. A
 * `locked` video is the typed Adobe Pass login wall; no MVPD credential,
 * cookie, token, or signed media URL is stored here. Upstream marks every
 * class `_WORKING = False`. The port does not carry series/episode/season
 * fields or uploader_id (the channel id fills channelId), the article only
 * scans iframe-style embeds, and an m3u8 URL becomes one HLS row.
 */
package com.anydownload.core.extract.vice

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.adobepass.AdobePassIE
import com.anydownload.core.extract.sha512Hex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.time.Clock

private const val GRAPHQL_URL = "https://video.vice.com/api/v1/graphql"
private const val SHOW_PAGE_SIZE = 25
private const val MAX_PAGES = 5

/**
 * Upstream `ViceBaseIE`: the GraphQL call shared by the three Vice classes.
 * The class also extends the Adobe Pass base so a locked video reaches the
 * typed login wall.
 */
abstract class ViceBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : AdobePassIE(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun callApi(
        resource: String,
        resourceKey: String,
        resourceId: String,
        locale: String,
        fields: String,
        args: String = "",
    ): JsonArray {
        val query = "{\n  $resource(locale: \"$locale\", $resourceKey: \"$resourceId\"$args) {\n" +
            fields.lines().joinToString("\n") { "    $it" } + "\n  }\n}"
        val response = http.downloadJson(GRAPHQL_URL + "?query=" + percentEncode(query)) as? JsonObject
            ?: throw ExtractionError.Malformed("The Vice GraphQL response was not an object.")
        val data = response.obj("data")
            ?: throw ExtractionError.Malformed("The Vice GraphQL response had no data.")
        return data.array(resource)
            ?: throw ExtractionError.Malformed("The Vice GraphQL response had no $resource.")
    }
}

/** Upstream `ViceIE`: one video via the signed preplay API. */
class ViceIE(
    http: ExtractorHttp,
) : ViceBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val locale = match.groups["locale"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val video = callApi(
            "videos",
            "id",
            videoId,
            locale,
            "body\nlocked\nrating\nthumbnail_url\ntitle",
        ).firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Vice video API returned no video.")
        val title = video.str("title")?.trim()
            ?: throw ExtractionError.Malformed("The Vice video had no title.")
        val rating = video.str("rating")
        if (video.bool("locked") == true) {
            // Upstream `_get_mvpd_resource` / `_extract_mvpd_auth` ask for TV
            // provider credentials; the port fails typed instead.
            mvpdAuthRequired()
        }

        // Upstream signature generation over public fields:
        // sha512(f'{video_id}:GET:{exp}').
        val exp = Clock.System.now().toEpochMilliseconds() / 1000 + 1440
        val sign = sha512Hex("$videoId:GET:$exp".encodeToByteArray())
        val rn = Random.nextInt(10_000, 100_000)
        val preplayUrl = "https://vms.vice.com/$locale/video/preplay/$videoId" +
            "?exp=$exp&sign=$sign&skipadstitching=1&platform=desktop&rn=$rn"
        val preplay = try {
            http.downloadJson(preplayUrl) as? JsonObject
        } catch (error: ExtractionError) {
            throw error
        } ?: throw ExtractionError.Malformed("The Vice preplay response was not an object.")
        val playUrl = preplay.str("playURL")
            ?: throw ExtractionError.NoFormats("The Vice preplay response had no play URL.")
        val videoData = preplay.obj("video") ?: JsonObject(emptyMap())
        val channel = videoData.obj("channel") ?: JsonObject(emptyMap())

        val formats = listOf(
            MediaFormat(
                formatId = "hls",
                url = playUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            ),
        )
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in preplay.array("subtitleURLs").orEmpty()) {
            val subtitle = element as? JsonObject ?: continue
            val ccUrl = subtitle.str("url") ?: continue
            val language = (subtitle.array("languages")?.firstOrNull() as? JsonObject)
                ?.str("language_code") ?: "en"
            subtitles += SubtitleTrack(
                language = language,
                formats = listOf(
                    SubtitleFormat(ext = ExtractorUtils.determineExt(ccUrl), url = ccUrl),
                ),
            )
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = cleanHtml(video.str("body")),
            duration = videoData.number("video_duration"),
            uploadDate = videoData.number("created_at")?.toLong()?.div(1000)
                ?.let(ExtractorUtils::epochSecondsToDate),
            ageLimit = ExtractorUtils.parseAgeLimit(videoData.primitive("video_rating") ?: rating),
            channelId = channel.primitive("id"),
            uploader = channel.str("name"),
            thumbnails = listOfNotNull(video.str("thumbnail_url")?.let { Thumbnail(url = it) }),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "vice",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Vice"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:video|vms)\\.vice|(?:www\\.)?vice(?:land|tv))\\.com/" +
                "(?<locale>[^/]+)/(?:video/[^/]+|embed)/(?<id>[\\da-f]{24})",
        )
    }
}

/** Upstream `ViceShowIE`: a paged show listing. */
class ViceShowIE(
    http: ExtractorHttp,
) : ViceBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val locale = match.groups["locale"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val show = callApi("shows", "slug", displayId, locale, "dek\nid\ntitle")
            .firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Vice shows API returned no show.")
        val showId = show.str("id")
            ?: throw ExtractionError.Malformed("The Vice show had no id.")
        val entries = mutableListOf<InfoEntry>()
        // Upstream OnDemandPagedList starts at page 0; the API takes page + 1.
        var page = 0
        while (page < MAX_PAGES) {
            val videos = callApi(
                "videos",
                "show_id",
                showId,
                locale,
                "body\nid\nurl",
                ", page: ${page + 1}, per_page: $SHOW_PAGE_SIZE",
            )
            val pageEntries = videos.mapNotNull { element ->
                val video = element as? JsonObject ?: return@mapNotNull null
                val videoUrl = video.str("url") ?: return@mapNotNull null
                InfoEntry(id = video.primitive("id"), url = videoUrl)
            }
            if (pageEntries.isEmpty()) break
            entries += pageEntries
            if (pageEntries.size < SHOW_PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = showId,
            title = show.str("title"),
            description = show.str("dek"),
            entries = entries,
            webpageUrl = url,
            extractor = "vice:show",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ViceShow"

        val VALID_URL: Regex = Regex(
            "https?://(?:video\\.vice|(?:www\\.)?vice(?:land|tv))\\.com/" +
                "(?<locale>[^/]+)/show/(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `ViceArticleIE`: an article's embed becomes a redirect. */
class ViceArticleIE(
    http: ExtractorHttp,
) : ViceBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val locale = match.groups["locale"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val article = callApi("articles", "slug", displayId, locale, "body\nembed_code")
            .firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The Vice articles API returned no article.")
        val body = article.str("body").orEmpty()
        val viceUrl = extractViceEmbedUrl(body)
        if (viceUrl != null) return redirect(viceUrl, url)
        val youtubeUrl = extractYoutubeEmbedUrl(body)
        if (youtubeUrl != null) return redirect(youtubeUrl, url)
        val videoUrl = ExtractorUtils.searchRegex(
            "data-video-url=\"([^\"]+)\"",
            article.str("embed_code").orEmpty(),
        ) ?: throw ExtractionError.Malformed("The Vice article had no video URL.")
        return redirect(videoUrl, url)
    }

    private fun redirect(target: String, url: String): InfoDict = InfoDict(
        redirectUrl = target,
        webpageUrl = url,
        extractor = "vice:article",
        extractorKey = IE_KEY,
    )

    companion object {
        const val IE_KEY: String = "ViceArticle"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?vice\\.com/(?<locale>[^/]+)/article/" +
                "(?:[0-9a-z]{6}/)?(?<id>[^?#]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `ViceIE._EMBED_REGEX`: a video.vice.com player embed. */
private fun extractViceEmbedUrl(webpage: String): String? {
    val raw = Regex(
        "<iframe\\b[^>]+\\bsrc=[\"']((?:https?:)?//video\\.vice\\.com/[^/]+/embed/[\\da-f]{24})[\"']",
    ).find(webpage)?.groupValues?.get(1) ?: return null
    return if (raw.startsWith("//")) "https:$raw" else raw
}

/** Upstream `YoutubeIE._EMBED_REGEX` for the iframe-style forms. */
private fun extractYoutubeEmbedUrl(webpage: String): String? {
    val match = Regex(
        "(?:<(?:[0-9A-Za-z-]+?)?iframe[^>]+?src=|data-video-url=|<embed[^>]+?src=|" +
            "<object[^>]+data=)" +
            "([\"'])((?:https?:)?//(?:www\\.)?youtube(?:-nocookie)?\\.com/" +
            "(?:embed|v|p)/[0-9A-Za-z_-]{11}.*?)\\1",
        RegexOption.DOT_MATCHES_ALL,
    ).find(webpage) ?: return null
    val raw = match.groupValues[2]
    return if (raw.startsWith("//")) "https:$raw" else raw
}

/** Upstream `clean_html`: tags stripped, entities decoded, whitespace collapsed. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/** Percent-encoding for the GraphQL query parameter. */
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
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
