package com.anydownlod.android.engine.media

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.ToolkitError
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * T-080: the Android toolkit logic over a fake [MediaMuxerPort]. The real
 * platform muxer test is instrumented; this module proves the capability set,
 * the copy-only calls, and the typed failure and cleanup paths.
 */
class AndroidMediaToolkitTest {

    @Test
    fun capabilitiesAreMergeAndCopyOnly() {
        val toolkit = AndroidMediaToolkit(FakeMuxer())
        val capabilities = toolkit.capabilities()

        assertTrue(capabilities.canMerge)
        assertEquals(setOf(AudioContainer.M4A, AudioContainer.OPUS), capabilities.audioContainers)
        for (container in listOf(AudioContainer.MP3, AudioContainer.WAV, AudioContainer.FLAC)) {
            assertFalse(container in capabilities.audioContainers, container.wireName)
        }
    }

    @Test
    fun mergeWritesOneFileWithBothTracks() = runTest {
        val directory = Files.createTempDirectory("android-merge").toFile()
        try {
            val destination = File(directory, "merged.mp4")
            val muxer = FakeMuxer(tracks = 2)
            val toolkit = AndroidMediaToolkit(muxer)

            toolkit.merge(
                MediaFilePath(File(directory, "video.mp4").path),
                MediaFilePath(File(directory, "audio.m4a").path),
                MediaFilePath(destination.path),
            )

            assertTrue(destination.isFile)
            assertEquals(1, muxer.merges.size)
            assertEquals(2, muxer.trackCount)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun incompatiblePairFailsTypedAndDeletesTheDestination() = runTest {
        val directory = Files.createTempDirectory("android-merge-bad").toFile()
        try {
            val destination = File(directory, "merged.mp4")
            destination.writeBytes(byteArrayOf(1))
            val toolkit = AndroidMediaToolkit(
                FakeMuxer(failWith = MuxerFailure.Incompatible("no video track")),
            )

            val error = assertFailsWith<ToolkitError.IncompatibleStreams> {
                toolkit.merge(
                    MediaFilePath(File(directory, "video.mp4").path),
                    MediaFilePath(File(directory, "audio.m4a").path),
                    MediaFilePath(destination.path),
                )
            }

            assertFalse(error.retryable)
            assertFalse(destination.exists(), "a rejected pair must leave no file")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun ioFailureIsRetryableAndDeletesTheDestination() = runTest {
        val directory = Files.createTempDirectory("android-merge-io").toFile()
        try {
            val destination = File(directory, "merged.mp4")
            destination.writeBytes(byteArrayOf(1))
            val toolkit = AndroidMediaToolkit(FakeMuxer(failWith = MuxerFailure.Io("disk")))

            val error = assertFailsWith<ToolkitError.Io> {
                toolkit.merge(
                    MediaFilePath(File(directory, "video.mp4").path),
                    MediaFilePath(File(directory, "audio.m4a").path),
                    MediaFilePath(destination.path),
                )
            }

            assertTrue(error.retryable)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun extractAudioCopiesM4a() = runTest {
        val directory = Files.createTempDirectory("android-extract").toFile()
        try {
            val destination = File(directory, "copy.m4a")
            val muxer = FakeMuxer()
            val toolkit = AndroidMediaToolkit(muxer)

            toolkit.extractAudio(
                MediaFilePath(File(directory, "source.m4a").path),
                AudioContainer.M4A,
                MediaFilePath(destination.path),
            )

            assertTrue(destination.isFile)
            assertEquals(1, muxer.copies.size)
            assertEquals(AudioContainer.M4A, muxer.copies.single().third)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unsupportedContainerFailsTypedWithoutCallingThePort() = runTest {
        val directory = Files.createTempDirectory("android-extract-bad").toFile()
        try {
            val destination = File(directory, "copy.mp3")
            destination.writeBytes(byteArrayOf(1))
            val muxer = FakeMuxer()
            val toolkit = AndroidMediaToolkit(muxer)

            val error = assertFailsWith<ToolkitError.IncompatibleStreams> {
                toolkit.extractAudio(
                    MediaFilePath(File(directory, "source.m4a").path),
                    AudioContainer.MP3,
                    MediaFilePath(destination.path),
                )
            }

            assertFalse(error.retryable)
            assertTrue(muxer.copies.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeMuxer(
        private val failWith: MuxerFailure? = null,
        private val tracks: Int = 0,
    ) : MediaMuxerPort {
        val merges = mutableListOf<Triple<String, String, String>>()
        val copies = mutableListOf<Triple<String, String, AudioContainer>>()
        var trackCount: Int = 0
            private set

        override fun merge(videoPath: String, audioPath: String, destinationPath: String) {
            merges += Triple(videoPath, audioPath, destinationPath)
            failWith?.let { throw it }
            trackCount = tracks
            File(destinationPath).writeBytes(byteArrayOf(1, 2, 3))
        }

        override fun copyAudio(sourcePath: String, destinationPath: String, container: AudioContainer) {
            copies += Triple(sourcePath, destinationPath, container)
            failWith?.let { throw it }
            File(destinationPath).writeBytes(byteArrayOf(4, 5))
        }
    }
}
