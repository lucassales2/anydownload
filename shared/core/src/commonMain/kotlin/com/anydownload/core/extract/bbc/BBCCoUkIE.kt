/*
 * BBC extractors — AnyDownload
 *
 * Kotlin translation of the programme/iPlayer subset of `BBCCoUkIE` from
 * `yt_dlp/extractor/bbc.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `bbc.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `bbc.co.uk/programmes/<pid>` and
 * `bbc.co.uk/iplayer/[<channel>/](episode|playlist)/<pid>`. The page is read
 * for a `vpid` (`mediator.bind(...)` or the `"vpid"` field), then the public
 * media selector (`open.live.bbc.co.uk/mediaselector/6/.../mediaset/<set>/vpid/<id>`)
 * is called for the `iptv-all` and `pc` sets. Direct HTTP media, HLS, and DASH
 * connections, plus English TTML captions, map onto the info dict; HDS, RTMP,
 * and ASX connections and the legacy playlist fallback are not translated.
 * iPlayer playback is geo- and licence-restricted: `geolocation` /
 * `notukerror` fail typed as [ExtractionError.GeoRestricted] when no other
 * media set supplies a format. The `bbc.com` scraper (`BBCIE`), the article
 * page, and the iPlayer playlist classes are planned.
 *
 * No cookie, account, bearer token, or signed media URL is stored or
 * committed; fixture hosts are `*.example`.
 */
package com.anydownload.core.extract.bbc

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Upstream `BBCCoUkIE` subset: a BBC programme or iPlayer episode page.
 *
 * The media selector is the public endpoint the player itself calls. A
 * geo-blocked response is a typed [ExtractionError.GeoRestricted] after both
 * media sets are tried. The programme playlist fallback
 * (`/programmes/<id>/playlist.json`) is not translated, so a page without a
 * `vpid` fails typed.
 */
class BBCCoUkIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "BBC iPlayer"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val groupId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val webpage = http.downloadWebpage(url)
        PAGE_ERROR.find(webpage)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            throw ExtractionError.Unavailable(it)
        }

        var programmeId: String? = null
        var duration: Double? = null
        MEDIATOR.find(webpage)?.let { mediator ->
            val player = (ExtractorUtils.parseJson(mediator.groupValues[1]) as? JsonObject)?.obj("player")
            programmeId = player?.str("vpid")
            duration = player?.number("duration")
        }
        if (programmeId == null) programmeId = VPID.find(webpage)?.groupValues?.get(1)
        if (programmeId == null) {
            throw ExtractionError.Unavailable(
                "This BBC page did not expose a playable version; the programme playlist fallback is not translated.",
            )
        }

        val (formats, subtitles) = downloadMediaSelector(programmeId)
        if (formats.isEmpty()) throw ExtractionError.NoFormats("The BBC media selector returned no format.")

        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title", "twitter:title")
            ?: ExtractorUtils.searchRegex("<h1[^>]*>(.+?)</h1>", webpage, setOf(RegexOption.DOT_MATCHES_ALL))
                ?.let(::stripTags)
            ?: groupId
        val description = ExtractorUtils.htmlSearchMeta(webpage, "description", "og:description", "twitter:description")
        val thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image", "twitter:image")

        return InfoDict(
            id = programmeId,
            title = title,
            duration = duration,
            description = description,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "bbc",
            extractorKey = IE_KEY,
        )
    }

    /**
     * Upstream `_download_media_selector`: try `iptv-all` then `pc`; keep the
     * last geo/selection error and raise it only when nothing was produced.
     */
    private suspend fun downloadMediaSelector(programmeId: String): Pair<List<MediaFormat>, List<SubtitleTrack>> {
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        var lastError: String? = null
        for (mediaSet in MEDIA_SETS) {
            val json = try {
                http.downloadJson(MEDIA_SELECTOR_PREFIX + mediaSet + "/vpid/" + programmeId) as? JsonObject
            } catch (_: ExtractionError) {
                null
            } ?: continue
            val result = json.str("result")
            when {
                result == null -> {
                    val (setFormats, setSubtitles) = processMediaSelector(json)
                    formats += setFormats
                    subtitles += setSubtitles
                }

                result in GEO_ERRORS || result == "selectionunavailable" -> lastError = result
                else -> throw ExtractionError.Unavailable("The BBC media selector returned an error.")
            }
        }
        if (formats.isEmpty() && lastError != null) {
            throw if (lastError in GEO_ERRORS) {
                ExtractionError.GeoRestricted()
            } else {
                ExtractionError.Unavailable("The BBC media selection is unavailable.")
            }
        }
        return formats to subtitles.distinctBy { track -> track.formats.firstOrNull()?.url }
    }

    /** Upstream `_process_media_selector` for the connection kinds the port keeps. */
    private fun processMediaSelector(json: JsonObject): Pair<List<MediaFormat>, List<SubtitleTrack>> {
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        val seen = mutableSetOf<String>()

        for (element in json.array("media").orEmpty()) {
            val media = element as? JsonObject ?: continue
            when (val kind = media.str("kind")) {
                "captions" -> {
                    val href = media.array("connection").orEmpty()
                        .firstNotNullOfOrNull { (it as? JsonObject)?.str("href") }
                    if (href != null && subtitles.none { it.formats.firstOrNull()?.url == href }) {
                        subtitles += SubtitleTrack(
                            language = "en",
                            formats = listOf(SubtitleFormat(ext = "ttml", url = href)),
                        )
                    }
                }

                "video", "audio" -> {
                    val bitrate = media.number("bitrate")
                    val encoding = media.str("encoding")
                    val width = media.number("width")?.toLong()
                    val height = media.number("height")?.toLong()
                    val fileSize = media.number("media_file_size")?.toLong()
                    for (connectionElement in media.array("connection").orEmpty()) {
                        val connection = connectionElement as? JsonObject ?: continue
                        val href = connection.str("href") ?: continue
                        if (!seen.add(href)) continue
                        val supplier = connection.str("supplier")
                        val connKind = connection.str("kind")
                        val protocol = connection.str("protocol")
                        val transferFormat = connection.str("transferFormat")
                        if (supplier == "asx") continue // ASX playlists are not translated.
                        var formatId = supplier ?: connKind ?: protocol ?: "bbc"
                        when (transferFormat) {
                            "dash" -> formats += MediaFormat(
                                formatId = formatId,
                                url = href,
                                ext = "mp4",
                                protocol = "http_dash_segments",
                                formatNote = "DASH",
                            )

                            "hls" -> formats += MediaFormat(
                                formatId = formatId,
                                url = href,
                                ext = "mp4",
                                protocol = "m3u8_native",
                                formatNote = "HLS",
                            )

                            "hds" -> Unit // HDS is not translated.
                            else -> if (protocol == "http" || protocol == "https") {
                                if (supplier == null && bitrate != null) {
                                    formatId = "$formatId-${bitrate.toLong()}"
                                }
                                formats += if (kind == "video") {
                                    MediaFormat(
                                        formatId = formatId,
                                        url = href,
                                        filesize = fileSize,
                                        width = width,
                                        height = height,
                                        tbr = bitrate,
                                        vcodec = encoding,
                                    )
                                } else {
                                    MediaFormat(
                                        formatId = formatId,
                                        url = href,
                                        filesize = fileSize,
                                        abr = bitrate,
                                        acodec = encoding,
                                        vcodec = MediaFormat.CODEC_NONE,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return formats to subtitles
    }

    companion object {
        const val IE_KEY: String = "BBCCoUk"

        private const val ID_REGEX = "(?:[pbml][\\da-z]{7}|w[\\da-z]{7,14})"

        /** Upstream `_VALID_URL` programme and iPlayer branches. */
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bbc\\.co\\.uk/" +
                "(?:programmes/(?!articles/)|iplayer(?:/[^/]+)?/(?:episode/|playlist/))" +
                "(?<id>$ID_REGEX)(?!/(?:episodes|broadcasts|clips))",
        )

        private val MEDIA_SETS = listOf("iptv-all", "pc")
        private const val MEDIA_SELECTOR_PREFIX =
            "https://open.live.bbc.co.uk/mediaselector/6/select/version/2.0/mediaset/"
        private val GEO_ERRORS = setOf("notukerror", "geolocation")
    }
}

private val PAGE_ERROR = Regex(
    "<div\\b[^>]+\\bclass=[\"'](?:smp|playout)__message delta[\"'][^>]*>\\s*([^<]+?)\\s*<",
)

private val MEDIATOR = Regex(
    "mediator\\.bind\\((\\{.+?\\})\\s*,\\s*document\\.getElementById",
    RegexOption.DOT_MATCHES_ALL,
)

private val VPID = Regex("\"vpid\"\\s*:\\s*\"((?:[pbml][\\da-z]{7}|w[\\da-z]{7,14}))\"")

private fun stripTags(value: String): String {
    val withoutTags = Regex("<[^>]*>").replace(value, " ")
    return (ExtractorUtils.unescapeHtml(withoutTags) ?: withoutTags)
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
