package com.anydownlod.core.platform

import com.anydownlod.core.postprocess.MediaFilePath
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * [FileStore] for Android rooted at one app-private directory, mirroring the
 * JVM host. Every relative path is normalized against [root] and must stay
 * inside it; traversal, absolute paths, and the root itself are refused.
 * (Android's `java.nio` needs API 26+; this project's min SDK is 26.)
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

    override fun createTempFile(extension: String): FileHandle {
        val suffix = if (extension.startsWith(".")) extension else ".$extension"
        val temp = Files.createTempFile(rootAbs, ".anydownload-", suffix)
        return JavaNetFileHandle(temp)
    }

    override fun mediaFilePath(temp: FileHandle): MediaFilePath =
        MediaFilePath((temp as JavaNetFileHandle).path.toString())

    override fun mediaFilePath(relativePath: String): MediaFilePath =
        MediaFilePath(resolve(relativePath).toString())

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