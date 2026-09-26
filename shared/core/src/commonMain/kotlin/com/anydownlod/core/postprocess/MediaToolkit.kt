/*
 * Media toolkit contract — AnyDownload
 *
 * Merge one video file and one audio file, and copy or convert audio. Host
 * adapters live outside common code (desktop, Android, and iOS host tasks).
 * Common code never spawns a process and never imports a platform muxer; web
 * keeps [UnavailableToolkit]. D5 never re-encodes a merge: a container that
 * cannot hold the source codecs fails typed.
 */
package com.anydownlod.core.postprocess

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaTags
import kotlin.jvm.JvmInline

/**
 * Opaque reference to one local media file the host toolkit can open.
 *
 * The token is host-native (a path on desktop, Android, and iOS). Shared code
 * only moves it between the file store and the toolkit; it is never parsed,
 * logged, or shown in the UI. The web host never creates one because its
 * capability set is empty.
 */
@JvmInline
value class MediaFilePath(val token: String)

/**
 * What the running host can do with local media.
 *
 * [canMerge] is true only when the host can copy one video stream and one
 * audio stream into a single container. [audioContainers] names the containers
 * [MediaToolkit.extractAudio] can produce on this host, whether by stream copy
 * or by a platform encoder. [canEmbedTags] is true only when the host can
 * rewrite an audio file's tags; [canEmbedArtwork] additionally says the host
 * can write the artwork bytes the engine fetched. A host that cannot embed
 * still keeps the audio file; the engine records that the tags were skipped.
 */
data class ToolkitCapabilities(
    val canMerge: Boolean = false,
    val audioContainers: Set<AudioContainer> = emptySet(),
    val canEmbedTags: Boolean = false,
    val canEmbedArtwork: Boolean = false,
    /** Containers whose tags can carry lyrics; WAV is not one of them. */
    val lyricsContainers: Set<AudioContainer> = emptySet(),
) {
    companion object {
        /** The web and test default: no merge, no audio extraction, no tags. */
        val Unavailable: ToolkitCapabilities = ToolkitCapabilities()
    }
}

/**
 * A media toolkit failure the engine maps to a typed job error.
 *
 * Messages name the reason in user-safe words. Raw tool stderr, absolute
 * paths, and media URLs never enter one.
 */
sealed class ToolkitError(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** No toolkit is wired, or a required host tool is missing. */
    class ToolUnavailable(
        message: String = "The media toolkit is not available on this host.",
    ) : ToolkitError(message, retryable = false)

    /** The destination container cannot hold the source streams as they are. */
    class IncompatibleStreams(
        message: String = "This host cannot put the selected streams in one file without re-encoding.",
    ) : ToolkitError(message, retryable = false)

    /** The host toolkit failed while reading or writing a file. */
    class Io(
        message: String = "The media toolkit could not read or write a file.",
        cause: Throwable? = null,
    ) : ToolkitError(message, retryable = true, cause = cause)
}

/**
 * Host media operations: merge one video file with one audio file, and extract
 * audio into a named container.
 *
 * Implementations copy streams; a merge never re-encodes. The caller owns the
 * destination file: it creates the empty temp, passes its [MediaFilePath], and
 * publishes it after the call. Implementations delete a partial destination
 * before throwing. Implementations are cancellable: cancellation stops the
 * work and removes the partial destination.
 */
interface MediaToolkit {
    fun capabilities(): ToolkitCapabilities

    /**
     * Copies [video]'s video stream and [audio]'s audio stream into
     * [destination]. Throws [ToolkitError.IncompatibleStreams] when the
     * container cannot hold the codecs.
     */
    suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath)

    /**
     * Writes [source]'s audio into [destination] as [container]. A stream that
     * is already in [container] is copied; otherwise the host may encode when
     * [container] is in its capability set.
     */
    suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath)

    /**
     * Rewrites [file]'s metadata to [tags] in place, optionally embedding the
     * already-fetched [artwork] bytes. Called only when
     * [ToolkitCapabilities.canEmbedTags] is true. Implementations keep the
     * audio streams untouched, replace the file atomically, and delete a
     * partial rewrite before throwing. [artwork] is null when the host cannot
     * embed it or the fetch failed.
     */
    suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray? = null)
}

/**
 * The default when no host toolkit is wired (web, tests, and hosts before
 * their adapter lands). It reports empty capabilities and fails both
 * operations typed; it starts nothing.
 */
object UnavailableToolkit : MediaToolkit {
    override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities.Unavailable

    override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) {
        throw ToolkitError.ToolUnavailable()
    }

    override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
        throw ToolkitError.ToolUnavailable()
    }

    override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
        throw ToolkitError.ToolUnavailable()
    }
}
