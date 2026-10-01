/*
 * NHK extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `nhk.py` from
 * `yt_dlp/extractor/nhk.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nhk.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the NHK World shows API and episode mapping, the program listings,
 * the three NHK-for-School classes (bangumi variables/chapters, subject
 * portals, program lists), the Radiru on-demand series and news APIs, the
 * radio news redirect, and the Radiru live station config/NOA JSON. HLS
 * audio/video manifests are recorded as `m3u8_native` and parsed at download
 * time. The Radiru extended-metadata formatting (act/music lists, the XML
 * config detail URL) is not translated, so episodes keep the fallback
 * title/description/timestamps; the live area argument defaults to Tokyo
 * because the port has no extractor args; `series`, `episode`, `categories`,
 * `tags`, and `cast` are not modeled on the port's InfoDict. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.nhk

import com.anydownload.core.extract.Chapter
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

/** Upstream `NhkBaseIE`: the NHK World shows API and episode mapping. */
abstract class NhkBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_api`. */
    protected suspend fun callApi(
        mId: String,
        lang: String,
        isVideo: Boolean,
        isEpisode: Boolean,
        isClip: Boolean,
    ): JsonObject {
        val contentFormat = if (isVideo) "video" else "audio"
        val contentType = if (isClip) "clips" else "episodes"
        val extraPage: String
        val pageType: String
        if (!isEpisode) {
            extraPage = "/${contentFormat}_$contentType"
            pageType = "programs"
        } else {
            extraPage = ""
            pageType = contentType
        }
        val url = "https://api.nhkworld.jp/showsapi/v1/$lang/${contentFormat}_${pageType}/$mId$extraPage"
        return http.downloadJson(url) as? JsonObject
            ?: throw ExtractionError.Malformed("The NHK World API returned no object.")
    }

    /** Upstream `_extract_episode_info`. */
    protected suspend fun extractEpisodeInfo(url: String, episode: JsonObject?): InfoDict {
        val match = matchVod(url) ?: throw ExtractionError.UnsupportedUrl()
        val lang = match.groups["lang"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val mType = match.groups["type"]?.value
        val episodeId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val isVideo = mType != "audio"
        val data = episode ?: callApi(
            episodeId,
            lang,
            isVideo,
            isEpisode = true,
            isClip = episodeId.startsWith("9999"),
        )

        val videoId = joinNonEmpty(data.str("id"), data.str("lang"))
            ?: joinNonEmpty(episodeId, lang) ?: episodeId
        val rawTitle = data.str("title")
        val series = data.obj("video_program")?.str("title") ?: data.obj("audio_program")?.str("title")
        val title: String?
        if (series != null && rawTitle != null) {
            title = "$series - $rawTitle"
        } else {
            title = rawTitle ?: series
        }

        val streamInfo = data.obj("video") ?: data.obj("audio")
        val streamUrl = ExtractorUtils.urlOrNone(streamInfo?.str("url"))
        if (streamUrl == null) {
            throw ExtractionError.NoFormats("The NHK stream was not found; it has most likely expired.")
        }
        val formats = mutableListOf<MediaFormat>()
        var duration: Double? = null
        if (isVideo) {
            formats += MediaFormat(
                formatId = "hls",
                url = streamUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
            duration = streamInfo?.number("duration")
        } else {
            val audioPath = streamUrl.removeSuffix(".m4a")
            formats += MediaFormat(
                formatId = "hls",
                url = urlJoin("https://vod-stream.nhk.jp", audioPath) + "/index.m3u8",
                ext = "m4a",
                protocol = "m3u8_native",
                language = lang,
            )
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = data.str("description"),
            duration = duration,
            uploadDate = data.str("first_broadcasted_at")?.let(ExtractorUtils::unifiedStrdate)
                ?: streamInfo?.str("published_at")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = data.array("images").orEmpty().mapNotNull { element ->
                val image = element as? JsonObject ?: return@mapNotNull null
                val imageUrl = image.str("url") ?: return@mapNotNull null
                Thumbnail(
                    url = urlJoin(url, imageUrl),
                    width = image.number("width")?.toLong(),
                    height = image.number("height")?.toLong(),
                )
            },
            formats = formats,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = ieKey,
        )
    }

    companion object {
        /** Upstream `_BASE_URL_REGEX`. */
        const val BASE_URL_REGEX: String = "https?://www3\\.nhk\\.or\\.jp/nhkworld/(?<lang>[a-z]{2})/"

        /** Upstream `NhkVodIE._VALID_URL`. */
        val VOD_PATTERNS: List<Regex> = listOf(
            Regex(
                BASE_URL_REGEX + "shows/(?:(?<type>video)/)?(?<id>\\d{4}[\\da-z]\\d+)/?(?:$|[?#])",
            ),
            Regex(
                BASE_URL_REGEX + "(?:ondemand|shows)/(?<type>audio)/(?<id>[^/?#]+?-\\d{8}-[\\da-z]+)",
            ),
            Regex(BASE_URL_REGEX + "ondemand/(?<type>video)/(?<id>\\d{4}[\\da-z]\\d+)"),
        )

        private fun matchVod(url: String): MatchResult? =
            VOD_PATTERNS.firstNotNullOfOrNull { it.find(url) }

        internal fun vodSuitable(url: String): Boolean = VOD_PATTERNS.any { it.containsMatchIn(url) }
    }
}

/** Upstream `NhkVodIE`: the NHK World on-demand episode pages. */
class NhkVodIE(
    http: ExtractorHttp,
) : NhkBaseIE(
    ieKey = IE_KEY,
    http = http,
) {
    override fun suitable(url: String): Boolean = vodSuitable(url)

    override suspend fun extract(url: String): InfoDict = extractEpisodeInfo(url, null)

    companion object {
        const val IE_KEY: String = "NhkVod"
    }
}

/** Upstream `NhkVodProgramIE`: the NHK World program listing pages. */
class NhkVodProgramIE(
    http: ExtractorHttp,
) : NhkBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override fun suitable(url: String): Boolean = !vodSuitable(url) && super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val lang = match.groups["lang"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val mType = match.groups["type"]?.value
        val programId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val episodeType = match.groups["episodeType"]?.value
        val episodes = callApi(
            programId,
            lang,
            isVideo = mType != "audio",
            isEpisode = false,
            isClip = episodeType == "clip",
        )

        val entries = mutableListOf<InfoEntry>()
        for (element in episodes.array("items").orEmpty()) {
            val item = element as? JsonObject ?: continue
            val itemUrl = item.str("url") ?: continue
            val resolved = urlJoin(url, itemUrl)
            val info = extractEpisodeInfo(resolved, item)
            entries += InfoEntry(id = info.id, title = info.title, url = resolved)
        }

        val html = http.downloadWebpage(url)
        val title = metaFromClassElements(
            html,
            listOf(
                "p-programDetail__title",
                "pProgramHero__logoText",
                "tAudioProgramMain__title",
                "p-program-name",
            ),
        )
        val description = metaFromClassElements(
            html,
            listOf(
                "p-programDetail__text",
                "pProgramHero__description",
                "tAudioProgramMain__info",
                "p-program-description",
            ),
        )
        return InfoDict(
            id = programId,
            title = title,
            description = description,
            entries = entries,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NhkVodProgram"

        val VALID_URL: Regex = Regex(
            BASE_URL_REGEX + "(?:shows|tv)/(?:(?<type>audio)/programs/)?(?<id>\\w+)/?" +
                "(?:\\?(?:[^#]+&)?type=(?<episodeType>clip|(?:radio|tv)Episode))?",
        )
    }
}

/** Upstream `NhkForSchoolBangumiIE`: the school movie pages. */
class NhkForSchoolBangumiIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val programType = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(
            "https://www2.nhk.or.jp/school/movie/$programType.cgi?das_id=$videoId",
        )

        val baseValues = Regex("var\\s+([a-zA-Z_]+)\\s*=\\s*\"([^\"]+?)\";")
            .findAll(webpage)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val programValues = Regex("(?:program|clip)Obj\\.([a-zA-Z_]+)\\s*=\\s*([\"'])([^\"']+?)\\2;")
            .findAll(webpage)
            .associate { it.groupValues[1] to it.groupValues[3] }
        val chapterDurations = Regex("chapterTime\\.push\\('([0-9:]+?)'\\);")
            .findAll(webpage)
            .mapNotNull { ExtractorUtils.parseDuration(it.groupValues[1]) }
            .toList()
        val chapterTitles = Regex("<div class=\"cpTitle\"><span>(scene\\s*\\d+)?</span>([^<]+?)</div>")
            .findAll(webpage)
            .map { match ->
                listOf(match.groupValues[1], ExtractorUtils.unescapeHtml(match.groupValues[2]) ?: "")
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            }
            .toList()

        val version = baseValues["r_version"] ?: programValues["version"]
        if (version != null) {
            videoId = videoId.substringBefore('_') + "_" + version
        }
        val duration = ExtractorUtils.parseDuration(baseValues["r_duration"])

        var chapters: List<Chapter> = emptyList()
        if (chapterDurations.isNotEmpty() && chapterDurations.size == chapterTitles.size) {
            chapters = chapterDurations.mapIndexed { index, start ->
                Chapter(
                    startTime = start,
                    endTime = chapterDurations.getOrNull(index + 1) ?: duration,
                    title = chapterTitles[index],
                )
            }
        }

        return InfoDict(
            id = videoId,
            title = programValues["name"],
            duration = duration,
            uploadDate = baseValues["r_upload"]?.let(ExtractorUtils::unifiedStrdate),
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = "https://nhks-vh.akamaihd.net/i/das/${videoId.take(8)}/" +
                        "${videoId}_V_000.f4v/master.m3u8",
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            chapters = chapters,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NhkForSchoolBangumi"

        val VALID_URL: Regex = Regex(
            "https?://www2\\.nhk\\.or\\.jp/school/movie/(?<type>bangumi|clip)\\.cgi\\?" +
                "das_id=(?<id>[a-zA-Z0-9_-]+)",
        )
    }
}

/** Upstream `NhkForSchoolSubjectIE`: one school subject portal page. */
class NhkForSchoolSubjectIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val subjectId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = mutableListOf<InfoEntry>()
        for (match in Regex(
            "href=\"((?:https?://www\\.nhk\\.or\\.jp)?/school/${Regex.escape(subjectId)}/[^/]+/)\"",
        ).findAll(webpage)) {
            val entryUrl = urlJoin(url, match.groupValues[1])
            entries += InfoEntry(url = entryUrl)
        }
        val title = Regex("(?s)<span\\s+class=\"subjectName\">\\s*<img\\s*[^<]+>\\s*([^<]+?)</span>")
            .find(webpage)?.groupValues?.get(1)?.trim()
        return InfoDict(
            id = subjectId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NhkForSchoolSubject"

        val KNOWN_SUBJECTS = listOf(
            "rika", "syakai", "kokugo",
            "sansuu", "seikatsu", "doutoku",
            "ongaku", "taiiku", "zukou",
            "gijutsu", "katei", "sougou",
            "eigo", "tokkatsu",
            "tokushi", "sonota",
        )

        val VALID_URL: Regex = Regex(
            "https?://www\\.nhk\\.or\\.jp/school/(?<id>" +
                KNOWN_SUBJECTS.joinToString("|") { Regex.escape(it) } + ")/?(?:[?#].*)?$",
        )
    }
}

/** Upstream `NhkForSchoolProgramListIE`: one school program page. */
class NhkForSchoolProgramListIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val programId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage("https://www.nhk.or.jp/school/$programId/")
        var title = Regex("(?s)<title[^>]*>(.+?)</title>").find(webpage)
            ?.groupValues?.get(1)?.let(ExtractorUtils::unescapeHtml)?.trim()
        if (title.isNullOrEmpty()) {
            title = Regex("<h3>([^<]+?)とは？\\s*</h3>").find(webpage)?.groupValues?.get(1)
        }
        title = title?.replace(Regex("\\s*\\|\\s*NHK\\s+for\\s+School\\s*$"), "")
        val description = Regex("(?s)<div\\s+class=\"programDetail\\s*\">\\s*<p>[^<]+</p>")
            .find(webpage)?.value

        val bangumiList = http.downloadJson(
            "https://www.nhk.or.jp/school/$programId/meta/program.json",
        ) as? JsonObject
        val entries = mutableListOf<InfoEntry>()
        for (part in bangumiList?.array("part").orEmpty()) {
            val dasId = (part as? JsonObject)?.str("part-video-dasid") ?: continue
            entries += InfoEntry(
                id = dasId,
                url = "https://www2.nhk.or.jp/school/movie/bangumi.cgi?das_id=$dasId",
            )
        }
        return InfoDict(
            id = programId,
            title = title,
            description = description,
            entries = entries,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NhkForSchoolProgramList"

        val VALID_URL: Regex = Regex(
            "https?://www\\.nhk\\.or\\.jp/school/(?<id>(?:" +
                NhkForSchoolSubjectIE.KNOWN_SUBJECTS.joinToString("|") { Regex.escape(it) } +
                ")/[a-zA-Z0-9_-]+)",
        )
    }
}

/** Upstream `NhkRadiruIE`: the NHK radio on-demand series and news APIs. */
class NhkRadiruIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val siteId = match.groups["site"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val cornerId = match.groups["corner"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val headlineId = match.groups["headline"]?.value
        val programmeId = "${siteId}_$cornerId"

        if (siteId == NEWS_SITE_ID) {
            val meta = (http.downloadJson(
                "https://www.nhk.or.jp/s-media/news/news-site/list/v1/all.json",
            ) as? JsonObject)?.obj("main")
                ?: throw ExtractionError.Malformed("The NHK news API returned no main object.")
            val seriesMeta = mapOf(
                "title" to meta.str("program_name"),
                "channel" to meta.str("media_name"),
                "uploader" to meta.str("media_name"),
            )
            if (headlineId != null) {
                val headline = meta.array("detail_list").orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { it.str("headline_id") == headlineId }
                    ?: throw ExtractionError.Unavailable("The NHK content has most likely expired.")
                return newsInfo(headline, programmeId, seriesMeta, url)
            }
            val entries = meta.array("detail_list").orEmpty().mapNotNull { element ->
                val headline = element as? JsonObject ?: return@mapNotNull null
                val info = newsInfo(headline, programmeId, seriesMeta, url)
                InfoEntry(
                    id = info.id,
                    title = info.title,
                    url = "https://www.nhk.or.jp/radio/ondemand/detail.html?p=${programmeId}_${info.id.orEmpty().substringAfterLast('_')}",
                )
            }
            return InfoDict(
                id = programmeId,
                title = meta.str("program_name"),
                description = meta.str("site_detail"),
                channel = meta.str("media_name"),
                entries = entries,
                webpageUrl = url,
                extractor = "nhk",
                extractorKey = IE_KEY,
            )
        }

        val meta = http.downloadJson(
            "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series" +
                "?site_id=$siteId&corner_site_id=$cornerId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The NHK radio API returned no object.")
        val fallbackStation = joinNonEmpty("NHK", meta.str("radio_broadcast"))
        val seriesMeta = mapOf(
            "title" to joinNonEmpty(meta.str("title"), meta.str("corner_name"), delim = " "),
            "channel" to fallbackStation,
            "uploader" to fallbackStation,
            "thumbnail" to ExtractorUtils.urlOrNone(meta.str("thumbnail_url")),
        )
        if (headlineId != null) {
            val episode = meta.array("episodes").orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.number("id")?.toInt() == headlineId.toIntOrNull() }
                ?: throw ExtractionError.Unavailable("The NHK content has most likely expired.")
            return episodeInfo(episode, programmeId, seriesMeta, url)
        }
        val entries = meta.array("episodes").orEmpty().mapNotNull { element ->
            val episode = element as? JsonObject ?: return@mapNotNull null
            val info = episodeInfo(episode, programmeId, seriesMeta, url)
            InfoEntry(
                    id = info.id,
                    title = info.title,
                    url = "https://www.nhk.or.jp/radio/ondemand/detail.html?p=${programmeId}_${info.id.orEmpty().substringAfterLast('_')}",
                )
        }
        return InfoDict(
            id = programmeId,
            title = seriesMeta["title"],
            description = meta.str("series_description"),
            channel = fallbackStation,
            entries = entries,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_episode_info` without the extended-metadata call. */
    private fun episodeInfo(
        episode: JsonObject,
        programmeId: String,
        seriesMeta: Map<String, String?>,
        url: String,
    ): InfoDict {
        val episodeNumber = episode.number("id")?.toLong() ?: 0L
        val episodeId = "${programmeId}_$episodeNumber"
        val aaVinfo = episode.str("aa_contents_id")?.split(';').orEmpty()
        val times = aaVinfo.getOrNull(4)?.split('_').orEmpty()
        val streamUrl = ExtractorUtils.urlOrNone(episode.str("stream_url"))
        val formats = streamUrl?.let {
            listOf(MediaFormat(formatId = "hls", url = it, ext = "m4a", protocol = "m3u8_native"))
        }.orEmpty()
        return InfoDict(
            id = episodeId,
            title = episode.str("program_title") ?: seriesMeta["title"],
            description = episode.str("program_sub_title"),
            uploadDate = times.getOrNull(2)?.let(ExtractorUtils::unifiedStrdate)
                ?: times.getOrNull(0)?.let(ExtractorUtils::unifiedStrdate),
            channel = seriesMeta["channel"],
            uploader = seriesMeta["uploader"],
            thumbnails = seriesMeta["thumbnail"]?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            isLive = false,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_news_info`. */
    private fun newsInfo(
        headline: JsonObject,
        programmeId: String,
        seriesMeta: Map<String, String?>,
        url: String,
    ): InfoDict {
        val episodeId = "${programmeId}_${headline.str("headline_id")}"
        val file = headline.array("file_list")?.firstOrNull() as? JsonObject
        val streamUrl = ExtractorUtils.urlOrNone(file?.str("file_name"))
        val aaVinfo4 = file?.str("aa_vinfo4")
        return InfoDict(
            id = episodeId,
            title = file?.str("file_title"),
            description = file?.str("file_title_sub"),
            uploadDate = file?.str("open_time")?.let(ExtractorUtils::unifiedStrdate)
                ?: aaVinfo4?.substringBefore('_')?.let(ExtractorUtils::unifiedStrdate),
            channel = seriesMeta["channel"],
            uploader = seriesMeta["uploader"],
            thumbnails = (ExtractorUtils.urlOrNone(headline.str("headline_image"))
                ?: seriesMeta["thumbnail"])?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = streamUrl?.let {
                listOf(MediaFormat(formatId = "hls", url = it, ext = "m4a", protocol = "m3u8_native"))
            }.orEmpty(),
            isLive = false,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NhkRadiru"
        private const val NEWS_SITE_ID = "18439M2W42"

        val VALID_URL: Regex = Regex(
            "https?://www\\.nhk\\.or\\.jp/radio/(?:player/ondemand|ondemand/detail)\\.html\\?" +
                "p=(?<site>[\\da-zA-Z]+)_(?<corner>[\\da-zA-Z]+)(?:_(?<headline>[\\da-zA-Z]+))?",
        )
    }
}

/** Upstream `NhkRadioNewsPageIE`: the radio news page hands off to Radiru. */
class NhkRadioNewsPageIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = InfoDict(
        id = "18439M2W42_01",
        webpageUrl = url,
        redirectUrl = "https://www.nhk.or.jp/radio/ondemand/detail.html?p=18439M2W42_01",
        extractor = "nhk",
        extractorKey = IE_KEY,
    )

    companion object {
        const val IE_KEY: String = "NhkRadioNewsPage"

        val VALID_URL: Regex = Regex("https?://www\\.nhk\\.or\\.jp/radionews/?(?:$|[?#])")
    }
}

/** Upstream `NhkRadiruLiveIE`: the live radio station config and NOA JSON. */
class NhkRadiruLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val station = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val config = http.downloadWebpage("https://www.nhk.or.jp/radio/config/config_web.xml")
        val areas = Regex("<area>([^<]+)</area>").findAll(config).map { it.groupValues[1] }.toList()
        val area = DEFAULT_AREA
        val dataBlock = Regex("<data>(.*?)</data>", RegexOption.DOT_MATCHES_ALL).findAll(config)
            .firstOrNull { block ->
                Regex("<area>([^<]+)</area>").find(block.groupValues[1])?.groupValues?.get(1) == area
            }?.groupValues?.get(1)
        if (dataBlock == null) {
            throw ExtractionError.Unavailable(
                "Invalid area. Valid areas are: " + areas.joinToString(", ") + ".",
            )
        }
        val areaKey = Regex("<areakey>([^<]+)</areakey>").find(dataBlock)?.groupValues?.get(1)
            ?: throw ExtractionError.Malformed("The NHK config had no area key.")
        val noaTemplate = Regex("<url_program_noa>([^<]+)</url_program_noa>").find(config)
            ?.groupValues?.get(1)
            ?: throw ExtractionError.Malformed("The NHK config had no NOA URL.")
        val noaUrl = "https:" + noaTemplate.replace("{area}", areaKey)
        val noaInfo = http.downloadJson(noaUrl) as? JsonObject
        val broadcastService = noaInfo?.obj(NOA_STATION_IDS[station] ?: station)?.obj("publishedOn")
        val hls = Regex("<${Regex.escape(station)}hls>([^<]+)</${Regex.escape(station)}hls>")
            .find(config)?.groupValues?.get(1)
            ?: throw ExtractionError.Malformed("The NHK config had no HLS URL for $station.")

        return InfoDict(
            id = broadcastService?.str("id"),
            title = broadcastService?.str("broadcastDisplayName"),
            thumbnails = broadcastService?.array("logo").orEmpty().mapNotNull { element ->
                val logo = element as? JsonObject ?: return@mapNotNull null
                val logoUrl = ExtractorUtils.urlOrNone(logo.str("url")) ?: return@mapNotNull null
                Thumbnail(
                    url = logoUrl,
                    width = logo.number("width")?.toLong(),
                    height = logo.number("height")?.toLong(),
                )
            },
            formats = listOf(
                MediaFormat(formatId = "hls", url = hls, ext = "m4a", protocol = "m3u8_native"),
            ),
            isLive = true,
            webpageUrl = url,
            extractor = "nhk",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NhkRadiruLive"
        private const val DEFAULT_AREA = "tokyo"

        private val NOA_STATION_IDS = mapOf("r1" to "r1", "r2" to "r2", "fm" to "r3")

        val VALID_URL: Regex = Regex("https?://www\\.nhk\\.or\\.jp/radio/player/\\?ch=(?<id>r[12]|fm)")
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `get_element_by_class` + `clean_html` for the program pages. */
private fun metaFromClassElements(html: String, classValues: List<String>): String? {
    for (classValue in classValues) {
        val tag = Regex(
            "<([a-zA-Z][a-zA-Z0-9]*)[^>]*class=\"[^\"]*" + Regex.escape(classValue) + "[^\"]*\"[^>]*>(.*?)</\\1>",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html) ?: continue
        val text = ExtractorUtils.unescapeHtml(tag.groupValues[2].replace(Regex("<[^>]*>"), " "))
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
        if (!text.isNullOrEmpty()) return text
    }
    return null
}

/** Upstream `join_nonempty` over strings; null when every part is empty. */
private fun joinNonEmpty(vararg values: String?, delim: String = "-"): String? {
    val joined = values.filterNotNull().filter { it.isNotEmpty() }.joinToString(delim)
    return joined.ifEmpty { null }
}

/** Upstream `urljoin`. */
private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("/") -> base.substringBefore("://").let { scheme ->
        val host = base.substringAfter("://").substringBefore('/')
        "$scheme://$host$value"
    }

    else -> base.substringBefore('?').trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
