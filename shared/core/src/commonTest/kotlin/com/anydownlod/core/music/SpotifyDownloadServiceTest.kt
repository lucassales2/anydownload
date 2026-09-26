package com.anydownlod.core.music

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.OverwriteMode
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.fake.InMemorySpotifyListStore
import com.anydownlod.core.postprocess.ToolkitCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Spotify download service over fakes: resolve, match, and queue child
 * jobs. No engine downloads in the in-memory fake, no network, no media file.
 */
class SpotifyDownloadServiceTest {

    private val first = SongRecord(
        songId = "track1",
        title = "Never Gonna Give You Up",
        artists = listOf("Rick Astley"),
        album = "Whenever You Need Somebody",
        durationMs = 213_573,
        isrc = "GBARL9300135",
        artworkUrl = "https://i.scdn.co/image/fixture-cover",
        year = 1987,
    )

    private val second = SongRecord(
        songId = "track2",
        title = "Together Forever",
        artists = listOf("Rick Astley"),
        album = "Whenever You Need Somebody",
        durationMs = 206_000,
    )

    private fun metadataWith(vararg entries: SongListEntry) = object : SpotifyMetadataClient {
        override val backend: SpotifyBackend = SpotifyBackend.UNAUTHENTICATED
        override suspend fun resolve(query: SpotifyQuery): SongListResult =
            SongListResult(name = "Fixture Hits", url = "https://open.spotify.com/playlist/fixture", entries = entries.toList())
    }

    /** Returns one candidate for each title key the query contains. */
    private class TitleProvider(
        private val urls: Map<String, String>,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : AudioProvider {
        override val source: AudioSource = AudioSource.YOUTUBE_MUSIC
        override val supportsIsrc: Boolean = false
        var calls = 0
            private set

        override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> {
            calls++
            if (calls >= 2) gate?.await()
            return urls.entries
                .filter { query.contains(it.key) }
                .map { (title, url) ->
                    AudioCandidate(
                        source = source,
                        url = url,
                        title = title,
                        artists = listOf("Rick Astley"),
                        durationSeconds = 214.0,
                        verified = true,
                    )
                }
        }
    }

    private fun service(
        provider: AudioProvider,
        vararg entries: SongListEntry,
        engine: InMemoryDownloadEngine = InMemoryDownloadEngine(),
        listStore: SpotifyListStore = SpotifyListStore.Unavailable,
        lyrics: LyricsFetcher? = null,
    ): Triple<SpotifyDownloadService, InMemoryDownloadEngine, AudioMatcher> {
        val matcher = AudioMatcher(listOf(provider))
        return Triple(
            SpotifyDownloadService(
                metadataWith(*entries),
                matcher,
                engine,
                listStore = listStore,
                lyrics = lyrics,
                idGenerator = { "batch-1" },
            ),
            engine,
            matcher,
        )
    }

    private class FakeLyricsProvider(
        override val name: String,
        private val byTitle: Map<String, LyricsResult>,
    ) : LyricsProvider {
        override suspend fun fetch(title: String, artists: List<String>): LyricsResult? = byTitle[title]
    }

    private class MutableMetadata : SpotifyMetadataClient {
        var entries: List<SongListEntry> = emptyList()
        override val backend: SpotifyBackend = SpotifyBackend.UNAUTHENTICATED
        override suspend fun resolve(query: SpotifyQuery): SongListResult =
            SongListResult(name = "Fixture Hits", entries = entries)
    }

    private fun syncService(
        metadata: SpotifyMetadataClient,
        provider: AudioProvider,
        engine: InMemoryDownloadEngine = InMemoryDownloadEngine(),
        listStore: SpotifyListStore = SpotifyListStore.Unavailable,
    ): SpotifyDownloadService = SpotifyDownloadService(
        metadata = metadata,
        matcher = AudioMatcher(listOf(provider)),
        engine = engine,
        listStore = listStore,
        idGenerator = { "sync-1" },
    )

    @Test
    fun previewMapsSongsAndUnavailableEntries() = runTest {
        val (service, _, _) = service(
            TitleProvider(emptyMap()),
            SongListEntry.Song(first),
            SongListEntry.Unavailable(reason = UnavailableReason.LOCAL_TRACK, title = "Local Song"),
        )

        val preview = service.preview("https://open.spotify.com/playlist/fixture")

        assertEquals("Fixture Hits", preview.name)
        assertEquals(listOf(first), preview.songs)
        assertEquals(1, preview.unavailable.size)
        assertEquals("This is a local Spotify file.", preview.unavailable.single().reason)
        assertTrue(preview.isList)
    }

    @Test
    fun queueSubmitsOneChildJobPerSongWithTagsAndBatch() = runTest {
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val (service, engine, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
        )
        val preview = service.preview("playlist:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3, AudioContainer.M4A)),
        )

        assertEquals("batch-1", report.batchId)
        assertTrue(report.failures.isEmpty())
        assertEquals(2, report.jobs.size)
        assertEquals(listOf("batch-1", "batch-1"), report.jobs.map { it.request.parentBatchId })
        assertEquals(
            listOf("https://music.youtube.com/watch?v=one", "https://music.youtube.com/watch?v=two"),
            report.jobs.map { it.request.sourceUrl },
        )
        val firstRequest = report.jobs.first().request
        assertEquals(MediaType.AUDIO, firstRequest.options.mediaType)
        assertEquals(AudioContainer.MP3, firstRequest.options.audioContainer)
        assertEquals("Never Gonna Give You Up", firstRequest.metadata?.title)
        assertEquals(listOf("Rick Astley"), firstRequest.metadata?.artists)
        assertEquals("GBARL9300135", firstRequest.metadata?.isrc)
        assertEquals(first.artworkUrl, firstRequest.artworkUrl)
        // The fake engine mirrors the batch id on the job row.
        assertEquals("batch-1", report.jobs.first().parentBatchId)
        assertEquals(2, engine.jobs.value.size)
    }

    @Test
    fun oneFailedSongDoesNotHideTheOthers() = runTest {
        val provider = TitleProvider(
            mapOf("Together Forever" to "https://music.youtube.com/watch?v=two"),
        )
        val (service, _, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
            SongListEntry.Unavailable(reason = UnavailableReason.NOT_A_TRACK, title = "An Episode"),
        )
        val preview = service.preview("https://open.spotify.com/album/fixture")

        val report = service.queue(preview, DownloadOptions())

        assertEquals(1, report.jobs.size)
        assertEquals(2, report.failures.size)
        assertTrue(report.failures.any { it.title == "Rick Astley - Never Gonna Give You Up" })
        assertTrue(report.failures.any { it.title == "An Episode" })
        assertEquals("https://music.youtube.com/watch?v=two", report.jobs.single().request.sourceUrl)
    }

    @Test
    fun containerChoiceFollowsTheHostCapabilities() = runTest {
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (m4aService, _, _) = service(provider, SongListEntry.Song(first))
        val (plainService, _, _) = service(provider, SongListEntry.Song(first))

        val m4a = m4aService.queue(
            m4aService.preview("track:fixture"),
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.M4A)),
        )
        assertEquals(AudioContainer.M4A, m4a.jobs.single().request.options.audioContainer)

        val none = plainService.queue(
            plainService.preview("track:fixture"),
            DownloadOptions(),
            ToolkitCapabilities.Unavailable,
        )
        assertEquals(null, none.jobs.single().request.options.audioContainer)
    }

    @Test
    fun cancelStopsExpansionAndLeavesEarlierJobs() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
            gate = gate,
        )
        val (service, engine, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
        )
        val preview = service.preview("playlist:fixture")

        val queued = launch { service.queue(preview, DownloadOptions()) }
        advanceUntilIdle()
        assertEquals(1, engine.jobs.value.size, "the first song must be queued before the gate")

        queued.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, engine.jobs.value.size, "cancel must not remove the job already created")
        assertEquals(2, provider.calls)
    }

    // ------------------------------------------------------ T-087 list features

    @Test
    fun theTemplateNamesTheFilesAndTheM3uListsThemInOrder() = runTest {
        val store = InMemorySpotifyListStore()
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val (service, _, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
            listStore = store,
        )
        val preview = service.preview("playlist:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3)),
            SpotifyListOptions(writeM3u = true, m3uName = "Fixture Hits.m3u8"),
        )

        assertEquals(
            listOf(
                "Rick Astley - Never Gonna Give You Up.mp3",
                "Rick Astley - Together Forever.mp3",
            ),
            report.jobs.map { it.request.relativePath },
        )
        assertEquals("Fixture Hits.m3u8", report.m3uPath)
        val m3u = store.files["Fixture Hits.m3u8"].orEmpty()
        assertTrue(m3u.startsWith("#EXTM3U\n"), m3u)
        assertTrue(
            m3u.indexOf("Rick Astley - Never Gonna Give You Up.mp3") <
                m3u.indexOf("Rick Astley - Together Forever.mp3"),
            m3u,
        )
        assertTrue(m3u.contains("#EXTINF:213,Rick Astley - Never Gonna Give You Up"), m3u)
    }

    @Test
    fun anArchivedSongIsNotQueuedAgain() = runTest {
        val store = InMemorySpotifyListStore()
        store.write(SpotifyListOptions.DEFAULT_ARCHIVE, "track1\n")
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val (service, _, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
            listStore = store,
        )
        val preview = service.preview("playlist:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            listOptions = SpotifyListOptions(archive = true),
        )

        assertEquals(1, report.jobs.size)
        assertEquals("https://music.youtube.com/watch?v=two", report.jobs.single().request.sourceUrl)
        assertEquals(1, report.skipped.size)
        assertEquals("Already in the archive.", report.skipped.single().reason)
        val archive = store.readLines(SpotifyListOptions.DEFAULT_ARCHIVE).orEmpty()
        assertEquals(listOf("track1", "track2"), archive)
    }

    @Test
    fun skipExplicitDropsTheSongAndSaysSo() = runTest {
        val explicit = first.copy(explicit = true)
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, engine, _) = service(provider, SongListEntry.Song(explicit))
        val preview = service.preview("track:fixture")

        val report = service.queue(preview, DownloadOptions(), listOptions = SpotifyListOptions(skipExplicit = true))

        assertTrue(report.jobs.isEmpty())
        assertEquals(1, report.skipped.size)
        assertEquals("Explicit song skipped.", report.skipped.single().reason)
        assertTrue(engine.jobs.value.isEmpty())
    }

    @Test
    fun theOverwriteModeAndTemplateRideOnTheRequest() = runTest {
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, _, _) = service(provider, SongListEntry.Song(first.copy(trackNumber = 1)))
        val preview = service.preview("track:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3)),
            SpotifyListOptions(
                overwrite = OverwriteMode.FORCE,
                outputTemplate = "{album}/{track-number} - {title}.{output-ext}",
                restrict = SpotifyRestrict.STRICT,
            ),
        )

        val request = report.jobs.single().request
        assertEquals(OverwriteMode.FORCE, request.options.overwrite)
        assertEquals("Whenever You Need Somebody/01 - Never Gonna Give You Up.mp3", request.relativePath)
    }

    @Test
    fun scanForSongsSkipsAnotherKnownExtensionInTheRoot() = runTest {
        val store = InMemorySpotifyListStore()
        store.write("Rick Astley - Never Gonna Give You Up.flac", "fixture")
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, engine, _) = service(
            provider,
            SongListEntry.Song(first),
            listStore = store,
        )
        val preview = service.preview("track:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3)),
            SpotifyListOptions(scanForSongs = true),
        )

        assertTrue(report.jobs.isEmpty())
        assertEquals("A matching file already exists.", report.skipped.single().reason)
        assertTrue(engine.jobs.value.isEmpty())
    }

    @Test
    fun playlistNumberingRewritesTheTrackNumberBeforeTemplating() = runTest {
        val positioned = first.copy(listName = "Fixture Hits", listPosition = 7, listLength = 12)
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, _, _) = service(provider, SongListEntry.Song(positioned))
        val preview = service.preview("playlist:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3)),
            SpotifyListOptions(
                playlistNumbering = true,
                outputTemplate = "{list-name}/{list-position} - {title}.{output-ext}",
            ),
        )

        assertEquals("Fixture Hits/07 - Never Gonna Give You Up.mp3", report.jobs.single().request.relativePath)
    }

    // -------------------------------------------------------------- T-088 lyrics

    @Test
    fun lyricsRideOnTheRequestAndAMissIsRecorded() = runTest {
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val lyrics = LyricsFetcher(
            mapOf(
                LyricsProviders.GENIUS to FakeLyricsProvider(
                    LyricsProviders.GENIUS,
                    mapOf("Never Gonna Give You Up" to LyricsResult("genius lyrics")),
                ),
            ),
        )
        val (service, _, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
            lyrics = lyrics,
        )
        val preview = service.preview("playlist:fixture")

        val report = service.queue(preview, DownloadOptions())

        assertEquals(2, report.jobs.size)
        assertEquals("genius lyrics", report.jobs[0].request.metadata?.lyrics)
        assertEquals(null, report.jobs[1].request.metadata?.lyrics)
        assertEquals(listOf("Rick Astley - Together Forever"), report.lyricsMisses)
        assertEquals(null, report.jobs[0].request.lrcContent, "plain lyrics never write an LRC")
    }

    @Test
    fun generateLrcWritesOnlySyncedLines() = runTest {
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val lyrics = LyricsFetcher(
            mapOf(
                LyricsProviders.SYNCED to FakeLyricsProvider(
                    LyricsProviders.SYNCED,
                    mapOf(
                        "Never Gonna Give You Up" to LyricsResult("[00:01.00] timed", synced = true),
                        "Together Forever" to LyricsResult("plain"),
                    ),
                ),
            ),
        )
        val (service, _, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
            lyrics = lyrics,
        )
        val preview = service.preview("playlist:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            lyricsOptions = SpotifyLyricsOptions(providers = listOf(LyricsProviders.SYNCED), generateLrc = true),
        )

        assertEquals("[00:01.00] timed", report.jobs[0].request.lrcContent)
        assertEquals(null, report.jobs[1].request.lrcContent)
        assertEquals("[00:01.00] timed", report.jobs[0].request.metadata?.lyrics)
        assertEquals("plain", report.jobs[1].request.metadata?.lyrics)
    }

    @Test
    fun lyricsCanBeDisabled() = runTest {
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val lyrics = LyricsFetcher(
            mapOf(
                LyricsProviders.GENIUS to FakeLyricsProvider(
                    LyricsProviders.GENIUS,
                    mapOf("Never Gonna Give You Up" to LyricsResult("genius lyrics")),
                ),
            ),
        )
        val (service, _, _) = service(provider, SongListEntry.Song(first), lyrics = lyrics)
        val preview = service.preview("track:fixture")

        val report = service.queue(
            preview,
            DownloadOptions(),
            lyricsOptions = SpotifyLyricsOptions(enabled = false),
        )

        assertEquals(null, report.jobs.single().request.metadata?.lyrics)
        assertTrue(report.lyricsMisses.isEmpty())
    }

    // --------------------------------------------------- save / url / meta (T-090)

    @Test
    fun saveWritesASpotdlFileAndNoAudio() = runTest {
        val store = InMemorySpotifyListStore()
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val (service, engine, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
            listStore = store,
        )
        val preview = service.preview("playlist:fixture")

        val report = service.save(preview, "Fixture Hits.spotdl", preload = true)

        assertEquals("Fixture Hits.spotdl", report.path)
        assertEquals(2, report.savedCount)
        assertTrue(report.failures.isEmpty())
        assertTrue(engine.jobs.value.isEmpty(), "save must not start a download")
        val text = store.files["Fixture Hits.spotdl"].orEmpty()
        assertTrue(SpotifySaveFiles.isSaveFile(report.path))
        val decoded = SpotifySaveFiles.decode(text)
        assertEquals(2, decoded.songs.size)
        assertEquals("track1", decoded.songs[0].record.songId)
        assertEquals("https://music.youtube.com/watch?v=one", decoded.songs[0].downloadUrl)
        assertTrue(decoded.songs.all { it.downloadUrl != null })
    }

    @Test
    fun aSavedFileReloadsAsAPreview() = runTest {
        val store = InMemorySpotifyListStore()
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, _, _) = service(provider, SongListEntry.Song(first), listStore = store)
        val preview = service.preview("track:fixture")
        service.save(preview, "Fixture Hits.spotdl")

        val reloaded = service.previewSaveFile("Fixture Hits.spotdl")

        assertEquals(1, reloaded.songs.size)
        assertEquals(first, reloaded.songs.single())
        assertEquals("track:fixture", reloaded.query)
    }

    @Test
    fun saveRejectsAPathOutsideTheDownloadRoot() = runTest {
        val (service, _, _) = service(
            TitleProvider(emptyMap()),
            SongListEntry.Song(first),
            listStore = InMemorySpotifyListStore(),
        )
        val preview = service.preview("track:fixture")
        assertFailsWith<IllegalArgumentException> {
            service.save(preview, "../escape.spotdl")
        }
        assertFailsWith<IllegalArgumentException> {
            service.save(preview, "/absolute.spotdl")
        }
        assertFailsWith<IllegalArgumentException> {
            service.save(preview, "Fixture.txt")
        }
    }

    @Test
    fun urlsReturnOneUrlPerSongAndStartNoJobs() = runTest {
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Together Forever" to "https://music.youtube.com/watch?v=two",
            ),
        )
        val (service, engine, _) = service(
            provider,
            SongListEntry.Song(first),
            SongListEntry.Song(second),
        )
        val preview = service.preview("playlist:fixture")

        val entries = service.urls(preview)

        assertEquals(2, entries.size)
        assertEquals("https://music.youtube.com/watch?v=one", entries[0].url)
        assertEquals("https://music.youtube.com/watch?v=two", entries[1].url)
        assertTrue(entries.all { it.failure == null })
        assertTrue(engine.jobs.value.isEmpty(), "url must not start a download")
    }

    @Test
    fun urlsReportAMissPerSong() = runTest {
        val provider = TitleProvider(mapOf("Together Forever" to "https://music.youtube.com/watch?v=two"))
        val (service, _, _) = service(provider, SongListEntry.Song(first))
        val entries = service.urls(service.preview("track:fixture"))
        assertEquals(1, entries.size)
        assertEquals(null, entries.single().url)
        assertTrue(entries.single().failure != null)
    }

    @Test
    fun metaQueuesAMetadataJobForAnExistingFile() = runTest {
        val store = InMemorySpotifyListStore()
        store.write("Rick Astley - Never Gonna Give You Up.mp3", "fixture")
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, _, _) = service(provider, SongListEntry.Song(first), listStore = store)
        val preview = service.preview("track:fixture")

        val report = service.meta(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3), canEmbedTags = true),
        )

        assertEquals(1, report.jobs.size)
        val request = report.jobs.single().request
        assertEquals(OverwriteMode.METADATA, request.options.overwrite)
        assertEquals("Rick Astley - Never Gonna Give You Up.mp3", request.relativePath)
        assertEquals(first.artworkUrl, request.artworkUrl)
        assertEquals("Never Gonna Give You Up", request.metadata?.title)
        assertEquals(listOf("Rick Astley"), request.metadata?.artists)
    }

    @Test
    fun metaRedownloadUsesForceAndSkipAlbumArtClearsTheArtwork() = runTest {
        val store = InMemorySpotifyListStore()
        store.write("Rick Astley - Never Gonna Give You Up.mp3", "fixture")
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, _, _) = service(provider, SongListEntry.Song(first), listStore = store)
        val preview = service.preview("track:fixture")

        val report = service.meta(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3), canEmbedTags = true),
            skipAlbumArt = true,
            redownload = true,
        )

        val request = report.jobs.single().request
        assertEquals(OverwriteMode.FORCE, request.options.overwrite)
        assertEquals(null, request.artworkUrl)
    }

    @Test
    fun metaSkipsAFileThatIsNotInTheRoot() = runTest {
        val provider = TitleProvider(mapOf("Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one"))
        val (service, engine, _) = service(
            provider,
            SongListEntry.Song(first),
            listStore = InMemorySpotifyListStore(),
        )
        val preview = service.preview("track:fixture")

        val report = service.meta(
            preview,
            DownloadOptions(),
            ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3), canEmbedTags = true),
        )

        assertTrue(report.jobs.isEmpty())
        assertEquals("No matching file in the download root.", report.skipped.single().reason)
        assertTrue(engine.jobs.value.isEmpty())
    }

    // ------------------------------------------------------------------ T-091 sync

    private fun twoSongProvider() = TitleProvider(
        mapOf(
            "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
            "Together Forever" to "https://music.youtube.com/watch?v=two",
        ),
    )

    private val mp3Capabilities = ToolkitCapabilities(audioContainers = setOf(AudioContainer.MP3), canEmbedTags = true)

    @Test
    fun theFirstSyncDownloadsEverythingAndWritesTheFile() = runTest {
        val store = InMemorySpotifyListStore()
        store.write("foreign.mp3", "not ours")
        val metadata = MutableMetadata().apply {
            entries = listOf(SongListEntry.Song(first), SongListEntry.Song(second))
        }
        val service = syncService(metadata, twoSongProvider(), listStore = store)

        val plan = service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities)
        assertTrue(plan.removals.isEmpty())
        assertEquals(2, plan.additions.size)

        val report = service.applySync(plan, DownloadOptions(), mp3Capabilities)

        assertEquals(2, report.jobs.size)
        assertTrue(report.deleted.isEmpty())
        val save = SpotifySaveFiles.decode(store.files["Fixture Hits.spotdl"].orEmpty())
        assertTrue(save.sync)
        assertEquals(2, save.songs.size)
        assertTrue(save.songs.all { it.createdFiles.isNotEmpty() })
        assertTrue(store.exists("foreign.mp3"), "a file the sync did not create must stay")
    }

    @Test
    fun theSecondSyncAddsAndDeletesOnlyRemovedFiles() = runTest {
        val store = InMemorySpotifyListStore()
        val metadata = MutableMetadata().apply {
            entries = listOf(SongListEntry.Song(first), SongListEntry.Song(second))
        }
        val service = syncService(metadata, twoSongProvider(), listStore = store)
        service.applySync(
            service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities),
            DownloadOptions(),
            mp3Capabilities,
        )
        // The fake engine writes no media; seed the files the first sync created.
        store.write("Rick Astley - Never Gonna Give You Up.mp3", "audio-a")
        store.write("Rick Astley - Together Forever.mp3", "audio-b")
        store.write("foreign.mp3", "not ours")

        // The list now drops Together Forever and adds a third song.
        val third = SongRecord(
            songId = "track3",
            title = "Fixture New",
            artists = listOf("Rick Astley"),
            durationMs = 213_573,
        )
        metadata.entries = listOf(SongListEntry.Song(first), SongListEntry.Song(third))
        val provider = TitleProvider(
            mapOf(
                "Never Gonna Give You Up" to "https://music.youtube.com/watch?v=one",
                "Fixture New" to "https://music.youtube.com/watch?v=three",
            ),
        )
        val secondService = syncService(metadata, provider, listStore = store)

        val plan = secondService.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities)
        assertEquals(1, plan.additions.size)
        assertEquals("track3", plan.additions.single().songId)
        assertEquals(1, plan.removals.size)
        assertEquals(listOf("Rick Astley - Together Forever.mp3"), plan.removals.single().files)
        // The delete list is visible before anything is removed.
        assertTrue(store.exists("Rick Astley - Together Forever.mp3"))

        val report = secondService.applySync(plan, DownloadOptions(), mp3Capabilities)

        assertEquals(1, report.jobs.size)
        assertEquals(listOf("Rick Astley - Together Forever.mp3"), report.deleted)
        assertTrue(store.exists("Rick Astley - Never Gonna Give You Up.mp3"), "a kept song must stay")
        assertTrue(store.exists("foreign.mp3"), "a file the sync did not create must stay")
    }

    @Test
    fun syncWithoutDeletingDownloadsAdditionsAndDeletesNothing() = runTest {
        val store = InMemorySpotifyListStore()
        val metadata = MutableMetadata().apply {
            entries = listOf(SongListEntry.Song(first), SongListEntry.Song(second))
        }
        val service = syncService(metadata, twoSongProvider(), listStore = store)
        service.applySync(
            service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities),
            DownloadOptions(),
            mp3Capabilities,
        )
        store.write("Rick Astley - Together Forever.mp3", "audio-b")

        metadata.entries = listOf(SongListEntry.Song(first))
        val plan = service.planSync(
            "playlist:fixture",
            "Fixture Hits.spotdl",
            mp3Capabilities,
            deleteRemoved = false,
        )
        assertTrue(plan.removals.isEmpty())

        val report = service.applySync(plan, DownloadOptions(), mp3Capabilities)

        assertTrue(report.jobs.isEmpty())
        assertTrue(report.deleted.isEmpty())
        assertTrue(store.exists("Rick Astley - Together Forever.mp3"))
    }

    @Test
    fun aFailedAdditionKeepsTheOldFiles() = runTest {
        val store = InMemorySpotifyListStore()
        val metadata = MutableMetadata().apply {
            entries = listOf(SongListEntry.Song(first), SongListEntry.Song(second))
        }
        val service = syncService(metadata, twoSongProvider(), listStore = store)
        service.applySync(
            service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities),
            DownloadOptions(),
            mp3Capabilities,
        )
        store.write("Rick Astley - Together Forever.mp3", "audio-b")

        // The list drops Together Forever and adds a song the provider cannot match.
        val unmatched = SongRecord(songId = "track3", title = "Unmatchable Song", artists = listOf("Nobody"))
        metadata.entries = listOf(SongListEntry.Song(first), SongListEntry.Song(unmatched))
        val plan = service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities)
        assertEquals(1, plan.removals.size)

        val report = service.applySync(plan, DownloadOptions(), mp3Capabilities)

        assertTrue(report.jobs.isEmpty())
        assertTrue(report.failures.isNotEmpty())
        assertEquals(1, report.skippedDeletions.size)
        assertTrue(store.exists("Rick Astley - Together Forever.mp3"), "a failed addition must keep the old set")
    }

    @Test
    fun aRemovalAlsoDeletesTheLrcSibling() = runTest {
        val store = InMemorySpotifyListStore()
        val metadata = MutableMetadata().apply {
            entries = listOf(SongListEntry.Song(first), SongListEntry.Song(second))
        }
        val service = syncService(metadata, twoSongProvider(), listStore = store)
        service.applySync(
            service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities),
            DownloadOptions(),
            mp3Capabilities,
        )
        store.write("Rick Astley - Together Forever.mp3", "audio-b")
        store.write("Rick Astley - Together Forever.lrc", "[00:01.00] line")

        metadata.entries = listOf(SongListEntry.Song(first))
        val plan = service.planSync("playlist:fixture", "Fixture Hits.spotdl", mp3Capabilities)

        assertEquals(
            listOf(
                "Rick Astley - Together Forever.mp3",
                "Rick Astley - Together Forever.lrc",
            ),
            plan.removals.single().files,
        )

        val report = service.applySync(plan, DownloadOptions(), mp3Capabilities)
        assertTrue(report.deleted.contains("Rick Astley - Together Forever.lrc"))
        assertTrue(!store.exists("Rick Astley - Together Forever.lrc"))
    }
}
