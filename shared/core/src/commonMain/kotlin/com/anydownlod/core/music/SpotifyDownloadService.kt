/*
 * Spotify download service — AnyDownload
 *
 * The seam T-086's UI and tests use: resolve a Spotify query through T-084,
 * match each song through T-085, and hand each matched URL to the existing
 * [DownloadEngine] as a child job. Tags and the artwork URL ride on the
 * request so the engine can embed them through the D5 toolkit.
 *
 * This class never downloads media itself, never spawns spotdl, and never
 * returns a Spotify audio stream. One song's failure is recorded and the
 * others still queue; cancellation stops the expansion and leaves the child
 * jobs already created.
 */
package com.anydownlod.core.music

import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.OverwriteMode
import com.anydownlod.core.postprocess.ToolkitCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.random.Random

/** One preview row: a resolved record, or an unavailable entry. */
data class SpotifyPreviewEntry(
    val record: SongRecord? = null,
    /** Short, redacted reason when [record] is null. */
    val reason: String? = null,
    val title: String? = null,
)

/** What a Spotify query resolved to, before any matching or download. */
data class SpotifyPreview(
    val query: String,
    val name: String,
    val url: String? = null,
    val artworkUrl: String? = null,
    val entries: List<SpotifyPreviewEntry> = emptyList(),
) {
    val songs: List<SongRecord> get() = entries.mapNotNull { it.record }
    val unavailable: List<SpotifyPreviewEntry> get() = entries.filter { it.record == null }
    val isList: Boolean get() = entries.size > 1
}

/** One song that could not be matched or queued. */
data class SpotifyQueueFailure(
    val title: String,
    val reason: String,
)

/** What one Download click queued. */
data class SpotifyQueueReport(
    val batchId: String,
    val jobs: List<DownloadJob>,
    val failures: List<SpotifyQueueFailure>,
    /** Songs deliberately not queued (explicit, archived, already on disk). */
    val skipped: List<SpotifyQueueFailure> = emptyList(),
    /** Songs that were queued but had no lyrics hit; the audio is still kept. */
    val lyricsMisses: List<String> = emptyList(),
    /** Relative path of the written m3u, when one was requested. */
    val m3uPath: String? = null,
    /** Relative path of the written archive, when one was requested. */
    val archivePath: String? = null,
)

class SpotifyDownloadService(
    private val metadata: SpotifyMetadataClient,
    private val matcher: AudioMatcher,
    private val engine: DownloadEngine,
    /** Root-scoped file access for the m3u and archive sidecars. */
    private val listStore: SpotifyListStore = SpotifyListStore.Unavailable,
    /** Lyrics backends; null disables lyrics entirely. */
    private val lyrics: LyricsFetcher? = null,
    /** The user's library; null when the host has no on-device Spotify login. */
    private val library: SpotifyLibraryClient? = null,
    private val idGenerator: () -> String = { "spotify-${Random.nextLong().toULong().toString(16)}" },
) {

    /** Resolves the query into song records; no matcher, no engine call. */
    suspend fun preview(raw: String): SpotifyPreview {
        SpotifyLibraryQueries.parse(raw)?.let { libraryQuery ->
            val client = library
                ?: throw SpotifyMetadataError.NotAuthorized("Log in to Spotify to use this query.")
            return previewOf(raw, client.resolve(libraryQuery))
        }
        val query = SpotifyQueryParser.parse(raw) ?: throw SpotifyMetadataError.BadQuery()
        return previewOf(raw, metadata.resolve(query))
    }

    /** The shared record-to-preview mapping for public and library queries. */
    private fun previewOf(raw: String, result: SongListResult): SpotifyPreview = SpotifyPreview(
        query = raw,
        name = result.name,
        url = result.url,
        artworkUrl = result.coverUrl,
        entries = result.entries.map { entry ->
            when (entry) {
                is SongListEntry.Song -> SpotifyPreviewEntry(record = entry.record)
                is SongListEntry.Unavailable -> SpotifyPreviewEntry(
                    reason = entry.displayMessage(),
                    title = entry.title,
                )
            }
        },
    )

    /**
     * Matches every song and submits one engine job per hit. The container is
     * MP3 when the host can encode it, otherwise the best native container
     * (M4A or Opus), otherwise null so the engine keeps the source container.
     * [listOptions] drives the output template, overwrite mode, m3u, archive,
     * skip-explicit, playlist numbering, and scan-for-songs; [listStore] is
     * the root-scoped file seam those features need.
     */
    suspend fun queue(
        preview: SpotifyPreview,
        options: DownloadOptions,
        capabilities: ToolkitCapabilities = ToolkitCapabilities.Unavailable,
        listOptions: SpotifyListOptions = SpotifyListOptions(),
        lyricsOptions: SpotifyLyricsOptions = SpotifyLyricsOptions(),
    ): SpotifyQueueReport {
        val batchId = idGenerator()
        val container = preferredAudioContainer(capabilities)
        val extension = container?.wireName
        val jobs = mutableListOf<DownloadJob>()
        val failures = mutableListOf<SpotifyQueueFailure>()
        val skipped = mutableListOf<SpotifyQueueFailure>()
        val lyricsMisses = mutableListOf<String>()
        val queued = mutableListOf<QueuedSong>()
        val archived = if (listOptions.archive) {
            listStore.readLines(listOptions.archiveName).orEmpty().toSet()
        } else {
            emptySet()
        }

        preview.entries.forEachIndexed { index, entry ->
            // Cancel stops the expansion here; jobs already submitted stay.
            currentCoroutineContext().ensureActive()
            val record = entry.record
            if (record == null) {
                failures += SpotifyQueueFailure(
                    title = entry.title ?: "Unavailable entry",
                    reason = entry.reason ?: "This Spotify entry is unavailable.",
                )
                return@forEachIndexed
            }
            val numbered = if (listOptions.playlistNumbering) numberedRecord(record) else record
            if (listOptions.skipExplicit && numbered.explicit) {
                skipped += SpotifyQueueFailure(songDisplayName(numbered), "Explicit song skipped.")
                return@forEachIndexed
            }
            val archiveKey = archiveKey(numbered)
            if (archiveKey in archived) {
                skipped += SpotifyQueueFailure(songDisplayName(numbered), "Already in the archive.")
                return@forEachIndexed
            }

            val relativePath = if (extension != null) {
                try {
                    SpotifyOutputTemplate.format(
                        record = numbered,
                        extension = extension,
                        template = listOptions.outputTemplate,
                        restrict = listOptions.restrict,
                    )
                } catch (error: SpotifyTemplateError) {
                    failures += SpotifyQueueFailure(songDisplayName(numbered), error.message ?: "Unsafe output path.")
                    return@forEachIndexed
                }
            } else {
                null
            }
            if (relativePath != null && listOptions.scanForSongs && scanHit(relativePath, listStore)) {
                skipped += SpotifyQueueFailure(songDisplayName(numbered), "A matching file already exists.")
                return@forEachIndexed
            }

            val match = try {
                matcher.match(numbered)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpotifyMatchError) {
                failures += SpotifyQueueFailure(songDisplayName(numbered), safeMessage(error))
                return@forEachIndexed
            } catch (error: Exception) {
                failures += SpotifyQueueFailure(songDisplayName(numbered), "The song could not be matched.")
                return@forEachIndexed
            }

            val lyricsResult = if (lyricsOptions.enabled && lyrics != null) {
                lyrics.fetch(numbered, lyricsOptions.providers)
            } else {
                null
            }
            if (lyricsOptions.enabled && lyrics != null && lyricsResult == null) {
                lyricsMisses += songDisplayName(numbered)
            }
            val request = DownloadRequest(
                sourceUrl = match.url,
                options = options.copy(
                    mediaType = MediaType.AUDIO,
                    audioContainer = container,
                    overwrite = listOptions.overwrite,
                ),
                idempotencyKey = "$batchId-$index",
                metadata = numbered.toMediaTags().copy(lyrics = lyricsResult?.text),
                artworkUrl = numbered.artworkUrl,
                parentBatchId = batchId,
                relativePath = relativePath,
                lrcContent = if (lyricsOptions.generateLrc && lyricsResult?.synced == true) {
                    lyricsResult.text
                } else {
                    null
                },
            )
            val job = try {
                engine.submit(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += SpotifyQueueFailure(songDisplayName(numbered), "The download could not be queued.")
                return@forEachIndexed
            }
            jobs += job
            queued += QueuedSong(numbered, relativePath)
        }

        val m3uPath = if (listOptions.writeM3u && queued.isNotEmpty()) {
            writeM3u(preview, queued, listOptions, listStore)
        } else {
            null
        }
        val archivePath = if (listOptions.archive && queued.isNotEmpty()) {
            writeArchive(archived, queued, listOptions, listStore)
        } else {
            null
        }
        return SpotifyQueueReport(
            batchId = batchId,
            jobs = jobs,
            failures = failures,
            skipped = skipped,
            lyricsMisses = lyricsMisses,
            m3uPath = m3uPath,
            archivePath = archivePath,
        )
    }

    private data class QueuedSong(val record: SongRecord, val relativePath: String?)

    private fun writeM3u(
        preview: SpotifyPreview,
        queued: List<QueuedSong>,
        listOptions: SpotifyListOptions,
        listStore: SpotifyListStore,
    ): String? {
        val name = listOptions.m3uName?.takeIf { it.isNotBlank() }
            ?: "${SpotifyOutputTemplate.sanitizeValue(preview.name)}.m3u8"
        val content = buildString {
            append("#EXTM3U\n")
            for (song in queued) {
                val artist = song.record.albumArtist ?: song.record.artist
                val duration = song.record.durationSeconds ?: -1
                append("#EXTINF:$duration,$artist - ${song.record.title}\n")
                append(song.relativePath ?: "").append('\n')
            }
        }
        return if (listStore.write(name, content)) name else null
    }

    private fun writeArchive(
        archived: Set<String>,
        queued: List<QueuedSong>,
        listOptions: SpotifyListOptions,
        listStore: SpotifyListStore,
    ): String? {
        val keys = (archived + queued.map { archiveKey(it.record) }).filter { it.isNotBlank() }
        val content = keys.sorted().joinToString("\n", postfix = if (keys.isEmpty()) "" else "\n")
        return if (listStore.write(listOptions.archiveName, content)) listOptions.archiveName else null
    }

    /** Playlist numbering: the list position becomes the album track number. */
    private fun numberedRecord(record: SongRecord): SongRecord {
        val position = record.listPosition ?: return record
        return record.copy(
            trackNumber = position,
            tracksCount = record.listLength,
            album = record.listName ?: record.album,
            discNumber = 1,
            discCount = 1,
        )
    }

    /** spotDL archives the Spotify URL; the id is stable and secret-free. */
    private fun archiveKey(record: SongRecord): String =
        record.songId?.takeIf { it.isNotBlank() }
            ?: record.spotifyUrl?.takeIf { it.isNotBlank() }
            ?: songDisplayName(record)

    /** scan-for-songs looks only in the root for another known audio extension. */
    private fun scanHit(relativePath: String, listStore: SpotifyListStore): Boolean {
        val stem = relativePath.substringBeforeLast('.', relativePath)
        return AUDIO_EXTENSIONS.any { listStore.exists("$stem.$it") }
    }

    // -------------------------------------------------- save / url / meta (T-090)

    /**
     * Writes a `.spotdl` file of the resolved records and downloads nothing.
     * With [preload] each record also stores its matched URL; lyrics are
     * fetched when enabled. The path must stay inside the download root.
     */
    suspend fun save(
        preview: SpotifyPreview,
        relativePath: String,
        preload: Boolean = false,
        lyricsOptions: SpotifyLyricsOptions = SpotifyLyricsOptions(),
    ): SpotifySaveReport {
        val path = SpotifyListPaths.validate(relativePath)
        require(SpotifySaveFiles.isSaveFile(path)) {
            "The save file must end with ${SpotifySaveFile.EXTENSION}."
        }
        val failures = mutableListOf<SpotifyQueueFailure>()
        val saved = preview.entries.mapNotNull { entry ->
            val record = entry.record
            if (record == null) {
                failures += SpotifyQueueFailure(
                    title = entry.title ?: "Unavailable entry",
                    reason = entry.reason ?: "This Spotify entry is unavailable.",
                )
                return@mapNotNull null
            }
            var downloadUrl: String? = null
            if (preload) {
                downloadUrl = try {
                    matcher.match(record).url
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failures += SpotifyQueueFailure(
                        songDisplayName(record),
                        error.message ?: "No audio match was found.",
                    )
                    null
                }
            }
            val lyricsResult = if (lyricsOptions.enabled && lyrics != null) {
                lyrics.fetch(record, lyricsOptions.providers)
            } else {
                null
            }
            SpotifySavedSong(
                record = record,
                downloadUrl = downloadUrl,
                lyrics = lyricsResult?.text,
            )
        }
        val written = listStore.write(
            path,
            SpotifySaveFiles.encode(SpotifySaveFile(query = preview.query, songs = saved)),
        )
        if (!written) failures += SpotifyQueueFailure(path, "The save file could not be written.")
        return SpotifySaveReport(path = path, savedCount = saved.size, failures = failures)
    }

    /**
     * Matches every song and returns one URL per song. It writes nothing and
     * starts no job; a miss is a per-entry failure.
     */
    suspend fun urls(preview: SpotifyPreview): List<SpotifyUrlEntry> =
        preview.entries.map { entry ->
            currentCoroutineContext().ensureActive()
            val record = entry.record
            if (record == null) {
                SpotifyUrlEntry(
                    title = entry.title ?: "Unavailable entry",
                    failure = entry.reason ?: "This Spotify entry is unavailable.",
                )
            } else {
                try {
                    SpotifyUrlEntry(songDisplayName(record), url = matcher.match(record).url)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    SpotifyUrlEntry(
                        title = songDisplayName(record),
                        failure = error.message ?: "No audio match was found.",
                    )
                }
            }
        }

    /** Reads a `.spotdl` file back into the same preview shape. */
    suspend fun previewSaveFile(relativePath: String): SpotifyPreview {
        val path = SpotifyListPaths.validate(relativePath)
        val text = listStore.readLines(path)?.joinToString("\n")
            ?: throw SpotifyMetadataError.NotFound("The save file could not be read.")
        val file = SpotifySaveFiles.decode(text)
        return SpotifyPreview(
            query = file.query,
            name = file.query.ifBlank { "Saved songs" },
            entries = file.songs.map { SpotifyPreviewEntry(record = it.record) },
        )
    }

    /**
     * Retags files already in the download root. The engine's `metadata`
     * overwrite mode rewrites tags in place and never downloads; `redownload`
     * switches to `force` so the host downloads the matched URL again. A
     * missing file is skipped, and [skipAlbumArt] leaves existing artwork
     * untouched by clearing the request's artwork URL.
     */
    suspend fun meta(
        preview: SpotifyPreview,
        options: DownloadOptions = DownloadOptions(),
        capabilities: ToolkitCapabilities = ToolkitCapabilities.Unavailable,
        listOptions: SpotifyListOptions = SpotifyListOptions(),
        skipAlbumArt: Boolean = false,
        redownload: Boolean = false,
    ): SpotifyMetaReport {
        val batchId = idGenerator()
        val container = preferredAudioContainer(capabilities)
        val extension = container?.wireName
        val files = listStore.list().toSet()
        val jobs = mutableListOf<DownloadJob>()
        val failures = mutableListOf<SpotifyQueueFailure>()
        val skipped = mutableListOf<SpotifyQueueFailure>()

        preview.entries.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            val record = entry.record
            if (record == null) {
                failures += SpotifyQueueFailure(
                    title = entry.title ?: "Unavailable entry",
                    reason = entry.reason ?: "This Spotify entry is unavailable.",
                )
                return@forEachIndexed
            }
            if (extension == null) {
                skipped += SpotifyQueueFailure(
                    songDisplayName(record),
                    "This host has no audio container for metadata.",
                )
                return@forEachIndexed
            }
            val relativePath = try {
                SpotifyOutputTemplate.format(
                    record = record,
                    extension = extension,
                    template = listOptions.outputTemplate,
                    restrict = listOptions.restrict,
                )
            } catch (error: SpotifyTemplateError) {
                failures += SpotifyQueueFailure(songDisplayName(record), error.message ?: "Unsafe output path.")
                return@forEachIndexed
            }
            if (relativePath !in files) {
                skipped += SpotifyQueueFailure(songDisplayName(record), "No matching file in the download root.")
                return@forEachIndexed
            }
            val match = try {
                matcher.match(record)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += SpotifyQueueFailure(songDisplayName(record), error.message ?: "No audio match was found.")
                return@forEachIndexed
            }
            val request = DownloadRequest(
                sourceUrl = match.url,
                options = options.copy(
                    mediaType = MediaType.AUDIO,
                    audioContainer = container,
                    overwrite = if (redownload) OverwriteMode.FORCE else OverwriteMode.METADATA,
                ),
                idempotencyKey = "$batchId-$index",
                metadata = record.toMediaTags(),
                artworkUrl = if (skipAlbumArt) null else record.artworkUrl,
                parentBatchId = batchId,
                relativePath = relativePath,
            )
            val job = try {
                engine.submit(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += SpotifyQueueFailure(songDisplayName(record), "The metadata job could not be queued.")
                return@forEachIndexed
            }
            jobs += job
        }
        return SpotifyMetaReport(batchId = batchId, jobs = jobs, failures = failures, skipped = skipped)
    }

    // -------------------------------------------------------------- sync (T-091)

    /**
     * Computes what a sync would change without touching any file. The caller
     * shows [SpotifySyncPlan.removals] before [applySync] deletes anything.
     */
    suspend fun planSync(
        query: String,
        savePath: String,
        capabilities: ToolkitCapabilities = ToolkitCapabilities.Unavailable,
        listOptions: SpotifyListOptions = SpotifyListOptions(),
        deleteRemoved: Boolean = true,
    ): SpotifySyncPlan {
        val path = SpotifyListPaths.validate(savePath)
        require(SpotifySaveFiles.isSaveFile(path)) {
            "The sync file must end with ${SpotifySaveFile.EXTENSION}."
        }
        val preview = preview(query)
        val current = preview.songs
        val previous = readSaveFileOrNull(path)?.songs.orEmpty()
        val currentKeys = current.map { archiveKey(it) }.toSet()
        val previousKeys = previous.map { archiveKey(it.record) }.toSet()
        val additions = current.filter { archiveKey(it) !in previousKeys }
        val unchanged = current.filter { archiveKey(it) in previousKeys }
        val extension = preferredAudioContainer(capabilities)?.wireName
        val removals = if (!deleteRemoved) {
            emptyList()
        } else {
            previous.filter { archiveKey(it.record) !in currentKeys }.map { saved ->
                val audio = saved.createdFiles.ifEmpty {
                    templatedAudioFiles(saved.record, extension, listOptions)
                }
                val siblings = audio.mapNotNull(::lrcSibling)
                SpotifySyncRemoval(
                    title = songDisplayName(saved.record),
                    files = (audio + siblings).distinct(),
                )
            }
        }
        return SpotifySyncPlan(
            savePath = path,
            query = query,
            additions = additions,
            unchanged = unchanged,
            removals = removals,
            deleteRemoved = deleteRemoved,
            failures = preview.unavailable.map {
                SpotifyQueueFailure(it.title ?: "Unavailable entry", it.reason ?: "Unavailable")
            },
        )
    }

    /**
     * Downloads the additions and deletes the planned removals. Deletion only
     * happens when every addition queued cleanly, so a failed new song never
     * removes the old set. Writes the new sync file with the files it created.
     */
    suspend fun applySync(
        plan: SpotifySyncPlan,
        options: DownloadOptions = DownloadOptions(),
        capabilities: ToolkitCapabilities = ToolkitCapabilities.Unavailable,
        listOptions: SpotifyListOptions = SpotifyListOptions(),
        lyricsOptions: SpotifyLyricsOptions = SpotifyLyricsOptions(),
    ): SpotifySyncReport {
        val queued = if (plan.additions.isEmpty()) {
            SpotifyQueueReport(batchId = idGenerator(), jobs = emptyList(), failures = emptyList())
        } else {
            queue(
                preview = SpotifyPreview(
                    query = plan.query,
                    name = plan.query,
                    entries = plan.additions.map { SpotifyPreviewEntry(record = it) },
                ),
                options = options,
                capabilities = capabilities,
                listOptions = listOptions.copy(writeM3u = false, archive = false),
                lyricsOptions = lyricsOptions,
            )
        }

        val deleted = mutableListOf<String>()
        val skippedDeletions = mutableListOf<SpotifyQueueFailure>()
        if (plan.removals.isNotEmpty()) {
            if (queued.failures.isNotEmpty()) {
                plan.removals.forEach { removal ->
                    skippedDeletions += SpotifyQueueFailure(
                        removal.title,
                        "An addition failed; the old files were kept.",
                    )
                }
            } else {
                plan.removals.forEach { removal ->
                    removal.files.forEach { file ->
                        if (listStore.delete(file)) deleted += file
                    }
                }
            }
        }

        val extension = preferredAudioContainer(capabilities)?.wireName
        val savedSongs = (plan.unchanged + plan.additions).map { record ->
            SpotifySavedSong(
                record = record,
                createdFiles = templatedAudioFiles(record, extension, listOptions),
            )
        }
        listStore.write(
            plan.savePath,
            SpotifySaveFiles.encode(
                SpotifySaveFile(query = plan.query, sync = true, songs = savedSongs),
            ),
        )
        return SpotifySyncReport(
            plan = plan,
            jobs = queued.jobs,
            deleted = deleted,
            failures = queued.failures,
            skippedDeletions = skippedDeletions,
        )
    }

    private fun readSaveFileOrNull(path: String): SpotifySaveFile? {
        val text = listStore.readLines(path)?.joinToString("\n") ?: return null
        return runCatching { SpotifySaveFiles.decode(text) }.getOrNull()
    }

    private fun templatedAudioFiles(
        record: SongRecord,
        extension: String?,
        listOptions: SpotifyListOptions,
    ): List<String> {
        if (extension == null) return emptyList()
        return try {
            listOf(
                SpotifyOutputTemplate.format(
                    record = record,
                    extension = extension,
                    template = listOptions.outputTemplate,
                    restrict = listOptions.restrict,
                ),
            )
        } catch (_: SpotifyTemplateError) {
            emptyList()
        }
    }

    /** The `.lrc` sibling that exists next to an audio path, if any. */
    private fun lrcSibling(audioPath: String): String? {
        val dot = audioPath.lastIndexOf('.')
        if (dot <= 0) return null
        val candidate = audioPath.substring(0, dot) + ".lrc"
        return candidate.takeIf { listStore.exists(candidate) }
    }

    private fun safeMessage(error: SpotifyMatchError): String =
        error.message ?: "No audio match was found."

    private fun preferredAudioContainer(capabilities: ToolkitCapabilities): AudioContainer? = when {
        AudioContainer.MP3 in capabilities.audioContainers -> AudioContainer.MP3
        AudioContainer.M4A in capabilities.audioContainers -> AudioContainer.M4A
        AudioContainer.OPUS in capabilities.audioContainers -> AudioContainer.OPUS
        else -> null
    }

    private companion object {
        val AUDIO_EXTENSIONS = listOf("mp3", "m4a", "opus", "wav", "flac")
    }
}

/** What `save` wrote; no media file is involved. */
data class SpotifySaveReport(
    val path: String,
    val savedCount: Int,
    val failures: List<SpotifyQueueFailure> = emptyList(),
)

/** One row of the `url` operation: a matched URL or a redacted failure. */
data class SpotifyUrlEntry(
    val title: String,
    val url: String? = null,
    val failure: String? = null,
)

/** What `meta` queued: in-place retags or forced redownloads. */
data class SpotifyMetaReport(
    val batchId: String,
    val jobs: List<DownloadJob>,
    val failures: List<SpotifyQueueFailure> = emptyList(),
    val skipped: List<SpotifyQueueFailure> = emptyList(),
)

/** One song's files a sync would remove. */
data class SpotifySyncRemoval(
    val title: String,
    val files: List<String>,
)

/**
 * What a sync would change, computed before anything is written or deleted.
 * [removals] is the delete list the UI shows first; [additions] are the songs
 * that will be matched and queued.
 */
data class SpotifySyncPlan(
    val savePath: String,
    val query: String,
    val additions: List<SongRecord> = emptyList(),
    val unchanged: List<SongRecord> = emptyList(),
    val removals: List<SpotifySyncRemoval> = emptyList(),
    val deleteRemoved: Boolean = true,
    val failures: List<SpotifyQueueFailure> = emptyList(),
)

/** What `applySync` did: queued additions and the files it removed. */
data class SpotifySyncReport(
    val plan: SpotifySyncPlan,
    val jobs: List<DownloadJob>,
    val deleted: List<String> = emptyList(),
    val failures: List<SpotifyQueueFailure> = emptyList(),
    val skippedDeletions: List<SpotifyQueueFailure> = emptyList(),
)

/** The tags the engine embeds for one resolved Spotify song. */
internal fun SongRecord.toMediaTags(): MediaTags = MediaTags(
    title = title,
    artists = artists,
    album = album,
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    isrc = isrc,
)

/** Short, redacted reason for a list entry Spotify could not turn into a song. */
internal fun SongListEntry.Unavailable.displayMessage(): String = when (reason) {
    UnavailableReason.LOCAL_TRACK -> "This is a local Spotify file."
    UnavailableReason.NOT_A_TRACK -> "This playlist entry is not a track."
    UnavailableReason.NO_DURATION -> "Spotify returned no duration for this entry."
    UnavailableReason.MISSING_DATA -> "Spotify returned incomplete metadata for this entry."
    UnavailableReason.FETCH_FAILED -> message ?: "This Spotify item could not be loaded."
}
