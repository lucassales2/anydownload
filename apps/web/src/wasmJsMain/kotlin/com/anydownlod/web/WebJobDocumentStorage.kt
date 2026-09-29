package com.anydownlod.web

import com.anydownlod.core.persist.JobDocumentStorage
import com.anydownlod.core.persist.JobDocumentStorageError
import kotlinx.browser.window
import org.w3c.dom.Storage

/** The browser key-value surface the jobs document store uses. */
interface WebKeyValueStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
}

/** `localStorage` when the page has it; tests inject an in-memory fake. */
private class BrowserLocalStorage(
    private val storage: Storage = window.localStorage,
) : WebKeyValueStorage {
    override fun getItem(key: String): String? = storage.getItem(key)
    override fun setItem(key: String, value: String) = storage.setItem(key, value)
}

/**
 * The web jobs document: one `localStorage` key holding metadata only (T-103).
 *
 * Media bytes never pass through this store. The document is the JSON string
 * from the shared codec; inline media markers are refused, and a document
 * over [maxDocumentBytes] returns [JobDocumentStorageError.FULL] without
 * touching the stored value or the in-memory queue.
 *
 * The page keeps the whole queue in its engine's `StateFlow`; a refused write
 * only surfaces [JobDocumentStorageError], so rows already on screen are not
 * dropped.
 */
class WebJobDocumentStorage(
    private val storage: WebKeyValueStorage = BrowserLocalStorage(),
    private val maxDocumentBytes: Int = MAX_DOCUMENT_BYTES,
    private val key: String = KEY,
) : JobDocumentStorage {

    companion object {
        const val KEY = "anydownload.jobs.document"

        /**
         * Metadata cap. `localStorage` is commonly 5 MB; the jobs document is
         * a few KB per row, so half a megabyte is a generous ceiling that
         * still fails loudly instead of consuming the whole origin quota.
         */
        const val MAX_DOCUMENT_BYTES = 512 * 1024

        /** A document that appears to carry inline media is refused. */
        val INLINE_MEDIA = Regex(
            pattern = "data:(video|audio|application/octet-stream)",
            option = RegexOption.IGNORE_CASE,
        )
    }

    /** The localStorage key this store owns; useful to assert isolation. */
    val storageKey: String get() = key

    override fun read(): String? = try {
        storage.getItem(key)
    } catch (failure: Throwable) {
        null
    }

    override fun write(document: String): JobDocumentStorageError? {
        if (INLINE_MEDIA.containsMatchIn(document)) return JobDocumentStorageError.UNAVAILABLE
        if (document.encodeToByteArray().size > maxDocumentBytes) return JobDocumentStorageError.FULL
        return try {
            storage.setItem(key, document)
            null
        } catch (failure: Throwable) {
            // A browser quota error arrives as a JS exception here.
            JobDocumentStorageError.FULL
        }
    }
}
