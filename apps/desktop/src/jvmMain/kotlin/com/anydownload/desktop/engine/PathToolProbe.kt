package com.anydownload.desktop.engine

import com.anydownload.core.ToolProbe
import com.anydownload.core.domain.ToolAvailability
import com.anydownload.core.domain.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Runs `yt-dlp --version` and `ffmpeg -version` with an argument list, a short
 * timeout, and no shell. Only the first output line is kept.
 */
class PathToolProbe(
    private val runner: CliProcessRunner = JavaCliProcessRunner,
    private val resolveExecutable: (String) -> String? = ExecutableOnPath::find,
    private val timeoutMillis: Long = 5_000,
    private val workingDirectory: Path = Path.of(System.getProperty("user.home")),
    private val jsRuntime: com.anydownload.core.jsc.JsRuntime = com.anydownload.core.jsc.NoJsRuntime,
) : ToolProbe {

    override suspend fun probe(): ToolStatus = withContext(Dispatchers.IO) {
        ToolStatus(
            ytDlp = probe("yt-dlp", "--version"),
            ffmpeg = probe("ffmpeg", "-version"),
            jsRuntime = com.anydownload.core.domain.ToolAvailability(
                available = jsRuntime.available,
                version = if (jsRuntime.available) {
                    "${jsRuntime.name} ${jsRuntime.version ?: ""} (embedded)".trim()
                } else {
                    null
                },
            ),
        )
    }

    private fun probe(name: String, versionFlag: String): ToolAvailability {
        val executable = resolveExecutable(name) ?: return ToolAvailability(available = false)
        return runCatching {
            val process = runner.start(listOf(executable, versionFlag), workingDirectory)
            try {
                val line = process.stdout.readLine()?.trim().orEmpty()
                val exit = process.waitFor(timeoutMillis)
                if (exit == 0 && line.isNotEmpty()) {
                    ToolAvailability(available = true, version = line.take(160))
                } else {
                    process.destroyTree()
                    ToolAvailability(available = false)
                }
            } catch (failure: Exception) {
                process.destroyTree()
                ToolAvailability(available = false)
            }
        }.getOrElse { ToolAvailability(available = false) }
    }
}
