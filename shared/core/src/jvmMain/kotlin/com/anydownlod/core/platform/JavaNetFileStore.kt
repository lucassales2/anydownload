package com.anydownlod.core.platform

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * [FileStore] for JVM and Android rooted at one absolute directory.
 *
 * Every relative path is normalized against [root] and must stay inside it;
 * traversal, absolute paths, and the root itself are refused. The engine only
 * ever sees relative paths, so [JavaNetFileStore.publish] returning a
 * normalized relative path keeps that promise.
 */
class JavaNetFileStore(root: Path) : FileStore {

    private val rootAbs: Path = root.toAbsolutePath().normalize()

    init {
        Files.createDirectories(rootAbs)
    }

    override fun createTempFile(): FileHandle {
        val temp = Files.createTempFile(rootAbs, ".anydownload-", ".part")
        return JavaNetFileHandle(temp)
    }

    override fun publish(temp: FileHandle, relativePath: String): String {
        val target = resolve(relativePath)
        val source = (temp as JavaNetFileHandle).path
        if (Files.exists(target)) {
            Files.delete(target)
        }
        Files.createDirectories(target.parent)
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        return rootAbs.relativize(target).toString().replace('\\', '/')
    }

    override fun delete(relativePath: String): Boolean =
        Files.deleteIfExists(resolve(relativePath))

    override fun size(relativePath: String): Long? {
        val path = resolve(relativePath)
        return if (Files.isRegularFile(path)) Files.size(path) else null
    }

    private fun resolve(relativePath: String): Path {
        if (relativePath.isBlank()) {
            throw IllegalArgumentException("The artifact path is empty.")
        }
        val resolved = rootAbs.resolve(relativePath).normalize()
        if (resolved == rootAbs || !resolved.startsWith(rootAbs)) {
            throw IllegalArgumentException("The artifact path escapes the download root.")
        }
        return resolved
    }
}

private class JavaNetFileHandle(val path: Path) : FileHandle {
    private val output: OutputStream = Files.newOutputStream(path)
    private var closed = false

    override fun write(bytes: ByteArray, length: Int) {
        output.write(bytes, 0, length)
    }

    override fun close() {
        output.flush()
        output.close()
        closed = true
    }

    override fun discard() {
        runCatching { if (!closed) output.close() }
        runCatching { Files.deleteIfExists(path) }
        closed = true
    }
}