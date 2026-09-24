package com.anydownlod.core.platform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlin.random.Random
import platform.posix.FILE
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.rename
import platform.posix.stat
import platform.posix.unlink

/**
 * [FileStore] rooted at one sandbox directory (the iOS app's Documents
 * folder, or a temp folder in tests).
 *
 * Writes stream through a POSIX `FILE*` one chunk at a time; nothing buffers
 * a whole file in memory. Every relative path is validated before use:
 * traversal, absolute paths, empty names, and the root itself are refused, so
 * a published file always stays inside the root.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileStore(
    root: String,
) : FileStore {

    private val rootAbs = root.trimEnd('/').ifEmpty { error("The download root is empty.") }
    private val tempDir = "$rootAbs/.tmp"

    init {
        createDirectories(rootAbs)
        createDirectories(tempDir)
    }

    override fun createTempFile(): FileHandle {
        val path = "$tempDir/.anydownload-${Random.nextLong().toULong().toString(16)}.part"
        val stream = fopen(path, "wb") ?: error("Could not create a temp file under the download root.")
        return IosFileHandle(path, stream)
    }

    override fun publish(temp: FileHandle, relativePath: String): String {
        val target = resolve(relativePath)
        val handle = temp as IosFileHandle
        handle.closeStream()
        createDirectories(target.substringBeforeLast('/'))
        check(rename(handle.path, target) == 0) { "The finished file could not be moved into place." }
        return relativePath
    }

    override fun delete(relativePath: String): Boolean =
        unlink(resolve(relativePath)) == 0

    override fun size(relativePath: String): Long? = memScoped {
        val info = alloc<platform.posix.stat>()
        if (stat(resolve(relativePath), info.ptr) == 0) info.st_size.toLong() else null
    }

    private fun resolve(relativePath: String): String {
        if (relativePath.isBlank()) throw IllegalArgumentException("The artifact path is empty.")
        if (relativePath.startsWith("/")) {
            throw IllegalArgumentException("The artifact path escapes the download root.")
        }
        val segments = relativePath.split('/')
        if (segments.any { it == ".." || it.contains('\\') || it.contains(':') }) {
            throw IllegalArgumentException("The artifact path escapes the download root.")
        }
        val joined = segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
        if (joined.isEmpty()) throw IllegalArgumentException("The artifact path escapes the download root.")
        return "$rootAbs/$joined"
    }

    private fun createDirectories(path: String) {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return
        var current = ""
        for (segment in normalized.split('/')) {
            if (segment.isEmpty()) continue
            current = "$current/$segment"
            if (access(current, 0) != 0) {
                mkdir(current, 0x1EDu) // 0755
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosFileHandle(
    val path: String,
    private var stream: CPointer<FILE>?,
) : FileHandle {

    private var closed = false

    override fun write(bytes: ByteArray, length: Int) {
        val current = stream ?: error("The temp file is already closed.")
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1uL, length.toULong(), current)
        }
    }

    override fun close() = closeStream()

    override fun discard() {
        closeStream()
        unlink(path)
    }

    fun closeStream() {
        if (closed) return
        closed = true
        stream?.let { fclose(it) }
        stream = null
    }
}