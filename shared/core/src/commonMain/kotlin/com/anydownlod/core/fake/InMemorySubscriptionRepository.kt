package com.anydownlod.core.fake

import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * In-memory [SubscriptionRepository]. Checks only record timestamps; nothing
 * is downloaded, which keeps the fake honest for every non-desktop host.
 */
class InMemorySubscriptionRepository(
    seedSubscriptions: List<Subscription> = emptyList(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = defaultIdGenerator(),
) : SubscriptionRepository {

    private val _subscriptions = MutableStateFlow(seedSubscriptions)
    override val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    override fun add(
        sourceUrl: String,
        displayName: String,
        downloadOptions: DownloadOptions,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Subscription {
        val created = Subscription(
            id = "sub-${idGenerator()}",
            sourceUrl = sourceUrl,
            displayName = displayName,
            checkIntervalMinutes = checkIntervalMinutes,
            titleFilterRegex = titleFilterRegex,
            skipMembersOnly = skipMembersOnly,
            downloadOptions = downloadOptions,
            nextCheckAtEpochMillis = now() + checkIntervalMinutes * MILLIS_PER_MINUTE,
        )
        _subscriptions.value = _subscriptions.value + created
        return created
    }

    override fun update(
        id: String,
        displayName: String,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Boolean {
        val existing = find(id) ?: return false
        replace(
            existing.copy(
                displayName = displayName,
                checkIntervalMinutes = checkIntervalMinutes,
                titleFilterRegex = titleFilterRegex,
                skipMembersOnly = skipMembersOnly,
                nextCheckAtEpochMillis = if (existing.paused) {
                    null
                } else {
                    now() + checkIntervalMinutes * MILLIS_PER_MINUTE
                },
                // downloadOptions and seenIds are intentionally untouched.
            )
        )
        return true
    }

    override fun pause(id: String): Boolean {
        val existing = find(id) ?: return false
        replace(existing.copy(paused = true, nextCheckAtEpochMillis = null))
        return true
    }

    override fun resume(id: String): Boolean {
        val existing = find(id) ?: return false
        replace(
            existing.copy(
                paused = false,
                nextCheckAtEpochMillis = now() + existing.checkIntervalMinutes * MILLIS_PER_MINUTE,
            )
        )
        return true
    }

    override fun delete(id: String): Boolean {
        val existing = find(id) ?: return false
        _subscriptions.value = _subscriptions.value.filterNot { it.id == existing.id }
        return true
    }

    override fun checkNow(id: String): Boolean {
        val existing = find(id) ?: return false
        val at = now()
        replace(
            existing.copy(
                lastCheckedAtEpochMillis = at,
                nextCheckAtEpochMillis = at + existing.checkIntervalMinutes * MILLIS_PER_MINUTE,
                lastError = null,
            )
        )
        return true
    }

    override fun checkAll() {
        _subscriptions.value.filterNot { it.paused }.map { it.id }.forEach(::checkNow)
    }

    /** Fake-only mutator for checks that need to store seen ids or an error. */
    fun updateSubscription(id: String, transform: (Subscription) -> Subscription): Boolean {
        val existing = find(id) ?: return false
        replace(transform(existing))
        return true
    }

    private fun find(id: String): Subscription? = _subscriptions.value.firstOrNull { it.id == id }

    private fun replace(subscription: Subscription) {
        _subscriptions.value = _subscriptions.value.map {
            if (it.id == subscription.id) subscription else it
        }
    }
}
