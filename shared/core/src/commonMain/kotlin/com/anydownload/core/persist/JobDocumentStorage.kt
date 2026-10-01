package com.anydownload.core.persist

/**
 * Where one host keeps the encoded jobs document (T-103).
 *
 * The document is metadata only: hosts write the string [JobDocumentStore]
 * produces and read it back unchanged. Media bytes never go through this
 * interface, and the storage location is never the download root. Android
 * uses a state directory beside the app files, iOS uses Application Support,
 * and web uses `localStorage`.
 */
interface JobDocumentStorage {
    /** The stored document, or null when the host has none yet. */
    fun read(): String?

    /**
     * Writes the document. A full or unavailable store returns the typed
     * error instead of throwing, so the caller keeps the in-memory job list
     * and surfaces the failure.
     */
    fun write(document: String): JobDocumentStorageError?
}

/** Why a host storage refused a jobs write. */
enum class JobDocumentStorageError {
    /** The store has no room for this document (for example a web quota). */
    FULL,

    /** The store could not be written for any other reason. */
    UNAVAILABLE,
}
