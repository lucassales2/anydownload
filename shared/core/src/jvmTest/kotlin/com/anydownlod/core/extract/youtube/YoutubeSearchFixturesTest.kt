package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.ClasspathFixtureStore
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The saved search result documents under
 * `commonTest/resources/fixtures/youtube-search/`. They follow the public
 * innertube shapes but keep only the fields the matcher scores: no thumbnail,
 * no visitor data, and no signed URL.
 */
class YoutubeSearchFixturesTest {

    private val fixtures = listOf("ytm_songs.json", "ytm_videos.json", "youtube_videos.json")

    private fun search(resource: String): YoutubeSearch {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://*.youtube.com/youtubei/v1/search*",
                    method = "POST",
                    contentType = "application/json",
                    bodyResource = "fixtures/youtube-search/$resource",
                ),
            ),
            ClasspathFixtureStore,
        )
        return YoutubeSearch(ExtractorHttp(transfer))
    }

    @Test
    fun youtubeMusicSongFixtureParses() = runTest {
        val results = search("ytm_songs.json").searchMusic("fixture", MusicSearchFilter.SONGS)
        assertEquals(2, results.size)
        val first = results[0]
        assertEquals("lYBUbBu4W08", first.videoId)
        assertEquals("Never Gonna Give You Up", first.title)
        assertEquals(listOf("Rick Astley"), first.artists)
        assertEquals("Whenever You Need Somebody", first.album)
        assertEquals(214.0, first.durationSeconds)
        assertTrue(first.verified)
        val second = results[1]
        assertEquals("Together Forever", second.title)
        assertEquals(206.0, second.durationSeconds)
        assertTrue(second.verified)
    }

    @Test
    fun youtubeMusicVideoFixtureParses() = runTest {
        val result = search("ytm_videos.json").searchMusic("fixture", MusicSearchFilter.VIDEOS).single()
        assertEquals("dQw4w9WgXcQ", result.videoId)
        assertEquals("Rick Astley", result.channel)
        assertEquals(1_800_000_000L, result.viewCount)
        assertEquals(false, result.verified)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result.url)
    }

    @Test
    fun youtubeFixtureParses() = runTest {
        val results = search("youtube_videos.json").searchYoutube("fixture")
        assertEquals(2, results.size)
        assertEquals("Rick Astley", results[0].channel)
        assertEquals(1_819_912_851L, results[0].viewCount)
        assertEquals(false, results[0].verified)
        assertEquals("https://www.youtube.com/watch?v=3BFTio5296w", results[1].url)
    }

    @Test
    fun noSavedSearchFixtureCarriesSignedOrSessionData() {
        for (name in fixtures) {
            val text = ClasspathFixtureStore.read("fixtures/youtube-search/$name")
            assertNotNull(text, "missing fixture $name")
            for (forbidden in listOf("visitorData", "googlevideo", "sqp=", "rs=", "ytimg", "yt3.googleusercontent")) {
                assertTrue(forbidden !in text, "$name must not contain '$forbidden'")
            }
        }
    }
}
