/*
 * Persisted subscription repository — AnyDownload (T-019)
 *
 * Wraps the in-memory repository and writes the encoded subscriptions
 * document after every mutation. The host owns the bytes (a state file on
 * Android/iOS/desktop, localStorage on web). A corrupt stored document is
 * ignored so the app still starts; a failed write keeps the in-memory list.
 */
package com.anydownload.core.persist

import com.anydownload.core.SubscriptionRepository
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.JobError
import com.anydownload.core.domain.Subscription
import com.anydownload.core.fake.InMemorySubscriptionRepository

/** Host storage for the encoded subscriptions document (T-019). */
interface SubscriptionDocumentStorage {
    fun read(): String?
    fun write(document: String): JobDocumentStorageError?
}

class PersistedSubscriptionRepository(
    private val delegate: InMemorySubscriptionRepository,
    private val storage: SubscriptionDocumentStorage,
) : SubscriptionRepository by delegate {

    override fun add(
        sourceUrl: String,
        displayName: String,
        downloadOptions: DownloadOptions,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Subscription = delegate.add(
        sourceUrl = sourceUrl,
        displayName = displayName,
        downloadOptions = downloadOptions,
        checkIntervalMinutes = checkIntervalMinutes,
        titleFilterRegex = titleFilterRegex,
        skipMembersOnly = skipMembersOnly,
    ).also { persist() }

    override fun update(
        id: String,
        displayName: String,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Boolean = delegate.update(id, displayName, checkIntervalMinutes, titleFilterRegex, skipMembersOnly)
        .also { if (it) persist() }

    override fun pause(id: String): Boolean = delegate.pause(id).also { if (it) persist() }

    override fun resume(id: String): Boolean = delegate.resume(id).also { if (it) persist() }

    override fun delete(id: String): Boolean = delegate.delete(id).also { if (it) persist() }

    override fun checkNow(id: String): Boolean = delegate.checkNow(id).also { if (it) persist() }

    override fun checkAll() {
        delegate.checkAll()
        persist()
    }

    override fun checkSelected(ids: List<String>) {
        delegate.checkSelected(ids)
        persist()
    }

    override fun recordCheck(
        id: String,
        seenIds: List<String>,
        error: JobError?,
        nextCheckAtEpochMillis: Long,
    ): Boolean = delegate.recordCheck(id, seenIds, error, nextCheckAtEpochMillis).also { if (it) persist() }

    private fun persist() {
        runCatching { storage.write(SubscriptionDocumentCodec.encode(delegate.subscriptions.value)) }
    }

    companion object {
        /** The stored subscriptions; a missing or corrupt document is empty. */
        fun restore(storage: SubscriptionDocumentStorage): List<Subscription> =
            runCatching {
                storage.read()?.takeIf { it.isNotBlank() }?.let(SubscriptionDocumentCodec::decode)
            }.getOrNull().orEmpty()
    }
}
