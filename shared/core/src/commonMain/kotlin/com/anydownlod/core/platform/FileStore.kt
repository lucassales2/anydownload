package com.anydownlod.core.platform

import com.anydownlod.core.postprocess.MediaFilePath

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
     * Creates a new empty temp file whose name ends with `.[extension]`, for a
     * host toolkit that picks its container from the file name. The default
     * keeps [createTempFile] when a host does not need the suffix.
     */
    fun createTempFile(extension: String): FileHandle = createTempFile()

    /**
     * Host path token for [temp], for the media toolkit only.
     *
     * The engine moves the result straight into a `MediaToolkit` call; it
     * never parses, logs, or stores the token. Hosts without a toolkit keep
     * the default.
     */
    fun mediaFilePath(temp: FileHandle): MediaFilePath =
        throw UnsupportedOperationException("This host has no media toolkit.")

    /**
     * Host path token for an already-published file at [relativePath], for the
     * media toolkit only. Used by the overwrite `metadata` mode to retag an
     * existing file. The default refuses; hosts with a toolkit override it.
     */
    fun mediaFilePath(relativePath: String): MediaFilePath =
        throw UnsupportedOperationException("This host cannot open a published file for the media toolkit.")

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