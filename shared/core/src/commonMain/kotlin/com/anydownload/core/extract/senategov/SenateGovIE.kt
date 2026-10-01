/*
 * U.S. Senate extractors — AnyDownload
 *
 * Kotlin translation of the public page/player subset of `senategov.py` from
 * `yt_dlp/extractor/senategov.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `senategov.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the ISVP player query (filename/comm/poster, the four committee
 * stream alternatives probed in order, and the page title) and the committee
 * subdomain pages whose iframe embed is delegated to the ISVP extractor with
 * the page og title/description/thumbnail and the RTA age limit. An m3u8 URL
 * becomes one HLS row, so manifest subtitles are not parsed, and
 * `_old_archive_ids`/`display_id` are not carried. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.senategov

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail

/** One upstream `_COMMITTEES` entry. */
internal data class SenateCommittee(
    val streamNum: String,
    val streamDomain: String,
    val streamId: String,
    val msl3: String,
)

internal val SENATE_COMMITTEES: Map<String, SenateCommittee> = mapOf(
    "ag" to SenateCommittee("76440", "https://ag-f.akamaihd.net", "2036803", "agriculture"),
    "aging" to SenateCommittee("76442", "https://aging-f.akamaihd.net", "2036801", "aging"),
    "approps" to SenateCommittee("76441", "https://approps-f.akamaihd.net", "2036802", "appropriations"),
    "arch" to SenateCommittee("", "https://ussenate-f.akamaihd.net", "", "arch"),
    "armed" to SenateCommittee("76445", "https://armed-f.akamaihd.net", "2036800", "armedservices"),
    "banking" to SenateCommittee("76446", "https://banking-f.akamaihd.net", "2036799", "banking"),
    "budget" to SenateCommittee("76447", "https://budget-f.akamaihd.net", "2036798", "budget"),
    "cecc" to SenateCommittee("76486", "https://srs-f.akamaihd.net", "2036782", "srs_cecc"),
    "commerce" to SenateCommittee("80177", "https://commerce1-f.akamaihd.net", "2036779", "commerce"),
    "csce" to SenateCommittee("75229", "https://srs-f.akamaihd.net", "2036777", "srs_srs"),
    "dpc" to SenateCommittee("76590", "https://dpc-f.akamaihd.net", "", "dpc"),
    "energy" to SenateCommittee("76448", "https://energy-f.akamaihd.net", "2036797", "energy"),
    "epw" to SenateCommittee("76478", "https://epw-f.akamaihd.net", "2036783", "environment"),
    "ethics" to SenateCommittee("76449", "https://ethics-f.akamaihd.net", "2036796", "ethics"),
    "finance" to SenateCommittee("76450", "https://finance-f.akamaihd.net", "2036795", "finance_finance"),
    "foreign" to SenateCommittee("76451", "https://foreign-f.akamaihd.net", "2036794", "foreignrelations"),
    "govtaff" to SenateCommittee("76453", "https://govtaff-f.akamaihd.net", "2036792", "hsgac"),
    "help" to SenateCommittee("76452", "https://help-f.akamaihd.net", "2036793", "help"),
    "indian" to SenateCommittee("76455", "https://indian-f.akamaihd.net", "2036791", "indianaffairs"),
    "intel" to SenateCommittee("76456", "https://intel-f.akamaihd.net", "2036790", "intelligence"),
    "intlnarc" to SenateCommittee("76457", "https://intlnarc-f.akamaihd.net", "", "internationalnarcoticscaucus"),
    "jccic" to SenateCommittee("85180", "https://jccic-f.akamaihd.net", "2036778", "jccic"),
    "jec" to SenateCommittee("76458", "https://jec-f.akamaihd.net", "2036789", "jointeconomic"),
    "judiciary" to SenateCommittee("76459", "https://judiciary-f.akamaihd.net", "2036788", "judiciary"),
    "rpc" to SenateCommittee("76591", "https://rpc-f.akamaihd.net", "", "rpc"),
    "rules" to SenateCommittee("76460", "https://rules-f.akamaihd.net", "2036787", "rules"),
    "saa" to SenateCommittee("76489", "https://srs-f.akamaihd.net", "2036780", "srs_saa"),
    "smbiz" to SenateCommittee("76461", "https://smbiz-f.akamaihd.net", "2036786", "smallbusiness"),
    "srs" to SenateCommittee("75229", "https://srs-f.akamaihd.net", "2031966", "srs_srs"),
    "uscc" to SenateCommittee("76487", "https://srs-f.akamaihd.net", "2036781", "srs_uscc"),
    "vetaff" to SenateCommittee("76462", "https://vetaff-f.akamaihd.net", "2036785", "veteransaffairs"),
)

/** Upstream `SenateISVPIE`: the integrated Senate video player. */
class SenateISVPIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val qs = parseFormQuery(VALID_URL.find(url)?.groups?.get("qs")?.value.orEmpty())
        val filename = qs["filename"]?.firstOrNull()
            ?: throw ExtractionError.UnsupportedUrl("Invalid URL.")
        val committee = qs["comm"]?.firstOrNull()
            ?: throw ExtractionError.UnsupportedUrl("Invalid URL.")
        val videoId = filename.removeSuffix(".mp4")
        val webpage = http.downloadWebpage(url)
        val info = SENATE_COMMITTEES[committee]
            ?: throw ExtractionError.UnsupportedUrl("Unknown Senate committee.")
        val alternatives = listOf(
            "https://www-senate-gov-media-srs.akamaized.net/hls/live/${info.streamId}" +
                "/$committee/$filename/master.m3u8",
            "https://www-senate-gov-msl3archive.akamaized.net/${info.msl3}/${filename}_1/master.m3u8",
            "${info.streamDomain}/i/${filename}_1@${info.streamNum}/master.m3u8",
            "${info.streamDomain}/i/$filename.mp4/master.m3u8",
        )
        // Upstream `_extract_m3u8_formats_and_subtitles(..., fatal=False)`:
        // the first alternative that answers becomes the HLS row.
        val formats = mutableListOf<MediaFormat>()
        for (videoUrl in alternatives) {
            val available = try {
                http.downloadWebpage(videoUrl)
                true
            } catch (error: ExtractionError) {
                false
            }
            if (available) {
                formats += MediaFormat(
                    formatId = "hls",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                break
            }
        }
        val poster = qs["poster"]?.firstOrNull()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.searchRegex(
                "<title[^>]*>(.*?)</title>",
                webpage,
                setOf(RegexOption.DOT_MATCHES_ALL),
            )?.trim(),
            thumbnails = listOfNotNull(poster?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "senate.gov:isvp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SenateISVP"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?senate\\.gov/isvp/?\\?(?<qs>.+)",
        )
    }
}

/** Upstream `SenateGovIE`: a committee subdomain page carrying an ISVP embed. */
class SenateGovIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val webpage = http.downloadWebpage(url)
        val embedUrl = Regex(
            "<iframe[^>]+src=['\"](?<url>https?://www\\.senate\\.gov/isvp/?\\?[^'\"]+)['\"]",
        ).find(webpage)?.groups?.get("url")?.value
            ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `url_transparent`: the ISVP info plus this page's metadata.
        val isvp = SenateISVPIE(http).extract(embedUrl)
        val title = listOfNotNull(
            ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            ExtractorUtils.searchRegex(
                "(?s)<title>([^<]*?)</title>",
                webpage,
                setOf(RegexOption.DOT_MATCHES_ALL),
            ),
        ).firstOrNull()?.split('|')?.firstOrNull()?.replace(Regex("\\s+"), " ")?.trim()
        return isvp.copy(
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
            ).ifEmpty { isvp.thumbnails },
            ageLimit = rtaSearch(webpage),
            webpageUrl = url,
            extractor = "senate.gov",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SenateGov"

        private val SUBDOMAINS = listOf(
            "agriculture", "aging", "appropriations", "armed-services", "banking",
            "budget", "commerce", "energy", "epw", "finance", "foreign", "help",
            "intelligence", "inaugural", "judiciary", "rules", "sbc", "veterans",
        )

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:" + SUBDOMAINS.joinToString("|") { Regex.escape(it) } + ")\\.senate\\.gov",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `urllib.parse.parse_qs` (blank values dropped) for one query. */
private fun parseFormQuery(query: String): Map<String, List<String>> {
    val out = linkedMapOf<String, MutableList<String>>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val key = formDecode(pair.substringBefore('='))
        if (key.isEmpty()) continue
        if (!pair.contains('=')) continue
        val value = formDecode(pair.substringAfter('='))
        if (value.isEmpty()) continue
        out.getOrPut(key) { mutableListOf() } += value
    }
    return out
}

/** `unquote_plus`: `+` is a space and `%XX` bytes are decoded as UTF-8. */
private fun formDecode(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                bytes += code.toByte()
                index += 3
                continue
            }
        }
        if (character == '+') {
            bytes += ' '.code.toByte()
        } else {
            bytes += character.toString().encodeToByteArray().toList()
        }
        index++
    }
    return bytes.toByteArray().decodeToString()
}

/** Upstream `_rta_search`: the RTA meta/label and 2257 markers. */
private fun rtaSearch(webpage: String): Int? {
    if (
        Regex(
            "(?i)<meta\\s+name=\"rating\"\\s+content=\"RTA-5042-1996-1400-1577-RTA\"",
        ).containsMatchIn(webpage)
    ) {
        return 18
    }
    var ageLimit: Int? = null
    if (
        webpage.contains(
            "Proudly Labeled <a href=\"http://www.rtalabel.org/\" " +
                "title=\"Restricted to Adults\">RTA</a>",
        )
    ) {
        ageLimit = maxOf(ageLimit ?: 0, 18)
    }
    Regex(">[^<]*you acknowledge you are at least (\\d+) years old", RegexOption.IGNORE_CASE)
        .find(webpage)?.let {
            ageLimit = maxOf(ageLimit ?: 0, it.groupValues[1].toIntOrNull() ?: 18)
        }
    Regex(">\\s*(?:18\\s+U(?:\\.S\\.C\\.|SC)\\s+)?(?:§+\\s*)?2257\\b")
        .find(webpage)?.let {
            ageLimit = maxOf(ageLimit ?: 0, 18)
        }
    return ageLimit
}
