/*
 * Fallback audio providers — AnyDownload
 *
 * Reimplements the four spotDL v4.5.2 (commit cd4a4203) fallback audio
 * providers without copying its Python: SoundCloud (`providers/audio/
 * soundcloud.py`), Bandcamp (`bandcamp.py`), Piped (`piped.py`), and
 * slider.kz (`sliderkz.py`). They return candidate URLs only; the existing
 * `DownloadEngine` still performs the download. Every one is opt-in and the
 * default matcher never calls them.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The fallback provider names and the opt-in factory. */
object AudioProviders {
    const val SOUNDCLOUD = "soundcloud"
    const val BANDCAMP = "bandcamp"
    const val PIPED = "piped"
    const val SLIDER_KZ = "slider.kz"

    /** spotDL's fallback order. */
    val FALLBACK_ORDER: List<String> = listOf(SOUNDCLOUD, BANDCAMP, PIPED, SLIDER_KZ)

    fun normalize(value: String): String? =
        FALLBACK_ORDER.firstOrNull { it.equals(value.trim(), ignoreCase = true) }

    /** Builds the fallbacks the user enabled, in spotDL's order. */
    fun fallbacks(http: ExtractorHttp, enabled: List<String>): List<AudioProvider> {
        val wanted = enabled.mapNotNull(::normalize).toSet()
        return FALLBACK_ORDER.filter { it in wanted }.map { name ->
            when (name) {
                SOUNDCLOUD -> SoundcloudAudioProvider(http)
                BANDCAMP -> BandcampAudioProvider(http)
                PIPED -> PipedAudioProvider(http)
                else -> SliderKzAudioProvider(http)
            }
        }
    }
}

/**
 * SoundCloud: the public search page carries a short-lived web client id,
 * which the public v2 search endpoint needs. The client id is not a secret and
 * is never logged; it rides only in the request URL the endpoint requires.
 */
class SoundcloudAudioProvider(private val http: ExtractorHttp) : AudioProvider {
    override val source: AudioSource = AudioSource.SOUNDCLOUD
    override val supportsIsrc: Boolean = false

    override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> {
        val page = http.downloadWebpage(
            url = "https://soundcloud.com/search?q=${encodeQueryComponent(query)}",
            headers = BROWSER_HEADERS,
        )
        val clientId = parseSoundcloudClientId(page)
            ?: throw ExtractionError.Malformed("The SoundCloud search page did not carry a client id.")
        val json = http.downloadJson(
            url = "https://api-v2.soundcloud.com/search/tracks" +
                "?q=${encodeQueryComponent(query)}&client_id=${encodeQueryComponent(clientId)}&limit=20",
            headers = mapOf("accept" to "application/json"),
        )
        val collection = (json as? JsonObject).array("collection").objects()
        return collection.mapNotNull { item ->
            val url = item.str("permalink_url") ?: return@mapNotNull null
            if (url.contains("/preview/")) return@mapNotNull null
            AudioCandidate(
                source = source,
                url = url,
                title = item.str("title") ?: return@mapNotNull null,
                artists = listOfNotNull(item.obj("user")?.str("username")),
                channel = item.obj("user")?.str("username"),
                durationSeconds = item.num("duration")?.let { it / 1000.0 },
                verified = item.obj("user")?.flag("verified") == true,
                viewCount = item.num("playback_count"),
            )
        }
    }

    companion object {
        internal const val SEARCH_PAGE: String = "https://soundcloud.com/search"
    }
}

/**
 * Bandcamp: the public fuzzy-search API returns track ids; the mobile
 * tralbum-details API then gives the public track URL, artist, and duration.
 * Only the top few candidates get a details fetch.
 */
class BandcampAudioProvider(private val http: ExtractorHttp) : AudioProvider {
    override val source: AudioSource = AudioSource.BANDCAMP
    override val supportsIsrc: Boolean = false

    override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> {
        val json = http.downloadJson(
            url = "https://bandcamp.com/api/fuzzysearch/2/app_autocomplete" +
                "?q=${encodeQueryComponent(query)}&param_with_locations=true",
            headers = mapOf("accept" to "application/json"),
        )
        val tracks = (json as? JsonObject).array("results").objects()
            .filter { it.str("type") == "t" }
            .sortedByDescending { lyricsMatchScore(query, it.str("name").orEmpty()) }
            .take(MAX_DETAIL_FETCHES)
        return tracks.mapNotNull { track ->
            val bandId = track.num("band_id") ?: return@mapNotNull null
            val trackId = track.num("id") ?: return@mapNotNull null
            val details = try {
                http.downloadJson(
                    url = "https://bandcamp.com/api/mobile/25/tralbum_details" +
                        "?band_id=$bandId&tralbum_id=$trackId&tralbum_type=t",
                    headers = mapOf("accept" to "application/json"),
                ) as? JsonObject
            } catch (_: ExtractionError) {
                null
            } ?: return@mapNotNull null
            AudioCandidate(
                source = source,
                url = details.str("bandcamp_url") ?: return@mapNotNull null,
                title = details.str("title") ?: return@mapNotNull null,
                artists = listOfNotNull(details.obj("band")?.str("name")),
                channel = details.obj("band")?.str("name"),
                durationSeconds = details.array("tracks").objects().firstOrNull()?.dbl("duration"),
                album = details.str("album_title"),
            )
        }
    }

    private companion object {
        const val MAX_DETAIL_FETCHES = 3
    }
}

/**
 * Piped: a public instance's search API returns YouTube video results. The
 * provider normalizes every result to a `youtube.com/watch?v=` URL, which the
 * existing YouTube extractor already owns.
 */
class PipedAudioProvider(private val http: ExtractorHttp) : AudioProvider {
    override val source: AudioSource = AudioSource.PIPED
    override val supportsIsrc: Boolean = false

    override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> =
        (searchFilter(query, "music_songs") + searchFilter(query, "music_videos")).distinctBy { it.url }

    private suspend fun searchFilter(query: String, filter: String): List<AudioCandidate> {
        val json = http.downloadJson(
            url = "$PIPED_API/search?q=${encodeQueryComponent(query)}&filter=$filter",
            headers = mapOf("accept" to "application/json"),
        )
        val items = (json as? JsonObject).array("items").objects()
        return items.mapNotNull { item ->
            val url = pipedToYoutubeUrl(item.str("url") ?: return@mapNotNull null) ?: return@mapNotNull null
            AudioCandidate(
                source = source,
                url = url,
                title = item.str("title") ?: return@mapNotNull null,
                artists = listOfNotNull(item.str("uploaderName")),
                channel = item.str("uploaderName"),
                durationSeconds = item.num("duration")?.toDouble(),
                viewCount = item.num("views"),
            )
        }
    }

    companion object {
        const val PIPED_API: String = "https://api.piped.private.coffee"
    }
}

/**
 * slider.kz: the public search endpoint returns direct audio URLs. The live
 * service was shut down (spotDL v4.5.2 disables the module), so this provider
 * normally misses; the parser and the fixture keep the parity surface.
 */
class SliderKzAudioProvider(private val http: ExtractorHttp) : AudioProvider {
    override val source: AudioSource = AudioSource.SLIDER_KZ
    override val supportsIsrc: Boolean = false

    override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> {
        val json = try {
            http.downloadJson(
                url = "$SLIDER_KZ_HOST/vk_auth.php?q=${encodeQueryComponent(query)}",
                headers = mapOf("accept" to "application/json"),
            )
        } catch (_: ExtractionError) {
            return emptyList()
        }
        val items = (json as? JsonObject).obj("audios").array("").objects()
        return items.mapNotNull { item ->
            val raw = item.str("url") ?: return@mapNotNull null
            val url = if (raw.startsWith("http")) raw else "$SLIDER_KZ_HOST/${raw.trimStart('/')}"
            AudioCandidate(
                source = source,
                url = url,
                title = item.str("tit_art") ?: return@mapNotNull null,
                artists = listOf("slider.kz"),
                channel = "slider.kz",
                durationSeconds = item.str("duration")?.toDoubleOrNull() ?: item.num("duration")?.toDouble(),
                viewCount = 1,
            )
        }
    }

    companion object {
        const val SLIDER_KZ_HOST: String = "https://hayqbhgr.slider.kz"
    }
}

// ------------------------------------------------------------------ parsing

/** The SoundCloud search page's `apiClient` id, or null. */
internal fun parseSoundcloudClientId(html: String): String? {
    val match = Regex("""__sc_hydration\s*=\s*(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL).find(html)
        ?: return null
    val array = runCatching { Json.parseToJsonElement(match.groupValues[1]) as? JsonArray }.getOrNull()
        ?: return null
    return array.mapNotNull { it as? JsonObject }
        .firstOrNull { it.str("hydratable") == "apiClient" }
        ?.obj("data")
        ?.str("id")
}

/** A Piped result URL normalized to a YouTube watch URL, or null. */
internal fun pipedToYoutubeUrl(raw: String): String? {
    if (raw.startsWith("https://www.youtube.com/") || raw.startsWith("https://youtube.com/")) return raw
    if (raw.startsWith("/watch")) return "https://www.youtube.com$raw"
    if (raw.contains("piped.video/watch")) {
        val videoId = raw.substringAfter("v=", "").substringBefore('&')
        if (videoId.isNotBlank()) return "https://www.youtube.com/watch?v=$videoId"
    }
    return null
}

private val BROWSER_HEADERS = mapOf(
    "accept" to "text/html,application/xhtml+xml",
    "accept-language" to "en-US,en;q=0.8",
    "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36",
)
