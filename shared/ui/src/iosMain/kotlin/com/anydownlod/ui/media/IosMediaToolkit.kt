@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anydownlod.ui.media

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.core.postprocess.ToolkitError
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAssetExportPresetPassthrough
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCancelled
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileType
import platform.AVFoundation.AVFileTypeAppleM4A
import platform.AVFoundation.AVFileTypeCoreAudioFormat
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.duration
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * iOS [MediaToolkit] over AVFoundation. A merge builds an
 * `AVMutableComposition` from one video track and one audio track and exports
 * it with `AVAssetExportPresetPassthrough`, so nothing re-encodes. Audio is
 * copied the same way. No subprocess is started; work is foreground-only.
 *
 * M4A and Opus are the capability set, stream-copy only. Opus copies into a
 * Core Audio Format file because AVFoundation has no Ogg writer; MP3, WAV,
 * and FLAC stay out. A rejected codec deletes the partial file and fails
 * typed.
 */
class IosMediaToolkit : MediaToolkit {

    override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities(
        canMerge = true,
        audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
    )

    override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
        throw ToolkitError.ToolUnavailable("iOS does not write audio tags in this phase.")
    }

    override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) {
        val destinationUrl = fileUrl(destination.token)
        try {
            removeIfPresent(destinationUrl)
            export(mergeComposition(video.token, audio.token), destinationUrl, AVFileTypeMPEG4)
        } catch (cancelled: CancellationException) {
            removeIfPresent(destinationUrl)
            throw cancelled
        } catch (error: ToolkitError) {
            removeIfPresent(destinationUrl)
            throw error
        }
    }

    override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
        if (container !in capabilities().audioContainers) {
            throw ToolkitError.IncompatibleStreams(
                "This host cannot write ${container.wireName.uppercase()} audio yet.",
            )
        }
        val destinationUrl = fileUrl(destination.token)
        val fileType = when (container) {
            AudioContainer.M4A -> AVFileTypeAppleM4A
            AudioContainer.OPUS -> AVFileTypeCoreAudioFormat
            else -> throw ToolkitError.IncompatibleStreams(
                "This host cannot write ${container.wireName.uppercase()} audio yet.",
            )
        }
        try {
            removeIfPresent(destinationUrl)
            val asset = AVURLAsset(uRL = fileUrl(source.token), options = null)
            val sourceTrack = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
                ?: throw ToolkitError.IncompatibleStreams("The source file has no audio track.")
            val composition = AVMutableComposition()
            val audioTrack = composition.addMutableTrackWithMediaType(
                mediaType = AVMediaTypeAudio,
                preferredTrackID = kCMPersistentTrackID_Invalid,
            ) ?: throw ToolkitError.IncompatibleStreams("The audio track could not be copied.")
            if (!audioTrack.insertTimeRange(
                    timeRange = CMTimeRangeMake(CMTimeMake(0L, 1), asset.duration),
                    ofTrack = sourceTrack,
                    atTime = CMTimeMake(0L, 1),
                    error = null,
                )
            ) {
                throw ToolkitError.IncompatibleStreams("The audio track could not be copied.")
            }
            export(composition, destinationUrl, fileType)
        } catch (cancelled: CancellationException) {
            removeIfPresent(destinationUrl)
            throw cancelled
        } catch (error: ToolkitError) {
            removeIfPresent(destinationUrl)
            throw error
        }
    }

    private fun mergeComposition(videoPath: String, audioPath: String): AVMutableComposition {
        val videoAsset = AVURLAsset(uRL = fileUrl(videoPath), options = null)
        val audioAsset = AVURLAsset(uRL = fileUrl(audioPath), options = null)
        val videoSource = videoAsset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            ?: throw ToolkitError.IncompatibleStreams("The video file has no video track.")
        val audioSource = audioAsset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
            ?: throw ToolkitError.IncompatibleStreams("The audio file has no audio track.")
        val composition = AVMutableComposition()
        val videoTrack = composition.addMutableTrackWithMediaType(
            mediaType = AVMediaTypeVideo,
            preferredTrackID = kCMPersistentTrackID_Invalid,
        ) ?: throw ToolkitError.IncompatibleStreams("The video track could not be copied.")
        val audioTrack = composition.addMutableTrackWithMediaType(
            mediaType = AVMediaTypeAudio,
            preferredTrackID = kCMPersistentTrackID_Invalid,
        ) ?: throw ToolkitError.IncompatibleStreams("The audio track could not be copied.")
        if (!videoTrack.insertTimeRange(
                timeRange = CMTimeRangeMake(CMTimeMake(0L, 1), videoAsset.duration),
                ofTrack = videoSource,
                atTime = CMTimeMake(0L, 1),
                error = null,
            )
        ) {
            throw ToolkitError.IncompatibleStreams("The video track could not be copied.")
        }
        if (!audioTrack.insertTimeRange(
                timeRange = CMTimeRangeMake(CMTimeMake(0L, 1), audioAsset.duration),
                ofTrack = audioSource,
                atTime = CMTimeMake(0L, 1),
                error = null,
            )
        ) {
            throw ToolkitError.IncompatibleStreams("The audio track could not be copied.")
        }
        return composition
    }

    private suspend fun export(
        asset: platform.AVFoundation.AVAsset,
        outputUrl: NSURL,
        fileType: AVFileType,
    ) {
        val session = AVAssetExportSession(asset = asset, presetName = AVAssetExportPresetPassthrough)
            ?: throw ToolkitError.ToolUnavailable("This host cannot export media.")
        session.outputURL = outputUrl
        session.outputFileType = fileType
        suspendCancellableCoroutine { continuation ->
            session.exportAsynchronouslyWithCompletionHandler {
                when (session.status) {
                    AVAssetExportSessionStatusCompleted -> continuation.resume(Unit, onCancellation = null)
                    AVAssetExportSessionStatusCancelled -> continuation.cancel()
                    else -> continuation.resumeWithException(
                        ToolkitError.IncompatibleStreams(
                            "AVFoundation could not export the selected streams into one file.",
                        ),
                    )
                }
            }
            continuation.invokeOnCancellation { session.cancelExport() }
        }
    }

    private fun fileUrl(path: String): NSURL = NSURL.fileURLWithPath(path)

    private fun removeIfPresent(url: NSURL) {
        NSFileManager.defaultManager.removeItemAtURL(url, error = null)
    }
}
