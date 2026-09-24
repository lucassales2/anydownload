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

    @Test
    fun macFindsAHomebrewInstallWhenPathDoesNotIncludeIt() {
        val path = Files.createTempDirectory("anydownlod-path-gui")
        val homebrew = Files.createTempDirectory("anydownlod-homebrew-bin")
        val binary = homebrew.resolve("yt-dlp")
        Files.writeString(binary, "#!/bin/sh\n")
        binary.toFile().setExecutable(true)

        assertEquals(
            binary.toString(),
            ExecutableOnPath.findOnPath(
                "yt-dlp",
                path.toString(),
                windows = false,
                extraDirectories = listOf(homebrew.toString()),
            ),
        )
    }

    @Test
    fun pathWinsOverALaterInstallDirectory() {
        val path = Files.createTempDirectory("anydownlod-path-first")
        val extra = Files.createTempDirectory("anydownlod-path-extra")
        val preferred = path.resolve("yt-dlp")
        Files.writeString(preferred, "#!/bin/sh\n")
        preferred.toFile().setExecutable(true)
        val other = extra.resolve("yt-dlp")
        Files.writeString(other, "#!/bin/sh\n")
        other.toFile().setExecutable(true)

        assertEquals(
            preferred.toString(),
            ExecutableOnPath.findOnPath(
                "yt-dlp",
                path.toString(),
                windows = false,
                extraDirectories = listOf(extra.toString()),
            ),
        )
    }

    @Test
    fun windowsIgnoresTheMacInstallDirectories() {
        val extra = Files.createTempDirectory("anydownlod-win-extra")
        val binary = extra.resolve("yt-dlp.exe")
        Files.writeString(binary, "yt-dlp")
        binary.toFile().setExecutable(true)

        assertNull(
            ExecutableOnPath.findOnPath(
                "yt-dlp",
                path = null,
                windows = true,
                extraDirectories = listOf(extra.toString()),
            ),
        )
    }

    @Test
    fun macInstallDirectoriesCoverHomebrewAndTheUserBin() {
        assertEquals(
            listOf("/opt/homebrew/bin", "/usr/local/bin", "/Users/ada/.local/bin"),
            ExecutableOnPath.macInstallBins(home = "/Users/ada"),
        )
    }

    @Test
    fun childPathKeepsTheCallerPathAndAppendsAMissingInstallDirectory() {
        val joined = ExecutableOnPath.joinPath("/usr/bin:/bin", listOf("/opt/homebrew/bin", "/usr/bin"))
        assertEquals("/usr/bin:/bin:/opt/homebrew/bin", joined.replace(File.pathSeparator, ":"))
    }
}
