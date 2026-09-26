package com.anydownlod.android.engine.media

import com.anydownlod.core.domain.AudioContainer

/**
 * The platform muxing operations the Android media toolkit needs, behind an
 * interface so the toolkit logic compiles and runs in the JVM-equivalent test
 * module. The real implementation uses `android.media` and lives outside this
 * package; nothing here imports the Android framework.
 */
interface MediaMuxerPort {
    /**
     * Copies one video track from [videoPath] and one audio track from
     * [audioPath] into one MP4 at [destinationPath].
     */
    fun merge(videoPath: String, audioPath: String, destinationPath: String)

    /**
     * Copies the first audio track of [sourcePath] into [destinationPath] in
     * [container]. Throws [MuxerFailure.Incompatible] when the platform muxer
     * cannot hold the codec.
     */
    fun copyAudio(sourcePath: String, destinationPath: String, container: AudioContainer)
}

/** A platform muxer failure; the toolkit maps it to a typed `ToolkitError`. */
sealed class MuxerFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** A codec or track the platform muxer cannot place in the container. */
    class Incompatible(message: String) : MuxerFailure(message)

    /** A read/write failure while muxing. */
    class Io(message: String, cause: Throwable? = null) : MuxerFailure(message, cause)
}
