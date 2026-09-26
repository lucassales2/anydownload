package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.ClasspathFixtureStore
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The default matcher over the saved search fixtures: the real YouTube Music
 * and YouTube provider wrappers, no fake. No media is written.
 */
class AudioMatcherFixturesTest {

    private val record = SongRecord(
        songId = "4uLU6hMCjMI75M1A2tKUQC",
        title = "Never Gonna Give You Up",
        artists = listOf("Rick Astley"),
        album = "Whenever You Need Somebody",
        durationMs = 213_573,
        year = 1987,
    )

    /** Serves the saved fixture for the request body's section params. */
    private class FixtureSearchTransfer(private val musicBody: String? = null) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val body = request.body?.decodeToString().orEmpty()
            val resource = when {
                !request.url.contains("music.youtube.com") -> "youtube_videos.json"
                musicBody != null -> musicBody
                body.contains("EgWKAQII") -> "ytm_songs.json"
                else -> "ytm_videos.json"
            }
            val text = if (resource.startsWith("{")) {
                resource
            } else {
                ClasspathFixtureStore.read("fixtures/youtube-search/$resource")
            } ?: error("missing fixture $resource")
            val bytes = text.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/json",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    @Test
    fun theDefaultMatcherReturnsTheMusicFixtureHitBeforeYouTube() = runTest {
        val transfer = FixtureSearchTransfer()
        val matcher = AudioMatcher.default(YoutubeSearch(ExtractorHttp(transfer)))

        val match = matcher.match(record)

        assertEquals(AudioSource.YOUTUBE_MUSIC, match.source)
        assertEquals("https://music.youtube.com/watch?v=lYBUbBu4W08", match.url)
        assertTrue(match.score >= AudioMatcher.EARLY_RETURN_SCORE)
        assertTrue(
            transfer.requests.none { it.url.contains("www.youtube.com") },
            "YouTube must not be searched after a Music hit",
        )
    }

    @Test
    fun theDefaultMatcherFallsBackToTheYoutubeFixture() = runTest {
        val transfer = FixtureSearchTransfer(musicBody = """{"contents":{}}""")
        val matcher = AudioMatcher.default(YoutubeSearch(ExtractorHttp(transfer)))

        val match = matcher.match(record)

        assertEquals(AudioSource.YOUTUBE, match.source)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", match.url)
        assertTrue(transfer.requests.any { it.url.contains("www.youtube.com") })
    }
}
