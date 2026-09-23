package com.anydownlod.core.domain

/**
 * A channel or playlist the app checks while the window is open.
 *
 * [downloadOptions] are captured at subscribe time. Changing the add form or
 * the Settings defaults later does not change what an existing subscription
 * downloads; the user deletes and re-subscribes to change quality or format.
 *
 * [seenIds] is an ordered, unique list of source item ids, oldest first.
 * The desktop repository bounds its size when it persists new checks.
 */
data class Subscription(
    val id: String,
    val sourceUrl: String,
    val displayName: String,
    val paused: Boolean = false,
    val checkIntervalMinutes: Int = 60,
    /** Empty means every title passes. Validated as a regex by the UI. */
    val titleFilterRegex: String = "",
    val skipMembersOnly: Boolean = false,
    val downloadOptions: DownloadOptions,
    val lastCheckedAtEpochMillis: Long? = null,
    val nextCheckAtEpochMillis: Long? = null,
    val lastError: JobError? = null,
    val seenIds: List<String> = emptyList(),
)
