package com.anydownlod.ui.subscriptions

import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.domain.Subscription
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.subscription_filter_invalid
import com.anydownlod.ui.generated.resources.subscription_interval_invalid
import com.anydownlod.ui.generated.resources.subscription_name_required
import com.anydownlod.ui.generated.resources.subscription_not_found
import com.anydownlod.ui.i18n.UiText

/**
 * Actions and validation for the Subscriptions table. Checks only record
 * timestamps in the fake; T-035 replaces the repository with real scans behind
 * the same methods.
 */
class SubscriptionsPresenter(private val repository: SubscriptionRepository) {

    fun checkNow(id: String): Boolean = repository.checkNow(id)

    fun checkSelected(ids: Set<String>) {
        ids.forEach { repository.checkNow(it) }
    }

    fun checkAll() = repository.checkAll()

    fun pause(id: String): Boolean = repository.pause(id)

    fun resume(id: String): Boolean = repository.resume(id)

    fun delete(id: String): Boolean = repository.delete(id)

    /**
     * Validates the editable fields and calls the repository. Returns an error
     * message to show, or null on success. Captured download options are never
     * part of this call, so editing cannot replace them.
     */
    fun update(
        id: String,
        name: String,
        intervalText: String,
        titleFilter: String,
        skipMembersOnly: Boolean,
    ): UiText? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return UiText.of(Res.string.subscription_name_required)
        val interval = intervalText.trim().toIntOrNull()
        if (interval == null || interval <= 0) return UiText.of(Res.string.subscription_interval_invalid)
        if (!isValidTitleFilter(titleFilter)) return UiText.of(Res.string.subscription_filter_invalid)
        return if (repository.update(id, trimmedName, interval, titleFilter, skipMembersOnly)) {
            null
        } else {
            UiText.of(Res.string.subscription_not_found)
        }
    }

    companion object {
        fun rows(subscriptions: List<Subscription>): List<SubscriptionRow> =
            subscriptions.map { it.toSubscriptionRow() }

        /** Empty means every title; anything else must compile as a regex. */
        fun isValidTitleFilter(pattern: String): Boolean =
            pattern.isEmpty() || runCatching { Regex(pattern) }.isSuccess
    }
}
