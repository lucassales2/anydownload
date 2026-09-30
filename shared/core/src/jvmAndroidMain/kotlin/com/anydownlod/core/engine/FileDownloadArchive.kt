/*
 * File download archive — AnyDownload (T-017, E-25)
 *
 * One text file beside the host's app history, one `<extractor> <id>` line per
 * downloaded media (the upstream `--download-archive` shape). The file is
 * read once and appended on each new entry; an unreadable file is treated as
 * empty, and a failed append never fails the completed job. No cookie, token,
 * signed URL, or private path enters it.
 */
package com.anydownlod.core.engine

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileDownloadArchive(private val file: Path) : DownloadArchive {

    private val lock = Any()
    private var loaded = false
    private val entries = mutableSetOf<String>()

    override fun contains(entry: ArchiveEntry): Boolean = synchronized(lock) {
        ensureLoaded()
        entry.line() in entries
    }

    override fun add(entry: ArchiveEntry) {
        synchronized(lock) {
            ensureLoaded()
            if (!entries.add(entry.line())) return
            runCatching {
                file.parent?.let { Files.createDirectories(it) }
                Files.writeString(
                    file,
                    entry.line() + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            Files.readAllLines(file).forEach { line ->
                line.trim().takeIf { it.isNotEmpty() }?.let { entries += it }
            }
        }
    }
}
