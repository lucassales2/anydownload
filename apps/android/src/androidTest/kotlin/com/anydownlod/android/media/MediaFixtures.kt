package com.anydownlod.android.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Tiny on-device media fixtures for the instrumented muxer test. A video-only
 * H.264 MP4 and an audio-only AAC M4A are encoded with `MediaCodec`; nothing
 * is committed to the repository and no network is used.
 */
internal object MediaFixtures {

    private const val TIMEOUT_US = 10_000L

    fun encodeVideoOnly(file: File, width: Int = 128, height: Int = 72, frames: Int = 5) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 5)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val frame = ByteArray(width * height * 3 / 2) { 0x80.toByte() }
        encode(file, MediaFormat.MIMETYPE_VIDEO_AVC, format, frames, 200_000L) { frame }
    }

    fun encodeAudioOnly(file: File, frames: Int = 10) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44_100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
        }
        val silence = ByteArray(1024 * 2)
        encode(file, MediaFormat.MIMETYPE_AUDIO_AAC, format, frames, 23_219L) { silence }
    }

    fun trackMimes(file: File): List<String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            }
        } finally {
            extractor.release()
        }
    }

    private fun encode(
        file: File,
        mime: String,
        format: MediaFormat,
        frames: Int,
        frameDurationUs: Long,
        fill: () -> ByteArray,
    ) {
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        try {
            for (frame in 0 until frames) {
                val inputIndex = awaitInput(codec)
                val input = codec.getInputBuffer(inputIndex)!!
                val bytes = fill()
                input.clear()
                input.put(bytes)
                codec.queueInputBuffer(inputIndex, 0, bytes.size, frame * frameDurationUs, 0)
            }
            val endIndex = awaitInput(codec)
            codec.queueInputBuffer(endIndex, 0, 0, frames * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }

                    outIndex >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0 && started) {
                            val output = codec.getOutputBuffer(outIndex)!!
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, output, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun awaitInput(codec: MediaCodec): Int {
        while (true) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) return index
        }
    }
}
