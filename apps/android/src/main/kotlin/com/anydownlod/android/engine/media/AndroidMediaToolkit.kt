package com.anydownlod.android.engine.media

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.core.postprocess.ToolkitError
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [MediaToolkit] over a [MediaMuxerPort]. The port remuxes; nothing
 * here re-encodes. M4A and Opus are stream-copy only, and MP3, WAV, and FLAC
 * stay out of the capability set. A codec the muxer rejects deletes the
 * partial file and fails typed.
 */
class AndroidMediaToolkit(
    private val muxer: MediaMuxerPort,
) : MediaToolkit {

    override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities(
        canMerge = true,
        audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
    )

    override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) {
        runMuxer(destination.token) {
            muxer.merge(video.token, audio.token, destination.token)
        }
    }

    override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
        if (container !in capabilities().audioContainers) {
            throw ToolkitError.IncompatibleStreams(
                "This host cannot write ${container.wireName.uppercase()} audio yet.",
            )
        }
        runMuxer(destination.token) {
            muxer.copyAudio(source.token, destination.token, container)
        }
    }

    override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
        throw ToolkitError.ToolUnavailable("Android does not write audio tags in this phase.")
    }

    private suspend fun runMuxer(destinationPath: String, block: () -> Unit) {
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (cancelled: CancellationException) {
            deleteQuietly(destinationPath)
            throw cancelled
        } catch (failure: MuxerFailure) {
            deleteQuietly(destinationPath)
            throw when (failure) {
                is MuxerFailure.Incompatible -> ToolkitError.IncompatibleStreams(
                    failure.message ?: DEFAULT_INCOMPATIBLE,
                )

                is MuxerFailure.Io -> ToolkitError.Io(failure.message ?: DEFAULT_IO, failure)
            }
        } catch (failure: Throwable) {
            deleteQuietly(destinationPath)
            throw ToolkitError.Io(DEFAULT_IO, failure)
        }
    }

    private fun deleteQuietly(path: String) {
        runCatching { File(path).delete() }
    }

    private companion object {
        const val DEFAULT_INCOMPATIBLE =
            "This host cannot put the selected streams in one file without re-encoding."
        const val DEFAULT_IO = "The media toolkit could not read or write a file."
    }
}
