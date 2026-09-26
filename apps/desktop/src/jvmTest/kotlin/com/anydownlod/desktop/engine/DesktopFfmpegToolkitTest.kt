package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.core.postprocess.ToolkitError
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * T-076: the desktop toolkit over the `ffmpeg`/`ffprobe` on `PATH`.
 *
 * Fixtures are generated locally with lavfi and deleted after each test; no
 * network and no media URL is involved. Tests skip with a recorded reason when
 * the tools are absent instead of downloading a binary.
 */
class DesktopFfmpegToolkitTest {

    private fun tempDir(): Path = Files.createTempDirectory("anydownlod-ffmpeg")

    @Test
    fun mergeCopiesBothStreamsIntoOneFile() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val directory = tempDir()
        try {
            val video = directory.resolve("video-only.mp4")
            val audio = directory.resolve("audio-only.m4a")
            FfmpegFixtures.generateVideoOnly(ffmpeg, video)
            FfmpegFixtures.generateAudioOnly(ffmpeg, audio)
            val videoCodec = FfmpegFixtures.probeStreams(ffprobe, video).single { it.first == "video" }.second
            val audioCodec = FfmpegFixtures.probeStreams(ffprobe, audio).single { it.first == "audio" }.second
            val destination = directory.resolve("merged.mp4")
            Files.createFile(destination)

            DesktopFfmpegToolkit().merge(path(video), path(audio), path(destination))

            val merged = FfmpegFixtures.probeStreams(ffprobe, destination)
            assertTrue(merged.contains("video" to videoCodec), "merged streams: $merged")
            assertTrue(merged.contains("audio" to audioCodec), "merged streams: $merged")
            assertEquals(2, merged.size)
            assertTrue(FfmpegFixtures.probeFormat(ffprobe, destination).contains("mp4"))
            assertTrue(Files.size(destination) > 0)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun extractAudioCopiesM4aWithoutReencoding() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val directory = tempDir()
        try {
            val audio = directory.resolve("audio-only.m4a")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audio)
            val sourceCodec = FfmpegFixtures.probeStreams(ffprobe, audio).single { it.first == "audio" }.second
            val destination = directory.resolve("copy.m4a")
            Files.createFile(destination)

            DesktopFfmpegToolkit().extractAudio(path(audio), AudioContainer.M4A, path(destination))

            assertEquals(listOf("audio" to sourceCodec), FfmpegFixtures.probeStreams(ffprobe, destination))
            assertTrue(FfmpegFixtures.probeFormat(ffprobe, destination).contains("mp4"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun extractAudioProducesMp3WavAndFlac() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val directory = tempDir()
        try {
            val audio = directory.resolve("audio-only.m4a")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audio)
            val expected = mapOf(
                AudioContainer.MP3 to "mp3",
                AudioContainer.WAV to "pcm_s16le",
                AudioContainer.FLAC to "flac",
            )
            for ((container, codec) in expected) {
                val destination = directory.resolve("copy.${container.wireName}")
                Files.createFile(destination)

                DesktopFfmpegToolkit().extractAudio(path(audio), container, path(destination))

                assertEquals(
                    listOf("audio" to codec),
                    FfmpegFixtures.probeStreams(ffprobe, destination),
                    container.wireName,
                )
                val format = FfmpegFixtures.probeFormat(ffprobe, destination)
                assertTrue(format.contains(container.wireName), "${container.wireName}: $format")
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun incompatibleCopyFailsTypedAndDeletesTheDestination() = runBlocking {
        val (ffmpeg, _) = FfmpegFixtures.assumeTools()
        val directory = tempDir()
        try {
            val source = directory.resolve("audio.opus")
            FfmpegFixtures.generateOpusOnly(ffmpeg, source)
            val destination = directory.resolve("copy.m4a")
            Files.createFile(destination)

            val error = assertFailsWith<ToolkitError.IncompatibleStreams> {
                DesktopFfmpegToolkit().extractAudio(path(source), AudioContainer.M4A, path(destination))
            }

            assertFalse(error.retryable)
            assertFalse(Files.exists(destination), "a failed extraction must leave no output file")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingFfmpegFailsTypedAndStartsNoProcess() = runBlocking {
        val toolkit = DesktopFfmpegToolkit(
            runner = CliProcessRunner { _, _ -> error("must not start a process") },
            resolveExecutable = { null },
        )
        assertEquals(ToolkitCapabilities.Unavailable, toolkit.capabilities())

        val mergeError = assertFailsWith<ToolkitError.ToolUnavailable> {
            toolkit.merge(path(Path.of("video.mp4")), path(Path.of("audio.m4a")), path(Path.of("out.mp4")))
        }
        assertFalse(mergeError.retryable)
        val extractError = assertFailsWith<ToolkitError.ToolUnavailable> {
            toolkit.extractAudio(path(Path.of("in.m4a")), AudioContainer.M4A, path(Path.of("out.m4a")))
        }
        assertFalse(extractError.retryable)
    }

    @Test
    fun capabilitiesNeedBothTools() {
        val both = DesktopFfmpegToolkit(resolveExecutable = { "/fake/$it" })
        assertEquals(
            ToolkitCapabilities(
                canMerge = true,
                audioContainers = setOf(
                    AudioContainer.M4A,
                    AudioContainer.OPUS,
                    AudioContainer.MP3,
                    AudioContainer.WAV,
                    AudioContainer.FLAC,
                ),
                canEmbedTags = true,
                canEmbedArtwork = true,
                lyricsContainers = setOf(
                    AudioContainer.M4A,
                    AudioContainer.MP3,
                    AudioContainer.OPUS,
                    AudioContainer.FLAC,
                ),
            ),
            both.capabilities(),
        )

        val onlyFfmpeg = DesktopFfmpegToolkit(
            resolveExecutable = { name -> if (name == "ffmpeg") "/fake/ffmpeg" else null },
        )
        assertEquals(ToolkitCapabilities.Unavailable, onlyFfmpeg.capabilities())
    }

    @Test
    fun cancelKillsTheProcessAndLeavesNoOutput() = runBlocking {
        val directory = tempDir()
        try {
            val destination = directory.resolve("merged.mp4")
            Files.createFile(destination)
            val started = CompletableDeferred<Unit>()
            val process = BlockingCliProcess()
            val toolkit = DesktopFfmpegToolkit(
                runner = CliProcessRunner { _, _ ->
                    started.complete(Unit)
                    process
                },
                resolveExecutable = { "/fake/$it" },
            )

            val job = launch {
                toolkit.merge(
                    path(directory.resolve("video.mp4")),
                    path(directory.resolve("audio.m4a")),
                    path(destination),
                )
            }
            started.await()
            job.cancelAndJoin()

            assertTrue(process.destroyed, "cancel must destroy the process tree")
            assertFalse(Files.exists(destination), "cancel must delete the partial destination")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun embedTagsWritesTitleArtistAlbumAndArtwork() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val directory = tempDir()
        try {
            val audio = directory.resolve("song.m4a")
            val artwork = directory.resolve("cover.jpg")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audio)
            FfmpegFixtures.runFfmpeg(
                ffmpeg,
                "-f", "lavfi", "-i", "color=c=red:s=64x64",
                "-frames:v", "1",
                artwork.toString(),
            )
            val artworkBytes = Files.readAllBytes(artwork)
            Files.delete(artwork)

            val toolkit = DesktopFfmpegToolkit()
            val tags = com.anydownlod.core.domain.MediaTags(
                title = "Fixture Song",
                artists = listOf("Fixture Artist", "Second Artist"),
                album = "Fixture Album",
                albumArtist = "Fixture Artist",
                year = 2020,
                trackNumber = 3,
                isrc = "ISRCFIXTURE01",
            )

            toolkit.embedTags(path(audio), tags, artworkBytes)

            val probed = FfmpegFixtures.probeTags(ffprobe, audio)
            assertEquals("Fixture Song", probed["title"])
            assertEquals("Fixture Artist; Second Artist", probed["artist"])
            assertEquals("Fixture Album", probed["album"])
            assertEquals("Fixture Artist", probed["album_artist"])
            assertEquals("2020", probed["date"])
            val streams = FfmpegFixtures.probeStreams(ffprobe, audio)
            assertTrue(streams.contains("audio" to "aac"), streams.toString())
            assertTrue(streams.any { it.first == "video" }, "the cover art stream must be present: $streams")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun embedTagsWritesLyricsForM4a() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val directory = tempDir()
        try {
            val audio = directory.resolve("song.m4a")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audio)
            val toolkit = DesktopFfmpegToolkit()

            toolkit.embedTags(
                path(audio),
                com.anydownlod.core.domain.MediaTags(
                    title = "Fixture Song",
                    lyrics = "[00:01.00] line one\n[00:02.00] line two",
                ),
            )

            val tags = FfmpegFixtures.probeTags(ffprobe, audio)
            assertEquals("Fixture Song", tags["title"])
            assertEquals("[00:01.00] line one\n[00:02.00] line two", tags["lyrics"])
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun path(file: Path) = MediaFilePath(file.toString())
}
