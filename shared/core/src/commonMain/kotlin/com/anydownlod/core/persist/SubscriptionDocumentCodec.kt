/*
 * Subscription document codec — AnyDownload (T-019)
 *
 * The on-device subscriptions document: source, name, captured options,
 * interval, seen ids, backlog policy, and the last check/error. Metadata only;
 * no cookie, token, or media URL enters it. Unknown fields are ignored so a
 * newer file still loads, and the shape is shared by every host.
 */
package com.anydownlod.core.persist

import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.Subscription
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SubscriptionDocumentCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(subscriptions: List<Subscription>): String =
        json.encodeToString(SubscriptionsDocument(subscriptions.map { it.toDto() }))

    /** Throws the serializer's exception for malformed JSON; the host decides. */
    fun decode(text: String): List<Subscription> =
        json.decodeFromString<SubscriptionsDocument>(text).subscriptions.map { it.toDomain() }
}

@Serializable
internal data class SubscriptionsDocument(val subscriptions: List<SubscriptionDto> = emptyList())

@Serializable
internal data class SubscriptionDto(
    val id: String,
    val sourceUrl: String,
    val displayName: String,
    val paused: Boolean = false,
    val checkIntervalMinutes: Int = 60,
    val titleFilterRegex: String = "",
    val skipMembersOnly: Boolean = false,
    val downloadExisting: Boolean = false,
    val options: OptionsDto = OptionsDto(),
    val lastCheckedAtEpochMillis: Long? = null,
    val nextCheckAtEpochMillis: Long? = null,
    val lastError: ErrorDto? = null,
    val seenIds: List<String> = emptyList(),
)

internal fun Subscription.toDto(): SubscriptionDto = SubscriptionDto(
    id = id,
    sourceUrl = sourceUrl,
    displayName = displayName,
    paused = paused,
    checkIntervalMinutes = checkIntervalMinutes,
    titleFilterRegex = titleFilterRegex,
    skipMembersOnly = skipMembersOnly,
    downloadExisting = downloadExisting,
    options = downloadOptions.toDto(),
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    nextCheckAtEpochMillis = nextCheckAtEpochMillis,
    lastError = lastError?.toDto(),
    seenIds = seenIds,
)

internal fun SubscriptionDto.toDomain(): Subscription = Subscription(
    id = id,
    sourceUrl = sourceUrl,
    displayName = displayName,
    paused = paused,
    checkIntervalMinutes = checkIntervalMinutes,
    titleFilterRegex = titleFilterRegex,
    skipMembersOnly = skipMembersOnly,
    downloadExisting = downloadExisting,
    downloadOptions = options.toDomain(),
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    nextCheckAtEpochMillis = nextCheckAtEpochMillis,
    lastError = lastError?.toDomain(),
    seenIds = seenIds,
)
