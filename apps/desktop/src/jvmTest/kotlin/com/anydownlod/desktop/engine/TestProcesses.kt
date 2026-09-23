package com.anydownlod.desktop.engine

import java.io.BufferedReader
import java.io.PipedReader
import java.io.PipedWriter
import java.io.StringReader

/** Emits the given lines immediately and returns [exitCode]. */
internal class FakeCliProcess(
    lines: List<String>,
    private val exitCode: Int = 0,
    stderrLines: List<String> = emptyList(),
) : CliProcess {
    override val stdout: BufferedReader = BufferedReader(
        StringReader(if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n"),
    )
    override val stderr: BufferedReader = BufferedReader(
        StringReader(if (stderrLines.isEmpty()) "" else stderrLines.joinToString("\n") + "\n"),
    )

    override fun waitFor(timeoutMillis: Long): Int = exitCode

    override fun destroyTree() = Unit
}

/** Never emits a line and never exits until destroyed. */
internal class BlockingCliProcess : CliProcess {
    private val writer = PipedWriter()
    private val reader = PipedReader(writer)
    override val stdout: BufferedReader = BufferedReader(reader)
    override val stderr: BufferedReader = BufferedReader(StringReader(""))

    @Volatile
    var destroyed = false
        private set

    override fun waitFor(timeoutMillis: Long): Int? = if (destroyed) 1 else null

    override fun destroyTree() {
        destroyed = true
        runCatching { writer.close() }
    }
}
