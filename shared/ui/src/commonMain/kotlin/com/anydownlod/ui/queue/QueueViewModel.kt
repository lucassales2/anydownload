/*
 * Queue ViewModel — AnyDownload
 *
 * T-122: the Downloading list state moves out of `QueueScreen` and into a
 * MetroX ViewModel. The ViewModel owns selection, the cancel confirmation,
 * and the copy decisions; the screen only lays out, resolves strings, and
 * forwards events.
 */
package com.anydownlod.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.UrlOpener
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.export.JobSourceUrls
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
    private val openUrl: UrlOpener = UrlOpener { _ -> },
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingCancelIds = MutableStateFlow<Set<String>>(emptySet())
    private val statusMessage = MutableStateFlow<UiText?>(null)

    val state: StateFlow<QueueUiState> = combine(
        engine.jobs,
        selectedIds,
        pendingCancelIds,
        statusMessage,
    ) { jobs, selected, pending, status ->
        val rows = rows(jobs)
        QueueUiState(
            rows = rows,
            selectedIds = selected,
            pendingCancelIds = pending,
            statusMessage = status,
            working = rows.count { it.state.isActive },
            notStarted = rows.size - rows.count { it.state.isActive },
            batchCopyEnabled = batchUrls(selected).isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QueueUiState())

    init {
        // Rows that leave the list stop being selected or pending, exactly
        // like the old screen-side `LaunchedEffect(rows)`.
        viewModelScope.launch {
            engine.jobs.collect { jobs ->
                val ids = rows(jobs).map { it.id }.toSet()
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
        startSelectedJobs(ids)
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
            cancelSelectedJobs(ids)
            selectedIds.update { it - ids }
        }
    }

    fun confirmCancel() {
        val pending = pendingCancelIds.value
        if (pending.isEmpty()) return
        cancelSelectedJobs(pending)
        selectedIds.update { it - pending }
        pendingCancelIds.value = emptySet()
    }

    fun dismissCancel() {
        pendingCancelIds.value = emptySet()
    }

    /** Source URLs of the selected rows; the screen does the clipboard write. */
    fun copySelected(): List<String> = selectedUrls(selectedIds.value)

    fun copyBatch(): List<String> = batchUrls(selectedIds.value)

    fun openSource(url: String) = openUrl(url)

    /** Records a completed copy so the screen can show the count. */
    fun noteCopied(count: Int) {
        statusMessage.value = UiText.of(Res.string.copied_urls, count)
    }

    private fun startSelectedJobs(ids: Set<String>) {
        engine.jobs.value
            .filter { it.id in ids && (it.state == JobState.PENDING || it.state == JobState.SCHEDULED) }
            .forEach { engine.start(it.id) }
    }

    private fun cancelSelectedJobs(ids: Set<String>) {
        engine.jobs.value
            .filter { it.id in ids && !it.state.isTerminal && it.state != JobState.UNKNOWN }
            .forEach { engine.cancel(it.id) }
    }

    private fun selectedUrls(ids: Set<String>): List<String> = JobSourceUrls.forSelected(engine.jobs.value, ids)

    private fun batchUrls(ids: Set<String>): List<String> = JobSourceUrls.forBatches(engine.jobs.value, ids)

    companion object {
        fun rows(jobs: List<DownloadJob>): List<QueueRow> =
            jobs.filter { !it.state.isTerminal && it.state != JobState.UNKNOWN }
                .map { it.toQueueRow() }
    }
}
