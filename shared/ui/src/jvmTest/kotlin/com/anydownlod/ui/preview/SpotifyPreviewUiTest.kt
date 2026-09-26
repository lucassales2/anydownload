package com.anydownlod.ui.preview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.music.AudioCandidate
import com.anydownlod.core.music.AudioMatcher
import com.anydownlod.core.music.AudioProvider
import com.anydownlod.core.music.AudioSource
import com.anydownlod.core.music.SongListEntry
import com.anydownlod.core.music.SongListResult
import com.anydownlod.core.music.SongRecord
import com.anydownlod.core.music.SpotifyBackend
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyMetadataClient
import com.anydownlod.core.music.SpotifyQuery
import com.anydownlod.ui.App
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

        onNodeWithTag("preview-download").performClick()
        waitForIdle()

        assertEquals(2, engine.jobs.value.size)
        assertTrue(engine.jobs.value.all { it.parentBatchId == "batch-ui" })
        assertTrue(
            engine.jobs.value.all { it.request.metadata?.artists == listOf("Rick Astley") },
            "the Spotify record must ride along for tags",
        )
    }
}
