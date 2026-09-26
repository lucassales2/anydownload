package com.anydownlod.android.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anydownlod.android.engine.media.AndroidMediaToolkit
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.ToolkitError
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T-080 instrumented evidence: the real `AndroidPlatformMuxer` (platform
 * `MediaExtractor` + `MediaMuxer`) behind `AndroidMediaToolkit`. Fixtures are
 * encoded on-device; no network and no committed media.
 *
 * This test needs a device or emulator. The default CI checkout has none, so
 * it is compiled but not run; the JVM-equivalent module covers the toolkit
 * logic with a fake port.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPlatformMuxerInstrumentedTest {

    private fun workspace(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "muxer-test-${System.nanoTime()}")
        check(directory.mkdirs()) { "The test workspace could not be created." }
        return directory
    }

    @Test
    fun compatiblePairBecomesOneFileWithBothTracks() = runBlocking {
        val directory = workspace()
        try {
            val video = File(directory, "video.mp4")
            val audio = File(directory, "audio.m4a")
            MediaFixtures.encodeVideoOnly(video)
            MediaFixtures.encodeAudioOnly(audio)
            val destination = File(directory, "merged.mp4")

            AndroidMediaToolkit(AndroidPlatformMuxer()).merge(
                MediaFilePath(video.path),
                MediaFilePath(audio.path),
                MediaFilePath(destination.path),
            )

            assertTrue("the merged file must exist", destination.isFile)
            val mimes = MediaFixtures.trackMimes(destination)
            assertEquals("one video and one audio track", 2, mimes.size)
            assertTrue(mimes.toString(), mimes.any { it.startsWith("video/") })
            assertTrue(mimes.toString(), mimes.any { it.startsWith("audio/") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun incompatiblePairFailsTypedAndLeavesNoFile() = runBlocking {
        val directory = workspace()
        try {
            val audio = File(directory, "audio.m4a")
            MediaFixtures.encodeAudioOnly(audio)
            val destination = File(directory, "merged.mp4")

            val error = try {
                AndroidMediaToolkit(AndroidPlatformMuxer()).merge(
                    MediaFilePath(audio.path),
                    MediaFilePath(audio.path),
                    MediaFilePath(destination.path),
                )
                null
            } catch (failure: ToolkitError) {
                failure
            }

            assertTrue(error?.message, error is ToolkitError.IncompatibleStreams)
            assertFalse("a rejected pair must leave no file", destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun capabilitiesOmitMp3WavAndFlac() {
        val capabilities = AndroidMediaToolkit(AndroidPlatformMuxer()).capabilities()
        assertTrue(capabilities.canMerge)
        assertEquals(setOf(AudioContainer.M4A, AudioContainer.OPUS), capabilities.audioContainers)
    }
}
