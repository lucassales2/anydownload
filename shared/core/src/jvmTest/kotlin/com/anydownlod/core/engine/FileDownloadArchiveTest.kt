package com.anydownlod.core.engine

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-017 file-backed archive: one line per entry, readable across restarts. */
class FileDownloadArchiveTest {

    private fun tempFile(): Path = Files.createTempDirectory("anydownlod-archive-").resolve("downloadArchive.txt")

    @Test
    fun entriesPersistAcrossInstances() {
        val file = tempFile()
        val first = FileDownloadArchive(file)
        first.add(ArchiveEntry("youtube", "abcdefghijk"))
        first.add(ArchiveEntry("youtube", "abcdefghijk"))

        val second = FileDownloadArchive(file)

        assertTrue(second.contains(ArchiveEntry("youtube", "abcdefghijk")))
        assertFalse(second.contains(ArchiveEntry("youtube", "zzzzzzzzzzz")))
        assertTrue(Files.readString(file).lines().count { it.isNotBlank() } == 1)
    }

    @Test
    fun anUnreadableFileIsEmptyAndAnAppendFailureIsSilent() {
        val directory = Files.createTempDirectory("anydownlod-archive-")
        val archive = FileDownloadArchive(directory)

        assertFalse(archive.contains(ArchiveEntry("youtube", "abcdefghijk")))
        // Adding to a path that is a directory fails silently; the job stays complete.
        archive.add(ArchiveEntry("youtube", "abcdefghijk"))
    }
}
