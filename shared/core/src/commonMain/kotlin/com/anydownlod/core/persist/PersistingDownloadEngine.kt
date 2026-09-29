package com.anydownlod.core.persist

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps a host [DownloadEngine] and writes the shared jobs document after
 * every mutation (T-103).
 *
 * The wrapper never mutates the engine's job rows. When a host store refuses
 * a write, [storageError] carries the typed reason while the in-memory list
 * stays exactly as it was, so a full store does not drop rows already on
 * screen. A later successful write clears the error.
 *
 * [jobsSnapshot] exists for hosts whose [DownloadEngine.jobs] flow merges two
 * engines asynchronously (Android's routing engine). Hosts with one engine
 * keep the default, which reads the delegate directly.
 */
class PersistingDownloadEngine(
    private val delegate: DownloadEngine,
    private val documentStore: JobDocumentStore,
    private val writeDocument: (String) -> JobDocumentStorageError?,
    private val clearAfterSeconds: () -> Long = { 0L },
    private val jobsSnapshot: () -> List<DownloadJob> = { delegate.jobs.value },
) : DownloadEngine by delegate {

    private val _storageError = MutableStateFlow<JobDocumentStorageError?>(null)

    /** The last refused write, or null once a write succeeded. */
    val storageError: StateFlow<JobDocumentStorageError?> = _storageError.asStateFlow()

    override fun submit(request: DownloadRequest): DownloadJob =
        delegate.submit(request).also { persist() }

    override fun start(jobId: String): DownloadJob? =
        delegate.start(jobId).also { persist() }

    override fun cancel(jobId: String): DownloadJob? =
        delegate.cancel(jobId).also { persist() }

    override fun retry(jobId: String): DownloadJob? =
        delegate.retry(jobId).also { persist() }

    override fun removeHistory(jobId: String): Boolean =
        delegate.removeHistory(jobId).also { persist() }

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult =
        delegate.deleteArtifacts(jobId).also { persist() }

    /** Writes the current rows now; used for the first normalized save. */
    fun persistNow() = persist()

    private fun persist() {
        val saved = documentStore.save(jobsSnapshot(), clearAfterSeconds())
        _storageError.value = writeDocument(saved.document)
    }
}
