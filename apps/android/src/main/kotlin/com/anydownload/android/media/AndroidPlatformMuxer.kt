package com.anydownlod.android.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.anydownlod.android.engine.media.MediaMuxerPort
import com.anydownlod.android.engine.media.MuxerFailure
import com.anydownlod.core.domain.AudioContainer
import java.io.File
import java.nio.ByteBuffer

/**
 * [MediaMuxerPort] over Android's platform `MediaExtractor` and `MediaMuxer`.
 *
 * Tracks are copied sample by sample; no encoder runs and no subprocess is
 * started. The MP4 muxer accepts the codecs the platform supports, so a pair
 * it rejects throws [MuxerFailure.Incompatible] and the caller deletes the
 * partial file. Opus copy uses the WebM muxer, which older platforms may
 * reject; that becomes the same typed failure.
 */
class AndroidPlatformMuxer : MediaMuxerPort {

    override fun merge(videoPath: String, audioPath: String, destinationPath: String) {
        val video = firstTrack(videoPath, "video/")
        val audio = try {
            firstTrack(audioPath, "audio/")
        } catch (failure: Throwable) {
            video.release()
            throw failure
        }
        mux(destinationPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, listOf(video, audio))
    }

    override fun copyAudio(sourcePath: String, destinationPath: String, container: AudioContainer) {
        val audio = firstTrack(sourcePath, "audio/")
        val outputFormat = when (container) {
            AudioContainer.M4A -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            AudioContainer.OPUS -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else -> {
                audio.release()
                throw MuxerFailure.Incompatible("This host cannot write ${container.wireName} audio.")
            }
        }
        mux(destinationPath, outputFormat, listOf(audio))
    }

    private fun firstTrack(path: String, mimePrefix: String): ExtractedTrack {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith(mimePrefix)) {
                    extractor.selectTrack(index)
                    return ExtractedTrack(extractor, format)
                }
            }
        } catch (failure: Exception) {
            runCatching { extractor.release() }
            throw MuxerFailure.Io("The input file could not be read.", failure)
        }
        runCatching { extractor.release() }
        throw MuxerFailure.Incompatible("The input file has no $mimePrefix track.")
    }

    private fun mux(destinationPath: String, outputFormat: Int, tracks: List<ExtractedTrack>) {
        // MediaMuxer appends to an existing file on some platform versions.
        runCatching { File(destinationPath).delete() }
        val muxer = try {
            MediaMuxer(destinationPath, outputFormat)
        } catch (failure: Exception) {
            tracks.forEach { it.release() }
            throw MuxerFailure.Io("The output file could not be created.", failure)
        }
        try {
            val targets = tracks.map { track -> muxer.addTrack(track.format) }
            muxer.start()
            tracks.forEachIndexed { index, track -> copyTrack(track, muxer, targets[index]) }
            muxer.stop()
        } catch (failure: Exception) {
            throw MuxerFailure.Incompatible("The platform muxer refused the selected streams.")
        } finally {
            tracks.forEach { it.release() }
            runCatching { muxer.release() }
        }
    }

    private fun copyTrack(track: ExtractedTrack, muxer: MediaMuxer, targetIndex: Int) {
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = track.extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, track.extractor.sampleTime, track.extractor.sampleFlags)
            muxer.writeSampleData(targetIndex, buffer, info)
            track.extractor.advance()
        }
    }

    private class ExtractedTrack(val extractor: MediaExtractor, val format: MediaFormat) {
        fun release() = runCatching { extractor.release() }
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
