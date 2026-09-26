/*
 * Audio matcher — AnyDownload
 *
 * Turns a Spotify [SongRecord] into one provider URL, or a typed miss.
 * Reimplements the search order and the score shape spotDL v4.5.2 (commit
 * cd4a4203) uses in `providers/audio/base.py` and `utils/matching.py` without
 * copying its Python: main/other artists, name, duration, album, forbidden
 * words, and an ISRC pass for providers that support one.
 *
 * The matcher never downloads and never writes a media file; it returns a
 * URL. YouTube Music is tried before YouTube, and a provider that fails is
 * recorded and skipped so the next one still runs.
 */
package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.youtube.YoutubeSearch
import kotlin.math.abs
import kotlin.math.exp

class AudioMatcher(
    private val providers: List<AudioProvider>,
    private val options: MatcherOptions = MatcherOptions(),
    private val minimumScore: Double = MINIMUM_SCORE,
    /**
     * Whether the host can fetch a candidate URL. A URL the host cannot
     * handle fails that attempt only; the remaining candidates and providers
     * still run. Defaults to accepting everything.
     */
    private val canDownload: (String) -> Boolean = { true },
) {

    /**
     * Searches every provider in order. A verified result at or above
     * [EARLY_RETURN_SCORE] wins immediately; otherwise the highest-scoring
     * candidate above [minimumScore] wins. A lower score fails typed and no
     * URL is invented.
     */
    suspend fun match(record: SongRecord): AudioMatch {
        val displayName = displayName(record)
        val query = searchQuery(record)
        val scored = mutableListOf<Scored>()
        val tried = mutableListOf<AudioSource>()

        // ISRC pass: a single verified hit, or a strong score, returns early.
        val isrc = record.isrc
        if (!isrc.isNullOrBlank()) {
            for (provider in providers.filter { it.supportsIsrc }) {
                val results = searchOrNull(provider, isrc, matchedByIsrc = true) ?: continue
                val filtered = supported(verifiedOnly(results))
                if (filtered.size == 1 && filtered.first().verified) {
                    return matchOf(filtered.first(), 100.0, record)
                }
                val best = scoreAll(filtered, record).maxByOrNull { it.score } ?: continue
                if (best.score > ISRC_RETURN_SCORE) {
                    return matchOf(best.candidate, best.score, record)
                }
            }
        }

        for (provider in providers) {
            tried += provider.source
            val results = searchOrNull(provider, query, matchedByIsrc = false) ?: continue
            val filtered = supported(verifiedOnly(results))
            if (filtered.isEmpty()) continue
            if (!options.filterResults) {
                // spotDL `--dont-filter-results`: first result, score 100.
                return matchOf(filtered.first(), 100.0, record)
            }
            val scoredResults = scoreAll(filtered, record)
            val best = scoredResults.maxByOrNull { it.score } ?: continue
            if (best.score >= EARLY_RETURN_SCORE && best.candidate.verified) {
                return matchOf(best.candidate, best.score, record)
            }
            scored += scoredResults
        }

        val best = scored.maxByOrNull { it.score } ?: throw SpotifyMatchError.NoMatch(displayName, tried)
        if (best.score < minimumScore) throw SpotifyMatchError.NoMatch(displayName, tried)
        return matchOf(best.candidate, best.score, record)
    }

    /**
     * A manual `YouTubeURL|SpotifyURL` pair: no search, the given URL is
     * returned with the Spotify record kept for tags.
     */
    fun manual(record: SongRecord, audioUrl: String): AudioMatch = AudioMatch(
        url = audioUrl,
        source = sourceOf(audioUrl),
        score = 100.0,
        record = record,
        manual = true,
    )

    // -------------------------------------------------------------- providers

    /** A provider failure fails that attempt only; the next provider runs. */
    private suspend fun searchOrNull(
        provider: AudioProvider,
        query: String,
        matchedByIsrc: Boolean,
    ): List<AudioCandidate>? = try {
        provider.search(query, matchedByIsrc)
    } catch (_: ExtractionError) {
        null
    }

    private fun verifiedOnly(candidates: List<AudioCandidate>): List<AudioCandidate> =
        if (options.onlyVerified) candidates.filter { it.verified } else candidates

    /** Candidates whose URL this host can actually fetch. */
    private fun supported(candidates: List<AudioCandidate>): List<AudioCandidate> =
        candidates.filter { candidate -> runCatching { canDownload(candidate.url) }.getOrDefault(false) }

    private fun matchOf(candidate: AudioCandidate, score: Double, record: SongRecord): AudioMatch =
        AudioMatch(url = candidate.url, source = candidate.source, score = score, record = record)

    // ---------------------------------------------------------------- scoring

    private data class Scored(val candidate: AudioCandidate, val score: Double)

    private fun scoreAll(candidates: List<AudioCandidate>, record: SongRecord): List<Scored> =
        candidates.mapNotNull { candidate ->
            scoreCandidate(record, candidate)?.let { Scored(candidate, it) }
        }

    /** Null when a filter rejects the candidate; otherwise its 0..100 score. */
    private fun scoreCandidate(record: SongRecord, candidate: AudioCandidate): Double? {
        val title = normalize(record.title)
        val candidateTitle = normalize(candidate.title)
        var name = maxOf(
            SpotifyTextScore.ratio(title, candidateTitle),
            tokenScore(title, candidateTitle),
        )
        for (word in FORBIDDEN_WORDS) {
            if (word in candidateTitle && word !in title) name -= FORBIDDEN_PENALTY
        }
        if (name <= NAME_FLOOR) return null

        val artists = artistsScore(record, candidate)
        if (artists < ARTIST_FLOOR) return null

        val duration = durationScore(record, candidate)
        if (duration != null && duration < TIME_FLOOR) return null

        var average = (artists + name) / 2.0
        val album = albumScore(record, candidate)
        if (candidate.verified && !candidate.album.isNullOrBlank() && album <= ALBUM_ADD_LIMIT) {
            average = (average + album) / 2.0
        }
        if (duration != null) {
            if (duration < TIME_WEAK && average < WEAK_AVERAGE) return null
            if (average <= TIME_ADD_LIMIT) average = (average + duration) / 2.0
        }
        return minOf(average, 100.0)
    }

    private fun artistsScore(record: SongRecord, candidate: AudioCandidate): Double {
        val songArtists = record.artists.ifEmpty {
            listOfNotNull(record.artist.takeIf { it.isNotBlank() })
        }
        val candidateArtists = candidate.artists.ifEmpty {
            listOfNotNull(candidate.channel?.takeIf { it.isNotBlank() })
        }
        if (songArtists.isEmpty() || candidateArtists.isEmpty()) return 0.0
        return songArtists.map { songArtist ->
            candidateArtists.maxOf { candidateArtist ->
                SpotifyTextScore.ratio(normalize(songArtist), normalize(candidateArtist))
            }
        }.average()
    }

    /** spotDL's `exp(-0.1 * diff) * 100`; null when either duration is missing. */
    private fun durationScore(record: SongRecord, candidate: AudioCandidate): Double? {
        val songDuration = record.durationSeconds ?: return null
        val candidateDuration = candidate.durationSeconds ?: return null
        return exp(-0.1 * abs(songDuration - candidateDuration)) * 100.0
    }

    private fun albumScore(record: SongRecord, candidate: AudioCandidate): Double {
        val album = record.album
        val candidateAlbum = candidate.album
        if (album.isNullOrBlank() || candidateAlbum.isNullOrBlank()) return 0.0
        return SpotifyTextScore.ratio(normalize(album), normalize(candidateAlbum))
    }

    /** Fraction of the song title's words present in the candidate title. */
    private fun tokenScore(title: String, candidateTitle: String): Double {
        val titleTokens = title.split(' ').filter { it.isNotBlank() }
        if (titleTokens.isEmpty()) return 0.0
        val candidateTokens = candidateTitle.split(' ').toSet()
        val matched = titleTokens.count { it in candidateTokens }
        return 100.0 * matched / titleTokens.size
    }

    private fun normalize(text: String): String = text.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

    private fun displayName(record: SongRecord): String = songDisplayName(record)

    private fun searchQuery(record: SongRecord): String =
        if (record.artists.isEmpty()) record.title else "${record.artists.joinToString(", ")} - ${record.title}"

    private fun sourceOf(url: String): AudioSource =
        if (url.lowercase().contains("music.youtube.com")) {
            AudioSource.YOUTUBE_MUSIC
        } else {
            AudioSource.YOUTUBE
        }

    companion object {
        const val MINIMUM_SCORE: Double = 70.0
        const val EARLY_RETURN_SCORE: Double = 80.0
        const val ISRC_RETURN_SCORE: Double = 80.0

        private const val NAME_FLOOR = 60.0
        private const val ARTIST_FLOOR = 70.0
        private const val TIME_FLOOR = 25.0
        private const val TIME_WEAK = 50.0
        private const val WEAK_AVERAGE = 75.0
        private const val ALBUM_ADD_LIMIT = 80.0
        private const val TIME_ADD_LIMIT = 85.0
        private const val FORBIDDEN_PENALTY = 15.0

        /** spotDL's `FORBIDDEN_WORDS`: a variant the song itself lacks. */
        private val FORBIDDEN_WORDS = listOf(
            "bassboosted", "remix", "remastered", "remaster", "reverb", "bassboost",
            "live", "acoustic", "8daudio", "concert", "acapella", "slowed",
            "instrumental", "cover",
        )

        /** YouTube Music then YouTube, spotDL's default provider order. */
        fun default(
            search: YoutubeSearch,
            options: MatcherOptions = MatcherOptions(),
        ): AudioMatcher = AudioMatcher(
            providers = listOf(YoutubeMusicAudioProvider(search), YoutubeAudioProvider(search)),
            options = options,
        )

        /**
         * YouTube Music, then YouTube, then the enabled fallback providers in
         * spotDL's order: soundcloud, bandcamp, piped, slider.kz. The fallback
         * list is empty by default; a user must opt in.
         */
        fun withFallbacks(
            search: YoutubeSearch,
            fallbacks: List<AudioProvider>,
            options: MatcherOptions = MatcherOptions(),
            canDownload: (String) -> Boolean = { true },
        ): AudioMatcher = AudioMatcher(
            providers = listOf(YoutubeMusicAudioProvider(search), YoutubeAudioProvider(search)) + fallbacks,
            options = options,
            canDownload = canDownload,
        )
    }
}
