package com.anydownload.web

import com.anydownload.core.persist.JobDocumentStorageError
import com.anydownload.core.persist.SubscriptionDocumentStorage

/**
 * The web subscriptions document: one `localStorage` key holding metadata
 * only (T-019). A document over the cap returns the typed error without
 * touching the stored value or the in-memory list.
 */
class WebSubscriptionDocumentStorage(
    private val storage: WebKeyValueStorage = BrowserLocalStorage(),
    private val maxDocumentBytes: Int = MAX_DOCUMENT_BYTES,
    private val key: String = KEY,
) : SubscriptionDocumentStorage {

    companion object {
        const val KEY = "anydownload.subscriptions.document"
        const val MAX_DOCUMENT_BYTES = 512 * 1024
    }

    /** The localStorage key this store owns; useful to assert isolation. */
    val storageKey: String get() = key

    override fun read(): String? = try {
        storage.getItem(key)
    } catch (failure: Throwable) {
        null
    }

    override fun write(document: String): JobDocumentStorageError? {
        if (document.encodeToByteArray().size > maxDocumentBytes) return JobDocumentStorageError.FULL
        return try {
            storage.setItem(key, document)
            null
        } catch (failure: Throwable) {
            JobDocumentStorageError.FULL
        }
    }
}
