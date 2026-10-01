/*
 * Japanese Diet extractors — AnyDownload
 *
 * Kotlin translation of `japandiet.py` from
 * `yt_dlp/extractor/japandiet.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `japandiet.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Shugiin live room list and room HLS row, the Shugiin VOD page
 * (m3u8 URL, title, release date as uploadDate, and the play_vod chapters
 * with the last end-time), the Sangiin detail page (date/title/description,
 * live marker, videopath HLS row), the instruction page's typed message, and
 * the Japanese era date and duration helpers. Limitations: upstream reads
 * the Shugiin pages as EUC-JP; the port's HTTP layer decodes UTF-8, so real
 * EUC-JP titles may be garbled (fixture-level fidelity only); the smuggled
 * room tuple is replaced by the same room-list fetch; m3u8 subtitles are not
 * parsed (one HLS row per manifest); `release_date` folds into `uploadDate`.
 * No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.japandiet

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat

/** Upstream `ShugiinItvBaseIE`: the room list shared by the live classes. */
abstract class ShugiinItvBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    private var cachedRooms: List<InfoEntry>? = null

    /** Upstream `_find_rooms` / `_fetch_rooms` (the smuggle tuple is not carried). */
    protected suspend fun fetchRooms(): List<InfoEntry> {
        cachedRooms?.let { return it }
        val webpage = http.downloadWebpage("https://www.shugiintv.go.jp/jp/index.php")
        val rooms = ROOM.findAll(webpage).map { match ->
            val roomId = match.groupValues[1]
            InfoEntry(
                id = roomId,
                title = cleanHtml(match.groupValues[2])?.trim(),
                url = "https://www.shugiintv.go.jp/jp/index.php?room_id=$roomId",
            )
        }.toList()
        cachedRooms = rooms
        return rooms
    }

    companion object {
        private val ROOM = Regex(
            "(?s)<a\\s+href=\"[^\"]+\\?room_id=(room\\d+)\"\\s*class=\"play_live\"" +
                ".+?class=\"s12_14\">(.+?)</td>",
        )
    }
}

/** Upstream `ShugiinItvLiveIE`: the index page with every running room. */
class ShugiinItvLiveIE(
    http: ExtractorHttp,
) : ShugiinItvBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        !ShugiinItvLiveRoomIE.VALID_URL.containsMatchIn(url) &&
            !ShugiinItvVodIE.VALID_URL.containsMatchIn(url) &&
            super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        if (!VALID_URL.containsMatchIn(url)) throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            title = "All proceedings for today",
            entries = fetchRooms(),
            webpageUrl = url,
            extractor = "shugiin:live",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "ShugiinItvLive"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?shugiintv\\.go\\.jp/(?:jp|en)(?:/index\\.php)?$",
        )
    }
}

/** Upstream `ShugiinItvLiveRoomIE`: one live room. */
class ShugiinItvLiveRoomIE(
    http: ExtractorHttp,
) : ShugiinItvBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val roomId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val title = fetchRooms().firstOrNull { it.id == roomId }?.title
        return InfoDict(
            id = roomId,
            title = title,
            isLive = true,
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = "https://hlslive.shugiintv.go.jp/$roomId/amlst:$roomId/playlist.m3u8",
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            webpageUrl = url,
            extractor = "shugiin:live:room",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "ShugiinItvLiveRoom"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?shugiintv\\.go\\.jp/(?:jp|en)/index\\.php\\?room_id=(?<id>room\\d+)",
        )
    }
}

/** Upstream `ShugiinItvVodIE`: one video-library entry. */
class ShugiinItvVodIE(
    http: ExtractorHttp,
) : ShugiinItvBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(
            "https://www.shugiintv.go.jp/jp/index.php?ex=VL&media_type=&deli_id=$videoId",
        )

        val m3u8Url = (ExtractorUtils.searchRegex(
            "id=\"vtag_src_base_vod\"\\s*value=\"(http.+?\\.m3u8)\"",
            webpage,
        ) ?: throw ExtractionError.Malformed("The Shugiin VOD page carried no m3u8 URL."))
            .replace(Regex("^http://"), "https://")

        val title = ExtractorUtils.searchRegex(
            "<td\\s+align=\"left\">(.+)\\s*\\(\\d+分\\)",
            webpage,
        ) ?: ExtractorUtils.searchRegex(
            "<TD.+?<IMG\\s*src=\".+?/spacer\\.gif\".+?height=\"15\">(.+?)<IMG",
            webpage,
        )

        val releaseDate = parseJapaneseDate(
            ExtractorUtils.searchRegex(
                "開会日</td>\\s*<td.+?/td>\\s*<TD>(.+?)</TD>",
                webpage,
            ),
        )

        val chapters = mutableListOf<Chapter>()
        for (match in CHAPTER.findAll(webpage)) {
            val href = match.groupValues[1]
            val startTime = Regex("[?&]time=([^&]+)").find(href)
                ?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
            chapters += Chapter(
                title = cleanHtml(match.groupValues[2])?.trim(),
                startTime = startTime,
            )
        }
        val lastRow = LAST_ROW.findAll(webpage).lastOrNull()?.groupValues?.get(1)
        if (lastRow != null && chapters.isNotEmpty()) {
            val lastCell = LAST_CELL.findAll(lastRow).lastOrNull()?.value
            if (lastCell != null) {
                val last = chapters.last()
                if (last.startTime != null) {
                    chapters[chapters.size - 1] = last.copy(
                        endTime = last.startTime + parseJapaneseDuration(cleanHtml(lastCell)),
                    )
                }
            }
        }

        return InfoDict(
            id = videoId,
            title = title,
            uploadDate = releaseDate,
            chapters = chapters,
            formats = listOf(
                MediaFormat(formatId = "hls", url = m3u8Url, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "shugiin:vod",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "ShugiinItvVod"

        private val CHAPTER = Regex(
            "(?i)<A\\s+HREF=\"([^\"]+?)\"\\s*class=\"play_vod\">(?!<img)(.+)</[Aa]>",
        )
        private val LAST_ROW = Regex("(?s)<TR\\s+class=\"s14_24\">(.+?)</TR>")
        private val LAST_CELL = Regex("<TD.+?</TD>")

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?shugiintv\\.go\\.jp/(?:jp|en)/index\\.php" +
                "\\?ex=VL(?:\\&[^=]+=[^&]*)*\\&deli_id=(?<id>\\d+)",
        )
    }
}

/** Upstream `SangiinInstructionIE`: the typed copy-the-link message. */
class SangiinInstructionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        throw ExtractionError.Unavailable(
            "Copy the link from the button below the video description/player and use that link " +
                "to download. If there is no button in the frame, get the URL of the frame showing the video.",
        )
    }

    companion object {
        const val IE_KEY: String = "SangiinInstruction"

        val VALID_URL: Regex = Regex("https?://www\\.webtv\\.sangiin\\.go\\.jp/webtv/index\\.php")
    }
}

/** Upstream `SangiinIE`: an archive detail page. */
class SangiinIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        val date = ExtractorUtils.searchRegex(
            "<dt[^>]*>\\s*開会日\\s*</dt>\\s*<dd[^>]*>\\s*(.+?)\\s*</dd>",
            webpage,
        )
        val uploadDate = parseJapaneseDate(date)
        val meetingTitle = ExtractorUtils.searchRegex(
            "<dt[^>]*>\\s*会議名\\s*</dt>\\s*<dd[^>]*>\\s*(.+?)\\s*</dd>",
            webpage,
        )
        val description = ExtractorUtils.searchRegex(
            "会議の経過\\s*</h3>\\s*<span[^>]*>(.+?)</span>",
            webpage,
        )
        val isLive = ExtractorUtils.searchRegex(
            "<dt[^>]*>\\s*公報掲載時刻\\s*</dt>\\s*<dd[^>]*>\\s*(.+?)\\s*</dd>",
            webpage,
        ) != null
        val m3u8Url = ExtractorUtils.searchRegex(
            "var\\s+videopath\\s*=\\s*([\"'])([^\"']+)\\1",
            webpage,
            group = 2,
        ) ?: throw ExtractionError.Malformed("The Sangiin page carried no videopath.")

        return InfoDict(
            id = videoId,
            title = listOfNotNull(date, meetingTitle).joinToString(" ").takeIf { it.isNotBlank() },
            description = description,
            uploadDate = uploadDate,
            isLive = isLive,
            formats = listOf(
                MediaFormat(formatId = "hls", url = m3u8Url, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "sangiin",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Sangiin"

        val VALID_URL: Regex = Regex(
            "https?://www\\.webtv\\.sangiin\\.go\\.jp/webtv/detail\\.php\\?sid=(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val ERA_TABLE = linkedMapOf(
    "明治" to 1868,
    "大正" to 1912,
    "昭和" to 1926,
    "平成" to 1989,
    "令和" to 2019,
)

/** Upstream `_parse_japanese_date`: the era date as `YYYYMMDD`. */
private fun parseJapaneseDate(text: String?): String? {
    val stripped = text?.replace(Regex("[\\s\\u3000]+"), "") ?: return null
    val eras = ERA_TABLE.keys.joinToString("|") { Regex.escape(it) }
    val match = Regex("($eras)?(\\d+)年(\\d+)月(\\d+)日").find(stripped) ?: return null
    val era = match.groupValues[1]
    var year = match.groupValues[2].toIntOrNull() ?: return null
    val month = match.groupValues[3].toIntOrNull() ?: return null
    val day = match.groupValues[4].toIntOrNull() ?: return null
    if (era.isNotEmpty()) year += ERA_TABLE[era] ?: 0
    return pad(year, 4) + pad(month, 2) + pad(day, 2)
}

/** Upstream `_parse_japanese_duration`: days/hours/minutes/seconds to seconds. */
private fun parseJapaneseDuration(text: String?): Double {
    val stripped = text?.replace(Regex("[\\s\\u3000]+"), "") ?: ""
    val match = Regex("(?:(\\d+)日間?)?(?:(\\d+)時間?)?(?:(\\d+)分)?(?:(\\d+)秒)?").find(stripped)
        ?: return 0.0
    val days = match.groupValues[1].toDoubleOrNull() ?: 0.0
    val hours = match.groupValues[2].toDoubleOrNull() ?: 0.0
    val minutes = match.groupValues[3].toDoubleOrNull() ?: 0.0
    val seconds = match.groupValues[4].toDoubleOrNull() ?: 0.0
    return seconds + minutes * 60 + hours * 3600 + days * 86400
}

private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}
