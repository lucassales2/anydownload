/*
 * Safari / O'Reilly extractors — AnyDownload
 *
 * Kotlin translation of `safari.py` from `yt_dlp/extractor/safari.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `safari.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the library/videos URL forms, the `data-reference-id` /
 * `data-partner-id` / `data-ui-id` page search, the Kaltura `mwEmbedFrame`
 * URL construction dispatched to the port's `KalturaIE`, the API chapter
 * `web_url` rewrite with `natural_key`, and the course `chapters` playlist.
 * Limitations: the email/password login (`_perform_login`, the two
 * Set-Cookie instances, the kaltura_session branch) is not translated, so
 * `LOGGED_IN` is always false; the post-redirect URL re-match uses
 * `followRedirects`; the transparent dispatches to Kaltura and Safari are
 * direct calls. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.safari

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.kaltura.KalturaIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_BASE = "https://learning.oreilly.com/api/v1"
private const val PARTNER_ID = "1926081"
private const val UICONF_ID = "29375172"
private const val MW_EMBED_URL = "https://cdnapisec.kaltura.com/html5/html5lib/v2.37.1/mwEmbedFrame.php"

/** Upstream `SafariBaseIE`: the API base and the never-logged-in state. */
abstract class SafariBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `LOGGED_IN`; the login flow is not translated. */
    protected val loggedIn: Boolean = false

    /** Upstream `_API_BASE`. */
    protected val apiBase: String = API_BASE
}

/** Upstream `SafariIE`: one library part or videos page. */
class SafariIE(
    http: ExtractorHttp,
) : SafariBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val referenceId: String
        val partnerId: String
        val uiId: String
        val urlReference = match.groups["referenceid"]?.value
        if (urlReference != null) {
            referenceId = urlReference
            partnerId = PARTNER_ID
            uiId = UICONF_ID
        } else {
            val courseId = match.groups["courseid"]?.value ?: throw ExtractionError.UnsupportedUrl()
            val part = match.groups["part"]?.value ?: throw ExtractionError.UnsupportedUrl()
            val finalUrl = http.followRedirects(url)
            val webpage = http.downloadWebpage(finalUrl)
            referenceId = VALID_URL.find(finalUrl)?.groups?.get("referenceid")?.value
                ?: dataAttribute(webpage, "data-reference-id")
                ?: throw ExtractionError.Malformed("The Safari page carried no kaltura reference id.")
            partnerId = dataAttribute(webpage, "data-partner-id") ?: PARTNER_ID
            uiId = dataAttribute(webpage, "data-ui-id") ?: UICONF_ID
        }

        // Upstream `update_url_query(...)`; the kaltura_session branch needs the login.
        val embedUrl = MW_EMBED_URL + "?" + queryString(
            mapOf(
                "wid" to "_$partnerId",
                "uiconf_id" to uiId,
                "flashvars[referenceId]" to referenceId,
            ),
        )
        val info = KalturaIE(http).extract(embedUrl)
        return info.copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "Safari"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:safaribooksonline|(?:learning\\.)?oreilly)\\.com/" +
                "(?:library/view/[^/]+/(?<courseid>[^/]+)/(?<part>[^/?\\#&]+)\\.html|" +
                "videos/[^/]+/[^/]+/(?<referenceid>[^-]+-[^/?\\#&]+))",
        )
    }
}

/** Upstream `SafariApiIE`: the chapter API endpoint. */
class SafariApiIE(
    http: ExtractorHttp,
) : SafariBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val courseId = match.groups["courseid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val part = match.groups["part"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val partJson = http.downloadJson(url) as? JsonObject
            ?: throw ExtractionError.Malformed("The Safari chapter API was not an object.")
        var webUrl = partJson.str("web_url")
            ?: throw ExtractionError.Malformed("The Safari chapter API carried no web URL.")
        if ("library/view" in webUrl) {
            webUrl = webUrl.replace("library/view", "videos")
            val naturalKeys = partJson.array("natural_key")
                ?: throw ExtractionError.Malformed("The Safari chapter API carried no natural key.")
            val first = (naturalKeys.getOrNull(0) as? JsonPrimitive)?.content
                ?: throw ExtractionError.Malformed("The Safari natural key was malformed.")
            val second = (naturalKeys.getOrNull(1) as? JsonPrimitive)?.content
                ?: throw ExtractionError.Malformed("The Safari natural key was malformed.")
            webUrl = webUrl.substringBeforeLast('/') + "/" + first + "-" + second.dropLast(5)
        }

        val info = SafariIE(http).extract(webUrl)
        return info.copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "SafariApi"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:safaribooksonline|(?:learning\\.)?oreilly)\\.com/" +
                "api/v1/book/(?<courseid>[^/]+)/chapter(?:-content)?/(?<part>[^/?#&]+)\\.html",
        )
    }
}

/** Upstream `SafariCourseIE`: a course page. */
class SafariCourseIE(
    http: ExtractorHttp,
) : SafariBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        !SafariIE.VALID_URL.containsMatchIn(url) &&
            !SafariApiIE.VALID_URL.containsMatchIn(url) &&
            super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val courseId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val courseJson = http.downloadJson("$apiBase/book/$courseId/?override_format=json") as? JsonObject
            ?: throw ExtractionError.Malformed("The Safari course API was not an object.")
        val chapters = courseJson.array("chapters")
            ?: throw ExtractionError.Unavailable("No chapters found for course $courseId.")

        val entries = chapters.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.let { InfoEntry(url = it) }
        }
        return InfoDict(
            id = courseId,
            title = courseJson.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "safari:course",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "SafariCourse"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www\\.)?(?:safaribooksonline|(?:learning\\.)?oreilly)\\.com/" +
                "(?:library/view/[^/]+|api/v1/book|videos/[^/]+)|techbus\\.safaribooksonline\\.com)" +
                "/(?<id>[^/]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `data-NAME=(["'])(?:(?!\1).)+` search. */
private fun dataAttribute(webpage: String, name: String): String? =
    ExtractorUtils.searchRegex(
        "${Regex.escape(name)}=([\"'])((?:(?!\\1).)+)\\1",
        webpage,
        group = 2,
    )

private fun queryString(params: Map<String, String>): String =
    params.entries.joinToString("&") { (name, value) ->
        "${percentEncode(name)}=${percentEncode(value)}"
    }

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

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}
