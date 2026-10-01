package com.anydownload.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydownload.core.SubscriptionRepository
import com.anydownload.core.domain.Subscription
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.subscription_filter_invalid
import com.anydownload.ui.generated.resources.subscription_interval_invalid
import com.anydownload.ui.generated.resources.subscription_name_required
import com.anydownload.ui.generated.resources.subscription_not_found
import com.anydownload.ui.i18n.UiText
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Everything the Subscriptions list renders. */
data class SubscriptionsUiState(
    val rows: List<SubscriptionRow> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val editingId: String? = null,
    val deletingId: String? = null,
    /** T-019: show the suspension-stops-a-scan note (iOS and web). */
    val pauseOnSuspend: Boolean = false,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SubscriptionsViewModel(
    private val repository: SubscriptionRepository,
    private val pauseOnSuspend: Boolean = false,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val editingId = MutableStateFlow<String?>(null)
    private val deletingId = MutableStateFlow<String?>(null)

    val state: StateFlow<SubscriptionsUiState> = combine(
        repository.subscriptions,
        selectedIds,
        editingId,
        deletingId,
    ) { subscriptions, selected, editing, deleting ->
        val rows = rows(subscriptions)
        val ids = rows.map { it.id }.toSet()
        SubscriptionsUiState(
            rows = rows,
            selectedIds = selected.intersect(ids),
            editingId = editing?.takeIf { it in ids },
            deletingId = deleting?.takeIf { it in ids },
            pauseOnSuspend = pauseOnSuspend,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SubscriptionsUiState(pauseOnSuspend = pauseOnSuspend),
    )

    fun toggle(id: String) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun toggleAll() {
        val all = state.value.rows.map { it.id }.toSet()
        selectedIds.update { if (it == all) emptySet() else all }
    }

    fun checkNow(id: String): Boolean = repository.checkNow(id)

    fun checkSelected(ids: Set<String> = selectedIds.value) {
        ids.forEach { repository.checkNow(it) }
    }

    fun checkAll() = repository.checkAll()

    fun pause(id: String): Boolean = repository.pause(id)

    fun resume(id: String): Boolean = repository.resume(id)

    fun beginEdit(id: String) {
        editingId.value = id
    }

    fun dismissEdit() {
        editingId.value = null
    }

    fun beginDelete(id: String) {
        deletingId.value = id
    }

    fun dismissDelete() {
        deletingId.value = null
    }

    fun confirmDelete() {
        val id = deletingId.value ?: return
        repository.delete(id)
        deletingId.value = null
    }

    fun delete(id: String): Boolean = repository.delete(id)

    /** T-019: bulk delete of the selected rows. */
    fun deleteSelected(ids: Set<String> = selectedIds.value) {
        ids.forEach { repository.delete(it) }
        selectedIds.value = emptySet()
    }

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
        val saved = repository.update(id, trimmedName, interval, titleFilter, skipMembersOnly)
        if (!saved) return UiText.of(Res.string.subscription_not_found)
        editingId.value = null
        return null
    }

    companion object {
        fun rows(subscriptions: List<Subscription>): List<SubscriptionRow> =
            subscriptions.map { it.toSubscriptionRow() }

        /** Empty means every title; anything else must compile as a regex. */
        fun isValidTitleFilter(pattern: String): Boolean =
            pattern.isEmpty() || runCatching { Regex(pattern) }.isSuccess
    }
}
