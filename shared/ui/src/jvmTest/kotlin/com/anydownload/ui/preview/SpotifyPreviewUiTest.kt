package com.anydownload.ui.preview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownload.core.fake.InMemoryAppGraph
import com.anydownload.core.fake.InMemoryDownloadEngine
import com.anydownload.core.music.AudioCandidate
import com.anydownload.core.music.AudioMatcher
import com.anydownload.core.music.AudioProvider
import com.anydownload.core.music.AudioSource
import com.anydownload.core.music.SongListEntry
import com.anydownload.core.music.SongListResult
import com.anydownload.core.music.SongRecord
import com.anydownload.core.music.SpotifyBackend
import com.anydownload.core.music.SpotifyDownloadService
import com.anydownload.core.music.SpotifyMetadataClient
import com.anydownload.core.music.SpotifyQuery
import com.anydownload.ui.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-086 UI path: a Spotify URL opens the preview with the song list, and
 * Download queues one engine job per song. In-memory fakes only; no network.
 */
@OptIn(ExperimentalTestApi::class)
class SpotifyPreviewUiTest {

    private val first = SongRecord(
        songId = "track1",
        title = "Never Gonna Give You Up",
        artists = listOf("Rick Astley"),
        album = "Whenever You Need Somebody",
        durationMs = 213_573,
    )

    private val second = SongRecord(
        songId = "track2",
        title = "Together Forever",
        artists = listOf("Rick Astley"),
        album = "Whenever You Need Somebody",
        durationMs = 206_000,
    )

    private class FixedMetadata(private val entries: List<SongRecord>) : SpotifyMetadataClient {
        override val backend: SpotifyBackend = SpotifyBackend.UNAUTHENTICATED
        override suspend fun resolve(query: SpotifyQuery): SongListResult = SongListResult(
            name = "Fixture Hits",
            url = "https://open.spotify.com/playlist/fixture",
            entries = entries.map { SongListEntry.Song(it) },
        )
    }

    private class TwoHitProvider : AudioProvider {
        override val source: AudioSource = AudioSource.YOUTUBE_MUSIC
        override val supportsIsrc: Boolean = false
        override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> = listOf(
            AudioCandidate(
                source = source,
                url = "https://music.youtube.com/watch?v=one",
                title = "Never Gonna Give You Up",
                artists = listOf("Rick Astley"),
                durationSeconds = 214.0,
                verified = true,
            ),
            AudioCandidate(
                source = source,
                url = "https://music.youtube.com/watch?v=two",
                title = "Together Forever",
                artists = listOf("Rick Astley"),
                durationSeconds = 206.0,
                verified = true,
            ),
        )
    }

    @Test
    fun aSpotifyUrlListsTheSongsAndDownloadQueuesThem() = runComposeUiTest {
        val engine = InMemoryDownloadEngine()
        val service = SpotifyDownloadService(
            metadata = FixedMetadata(listOf(first, second)),
            matcher = AudioMatcher(listOf(TwoHitProvider())),
            engine = engine,
            idGenerator = { "batch-ui" },
        )
        val graph = InMemoryAppGraph(engine = engine, spotify = service)
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("https://open.spotify.com/playlist/fixture")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()

        onNodeWithTag("preview-songs").assertExists()
        onNodeWithText("Never Gonna Give You Up — Rick Astley").assertExists()
        onNodeWithText("Together Forever — Rick Astley").assertExists()

        onNodeWithTag("preview-download").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { engine.jobs.value.size == 2 }

        assertEquals(2, engine.jobs.value.size)
        assertTrue(engine.jobs.value.all { it.parentBatchId == "batch-ui" })
        assertTrue(
            engine.jobs.value.all { it.request.metadata?.artists == listOf("Rick Astley") },
            "the Spotify record must ride along for tags",
        )
    }
}
