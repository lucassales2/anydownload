package com.anydownlod.core.platform

/**
 * File operations scoped to one download root.
 *
 * The engine never sees absolute paths and never shows them in the UI.
 * [publish] validates and normalizes [relativePath] against the root and moves
 * the finished temp file onto it; implementations must refuse any path that
 * escapes the root (T-006 / T-038 controls). T-039 wires per-target
 * implementations.
 */
interface FileStore {
    /** Creates a new empty temp file owned by this store. */
    fun createTempFile(): FileHandle

    /**
     * Moves the file behind [temp] to [relativePath] inside the root and
     * returns the normalized relative path. Throws for a traversal or escaping
     * path.
     */
    fun publish(temp: FileHandle, relativePath: String): String

    /** Deletes the file at [relativePath]. True when a file was removed. */
    fun delete(relativePath: String): Boolean

    /** Size in bytes of the file at [relativePath], or null when missing. */
    fun size(relativePath: String): Long?
}

/** An open writable file. Callers close it exactly once. */
interface FileHandle {
    /** Appends the first [length] bytes of [bytes]. */
    fun write(bytes: ByteArray, length: Int)

    /** Flushes and closes the handle. */
    fun close()

    /** Best-effort delete of the underlying file (failure or cancel path). */
    fun discard()
}