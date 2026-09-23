package com.anydownlod.desktop.engine

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExecutableOnPathTest {

    @Test
    fun windowsPathWithoutYtDlpOrFfmpegFindsNothing() {
        val path = Files.createTempDirectory("anydownlod-empty-path")
        assertNull(ExecutableOnPath.findOnPath("yt-dlp", path.toString(), windows = true))
        assertNull(ExecutableOnPath.findOnPath("ffmpeg", path.toString(), windows = true))
    }

    @Test
    fun windowsFindsYtDlpExeCmdAndBatInPathOrder() {
        val first = Files.createTempDirectory("anydownlod-path-a")
        val second = Files.createTempDirectory("anydownlod-path-b")
        val exe = second.resolve("yt-dlp.exe")
        Files.writeString(exe, "yt-dlp")
        exe.toFile().setExecutable(true)
        val path = listOf(first.toString(), second.toString()).joinToString(File.pathSeparator)

        assertEquals(exe.toString(), ExecutableOnPath.findOnPath("yt-dlp", path, windows = true))
    }

    @Test
    fun windowsPrefersTheBareNameThenExeThenCmd() {
        val directory = Files.createTempDirectory("anydownlod-path-order")
        val cmd = directory.resolve("ffmpeg.cmd")
        Files.writeString(cmd, "ffmpeg")
        cmd.toFile().setExecutable(true)
        assertEquals(cmd.toString(), ExecutableOnPath.findOnPath("ffmpeg", directory.toString(), windows = true))

        val exe = directory.resolve("ffmpeg.exe")
        Files.writeString(exe, "ffmpeg")
        exe.toFile().setExecutable(true)
        assertEquals(exe.toString(), ExecutableOnPath.findOnPath("ffmpeg", directory.toString(), windows = true))
    }

    @Test
    fun otherPlatformsIgnoreTheWindowsSuffix() {
        val directory = Files.createTempDirectory("anydownlod-path-posix")
        val exe = directory.resolve("yt-dlp.exe")
        Files.writeString(exe, "yt-dlp")
        exe.toFile().setExecutable(true)

        assertNull(ExecutableOnPath.findOnPath("yt-dlp", directory.toString(), windows = false))
    }

    @Test
    fun aMissingPathAndBlankEntriesAreMisses() {
        assertNull(ExecutableOnPath.findOnPath("yt-dlp", null, windows = true))
        val directory = Files.createTempDirectory("anydownlod-path-blank")
        val exe = directory.resolve("yt-dlp.exe")
        Files.writeString(exe, "yt-dlp")
        exe.toFile().setExecutable(true)
        val path = listOf("", directory.toString()).joinToString(File.pathSeparator)

        assertEquals(exe.toString(), ExecutableOnPath.findOnPath("yt-dlp", path, windows = true))
    }
}
