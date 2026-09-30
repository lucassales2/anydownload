package com.anydownlod.core

import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.Subscription
import kotlinx.coroutines.flow.StateFlow

/**
 * Local channel and playlist subscriptions. The in-memory fake records checks
 * without downloading; the desktop implementation performs real flat scans.
 */
interface SubscriptionRepository {
    val subscriptions: StateFlow<List<Subscription>>

    /**
     * Adds a subscription that captures [downloadOptions] as its fixed
     * download options. Changing the add form later must not affect it.
     */
    fun add(
        sourceUrl: String,
        displayName: String,
        downloadOptions: DownloadOptions,
        checkIntervalMinutes: Int = AppSettingsDefaults.SUBSCRIPTION_INTERVAL_MINUTES,
        titleFilterRegex: String = "",
        skipMembersOnly: Boolean = false,
    ): Subscription

    /**
     * Updates only the editable fields. The captured [DownloadOptions] and
     * seen ids are left untouched. Returns false for an unknown id.
     */
    fun update(
        id: String,
        displayName: String,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Boolean

    fun pause(id: String): Boolean

    fun resume(id: String): Boolean

    fun delete(id: String): Boolean

    /** Records a check timestamp. The fake does not enqueue any job. */
    fun checkNow(id: String): Boolean

    /** Checks unpaused subscriptions. Paused rows are skipped. */
    fun checkAll()

    /** T-019: checks only the given ids; paused and unknown ids are skipped. */
    fun checkSelected(ids: List<String>)

    /**
     * T-019: persists one scan outcome. [seenIds] is already capped by the
     * caller; a null [error] clears the last error. The captured options and
     * the subscription id are untouched.
     */
    fun recordCheck(
        id: String,
        seenIds: List<String>,
        error: JobError?,
        nextCheckAtEpochMillis: Long,
    ): Boolean
}
