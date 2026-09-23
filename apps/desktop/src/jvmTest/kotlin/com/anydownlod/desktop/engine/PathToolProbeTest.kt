package com.anydownlod.desktop.engine

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathToolProbeTest {

    @Test
    fun missingExecutablesAreUnavailableWithoutStarting() = runBlocking {
        val probe = PathToolProbe(
            runner = CliProcessRunner { _, _ -> error("must not start") },
            resolveExecutable = { null },
        )

        val status = probe.probe()

        assertFalse(status.ytDlp.available)
        assertFalse(status.ffmpeg.available)
    }

    @Test
    fun onlyTheFirstVersionLineIsCaptured() = runBlocking {
        val probe = PathToolProbe(
            runner = CliProcessRunner { command, _ ->
                if (command.first().contains("yt-dlp")) {
                    FakeCliProcess(listOf("2026.08.19"))
                } else {
                    FakeCliProcess(listOf("ffmpeg version 9.0.1", "built with clang"))
                }
            },
            resolveExecutable = { "/opt/homebrew/bin/$it" },
        )

        val status = probe.probe()

        assertTrue(status.ytDlp.available)
        assertEquals("2026.08.19", status.ytDlp.version)
        assertTrue(status.ffmpeg.available)
        assertEquals("ffmpeg version 9.0.1", status.ffmpeg.version)
    }

    @Test
    fun aNonZeroExitIsUnavailable() = runBlocking {
        val probe = PathToolProbe(
            runner = CliProcessRunner { _, _ -> FakeCliProcess(emptyList(), exitCode = 1) },
            resolveExecutable = { "/fake/$it" },
        )

        assertFalse(probe.probe().ytDlp.available)
    }
}
