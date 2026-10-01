package com.anydownload.android.engine

import com.anydownload.core.persist.JobDocumentStorage
import com.anydownload.core.persist.JobDocumentStorageError
import java.io.File

/**
 * The Android jobs document: one UTF-8 file in an app state directory that
 * is not the download root (T-103).
 *
 * Reads never throw; a missing or unreadable file returns null and the shared
 * codec starts an empty queue. A failed write returns the typed
 * [JobDocumentStorageError.UNAVAILABLE] instead of throwing so the in-memory
 * queue is never dropped.
 */
class AndroidJobDocumentStorage(
    private val file: File,
) : JobDocumentStorage {

    /** The file this store reads and writes; useful to assert the location. */
    val path: String get() = file.absolutePath

    override fun read(): String? {
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()
    }

    override fun write(document: String): JobDocumentStorageError? = try {
        file.parentFile?.mkdirs()
        file.writeText(document)
        null
    } catch (failure: Exception) {
        JobDocumentStorageError.UNAVAILABLE
    }
}
