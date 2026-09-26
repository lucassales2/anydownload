/*
 * Lyrics — AnyDownload
 *
 * Reimplements the lyrics side of spotDL v4.5.2 (commit cd4a4203): the
 * provider order and the "first hit wins" rule from `providers/lyrics/`, and
 * the `synced` provider's timed-lines behavior. Genius uses the official API
 * with an optional on-device token; azlyrics and musixmatch use their public
 * pages; the synced provider uses LRCLIB's public JSON. No Python is copied
 * or vendored, and no token is ever logged.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** One lyrics hit. [synced] is true only for timed LRC lines. */
data class LyricsResult(
    val text: String,
    val synced: Boolean = false,
)

/** One lyrics backend; returns null for a miss and never throws to the fetcher. */
interface LyricsProvider {
    val name: String

    suspend fun fetch(title: String, artists: List<String>): LyricsResult?
}

/** Provider names and spotDL's default order. */
object LyricsProviders {
    const val GENIUS = "genius"
    const val AZLYRICS = "azlyrics"
    const val MUSIXMATCH = "musixmatch"
    const val SYNCED = "synced"

    val DEFAULT_ORDER: List<String> = listOf(GENIUS, AZLYRICS, MUSIXMATCH, SYNCED)

    /** Case-insensitive provider name lookup; null for an unknown name. */
    fun normalize(value: String): String? =
        DEFAULT_ORDER.firstOrNull { it.equals(value.trim(), ignoreCase = true) }
}

/**
 * Tries providers in [order] and returns the first non-blank hit. A provider
 * that fails or misses does not stop the rest. [order] is the user's choice;
 * the default is genius, azlyrics, musixmatch, then synced.
 */
class LyricsFetcher(private val providers: Map<String, LyricsProvider>) {

    suspend fun fetch(
        record: SongRecord,
        order: List<String> = LyricsProviders.DEFAULT_ORDER,
    ): LyricsResult? {
        for (name in order) {
            val provider = providers[name] ?: continue
            val result = try {
                provider.fetch(record.title, record.artists)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (result != null && result.text.isNotBlank()) return result
        }
        return null
    }

    companion object {
        /** The four default providers over the shared extractor HTTP seam. */
        fun default(http: ExtractorHttp, geniusToken: String? = null): LyricsFetcher = LyricsFetcher(
            mapOf(
                LyricsProviders.GENIUS to GeniusLyricsProvider(http, geniusToken),
                LyricsProviders.AZLYRICS to AzlyricsLyricsProvider(http),
                LyricsProviders.MUSIXMATCH to MusixmatchLyricsProvider(http),
                LyricsProviders.SYNCED to SyncedLyricsProvider(http),
            ),
        )
    }
}

/**
 * Genius: the official search API plus the public song page. Without a stored
 * token the provider misses immediately; the token is sent only in the
 * explicit authorization field and never logged or persisted here.
 */
class GeniusLyricsProvider(
    private val http: ExtractorHttp,
    private val token: String?,
) : LyricsProvider {
    override val name: String = LyricsProviders.GENIUS

    override suspend fun fetch(title: String, artists: List<String>): LyricsResult? {
        val accessToken = token?.takeIf { it.isNotBlank() } ?: return null
        val query = query(title, artists)
        val search = http.downloadJson(
            url = "https://api.genius.com/search?q=${encodeQueryComponent(query)}",
            headers = mapOf("accept" to "application/json"),
            authorization = "Bearer $accessToken",
        ) as? JsonObject ?: return null
        val hits = search.obj("response").array("hits").objects()
        val best = hits.mapNotNull { it.obj("result") }
            .maxByOrNull { lyricsMatchScore(query, it.str("full_title").orEmpty()) }
            ?: return null
        if (lyricsMatchScore(query, best.str("full_title").orEmpty()) < LYRICS_MATCH_FLOOR) return null
        val songId = best.num("id") ?: return null
        val song = http.downloadJson(
            url = "https://api.genius.com/songs/$songId",
            authorization = "Bearer $accessToken",
        ) as? JsonObject ?: return null
        val pageUrl = song.obj("response").obj("song").str("url") ?: return null
        val html = http.downloadWebpage(pageUrl, headers = BROWSER_HEADERS)
        return extractGeniusLyrics(html)?.let { LyricsResult(it) }
    }
}

/** AzLyrics: the public search page, then the song page. */
class AzlyricsLyricsProvider(private val http: ExtractorHttp) : LyricsProvider {
    override val name: String = LyricsProviders.AZLYRICS

    override suspend fun fetch(title: String, artists: List<String>): LyricsResult? {
        val query = "$title ${artists.firstOrNull().orEmpty()}".trim()
        val search = http.downloadWebpage(
            url = "https://www.azlyrics.com/search/?q=${encodeQueryComponent(query)}",
            headers = BROWSER_HEADERS,
        )
        val candidates = parseAzlyricsSearch(search)
        val best = candidates.maxByOrNull { lyricsMatchScore(query, it.title) } ?: return null
        if (lyricsMatchScore(query, best.title) < LYRICS_MATCH_FLOOR) return null
        val page = http.downloadWebpage(best.url, headers = BROWSER_HEADERS)
        return extractAzlyricsLyrics(page)?.let { LyricsResult(it) }
    }
}

/** Musixmatch: the public search page, then the lyrics page. */
class MusixmatchLyricsProvider(private val http: ExtractorHttp) : LyricsProvider {
    override val name: String = LyricsProviders.MUSIXMATCH

    override suspend fun fetch(title: String, artists: List<String>): LyricsResult? {
        val query = query(title, artists)
        val search = http.downloadWebpage(
            url = "https://www.musixmatch.com/search/${encodeQueryComponent(query)}",
            headers = BROWSER_HEADERS,
        )
        val candidates = parseMusixmatchSearch(search)
        val best = candidates.maxByOrNull { lyricsMatchScore(query, it.title) } ?: return null
        if (lyricsMatchScore(query, best.title) < LYRICS_MATCH_FLOOR) return null
        val page = http.downloadWebpage(best.url, headers = BROWSER_HEADERS)
        return extractMusixmatchLyrics(page)?.let { LyricsResult(it) }
    }
}

/**
 * Synced: LRCLIB's public search API. Timed lines win; a plain-only hit is
 * still a valid lyrics result but never produces an LRC file.
 */
class SyncedLyricsProvider(private val http: ExtractorHttp) : LyricsProvider {
    override val name: String = LyricsProviders.SYNCED

    override suspend fun fetch(title: String, artists: List<String>): LyricsResult? {
        val query = "$title ${artists.firstOrNull().orEmpty()}".trim()
        val json = try {
            http.downloadJson("https://lrclib.net/api/search?q=${encodeQueryComponent(query)}")
        } catch (_: ExtractionError) {
            return null
        }
        val entries = (json as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val best = entries.maxByOrNull { entry ->
            lyricsMatchScore(
                query,
                "${entry.str("trackName").orEmpty()} - ${entry.str("artistName").orEmpty()}",
            )
        } ?: return null
        val synced = best.str("syncedLyrics")
        if (synced != null) return LyricsResult(synced, synced = true)
        val plain = best.str("plainLyrics") ?: return null
        return LyricsResult(plain, synced = false)
    }
}

// ------------------------------------------------------------------ parsing

internal data class LyricsCandidate(val title: String, val url: String)

internal const val LYRICS_MATCH_FLOOR: Double = 55.0

/**
 * Match score for a lyrics candidate: the better of the direct similarity and
 * the share of the query's words present in the candidate, so a reordered
 * `Artist - Title` still scores. spotDL's `based_sort` does the same job.
 */
internal fun lyricsMatchScore(query: String, candidate: String): Double {
    val direct = SpotifyTextScore.ratio(query, candidate)
    val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return direct
    val candidateText = candidate.lowercase()
    val overlap = tokens.count { it in candidateText } * 100.0 / tokens.size
    return maxOf(direct, overlap)
}

/** Genius `div.lyrics` or one or more `div[class^=Lyrics__Container]`. */
internal fun extractGeniusLyrics(html: String): String? {
    val normalized = html.replace(Regex("(?i)<br\\s*/?>"), "\n")
    val containers = Regex(
        """<div[^>]*class="[^"]*Lyrics__Container[^"]*"[^>]*>(.*?)</div>""",
        RegexOption.DOT_MATCHES_ALL,
    ).findAll(normalized).map { it.groupValues[1] }.toList()
    val legacy = Regex(
        """<div[^>]*class="[^"]*\blyrics\b[^"]*"[^>]*>(.*?)</div>""",
        RegexOption.DOT_MATCHES_ALL,
    ).find(normalized)?.groupValues?.get(1)
    val raw = when {
        containers.isNotEmpty() -> containers.joinToString("\n")
        legacy != null -> legacy
        else -> return null
    }
    return htmlToText(raw).takeIf { it.isNotBlank() }
}

/** AzLyrics search results: one `<td>` per hit with a lyrics link. */
internal fun parseAzlyricsSearch(html: String): List<LyricsCandidate> =
    Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { match ->
        val block = match.groupValues[1]
        val href = Regex("""<a[^>]*href="([^"]+)"[^>]*>""").find(block)?.groupValues?.get(1)
            ?: return@mapNotNull null
        val text = htmlToText(block)
        if (href.isBlank() || text.isBlank()) null else LyricsCandidate(text, href)
    }.toList()

/** AzLyrics song page: the `<div>` after the usage comment. */
internal fun extractAzlyricsLyrics(html: String): String? {
    val marker = html.indexOf("Usage of azlyrics.com")
    if (marker < 0) return null
    val after = html.substring(marker)
    val divStart = after.indexOf("<div>")
    if (divStart < 0) return null
    val divEnd = after.indexOf("</div>", divStart)
    if (divEnd < 0) return null
    val raw = after.substring(divStart + "<div>".length, divEnd)
    return htmlToText(raw).takeIf { it.isNotBlank() }
}

/** Musixmatch search results: `a[href^=/lyrics/]` links. */
internal fun parseMusixmatchSearch(html: String): List<LyricsCandidate> =
    Regex("""<a[^>]*href="(/lyrics/[^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(html)
        .map { match ->
            LyricsCandidate(
                title = htmlToText(match.groupValues[2]),
                url = "https://www.musixmatch.com" + match.groupValues[1],
            )
        }
        .filter { it.title.isNotBlank() }
        .toList()

/** Musixmatch lyrics page: `p.mxm-lyrics__content` paragraphs. */
internal fun extractMusixmatchLyrics(html: String): String? {
    val paragraphs = Regex(
        """<p[^>]*class="[^"]*mxm-lyrics__content[^"]*"[^>]*>(.*?)</p>""",
        RegexOption.DOT_MATCHES_ALL,
    ).findAll(html).map { htmlToText(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
    return paragraphs.joinToString("\n").takeIf { it.isNotBlank() }
}

private fun query(title: String, artists: List<String>): String {
    val artistText = artists.joinToString(", ")
    return if (artistText.isBlank()) title else "$title - $artistText"
}

private fun htmlToText(html: String): String = ExtractorUtils.unescapeHtml(
    html.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("(?s)<[^>]+>"), ""),
).orEmpty().lines().joinToString("\n") { it.trim() }.trim()

private val BROWSER_HEADERS = mapOf(
    "accept" to "text/html,application/xhtml+xml",
    "accept-language" to "en-US,en;q=0.8",
    "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36",
)
