package com.anydownlod.ui.subscriptions

import com.anydownlod.core.domain.Subscription

/** One row of the Subscriptions table, already shaped for rendering. */
data class SubscriptionRow(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val sourceHost: String?,
    val intervalMinutes: Int,
    val titleFilter: String,
    val skipMembersOnly: Boolean,
    val lastCheckedAtEpochMillis: Long?,
    val nextCheckAtEpochMillis: Long?,
    val lastError: String?,
    val paused: Boolean,
)

fun Subscription.toSubscriptionRow(): SubscriptionRow = SubscriptionRow(
    id = id,
    name = displayName,
    sourceUrl = sourceUrl,
    sourceHost = hostOfSubscriptionUrl(sourceUrl),
    intervalMinutes = checkIntervalMinutes,
    titleFilter = titleFilterRegex,
    skipMembersOnly = skipMembersOnly,
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    nextCheckAtEpochMillis = nextCheckAtEpochMillis,
    lastError = lastError?.message,
    paused = paused,
)

internal fun hostOfSubscriptionUrl(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return null
    return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').ifEmpty { null }
}
