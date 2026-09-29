/*
 * Queue ViewModel — AnyDownload
 *
 * T-122: the Downloading list state moves out of `QueueScreen` and into a
 * MetroX ViewModel. The ViewModel owns selection, the cancel confirmation,
 * and the copy decisions; the screen only lays out, resolves strings, and
 * forwards events. `QueuePresenter` keeps the pure row mapping and the
 * engine-action rules.
 */
package com.anydownlod.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydownlod.core.DownloadEngine
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.copied_urls
import com.anydownlod.ui.i18n.UiText
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
import kotlinx.coroutines.launch

/** Everything the Downloading list renders. */
data class QueueUiState(
    val rows: List<QueueRow> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val pendingCancelIds: Set<String> = emptySet(),
    val statusMessage: UiText? = null,
    val working: Int = 0,
    val notStarted: Int = 0,
    val batchCopyEnabled: Boolean = false,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class QueueViewModel(
    private val engine: DownloadEngine,
) : ViewModel() {

    private val presenter = QueuePresenter(engine)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingCancelIds = MutableStateFlow<Set<String>>(emptySet())
    private val statusMessage = MutableStateFlow<UiText?>(null)

    val state: StateFlow<QueueUiState> = combine(
        engine.jobs,
        selectedIds,
        pendingCancelIds,
        statusMessage,
    ) { jobs, selected, pending, status ->
        val rows = QueuePresenter.rows(jobs)
        QueueUiState(
            rows = rows,
            selectedIds = selected,
            pendingCancelIds = pending,
            statusMessage = status,
            working = rows.count { it.state.isActive },
            notStarted = rows.size - rows.count { it.state.isActive },
            batchCopyEnabled = presenter.batchUrls(selected).isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QueueUiState())

    init {
        // Rows that leave the list stop being selected or pending, exactly
        // like the old screen-side `LaunchedEffect(rows)`.
        viewModelScope.launch {
            engine.jobs.collect { jobs ->
                val ids = QueuePresenter.rows(jobs).map { it.id }.toSet()
                selectedIds.update { it.intersect(ids) }
                pendingCancelIds.update { it.intersect(ids) }
            }
        }
    }

    fun toggle(id: String) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun toggleAll() {
        val all = state.value.rows.map { it.id }.toSet()
        selectedIds.update { if (it == all) emptySet() else all }
    }

    /** Starts the selected rows; [ids] defaults to the current selection. */
    fun startSelected(ids: Set<String> = selectedIds.value) {
        if (ids.isEmpty()) return
        presenter.startSelected(ids)
    }

    /**
     * Asks to cancel [ids] (the current selection by default). Rows that are
     * already working go to [QueueUiState.pendingCancelIds] for the confirm
     * dialog; the rest cancel immediately.
     */
    fun requestCancelSelected(ids: Set<String> = selectedIds.value) {
        if (ids.isEmpty()) return
        val needsConfirm = state.value.rows.any { it.id in ids && it.cancelNeedsConfirm }
        if (needsConfirm) {
            pendingCancelIds.value = ids
        } else {
            presenter.cancelSelected(ids)
            selectedIds.update { it - ids }
        }
    }

    fun confirmCancel() {
        val pending = pendingCancelIds.value
        if (pending.isEmpty()) return
        presenter.cancelSelected(pending)
        selectedIds.update { it - pending }
        pendingCancelIds.value = emptySet()
    }

    fun dismissCancel() {
        pendingCancelIds.value = emptySet()
    }

    /** Source URLs of the selected rows; the screen does the clipboard write. */
    fun copySelected(): List<String> = presenter.selectedUrls(selectedIds.value)

    /** Source URLs of every child of the selected rows' batches. */
    fun copyBatch(): List<String> = presenter.batchUrls(selectedIds.value)

    /** Records a completed copy so the screen can show the count. */
    fun noteCopied(count: Int) {
        statusMessage.value = UiText.of(Res.string.copied_urls, count)
    }
}
