@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anydownlod.ui.media

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVAssetWriterStatusWriting
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSURL
import platform.posix.memcpy

/**
 * T-081 on-simulator fixtures. A video-only H.264 MP4 is written with
 * `AVAssetWriter` and an audio-only AAC M4A with `AVAudioFile`; both are
 * deleted by the test. No network, no committed media, no subprocess.
 */
internal object IosMediaFixtures {

    suspend fun writeVideoOnly(url: NSURL, width: Int = 128, height: Int = 72, frames: Int = 5) {
        val writer = AVAssetWriter(uRL = url, fileType = AVFileTypeMPEG4, error = null)
        val input = AVAssetWriterInput(
            mediaType = AVMediaTypeVideo,
            outputSettings = mapOf(
                AVVideoCodecKey to AVVideoCodecTypeH264,
                AVVideoWidthKey to width,
                AVVideoHeightKey to height,
            ),
        )
        input.expectsMediaDataInRealTime = false
        val adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput = input,
            sourcePixelBufferAttributes = null,
        )
        writer.addInput(input)
        check(writer.startWriting()) { "The video fixture writer did not start." }
        writer.startSessionAtSourceTime(CMTimeMake(0L, 1))
        for (frame in 0 until frames) {
            while (!input.readyForMoreMediaData) {
                delay(10)
            }
            val buffer = createGrayPixelBuffer(width, height)
            check(adaptor.appendPixelBuffer(buffer, withPresentationTime = CMTimeMake(frame.toLong(), 5))) {
                "A video fixture frame was rejected."
            }
        }
        input.markAsFinished()
        awaitWriting(writer)
        check(writer.status == AVAssetWriterStatusCompleted) { "The video fixture did not finish." }
    }

    fun writeAudioOnly(url: NSURL, seconds: Double = 1.0) {
        val settings: Map<Any?, *> = mapOf(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 44_100.0,
            AVNumberOfChannelsKey to 1,
        )
        val format = AVAudioFormat(settings = settings) ?: error("The audio format is unavailable.")
        val file = AVAudioFile(forWriting = url, settings = settings, error = null)
        val buffer = AVAudioPCMBuffer(
            pCMFormat = file.processingFormat,
            frameCapacity = (format.sampleRate * seconds).toUInt(),
        ) ?: error("The audio buffer could not be created.")
        buffer.frameLength = buffer.frameCapacity
        check(file.writeFromBuffer(buffer, error = null)) { "The audio fixture could not be written." }
        file.close()
    }

    private suspend fun awaitWriting(writer: AVAssetWriter) {
        if (writer.status != AVAssetWriterStatusWriting) return
        suspendCancellableCoroutine { continuation ->
            writer.finishWritingWithCompletionHandler {
                continuation.resume(Unit, onCancellation = null)
            }
        }
    }

    private fun createGrayPixelBuffer(width: Int, height: Int): CVPixelBufferRef = memScoped {
        val out = alloc<CVPixelBufferRefVar>()
        val status = CVPixelBufferCreate(
            allocator = null,
            width = width.toULong(),
            height = height.toULong(),
            pixelFormatType = kCVPixelFormatType_32BGRA,
            pixelBufferAttributes = null,
            pixelBufferOut = out.ptr,
        )
        check(status == 0) { "The pixel buffer could not be created ($status)." }
        val buffer = out.value ?: error("The pixel buffer was not created.")
        CVPixelBufferLockBaseAddress(buffer, 0u)
        try {
            val base = CVPixelBufferGetBaseAddress(buffer) ?: error("The pixel buffer has no base address.")
            val bytesPerRow = CVPixelBufferGetBytesPerRow(buffer).toInt()
            val row = ByteArray(bytesPerRow) { 0x80.toByte() }
            val pointer = base.reinterpret<ByteVar>()
            for (y in 0 until height) {
                row.usePinned { pinned ->
                    memcpy(pointer + y * bytesPerRow, pinned.addressOf(0), bytesPerRow.toULong())
                }
            }
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, 0u)
        }
        buffer
    }
}
