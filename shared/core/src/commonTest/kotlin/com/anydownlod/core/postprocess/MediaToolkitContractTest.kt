package com.anydownlod.core.postprocess

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaTags
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaToolkitContractTest {

    @Test
    fun emptyCapabilitiesAreTheDefault() {
        val capabilities = ToolkitCapabilities()
        assertFalse(capabilities.canMerge)
        assertTrue(capabilities.audioContainers.isEmpty())
        assertEquals(ToolkitCapabilities.Unavailable, capabilities)
    }

    @Test
    fun unavailableToolkitReportsEmptyCapabilities() {
        val capabilities = UnavailableToolkit.capabilities()
        assertFalse(capabilities.canMerge)
        assertTrue(capabilities.audioContainers.isEmpty())
    }

    @Test
    fun unavailableToolkitMergeFailsTyped() = runTest {
        val error = assertFailsWith<ToolkitError.ToolUnavailable> {
            UnavailableToolkit.merge(path("video"), path("audio"), path("destination"))
        }
        assertFalse(error.retryable)
    }

    @Test
    fun unavailableToolkitExtractAudioFailsTyped() = runTest {
        val error = assertFailsWith<ToolkitError.ToolUnavailable> {
            UnavailableToolkit.extractAudio(path("source"), AudioContainer.M4A, path("destination"))
        }
        assertFalse(error.retryable)
    }

    @Test
    fun missingToolAndIncompatibleStreamsAreNotRetryableButIoIs() {
        assertFalse(ToolkitError.ToolUnavailable().retryable)
        assertFalse(ToolkitError.IncompatibleStreams().retryable)
        assertTrue(ToolkitError.Io().retryable)
    }

    @Test
    fun hostAdapterCanImplementTheContract() = runTest {
        val toolkit = RecordingToolkit()
        assertEquals(
            ToolkitCapabilities(canMerge = true, audioContainers = setOf(AudioContainer.M4A)),
            toolkit.capabilities(),
        )

        toolkit.merge(path("v"), path("a"), path("out"))
        toolkit.extractAudio(path("in"), AudioContainer.M4A, path("out-audio"))

        assertEquals(listOf("v", "a", "out"), toolkit.merges.single())
        assertEquals(listOf("in", AudioContainer.M4A, "out-audio"), toolkit.audioExtractions.single())
    }

    private fun path(token: String) = MediaFilePath(token)

    private class RecordingToolkit : MediaToolkit {
        val merges = mutableListOf<List<String>>()
        val audioExtractions = mutableListOf<List<Any>>()
        val tagWrites = mutableListOf<List<Any?>>()

        override fun capabilities(): ToolkitCapabilities =
            ToolkitCapabilities(canMerge = true, audioContainers = setOf(AudioContainer.M4A))

        override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) {
            merges += listOf(video.token, audio.token, destination.token)
        }

        override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
            audioExtractions += listOf(source.token, container, destination.token)
        }

        override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
            tagWrites += listOf(file.token, tags, artwork)
        }
    }
}
