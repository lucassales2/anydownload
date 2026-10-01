/*
 * Agora extractors — AnyDownload
 *
 * Kotlin translation of `agora.py` from `yt_dlp/extractor/agora.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `agora.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Wyborcza video API (standard/high rows with the p-height and
 * one DASH row), the Wyborcza podcast API with the Polish month mapping and
 * its TokFM audition playlist dispatch, the TokFM podcast metadata and
 * getSongUrl rows, and the TokFM audition paging (offset steps of 30, capped
 * at 100 pages). Limitations: the upstream `RetryManager` on an empty page
 * is replaced by a plain stop; m3u8/mpd subtitles are not parsed (one row
 * per manifest); the `series`/`episode`/`cast` fields are not modeled and
 * are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.agora

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

private val POLISH_MONTHS = linkedMapOf(
    "styczeń" to 1, "stycznia" to 1,
    "luty" to 2, "lutego" to 2,
    "marzec" to 3, "marca" to 3,
    "kwiecień" to 4, "kwietnia" to 4,
    "maj" to 5, "maja" to 5,
    "czerwiec" to 6, "czerwca" to 6,
    "lipiec" to 7, "lipca" to 7,
    "sierpień" to 8, "sierpnia" to 8,
    "wrzesień" to 9, "września" to 9,
    "październik" to 10, "października" to 10,
    "listopad" to 11, "listopada" to 11,
    "grudzień" to 12, "grudnia" to 12,
)

/** Upstream `WyborczaVideoIE`: the wyborcza.pl video API. */
class WyborczaVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val meta = http.downloadJson("https://wyborcza.pl/api-video/$videoId") as? JsonObject
            ?: throw ExtractionError.Malformed("The Wyborcza video API was not an object.")

        val redirector = meta.str("redirector")?.replace("http://", "https://")
            ?: throw ExtractionError.Malformed("The Wyborcza video API carried no redirector.")
        val baseUrl = redirector + (meta.str("basePath") ?: "")
        val files = meta.obj("files")

        val formats = mutableListOf<MediaFormat>()
        for (quality in listOf("standard", "high")) {
            val file = files?.str(quality) ?: continue
            formats += MediaFormat(
                url = baseUrl + file,
                formatId = quality,
                height = ExtractorUtils.searchRegex("p(\\d+)[a-z]+\\.mp4$", file)?.toLongOrNull(),
            )
        }
        files?.str("dash")?.let { dash ->
            formats += MediaFormat(
                formatId = "dash",
                url = baseUrl + dash,
                ext = "mp4",
                protocol = "mpd",
            )
        }

        return InfoDict(
            id = videoId,
            title = meta.str("title"),
            description = meta.str("lead"),
            uploader = meta.str("signature"),
            thumbnails = meta.str("imageUrl")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = meta.number("duration"),
            formats = formats,
            webpageUrl = url,
            extractor = "wyborcza:video",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "WyborczaVideo"

        val VALID_URL: Regex = Regex(
            "(?:wyborcza:video:|https?://wyborcza\\.pl/(?:api-)?video/)(?<id>\\d+)",
        )
    }
}

/** Upstream `WyborczaPodcastIE`: the Wyborcza/Wysokie Obcasy podcast pages. */
class WyborczaPodcastIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val podcastId = matchId(url)
        val isWysokieObcasy = "wysokieobcasy.pl/" in url
        if (podcastId == null) {
            // Upstream `url_result(TokFMAuditionIE._create_url(...))`.
            val playlistId = if (isWysokieObcasy) "395" else "334"
            val info = TokFMAuditionIE(http).extract("https://audycje.tokfm.pl/audycja/$playlistId")
            return info.copy(id = playlistId, webpageUrl = url)
        }

        val query = if (isWysokieObcasy) "?guid=$podcastId&type=wo" else "?guid=$podcastId"
        val meta = http.downloadJson("https://wyborcza.pl/api/podcast$query") as? JsonObject
            ?: throw ExtractionError.Malformed("The Wyborcza podcast API was not an object.")

        val dateMatch = Regex("^(\\d\\d?) (\\w+) (\\d{4})$")
            .find(meta.str("publishedDate") ?: "")
        val uploadDate = dateMatch?.let { match ->
            val month = POLISH_MONTHS[match.groupValues[2].lowercase()] ?: return@let null
            "${match.groupValues[3]}${month.toString().padStart(2, '0')}" +
                match.groupValues[1].padStart(2, '0')
        }

        val audioUrl = meta.str("url")
        return InfoDict(
            id = podcastId,
            title = meta.str("title"),
            description = meta.str("description"),
            uploader = meta.str("author"),
            uploadDate = uploadDate,
            thumbnails = meta.str("imageUrl")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = ExtractorUtils.parseDuration(meta.str("duration")),
            formats = audioUrl?.let {
                listOf(MediaFormat(url = it, ext = ExtractorUtils.determineExt(it, "mp3")))
            }.orEmpty(),
            webpageUrl = url,
            extractor = "wyborcza:podcast",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "WyborczaPodcast"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:" +
                "wyborcza\\.pl/podcast(?:/0,172673\\.html)?|" +
                "wysokieobcasy\\.pl/wysokie-obcasy/0,176631\\.html" +
                ")(?:\\?(?:[^&#]+?&)*podcast=(?<id>\\d+))?",
        )
    }
}

/** Upstream `TokFMPodcastIE`: one TokFM podcast episode. */
class TokFMPodcastIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val mediaId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadataList = http.downloadJson("https://audycje.tokfm.pl/getp/3$mediaId") as? JsonArray
            ?: throw ExtractionError.Malformed("The TokFM podcast API was not a list.")
        val metadata = metadataList.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Unavailable("No such podcast.")

        val song = http.downloadJson(
            "https://api.podcast.radioagora.pl/api4/getSongUrl" +
                "?podcast_id=$mediaId&device_id=${randomUuid()}&ppre=false&audio=mp3",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The TokFM song API was not an object.")
        val mp3Url = song.str("link_ssl")
            ?: throw ExtractionError.Malformed("The TokFM song API carried no link.")

        return InfoDict(
            id = mediaId,
            title = metadata.str("podcast_name"),
            formats = listOf(MediaFormat(url = mp3Url, ext = "mp3", vcodec = "none")),
            webpageUrl = url,
            extractor = "tokfm:podcast",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TokFMPodcast"

        val VALID_URL: Regex = Regex(
            "(?:https?://audycje\\.tokfm\\.pl/podcast/|tokfm:podcast:)(?<id>\\d+),?",
        )
    }
}

/** Upstream `TokFMAuditionIE`: a TokFM audition playlist. */
class TokFMAuditionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val auditionId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val seriesList = http.downloadJson(
            "https://api.podcast.radioagora.pl/api4/getSeries?series_id=$auditionId",
            headers = MOBILE_HEADERS,
        ) as? JsonArray ?: throw ExtractionError.Malformed("The TokFM series API was not a list.")
        val data = seriesList.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Unavailable("No such audition.")

        val entries = mutableListOf<InfoEntry>()
        var offset = 0
        var pages = 0
        while (pages < MAX_PAGES) {
            pages++
            val page = http.downloadJson(
                "https://api.podcast.radioagora.pl/api4/getPodcasts" +
                    "?series_id=$auditionId&limit=30&offset=$offset" +
                    "&with_guests=true&with_leaders_for_mobile=true",
                headers = MOBILE_HEADERS,
            ) as? JsonArray ?: throw ExtractionError.Malformed("The TokFM podcast list was not a list.")
            if (page.isEmpty()) break
            for (element in page) {
                val podcast = element as? JsonObject ?: continue
                val sharingUrl = podcast.str("podcast_sharing_url") ?: continue
                entries += InfoEntry(
                    title = podcast.str("podcast_name"),
                    url = sharingUrl,
                )
            }
            offset += 30
        }

        return InfoDict(
            id = auditionId,
            title = data.str("series_name"),
            entries = entries,
            webpageUrl = url,
            extractor = "tokfm:audition",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TokFMAudition"

        private const val MAX_PAGES = 100
        private val MOBILE_HEADERS = mapOf(
            "user-agent" to "Mozilla/5.0 (Linux; Android 9; Redmi 3S Build/PQ3A.190801.002; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/87.0.4280.101 " +
                "Mobile Safari/537.36",
        )

        val VALID_URL: Regex = Regex(
            "(?:https?://audycje\\.tokfm\\.pl/audycja/|tokfm:audition:)(?<id>\\d+),?",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `uuid.uuid4()` for the device id. */
private fun randomUuid(): String {
    val hex = "0123456789abcdef"
    fun segment(length: Int): String = buildString(length) {
        repeat(length) { append(hex[Random.nextInt(hex.length)]) }
    }
    return "${segment(8)}-${segment(4)}-${segment(4)}-${segment(4)}-${segment(12)}"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
