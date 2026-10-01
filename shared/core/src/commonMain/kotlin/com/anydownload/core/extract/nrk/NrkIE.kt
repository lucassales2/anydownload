/*
 * NRK extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `nrk.py` from
 * `yt_dlp/extractor/nrk.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nrk.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the psapi playback manifest and metadata, the asset/format walk
 * (HLS and MP3, encrypted assets skipped), subtitles, thumbnails, the
 * playability/geo errors, the catalog season and series listings with their
 * embedded pagination, the `nrk:` re-dispatch classes, the page playlist
 * scans, and the NRK Skole media lookup. HLS masters are recorded as
 * `m3u8_native` and parsed at download time, so the Akamai variant walk and
 * the CDN-replacement retry are not translated; `alt_title`, `series`,
 * `season_id`, `season_number`, `episode`, and `episode_number` are not
 * modeled on the port's InfoDict. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.nrk

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `NRKBaseIE`: the psapi call and the asset format walk. */
abstract class NRKBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_extract_nrk_formats`; one `m3u8_native` record per asset. */
    protected fun extractNrkFormats(assetUrl: String, videoId: String): List<MediaFormat> {
        var url = assetUrl
            .replace(Regex("(?:bw_(?:low|high)=\\d+|no_audio_only)&?"), "")
        url = removeQueryParam(url, "adap")
        url = setQueryParam(url, "s", "0")
        return listOf(
            MediaFormat(
                formatId = "hls",
                url = url,
                ext = "mp4",
                protocol = "m3u8_native",
            ),
        )
    }

    /** Upstream `_call_api`. */
    protected suspend fun callApi(
        path: String,
        videoId: String,
        item: String? = null,
        fatal: Boolean = true,
        query: Map<String, String> = emptyMap(),
    ): JsonObject? {
        val base = "https://psapi.nrk.no/"
        val full = if (path.startsWith("http")) path else base.trimEnd('/') + "/" + path.trimStart('/')
        val queryString = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.entries.joinToString("&") { (key, value) -> "$key=$value" }
        }
        val json = try {
            http.downloadJson(
                full + queryString,
                headers = mapOf(
                    "accept" to "application/vnd.nrk.psapi+json; version=9; player=tv-player; device=player-core",
                ),
            )
        } catch (error: ExtractionError) {
            if (fatal) throw error else return null
        }
        return json as? JsonObject
            ?: if (fatal) throw ExtractionError.Malformed("The NRK API returned no object.") else null
    }

    /** Upstream `_raise_error` for a non-playable manifest. */
    protected fun raiseError(data: JsonObject): Nothing {
        val messageType = data.str("messageType") ?: ""
        if ("IsGeoBlocked" in messageType || data.obj("usageRights")?.bool("isGeoBlocked") == true) {
            throw ExtractionError.GeoRestricted(listOf("NO"))
        }
        val message = data.str("endUserMessage") ?: MESSAGES[messageType] ?: messageType
        throw ExtractionError.Unavailable("NRK said: $message")
    }

    companion object {
        private val MESSAGES = mapOf(
            "ProgramRightsAreNotReady" to "Du kan dessverre ikke se eller høre programmet",
            "ProgramRightsHasExpired" to "Programmet har gått ut",
            "NoProgramRights" to "Ikke tilgjengelig",
            "ProgramIsGeoBlocked" to "NRK har ikke rettigheter til å vise dette programmet utenfor Norge",
        )
    }
}

/** Upstream `NRKIE`: the psapi playback and metadata. */
class NRKIE(
    http: ExtractorHttp,
) : NRKBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        var videoId = matchId(url)?.substringAfterLast('/') ?: throw ExtractionError.UnsupportedUrl()

        suspend fun callPlaybackApi(item: String): JsonObject {
            val first = runCatching {
                callApi("playback/$item/program/$videoId", videoId, item, query = mapOf("preferredCdn" to "akamai"))
            }.getOrNull()
            if (first != null) return first
            return callApi("playback/$item/$videoId", videoId, item)
                ?: throw ExtractionError.Unavailable("The NRK $item API is unavailable.")
        }

        val manifest = callPlaybackApi("manifest")
        videoId = manifest.str("id") ?: videoId
        if (manifest.str("playability") == "nonPlayable") {
            manifest.obj("nonPlayable")?.let { raiseError(it) }
            throw ExtractionError.Unavailable("NRK said: not playable.")
        }
        val playable = manifest.obj("playable")
            ?: throw ExtractionError.Malformed("The NRK manifest had no playable object.")

        val formats = mutableListOf<MediaFormat>()
        for (element in playable.array("assets").orEmpty()) {
            val asset = element as? JsonObject ?: continue
            if (asset.bool("encrypted") == true) continue
            val formatUrl = ExtractorUtils.urlOrNone(asset.str("url")) ?: continue
            val assetFormat = (asset.str("format") ?: "").lowercase()
            if (assetFormat == "hls" || ExtractorUtils.determineExt(formatUrl, defaultExt = "") == "m3u8") {
                formats += extractNrkFormats(formatUrl, videoId)
            } else if (assetFormat == "mp3") {
                formats += MediaFormat(
                    formatId = "mp3",
                    url = formatUrl,
                    ext = "mp3",
                    vcodec = MediaFormat.CODEC_NONE,
                )
            }
        }

        val data = callPlaybackApi("metadata")
        val preplay = data.obj("preplay")
        val titles = preplay?.obj("titles")
        var title = titles?.str("title")
        val altTitle = titles?.str("subtitle")
        val description = preplay?.str("description")?.replace("\r", "\n")
        val duration = ExtractorUtils.parseDuration(playable.str("duration"))
            ?: ExtractorUtils.parseDuration(data.str("duration"))

        val thumbnails = preplay?.obj("poster")?.array("images").orEmpty().mapNotNull { element ->
            val image = element as? JsonObject ?: return@mapNotNull null
            val imageUrl = ExtractorUtils.urlOrNone(image.str("url")) ?: return@mapNotNull null
            Thumbnail(
                url = imageUrl,
                width = image.number("pixelWidth")?.toLong(),
                height = image.number("pixelHeight")?.toLong(),
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in playable.array("subtitles").orEmpty()) {
            val sub = element as? JsonObject ?: continue
            val subUrl = ExtractorUtils.urlOrNone(sub.str("webVtt")) ?: continue
            var subKey = sub.str("language") ?: "nb"
            sub.str("type")?.let { subKey += "-$it" }
            subtitles += SubtitleTrack(
                language = subKey,
                formats = listOf(SubtitleFormat(ext = "vtt", url = subUrl)),
            )
        }

        val legalAge = data.obj("legalAge")?.obj("body")?.obj("rating")?.str("code")
        val ageLimit = when {
            legalAge == "A" -> 0
            legalAge != null && legalAge.all { it.isDigit() } -> legalAge.toIntOrNull()
            else -> null
        }

        val isSeries = data.obj("_links")?.obj("series")?.str("name") == "series"
        if (isSeries) {
            val programs = callApi("programs/$videoId", videoId, "programs", fatal = false)
            if (altTitle != null && title != null) title = "$title - $altTitle"
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            duration = duration,
            ageLimit = ageLimit,
            uploadDate = manifest.obj("availability")?.obj("onDemand")?.str("from")
                ?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = thumbnails,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRK"

        val VALID_URL: Regex = Regex(
            "(?:nrk:|https?://(?:(?:www\\.)?nrk\\.no/video/(?:PS\\*|[^_]+_)|" +
                "v8[-.]psapi\\.nrk\\.no/mediaelement/))(?<id>[^?\\#&]+)",
        )
    }
}

/** Upstream `NRKTVIE`: re-dispatches the tv/radio pages to `nrk:<id>`. */
open class NRKTVIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = videoId,
            webpageUrl = url,
            redirectUrl = "nrk:$videoId",
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRKTV"

        val EPISODE_RE: String = "[a-zA-Z]{4}\\d{8}"

        val VALID_URL: Regex = Regex(
            "https?://(?:tv|radio)\\.nrk(?:super)?\\.no/(?:[^/]+/)*(?<id>$EPISODE_RE)",
        )
    }
}

/** Upstream `NRKTVEpisodeIE`: the season/episode URLs. */
class NRKTVEpisodeIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = runCatching { http.followRedirects(url) }.getOrDefault(url)
        val nrkId: String?
        if (NRKTVIE.VALID_URL.containsMatchIn(finalUrl)) {
            nrkId = NRKTVIE.VALID_URL.find(finalUrl)?.groups?.get("id")?.value
        } else {
            val webpage = http.downloadWebpage(url)
            val pageData = extractJsonElement(webpage, Regex("<script\\b[^>]+\\bid=\"pageData\"[^>]*>"))
                as? JsonObject
            nrkId = pageData?.obj("initialState")?.str("selectedEpisodePrfId")
            if (nrkId == null || !Regex(NRKTVIE.EPISODE_RE).matches(nrkId)) {
                throw ExtractionError.Malformed("Unable to extract the NRK ID.")
            }
        }
        return InfoDict(
            id = nrkId,
            webpageUrl = url,
            redirectUrl = "nrk:$nrkId",
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRKTVEpisode"

        val VALID_URL: Regex = Regex(
            "https?://tv\\.nrk\\.no/serie/(?<id>[^/?#]+/sesong/(?<seasonNumber>\\d+)/episode/" +
                "(?<episodeNumber>\\d+))",
        )
    }
}

/** Upstream `NRKTVSerieBaseIE`: the catalog listing helper. */
abstract class NRKTVSerieBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : NRKBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_extract_entries`. */
    protected fun extractEntries(entryList: JsonArray?): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        for (element in entryList.orEmpty()) {
            val episode = element as? JsonObject ?: continue
            val nrkId = episode.str("prfId") ?: episode.str("episodeId") ?: continue
            entries += InfoEntry(id = nrkId, url = "https://tv.nrk.no/program/$nrkId")
        }
        return entries
    }

    /** Upstream `_entries` with the embedded pagination. */
    protected suspend fun listEntries(data: JsonObject, displayId: String): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        var current = data
        var page = 1
        while (true) {
            val embedded = current.obj("_embedded") ?: current
            val assetsKey = ASSETS_KEYS.firstOrNull { embedded[it] != null } ?: break
            val assetNode = embedded[assetsKey] as? JsonObject
            val assets = (assetNode?.obj("_embedded")?.array(assetsKey) ?: embedded.array(assetsKey))
            entries += extractEntries(assets)
            val nextUrl = current.obj("_links")?.obj("next")?.str("href")
                ?: assetNode?.obj("_links")?.obj("next")?.str("href")
                ?: break
            current = callApi(nextUrl, displayId, "Downloading $assetsKey JSON page $page", fatal = false)
                ?: break
            page++
        }
        return entries
    }

    companion object {
        private val ASSETS_KEYS = listOf("episodes", "instalments")

        /** Upstream `_catalog_name`. */
        internal fun catalogName(serieKind: String): String =
            if (serieKind == "podcast" || serieKind == "podkast") "podcast" else "series"
    }
}

/** Upstream `NRKTVSeasonIE`: one catalog season. */
class NRKTVSeasonIE(
    http: ExtractorHttp,
) : NRKTVSerieBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override fun suitable(url: String): Boolean =
        !NRKTVIE.VALID_URL.containsMatchIn(url) &&
            !NRKTVEpisodeIE.VALID_URL.containsMatchIn(url) &&
            !NRKRadioPodkastIE.VALID_URL.containsMatchIn(url) &&
            super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val domain = match.groups["domain"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val serieKind = match.groups["serieKind"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val serie = match.groups["serie"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val displayId = "$serie/$seasonId"
        val data = callApi(
            "$domain/catalog/${NRKTVSerieBaseIE.catalogName(serieKind)}/$serie/seasons/$seasonId",
            displayId,
            "season",
            query = mapOf("pageSize" to "50"),
        ) ?: throw ExtractionError.Unavailable("The NRK season API is unavailable.")
        return InfoDict(
            id = displayId,
            title = data.obj("titles")?.str("title") ?: displayId,
            entries = listEntries(data, displayId),
            webpageUrl = url,
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRKTVSeason"

        val VALID_URL: Regex = Regex(
            "https?://(?<domain>tv|radio)\\.nrk\\.no/(?<serieKind>serie|pod[ck]ast)/" +
                "(?<serie>[^/]+)/(?:(?:sesong/)?(?<id>\\d+)|sesong/(?<id2>[^/?#&]+))",
        )
    }
}

/** Upstream `NRKTVSeriesIE`: one catalog series or podcast. */
class NRKTVSeriesIE(
    http: ExtractorHttp,
) : NRKTVSerieBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override fun suitable(url: String): Boolean =
        !NRKTVIE.VALID_URL.containsMatchIn(url) &&
            !NRKTVEpisodeIE.VALID_URL.containsMatchIn(url) &&
            !NRKRadioPodkastIE.VALID_URL.containsMatchIn(url) &&
            !NRKTVSeasonIE.VALID_URL.containsMatchIn(url) &&
            super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val site = match.groups["domain"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val serieKind = match.groups["serieKind"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seriesId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val isRadio = site == "radio.nrk"
        val domain = if (isRadio) "radio" else "tv"
        val sizePrefix = if (isRadio) "p" else "embeddedInstalmentsP"
        val series = callApi(
            "$domain/catalog/${NRKTVSerieBaseIE.catalogName(serieKind)}/$seriesId",
            seriesId,
            "serie",
            query = mapOf("${sizePrefix}ageSize" to "50"),
        ) ?: throw ExtractionError.Unavailable("The NRK series API is unavailable.")
        val titles = series.obj("titles")
            ?: series.str("type")?.let { series.obj(it)?.obj("titles") }
            ?: series.str("seriesType")?.let { series.obj(it)?.obj("titles") }

        val entries = mutableListOf<InfoEntry>()
        entries += listEntries(series, seriesId)
        val embedded = series.obj("_embedded") ?: JsonObject(emptyMap())
        val linkedSeasons = series.obj("_links")?.array("seasons").orEmpty()
        val embeddedSeasons = embedded.array("seasons").orEmpty()
        if (linkedSeasons.size > embeddedSeasons.size) {
            for (season in linkedSeasons) {
                val seasonObject = season as? JsonObject ?: continue
                val seasonUrl = seasonObject.str("href")
                    ?: seasonObject.str("name")?.let { name ->
                        "https://$domain.nrk.no/serie/$seriesId/sesong/$name"
                    }
                if (seasonUrl != null) entries += InfoEntry(url = seasonUrl)
            }
        } else {
            for (season in embeddedSeasons) {
                entries += listEntries(season as? JsonObject ?: continue, seriesId)
            }
        }
        entries += listEntries(embedded.obj("extraMaterial") ?: JsonObject(emptyMap()), seriesId)

        return InfoDict(
            id = seriesId,
            title = titles?.str("title"),
            description = titles?.str("subtitle"),
            entries = entries,
            webpageUrl = url,
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRKTVSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?<domain>(?:tv|radio)\\.nrk|(?:tv\\.)?nrksuper)\\.no/" +
                "(?<serieKind>serie|pod[ck]ast)/(?<id>[^/]+)",
        )
    }
}

/** Upstream `NRKTVDirekteIE`: the direkte pages. */
class NRKTVDirekteIE(
    http: ExtractorHttp,
) : NRKTVIE(http) {
    override fun suitable(url: String): Boolean = VALID_URL.containsMatchIn(url)

    override fun matchId(url: String): String? = VALID_URL.find(url)?.groups?.get("id")?.value

    companion object {
        const val IE_KEY: String = "NRKTVDirekte"

        val VALID_URL: Regex = Regex("https?://(?:tv|radio)\\.nrk\\.no/direkte/(?<id>[^/?#&]+)")
    }
}

/** Upstream `NRKRadioPodkastIE`: the podcast episode URLs. */
class NRKRadioPodkastIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = videoId,
            webpageUrl = url,
            redirectUrl = "nrk:$videoId",
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRKRadioPodkast"

        val VALID_URL: Regex = Regex(
            "https?://radio\\.nrk\\.no/pod[ck]ast/(?:[^/]+/)+(?<id>l_[\\da-f]{8}-[\\da-f]{4}-" +
                "[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12})",
        )
    }
}

/** Upstream `NRKPlaylistBaseIE`: the page item scans. */
abstract class NRKPlaylistBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_ITEM_RE`. */
    protected abstract val itemRegex: Regex

    protected abstract fun extractTitle(webpage: String): String?

    protected open fun extractDescription(webpage: String): String? = null

    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = itemRegex.findAll(webpage)
            .map { match -> match.groupValues[1] }
            .distinct()
            .map { videoId -> InfoEntry(id = videoId, url = "https://tv.nrk.no/program/$videoId") }
            .toList()
        return InfoDict(
            id = playlistId,
            title = extractTitle(webpage),
            description = extractDescription(webpage),
            entries = entries,
            webpageUrl = url,
            extractor = "nrk",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `NRKPlaylistIE`: the nrk.no article playlists. */
class NRKPlaylistIE(
    http: ExtractorHttp,
) : NRKPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val itemRegex: Regex =
        Regex("class=\"[^\"]*\\brich\\b[^\"]*\"[^>]+data-video-id=\"([^\"]+)\"")

    override fun extractTitle(webpage: String): String? =
        ExtractorUtils.htmlSearchMeta(webpage, "og:title")

    override fun extractDescription(webpage: String): String? =
        ExtractorUtils.htmlSearchMeta(webpage, "og:description")

    companion object {
        const val IE_KEY: String = "NRKPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?nrk\\.no/(?!video|skole)(?:[^/]+/)+(?<id>[^/]+)",
        )
    }
}

/** Upstream `NRKTVEpisodesIE`: the tv.nrk.no program episode lists. */
class NRKTVEpisodesIE(
    http: ExtractorHttp,
) : NRKPlaylistBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val itemRegex: Regex =
        Regex("data-episode=[\"'](?<id>${NRKTVIE.EPISODE_RE})")

    override fun extractTitle(webpage: String): String? =
        ExtractorUtils.searchRegex("<h1>([^<]+)</h1>", webpage, default = null)

    companion object {
        const val IE_KEY: String = "NRKTVEpisodes"

        val VALID_URL: Regex = Regex(
            "https?://tv\\.nrk\\.no/program/[Ee]pisodes/[^/]+/(?<id>\\d+)",
        )
    }
}

/** Upstream `NRKSkoleIE`: the NRK Skole media lookup. */
class NRKSkoleIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val json = http.downloadJson("https://nrkno-skole-prod.kube.nrk.no/skole/api/media/$videoId")
            as? JsonObject ?: throw ExtractionError.Malformed("The NRK Skole API returned no object.")
        val nrkId = json.str("psId")
            ?: throw ExtractionError.Malformed("The NRK Skole response had no psId.")
        return InfoDict(
            id = nrkId,
            webpageUrl = url,
            redirectUrl = "nrk:$nrkId",
            extractor = "nrk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NRKSkole"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?nrk\\.no/skole/?\\?.*\\bmediaId=(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_search_json` subset: balanced JSON after [marker]. */
private fun extractJsonElement(html: String, marker: Regex): kotlinx.serialization.json.JsonElement? {
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    val opening = html.getOrNull(index) ?: return null
    if (opening != '{' && opening != '[') return null
    var depth = 0
    var inString = false
    var quote = ' '
    var escaped = false
    for (position in index until html.length) {
        val character = html[position]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> inString = false
            }
            continue
        }
        when (character) {
            '"', '\'' -> {
                inString = true
                quote = character
            }

            '{', '[' -> depth++
            '}', ']' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(html.substring(index, position + 1))
                }
            }
        }
    }
    return null
}

private fun removeQueryParam(url: String, key: String): String {
    val hash = url.indexOf('#').let { if (it >= 0) url.substring(it) else "" }
    val withoutHash = if (hash.isEmpty()) url else url.substring(0, url.length - hash.length)
    val question = withoutHash.indexOf('?')
    if (question < 0) return url
    val base = withoutHash.substring(0, question)
    val query = withoutHash.substring(question + 1)
    val params = query.split('&').filter { it.isNotEmpty() && it.substringBefore('=') != key }
    return if (params.isEmpty()) base + hash else base + "?" + params.joinToString("&") + hash
}

private fun setQueryParam(url: String, key: String, value: String): String {
    val hash = url.indexOf('#').let { if (it >= 0) url.substring(it) else "" }
    val withoutHash = if (hash.isEmpty()) url else url.substring(0, url.length - hash.length)
    val question = withoutHash.indexOf('?')
    val base = if (question >= 0) withoutHash.substring(0, question) else withoutHash
    val query = if (question >= 0) withoutHash.substring(question + 1) else ""
    val params = query.split('&').filter { it.isNotEmpty() && it.substringBefore('=') != key }.toMutableList()
    params += "$key=$value"
    return base + "?" + params.joinToString("&") + hash
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
