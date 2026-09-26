@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anydownlod.ui.media

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.ToolkitError
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.tracksWithMediaType
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

/**
 * T-081 simulator evidence: the real `IosMediaToolkit` (AVFoundation
 * passthrough export) on local synthetic fixtures. No network and no
 * subprocess; the sandbox workspace is deleted after each test.
 */
class IosMediaToolkitTest {

    private fun workspace(): String {
        val path = NSTemporaryDirectory() + "anydownlod-ios-muxer-${Random.nextLong().toULong().toString(16)}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return path
    }

    private fun removeWorkspace(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun path(file: NSURL): String = file.path ?: error("The fixture URL has no path.")

    @Test
    fun mergeWritesOneSandboxFileWithBothTracks() = runBlocking {
        val directory = workspace()
        try {
            val video = NSURL.fileURLWithPath("$directory/video.mp4")
            val audio = NSURL.fileURLWithPath("$directory/audio.m4a")
            IosMediaFixtures.writeVideoOnly(video)
            IosMediaFixtures.writeAudioOnly(audio)
            val destination = NSURL.fileURLWithPath("$directory/merged.mp4")

            IosMediaToolkit().merge(
                MediaFilePath(path(video)),
                MediaFilePath(path(audio)),
                MediaFilePath(path(destination)),
            )

            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path(destination)))
            val asset = AVURLAsset(uRL = destination, options = null)
            assertEquals(1, asset.tracksWithMediaType(AVMediaTypeVideo).size)
            assertEquals(1, asset.tracksWithMediaType(AVMediaTypeAudio).size)
        } finally {
            removeWorkspace(directory)
        }
    }

    @Test
    fun extractAudioCopiesM4a() = runBlocking {
        val directory = workspace()
        try {
            val audio = NSURL.fileURLWithPath("$directory/audio.m4a")
            IosMediaFixtures.writeAudioOnly(audio)
            val destination = NSURL.fileURLWithPath("$directory/copy.m4a")

            IosMediaToolkit().extractAudio(
                MediaFilePath(path(audio)),
                AudioContainer.M4A,
                MediaFilePath(path(destination)),
            )

            val asset = AVURLAsset(uRL = destination, options = null)
            assertEquals(1, asset.tracksWithMediaType(AVMediaTypeAudio).size)
        } finally {
            removeWorkspace(directory)
        }
    }

    @Test
    fun incompatiblePairFailsTypedAndLeavesNoFile() = runBlocking {
        val directory = workspace()
        try {
            val audio = NSURL.fileURLWithPath("$directory/audio.m4a")
            IosMediaFixtures.writeAudioOnly(audio)
            val destination = NSURL.fileURLWithPath("$directory/merged.mp4")

            val error = assertFailsWith<ToolkitError.IncompatibleStreams> {
                IosMediaToolkit().merge(
                    MediaFilePath(path(audio)),
                    MediaFilePath(path(audio)),
                    MediaFilePath(path(destination)),
                )
            }

            assertFalse(error.retryable)
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path(destination)))
        } finally {
            removeWorkspace(directory)
        }
    }

    @Test
    fun capabilitiesOmitMp3WavAndFlac() {
        val capabilities = IosMediaToolkit().capabilities()
        assertTrue(capabilities.canMerge)
        assertEquals(setOf(AudioContainer.M4A, AudioContainer.OPUS), capabilities.audioContainers)
        for (container in listOf(AudioContainer.MP3, AudioContainer.WAV, AudioContainer.FLAC)) {
            assertFalse(container in capabilities.audioContainers, container.wireName)
        }
    }
}
