package com.anydownlod.android.engine

import com.anydownlod.core.persist.JobDocumentStorageError
import com.anydownlod.core.persist.SubscriptionDocumentStorage
import java.io.File

/**
 * The Android subscriptions document: one UTF-8 file beside `jobs.json` in
 * the app state directory (T-019). Reads never throw; a failed write returns
 * the typed error so the in-memory list is kept.
 */
class AndroidSubscriptionDocumentStorage(
    private val file: File,
) : SubscriptionDocumentStorage {

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
