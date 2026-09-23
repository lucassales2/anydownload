package com.anydownlod.desktop.store

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.Subscription

/**
 * Delegates behavior to the in-memory engine and writes the job list after
 * every mutation. The shared fake stays ignorant of files.
 */
class PersistingDownloadEngine(
    private val delegate: DownloadEngine,
    private val store: DesktopStore,
) : DownloadEngine by delegate {

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

    private fun persist() {
        store.saveJobs(delegate.jobs.value)
    }
}

/** Delegates subscription behavior and writes the list after every change. */
class PersistingSubscriptionRepository(
    private val delegate: SubscriptionRepository,
    private val store: DesktopStore,
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
        .also { persist() }

    override fun pause(id: String): Boolean = delegate.pause(id).also { persist() }

    override fun resume(id: String): Boolean = delegate.resume(id).also { persist() }

    override fun delete(id: String): Boolean = delegate.delete(id).also { persist() }

    override fun checkNow(id: String): Boolean = delegate.checkNow(id).also { persist() }

    override fun checkAll() {
        delegate.checkAll()
        persist()
    }

    private fun persist() {
        store.saveSubscriptions(delegate.subscriptions.value)
    }
}

/** Delegates settings behavior and writes the document after every change. */
class PersistingSettingsRepository(
    private val delegate: SettingsRepository,
    private val store: DesktopStore,
) : SettingsRepository by delegate {

    override fun update(transform: (AppSettings) -> AppSettings): AppSettings =
        delegate.update(transform).also { store.saveSettings(it) }

    override fun addPreset(name: String, options: Map<String, String>): Preset? =
        delegate.addPreset(name, options).also { store.saveSettings(delegate.settings.value) }

    override fun removePreset(id: String): Boolean =
        delegate.removePreset(id).also { store.saveSettings(delegate.settings.value) }

    override fun reorderPresets(fromIndex: Int, toIndex: Int): Boolean =
        delegate.reorderPresets(fromIndex, toIndex).also { store.saveSettings(delegate.settings.value) }

    override fun setCookiesConfigured(configured: Boolean): AppSettings =
        delegate.setCookiesConfigured(configured).also { store.saveSettings(it) }
}
