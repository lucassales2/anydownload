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
        ExecutableOnPath.applyToolPath(builder.environment())
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

/**
 * Finds an executable on PATH without starting anything.
 *
 * A Mac app opened from Finder does not inherit the shell PATH, so Homebrew's
 * `/opt/homebrew/bin` is invisible there. After PATH, macOS also checks the
 * usual install directories. Nothing is bundled and nothing is started.
 */
object ExecutableOnPath {
    fun find(name: String): String? {
        val windows = isWindows()
        return findOnPath(
            name = name,
            path = System.getenv("PATH"),
            windows = windows,
            extraDirectories = if (isMac()) macInstallBins() else emptyList(),
        )
    }

    /**
     * Puts the macOS install directories on a child process PATH when they are
     * missing, so yt-dlp can still find ffmpeg. The caller's PATH stays first.
     */
    fun applyToolPath(environment: MutableMap<String, String>) {
        if (!isMac()) return
        val joined = joinPath(environment["PATH"], macInstallBins())
        if (joined.isNotEmpty()) environment["PATH"] = joined
    }

    /**
     * [windows] also looks for `.exe`, `.cmd`, and `.bat`, which is how a
     * Windows install of yt-dlp or ffmpeg is named. A missing file is a miss;
     * nothing is started. [extraDirectories] are searched after PATH and are
     * ignored on Windows.
     */
    internal fun findOnPath(
        name: String,
        path: String?,
        windows: Boolean,
        extraDirectories: List<String> = emptyList(),
    ): String? {
        val candidates = if (windows) {
            listOf(name, "$name.exe", "$name.cmd", "$name.bat")
        } else {
            listOf(name)
        }
        val directories = buildList {
            path?.split(File.pathSeparatorChar)?.let { addAll(it) }
            if (!windows) addAll(extraDirectories)
        }
        directories.forEach { directory ->
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

    internal fun macInstallBins(home: String? = System.getProperty("user.home")): List<String> =
        buildList {
            add("/opt/homebrew/bin")
            add("/usr/local/bin")
            if (!home.isNullOrBlank()) add(Path.of(home, ".local", "bin").toString())
        }

    internal fun joinPath(path: String?, extraDirectories: List<String>): String {
        val existing = path?.split(File.pathSeparatorChar)?.filter { it.isNotBlank() }.orEmpty()
        val suffix = extraDirectories.filter { it.isNotBlank() && it !in existing }
        return (existing + suffix).joinToString(File.pathSeparator)
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")
}
