package com.anydownlod.desktop.engine

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * A started process, abstracted so tests can script one without a real binary.
 *
 * `ProcessBuilder` and any other process API live only in this desktop module,
 * never in shared code.
 */
interface CliProcess {
    val stdout: BufferedReader
    val stderr: BufferedReader

    /** Returns the exit code, or null when the timeout elapsed. */
    fun waitFor(timeoutMillis: Long): Int?

    /** Destroys this process and every descendant; safe to call twice. */
    fun destroyTree()
}

/** Starts argument-list processes. Never builds or runs a shell string. */
fun interface CliProcessRunner {
    @Throws(IOException::class)
    fun start(command: List<String>, workingDirectory: Path): CliProcess
}

object JavaCliProcessRunner : CliProcessRunner {
    override fun start(command: List<String>, workingDirectory: Path): CliProcess {
        val builder = ProcessBuilder(command)
        builder.directory(workingDirectory.toFile())
        builder.redirectErrorStream(false)
        return JavaCliProcess(builder.start())
    }
}

private class JavaCliProcess(private val process: Process) : CliProcess {
    override val stdout: BufferedReader =
        BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
    override val stderr: BufferedReader =
        BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))

    override fun waitFor(timeoutMillis: Long): Int? =
        if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) process.exitValue() else null

    override fun destroyTree() {
        // Java 9+ process handles; a descendant failure is never fatal.
        val descendants = runCatching { process.toHandle().descendants().toList() }
            .getOrDefault(emptyList())
        descendants.forEach { handle -> runCatching { handle.destroy() } }
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            descendants.forEach { handle -> runCatching { handle.destroyForcibly() } }
            process.destroyForcibly()
        }
    }
}

/** Finds an executable on PATH without starting anything. */
object ExecutableOnPath {
    fun find(name: String): String? = findOnPath(
        name = name,
        path = System.getenv("PATH"),
        windows = isWindows(),
    )

    /**
     * [windows] also looks for `.exe`, `.cmd`, and `.bat`, which is how a
     * Windows install of yt-dlp or ffmpeg is named. A missing file is a miss;
     * nothing is started.
     */
    internal fun findOnPath(name: String, path: String?, windows: Boolean): String? {
        if (path == null) return null
        val candidates = if (windows) {
            listOf(name, "$name.exe", "$name.cmd", "$name.bat")
        } else {
            listOf(name)
        }
        path.split(File.pathSeparatorChar).forEach { directory ->
            if (directory.isBlank()) return@forEach
            val base = runCatching { Path.of(directory) }.getOrNull() ?: return@forEach
            candidates.forEach { candidate ->
                val file = base.resolve(candidate)
                if (Files.isRegularFile(file) && Files.isExecutable(file)) {
                    return file.toString()
                }
            }
        }
        return null
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}
