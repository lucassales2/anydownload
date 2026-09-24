package com.anydownlod.desktop.engine

import com.anydownlod.core.MediaPreviewResult
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.PreviewFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * One yt-dlp metadata lookup. `--skip-download` and `--flat-playlist` keep it
 * from fetching media. The URL is a separate argument, never a shell string.
 */
internal class YtDlpMediaPreviewSource(
    private val runner: CliProcessRunner,
    private val resolveExecutable: (String) -> String?,
    private val workingDirectory: () -> Path,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = 45_000,
) : MediaPreviewSource {

    override suspend fun load(url: String): MediaPreviewResult {
        val executable = resolveExecutable("yt-dlp")
            ?: return MediaPreviewResult.Failed(PreviewFailure.Unavailable)
        val directory = workingDirectory()
        val process = try {
            withContext(ioDispatcher) { runner.start(previewCommand(executable, url), directory) }
        } catch (_: Exception) {
            return MediaPreviewResult.Failed(PreviewFailure.Failed)
        }
        return try {
            coroutineScope {
                val stdout = async(ioDispatcher) { readCapped(process.stdout, MAX_CHARS) }
                val stderr = launch(ioDispatcher) {
                    runCatching { readCapped(process.stderr, MAX_CHARS) }
                }
                val exit = withContext(ioDispatcher) { process.waitFor(timeoutMillis) }
                if (exit == null) process.destroyTree()
                stderr.cancel()
                val output = try {
                    stdout.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ""
                }
                if (exit == null) {
                    MediaPreviewResult.Failed(PreviewFailure.TimedOut)
                } else {
                    val preview = YtDlpPreviewJson.parse(output, url)
                    if (preview != null) {
                        MediaPreviewResult.Ready(preview)
                    } else {
                        MediaPreviewResult.Failed(PreviewFailure.Failed)
                    }
                }
            }
        } finally {
            process.destroyTree()
        }
    }

    companion object {
        private const val MAX_CHARS = 8 * 1024 * 1024

        fun previewCommand(executable: String, url: String): List<String> = listOf(
            executable,
            "--dump-single-json",
            "--skip-download",
            "--no-warnings",
            "--no-progress",
            "--flat-playlist",
            "--",
            url,
        )
    }
}

private fun readCapped(reader: java.io.BufferedReader, maxChars: Int): String {
    val buffer = CharArray(4096)
    val out = StringBuilder()
    while (out.length < maxChars) {
        val count = reader.read(buffer)
        if (count < 0) break
        out.append(buffer, 0, minOf(count, maxChars - out.length))
    }
    return out.toString()
}
