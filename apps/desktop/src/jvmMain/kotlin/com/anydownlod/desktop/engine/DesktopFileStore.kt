package com.anydownlod.desktop.engine

import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.JavaNetFileStore
import java.nio.file.Path

/**
 * [FileStore] that re-reads the current download root on every call, so a
 * Settings change is honored by queued direct-file jobs without rebuilding
 * the engine. The shared engine checks the root before touching files, so a
 * blank root is already reported as a typed settings error.
 */
class DesktopFileStore(private val rootProvider: () -> String) : FileStore {

    private fun current(): JavaNetFileStore {
        val root = rootProvider()
        if (root.isBlank()) {
            throw IllegalArgumentException("Choose a download folder in Settings first.")
        }
        return JavaNetFileStore(Path.of(root).toAbsolutePath().normalize())
    }

    override fun createTempFile(): FileHandle = current().createTempFile()

    override fun publish(temp: FileHandle, relativePath: String): String =
        current().publish(temp, relativePath)

    override fun delete(relativePath: String): Boolean = current().delete(relativePath)

    override fun size(relativePath: String): Long? = current().size(relativePath)
}