package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.core.postprocess.ToolkitError
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Desktop [MediaToolkit] over the `ffmpeg` and `ffprobe` on `PATH`.
 *
 * Stream copy only: a merge copies one video stream and one audio stream and
 * never re-encodes. `ffprobe` checks the written file before it is accepted.
 * The binaries are resolved, never bundled; a missing one fails typed without
 * starting anything. Arguments are a list, never a shell string. Raw tool
 * output is drained and discarded; it never enters an error message or a log.
 */
class DesktopFfmpegToolkit(
    private val runner: CliProcessRunner = JavaCliProcessRunner,
    private val resolveExecutable: (String) -> String? = ExecutableOnPath::find,
    private val workingDirectory: Path = Path.of(System.getProperty("user.home")),
    private val timeoutMillis: Long = 10 * 60 * 1000,
) : MediaToolkit {

    private val ffmpegPath: String? by lazy { resolveExecutable("ffmpeg") }
    private val ffprobePath: String? by lazy { resolveExecutable("ffprobe") }

    override fun capabilities(): ToolkitCapabilities {
        if (ffmpegPath == null || ffprobePath == null) return ToolkitCapabilities.Unavailable
        return ToolkitCapabilities(
            canMerge = true,
            audioContainers = setOf(
                AudioContainer.M4A,
                AudioContainer.OPUS,
                AudioContainer.MP3,
                AudioContainer.WAV,
                AudioContainer.FLAC,
            ),
            canEmbedTags = true,
            canEmbedArtwork = true,
            // WAV has no standard lyrics tag; the other containers do.
            lyricsContainers = setOf(
                AudioContainer.M4A,
                AudioContainer.MP3,
                AudioContainer.OPUS,
                AudioContainer.FLAC,
            ),
        )
    }

    override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) {
        val ffmpeg = requireTool("ffmpeg", ffmpegPath)
        val ffprobe = requireTool("ffprobe", ffprobePath)
        val destinationPath = Path.of(destination.token)
        try {
            runTool(
                executable = ffmpeg,
                arguments = listOf(
                    "-hide_banner", "-nostdin", "-y",
                    "-i", video.token,
                    "-i", audio.token,
                    "-map", "0:v:0",
                    "-map", "1:a:0",
                    "-c", "copy",
                    destination.token,
                ),
                failure = {
                    ToolkitError.IncompatibleStreams(
                        "This host could not copy the selected video and audio into one file.",
                    )
                },
            )
            val probed = probe(ffprobe, destinationPath)
            val expected = mergeFormats(destination.token)
            if (
                probed.videoStreams != 1 ||
                probed.audioStreams != 1 ||
                (expected.isNotEmpty() && probed.formatNames.intersect(expected).isEmpty())
            ) {
                throw ToolkitError.IncompatibleStreams(
                    "The merged file did not keep one video stream and one audio stream.",
                )
            }
        } catch (cancelled: CancellationException) {
            deleteQuietly(destinationPath)
            throw cancelled
        } catch (error: ToolkitError) {
            deleteQuietly(destinationPath)
            throw error
        }
    }

    override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
        val ffmpeg = requireTool("ffmpeg", ffmpegPath)
        val ffprobe = requireTool("ffprobe", ffprobePath)
        val expected = expectedAudioFormats(container)
        val destinationPath = Path.of(destination.token)
        try {
            // Copy when the source already carries the target codec; otherwise
            // name the encoder explicitly. No free-form spec string is built.
            val sourceCodec = probe(ffprobe, Path.of(source.token)).audioCodec
            runTool(
                executable = ffmpeg,
                arguments = extractArguments(source, container, sourceCodec, destination),
                failure = {
                    ToolkitError.IncompatibleStreams(
                        "The audio stream could not be written as ${container.wireName.uppercase()}.",
                    )
                },
            )
            val probed = probe(ffprobe, destinationPath)
            if (probed.audioStreams != 1 || probed.formatNames.intersect(expected).isEmpty()) {
                throw ToolkitError.IncompatibleStreams(
                    "The written audio file is not a valid ${container.wireName.uppercase()} file.",
                )
            }
        } catch (cancelled: CancellationException) {
            deleteQuietly(destinationPath)
            throw cancelled
        } catch (error: ToolkitError) {
            deleteQuietly(destinationPath)
            throw error
        }
    }

    private fun requireTool(name: String, resolved: String?): String =
        resolved ?: throw ToolkitError.ToolUnavailable("The media toolkit is missing $name on this host.")

    override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
        val ffmpeg = requireTool("ffmpeg", ffmpegPath)
        val ffprobe = requireTool("ffprobe", ffprobePath)
        val sourcePath = Path.of(file.token)
        val parent = sourcePath.parent ?: throw ToolkitError.Io("The audio file has no folder.")
        val extension = sourcePath.fileName.toString().substringAfterLast('.', "").ifEmpty { "audio" }
        val token = System.nanoTime().toString(16)
        val outputPath = parent.resolve(".anydownload-tags-$token.$extension")
        val artworkPath = artwork?.let { bytes ->
            parent.resolve(".anydownload-art-$token").also { runCatching { Files.write(it, bytes) } }
        }
        try {
            val wrote = runCatching {
                runTagRewrite(ffmpeg, file, outputPath, tags, artworkPath, extension)
            }
            if (wrote.isFailure && artworkPath != null) {
                // Keep the audio and its tags even when this container cannot
                // hold the artwork.
                runTagRewrite(ffmpeg, file, outputPath, tags, artworkPath = null, extension)
            } else if (wrote.isFailure) {
                throw wrote.exceptionOrNull() ?: ToolkitError.Io("The tags could not be written.")
            }
            val probed = probe(ffprobe, outputPath)
            if (probed.audioStreams != 1) {
                throw ToolkitError.Io("The tagged file did not keep its audio stream.")
            }
            tags.title?.let { expected ->
                val written = probeFormatTags(ffprobe, outputPath)["title"]
                if (written != expected) throw ToolkitError.Io("The tagged file did not keep its title.")
            }
            Files.move(outputPath, sourcePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (cancelled: CancellationException) {
            deleteQuietly(outputPath)
            throw cancelled
        } catch (error: ToolkitError) {
            deleteQuietly(outputPath)
            throw error
        } finally {
            artworkPath?.let(::deleteQuietly)
        }
    }

    /** One ffmpeg tag rewrite into [outputPath]; artwork is optional. */
    private suspend fun runTagRewrite(
        ffmpeg: String,
        source: MediaFilePath,
        outputPath: Path,
        tags: MediaTags,
        artworkPath: Path?,
        extension: String,
    ) {
        val arguments = mutableListOf("-hide_banner", "-nostdin", "-y", "-i", source.token)
        if (artworkPath != null) arguments += listOf("-i", artworkPath.toString())
        arguments += listOf("-map", "0:a:0")
        if (artworkPath != null) {
            arguments += listOf("-map", "1:v:0")
            arguments += if (extension.lowercase() in setOf("m4a", "mp4", "m4v", "mov")) {
                listOf("-c:v", "copy", "-disposition:v", "attached_pic")
            } else {
                listOf("-c:v", "copy")
            }
        }
        arguments += listOf("-c:a", "copy")
        tags.title?.let { arguments += listOf("-metadata", "title=$it") }
        if (tags.artists.isNotEmpty()) {
            arguments += listOf("-metadata", "artist=${tags.artists.joinToString("; ")}")
        }
        tags.album?.let { arguments += listOf("-metadata", "album=$it") }
        tags.albumArtist?.let { arguments += listOf("-metadata", "album_artist=$it") }
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { arguments += listOf("-metadata", "lyrics=$it") }
        tags.year?.let { arguments += listOf("-metadata", "date=$it") }
        tags.trackNumber?.let { arguments += listOf("-metadata", "track=$it") }
        tags.discNumber?.let { arguments += listOf("-metadata", "disc=$it") }
        tags.isrc?.let { arguments += listOf("-metadata", "ISRC=$it") }
        if (extension.lowercase() == "mp3") arguments += listOf("-id3v2_version", "3")
        arguments += outputPath.toString()
        runTool(
            executable = ffmpeg,
            arguments = arguments,
            failure = { ToolkitError.Io("The tags could not be written.") },
        )
    }

    private suspend fun probeFormatTags(ffprobe: String, file: Path): Map<String, String> {
        val result = runTool(
            executable = ffprobe,
            arguments = listOf(
                "-v", "error",
                "-print_format", "json",
                "-show_entries", "format_tags",
                file.toString(),
            ),
            captureStdout = true,
            failure = { ToolkitError.Io("The tagged file could not be read back.") },
        )
        val root = runCatching { Json.parseToJsonElement(result.stdout).jsonObject }.getOrNull() ?: return emptyMap()
        val tags = root["format"]?.jsonObject?.get("tags")?.jsonObject ?: return emptyMap()
        return tags.mapNotNull { (key, value) ->
            value.jsonPrimitive.contentOrNull?.let { key.lowercase() to it }
        }.toMap()
    }

    /** The ffmpeg argument tail for one container, copy-first and codec-explicit. */
    private fun extractArguments(
        source: MediaFilePath,
        container: AudioContainer,
        sourceCodec: String?,
        destination: MediaFilePath,
    ): List<String> {
        val head = listOf("-hide_banner", "-nostdin", "-y", "-i", source.token, "-map", "0:a:0")
        val tail = when (container) {
            AudioContainer.M4A -> listOf("-c:a", "copy", "-f", "ipod")
            AudioContainer.OPUS -> listOf("-c:a", "copy", "-f", "opus")
            AudioContainer.MP3 -> if (sourceCodec == "mp3") {
                listOf("-c:a", "copy", "-f", "mp3")
            } else {
                listOf("-c:a", "libmp3lame", "-f", "mp3")
            }

            AudioContainer.WAV -> if (sourceCodec?.startsWith("pcm_") == true) {
                listOf("-c:a", "copy", "-f", "wav")
            } else {
                listOf("-c:a", "pcm_s16le", "-f", "wav")
            }

            AudioContainer.FLAC -> if (sourceCodec == "flac") {
                listOf("-c:a", "copy", "-f", "flac")
            } else {
                listOf("-c:a", "flac", "-f", "flac")
            }
        }
        return head + tail + destination.token
    }

    /** Container families `ffprobe` may report for each supported audio file. */
    private fun expectedAudioFormats(container: AudioContainer): Set<String> = when (container) {
        AudioContainer.M4A -> setOf("mov", "mp4", "m4a", "3gp", "3g2", "mj2")
        AudioContainer.OPUS -> setOf("ogg", "opus")
        AudioContainer.MP3 -> setOf("mp3")
        AudioContainer.WAV -> setOf("wav")
        AudioContainer.FLAC -> setOf("flac")
    }

    /** Container families the destination extension asks for, or empty when unknown. */
    private fun mergeFormats(destination: String): Set<String> {
        val lower = destination.lowercase()
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".mov") ->
                setOf("mov", "mp4", "m4a", "3gp", "3g2", "mj2")

            lower.endsWith(".mkv") || lower.endsWith(".webm") -> setOf("matroska", "webm")
            else -> emptySet()
        }
    }

    private suspend fun probe(ffprobe: String, file: Path): ProbedMedia {
        val result = runTool(
            executable = ffprobe,
            arguments = listOf(
                "-v", "error",
                "-print_format", "json",
                "-show_entries", "format=format_name:stream=codec_type,codec_name",
                file.toString(),
            ),
            captureStdout = true,
            failure = { ToolkitError.IncompatibleStreams("The written file could not be read back.") },
        )
        return parseProbe(result.stdout)
            ?: throw ToolkitError.Io("The media toolkit returned unreadable output.")
    }

    private fun parseProbe(json: String): ProbedMedia? {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val streams = root["streams"]?.jsonArray.orEmpty()
        var video = 0
        var audio = 0
        var audioCodec: String? = null
        streams.forEach { element ->
            val stream = element.jsonObject
            when (stream["codec_type"]?.jsonPrimitive?.content) {
                "video" -> video++
                "audio" -> {
                    audio++
                    if (audioCodec == null) audioCodec = stream["codec_name"]?.jsonPrimitive?.content
                }
            }
        }
        val formatName = root["format"]?.jsonObject?.get("format_name")?.jsonPrimitive?.content.orEmpty()
        val names = formatName.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ProbedMedia(video, audio, names, audioCodec)
    }

    private class ProbedMedia(
        val videoStreams: Int,
        val audioStreams: Int,
        val formatNames: Set<String>,
        val audioCodec: String?,
    )

    private class ProcessResult(val exitCode: Int?, val stdout: String)

    /**
     * Runs one tool to completion, draining both pipes so a chatty process
     * cannot block on a full buffer. Never returns the drained bytes.
     */
    private suspend fun runTool(
        executable: String,
        arguments: List<String>,
        captureStdout: Boolean = false,
        failure: () -> ToolkitError,
    ): ProcessResult {
        val process = try {
            runner.start(listOf(executable) + arguments, workingDirectory)
        } catch (error: Exception) {
            throw ToolkitError.Io("The media toolkit could not start.", error)
        }
        val result = await(process, captureStdout)
        currentCoroutineContext().ensureActive()
        if (result.exitCode == null) {
            process.destroyTree()
            throw ToolkitError.Io("The media toolkit timed out.")
        }
        if (result.exitCode != 0) {
            throw failure()
        }
        return result
    }

    /**
     * Waits on a cancellable suspension point, then destroys the process if
     * the wait was cancelled. Drains both pipes into daemon threads so a
     * chatty tool cannot block on a full buffer; the bytes are discarded.
     */
    private suspend fun await(process: CliProcess, captureStdout: Boolean): ProcessResult {
        val stdout = StringBuilder()
        val drainOut = Thread {
            runCatching {
                process.stdout.forEachLine { line ->
                    if (captureStdout && stdout.length < MAX_CAPTURED_CHARS) stdout.appendLine(line)
                }
            }
        }
        val drainErr = Thread { runCatching { process.stderr.forEachLine { } } }
        listOf(drainOut, drainErr).forEach {
            it.isDaemon = true
            it.start()
        }
        val exit = try {
            withContext(Dispatchers.IO) {
                val waiter = async { process.waitFor(timeoutMillis) }
                waiter.await()
            }
        } catch (cancelled: CancellationException) {
            process.destroyTree()
            throw cancelled
        }
        return ProcessResult(exit, stdout.toString())
    }

    private fun deleteQuietly(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }

    private companion object {
        const val MAX_CAPTURED_CHARS = 64 * 1024
    }
}
