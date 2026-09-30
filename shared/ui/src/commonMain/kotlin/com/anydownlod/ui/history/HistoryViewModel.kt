package com.anydownlod.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.FileOpener
import com.anydownlod.core.FileRevealer
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.export.JobSourceUrls
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.copied_urls
import com.anydownlod.ui.generated.resources.delete_failures
import com.anydownlod.ui.generated.resources.deleted_files
import com.anydownlod.ui.generated.resources.file_already_gone
import com.anydownlod.ui.i18n.UiText
import com.anydownlod.ui.theme.StatusTone
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

/** Everything the Completed list renders. */
data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val pendingRemoveIds: Set<String> = emptySet(),
    val pendingDeleteId: String? = null,
    val statusMessage: UiText? = null,
    val statusTone: StatusTone = StatusTone.Information,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class HistoryViewModel(
    private val engine: DownloadEngine,
    private val openFile: FileOpener = FileOpener { _ -> },
    private val revealFile: FileRevealer = FileRevealer { _ -> },
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingRemoveIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingDeleteId = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow<UiText?>(null)
    private val statusTone = MutableStateFlow(StatusTone.Information)

    private val selection = combine(
        selectedIds,
        pendingRemoveIds,
        pendingDeleteId,
        statusMessage,
        statusTone,
    ) { selected, pending, deleteId, message, tone ->
        SelectionSlice(selected, pending, deleteId, message, tone)
    }

    val state: StateFlow<HistoryUiState> = combine(
        engine.jobs,
        selection,
    ) { jobs, slice ->
        HistoryUiState(
            rows = rows(jobs),
            selectedIds = slice.selectedIds,
            pendingRemoveIds = slice.pendingRemoveIds,
            pendingDeleteId = slice.pendingDeleteId,
            statusMessage = slice.statusMessage,
            statusTone = slice.statusTone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        viewModelScope.launch {
            engine.jobs.collect { jobs ->
                val ids = rows(jobs).map { it.id }.toSet()
                selectedIds.update { it.intersect(ids) }
                pendingRemoveIds.update { it.intersect(ids) }
                pendingDeleteId.update { current -> current?.takeIf { it in ids } }
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

    fun retry(jobId: String): Boolean = engine.retry(jobId) != null

    fun retrySelected(ids: Set<String> = selectedIds.value) {
        engine.jobs.value
            .filter { it.id in ids && (it.state == JobState.FAILED || it.state == JobState.CANCELLED) }
            .forEach { engine.retry(it.id) }
    }

    fun remove(jobId: String): Boolean = engine.removeHistory(jobId)

    fun removeSelected(ids: Set<String>) {
        ids.forEach { engine.removeHistory(it) }
    }

    fun deleteArtifacts(jobId: String) = engine.deleteArtifacts(jobId)

    fun requestRemove(ids: Set<String> = selectedIds.value) {
        if (ids.isEmpty()) return
        pendingRemoveIds.value = ids
    }

    fun dismissRemove() {
        pendingRemoveIds.value = emptySet()
    }

    fun confirmRemove() {
        val pending = pendingRemoveIds.value
        if (pending.isEmpty()) return
        pending.forEach { engine.removeHistory(it) }
        selectedIds.update { it - pending }
        pendingRemoveIds.value = emptySet()
    }

    fun requestDelete(jobId: String) {
        pendingDeleteId.value = jobId
    }

    fun dismissDelete() {
        pendingDeleteId.value = null
    }

    fun confirmDelete() {
        val jobId = pendingDeleteId.value ?: return
        noteDeletion(engine.deleteArtifacts(jobId))
        pendingDeleteId.value = null
    }

    fun openArtifact(artifact: Artifact) = openFile(artifact)

    fun revealArtifact(artifact: Artifact) = revealFile(artifact)

    fun selectedUrls(ids: Set<String> = selectedIds.value): List<String> =
        JobSourceUrls.forSelected(engine.jobs.value, ids)

    fun batchUrls(ids: Set<String> = selectedIds.value): List<String> =
        JobSourceUrls.forBatches(engine.jobs.value, ids)

    fun noteCopied(count: Int) {
        statusMessage.value = UiText.of(Res.string.copied_urls, count)
        statusTone.value = StatusTone.Information
    }

    private fun noteDeletion(result: ArtifactDeletionResult) {
        when {
            result.failures.isNotEmpty() -> {
                statusMessage.value = UiText.of(Res.string.delete_failures, result.failures.joinToString())
                statusTone.value = StatusTone.Negative
            }
            result.deletedCount == 0 -> {
                statusMessage.value = UiText.of(Res.string.file_already_gone)
                statusTone.value = StatusTone.Information
            }
            else -> {
                statusMessage.value = UiText.quantity(Res.plurals.deleted_files, result.deletedCount, result.deletedCount)
                statusTone.value = StatusTone.Information
            }
        }
    }

    private data class SelectionSlice(
        val selectedIds: Set<String>,
        val pendingRemoveIds: Set<String>,
        val pendingDeleteId: String?,
        val statusMessage: UiText?,
        val statusTone: StatusTone,
    )

    companion object {
        fun rows(jobs: List<DownloadJob>): List<HistoryRow> =
            jobs.filter { it.state.isTerminal || it.state == JobState.UNKNOWN }
                .map { it.toHistoryRow() }
    }
}
