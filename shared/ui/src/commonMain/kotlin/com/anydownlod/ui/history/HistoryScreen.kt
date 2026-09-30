package com.anydownlod.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.cancel
import com.anydownlod.ui.generated.resources.copy_batch
import com.anydownlod.ui.generated.resources.copy_error
import com.anydownlod.ui.generated.resources.copy_urls
import com.anydownlod.ui.generated.resources.copied_urls
import com.anydownlod.ui.generated.resources.delete
import com.anydownlod.ui.generated.resources.delete_failures
import com.anydownlod.ui.generated.resources.delete_file
import com.anydownlod.ui.generated.resources.delete_file_fallback
import com.anydownlod.ui.generated.resources.delete_file_title
import com.anydownlod.ui.generated.resources.deleted_files
import com.anydownlod.ui.generated.resources.empty_history_body
import com.anydownlod.ui.generated.resources.empty_history_title
import com.anydownlod.ui.generated.resources.file_already_gone
import com.anydownlod.ui.generated.resources.file_removed
import com.anydownlod.ui.generated.resources.file_stays
import com.anydownlod.ui.generated.resources.remove_also_delete
import com.anydownlod.ui.generated.resources.history_subtitle
import com.anydownlod.ui.generated.resources.history_title
import com.anydownlod.ui.generated.resources.keep
import com.anydownlod.ui.generated.resources.keep_file
import com.anydownlod.ui.generated.resources.kpi_failed
import com.anydownlod.ui.generated.resources.kpi_saved
import com.anydownlod.ui.generated.resources.kpi_stopped
import com.anydownlod.ui.generated.resources.kpi_unrecognized
import com.anydownlod.ui.generated.resources.open_file
import com.anydownlod.ui.generated.resources.remove
import com.anydownlod.ui.generated.resources.remove_many_title
import com.anydownlod.ui.generated.resources.remove_one_title
import com.anydownlod.ui.generated.resources.remove_selected
import com.anydownlod.ui.generated.resources.retry
import com.anydownlod.ui.generated.resources.retry_failed
import com.anydownlod.ui.generated.resources.reveal_in_folder
import com.anydownlod.ui.i18n.UiText
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.shell.EmptyStatePanel
import org.jetbrains.compose.resources.stringResource
import com.anydownlod.ui.shell.formatBytes
import com.anydownlod.ui.theme.ActionRow
import com.anydownlod.ui.theme.DestructiveOutlinedButton
import com.anydownlod.ui.theme.DestructiveTextButton
import com.anydownlod.ui.theme.KpiTile
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.ObjectCard
import com.anydownlod.ui.theme.PageHeading
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.SelectionBar
import com.anydownlod.ui.theme.StatusBadge
import com.anydownlod.ui.theme.StatusTone
import com.anydownlod.ui.theme.colors
import com.anydownlod.ui.theme.statusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel
import com.anydownlod.ui.export.JobSourceUrls

/**
 * Completed list: successes, failures, cancellations, and unknown future
 * states. Removing history, deleting a file, and retrying stay three
 * differently-named actions with their own confirms.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onCopyUrls: ((String) -> Unit)? = null,
    viewModel: HistoryViewModel = metroViewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val rows = uiState.rows
    val selectedIds = uiState.selectedIds
    val pendingRemoveIds = uiState.pendingRemoveIds
    val pendingDeleteId = uiState.pendingDeleteId
    val statusMessage = uiState.statusMessage
    val statusTone = uiState.statusTone
    val clipboard = LocalClipboardManager.current
    val copyUrls: (String) -> Unit = onCopyUrls ?: { text -> clipboard.setText(AnnotatedString(text)) }

    fun copy(urls: List<String>) {
        if (urls.isEmpty()) return
        copyUrls(JobSourceUrls.text(urls))
        viewModel.noteCopied(urls.size)
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = PageInset)) {
            PageHeading(
                title = stringResource(Res.string.history_title),
                subtitle = stringResource(Res.string.history_subtitle),
            )
            if (rows.isEmpty()) {
                EmptyStatePanel(
                    title = stringResource(Res.string.empty_history_title),
                    body = stringResource(Res.string.empty_history_body),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        value = rows.count { it.state == JobState.COMPLETED }.toString(),
                        label = stringResource(Res.string.kpi_saved),
                        tone = StatusTone.Positive,
                    )
                    KpiTile(
                        value = rows.count { it.state == JobState.FAILED }.toString(),
                        label = stringResource(Res.string.kpi_failed),
                        tone = StatusTone.Negative,
                    )
                    KpiTile(
                        value = rows.count { it.state == JobState.CANCELLED }.toString(),
                        label = stringResource(Res.string.kpi_stopped),
                        tone = StatusTone.Neutral,
                    )
                    val unrecognized = rows.count { it.state == JobState.UNKNOWN }
                    if (unrecognized > 0) {
                        KpiTile(
                            value = unrecognized.toString(),
                            label = stringResource(Res.string.kpi_unrecognized),
                            tone = StatusTone.Critical,
                        )
                    }
                }
                statusMessage?.let { message ->
                    MessageStrip(
                        text = message.resolve(),
                        tone = statusTone,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                SelectionBar(
                    selection = selectionState(selectedIds.size, rows.size),
                    onToggleAll = { viewModel.toggleAll() },
                    selectAllTag = "history-select-all",
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.retrySelected(selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("history-retry-selected"),
                    ) {
                        Text(stringResource(Res.string.retry_failed))
                    }
                    DestructiveOutlinedButton(
                        text = stringResource(Res.string.remove_selected),
                        onClick = { viewModel.requestRemove(selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("history-remove-selected"),
                    )
                    TextButton(
                        onClick = { copy(viewModel.selectedUrls(selectedIds)) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("history-copy-selected"),
                    ) {
                        Text(stringResource(Res.string.copy_urls))
                    }
                    TextButton(
                        onClick = { copy(viewModel.batchUrls(selectedIds)) },
                        enabled = viewModel.batchUrls(selectedIds).isNotEmpty(),
                        modifier = Modifier.testTag("history-copy-batch"),
                    ) {
                        Text(stringResource(Res.string.copy_batch))
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("history-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(rows, key = { it.id }) { row ->
                        HistoryRowItem(
                            row = row,
                            selected = row.id in selectedIds,
                            onSelectedChange = { viewModel.toggle(row.id) },
                            onRetry = { viewModel.retry(row.id) },
                            onCopyError = {
                                row.errorMessage?.let { message ->
                                    clipboard.setText(AnnotatedString(message))
                                }
                            },
                            onOpenFile = {
                                row.artifacts.firstOrNull { !it.removed }?.let { viewModel.openArtifact(it.source) }
                            },
                            onRevealFile = {
                                row.artifacts.firstOrNull { !it.removed }?.let { viewModel.revealArtifact(it.source) }
                            },
                            onDeleteFile = { viewModel.requestDelete(row.id) },
                            onRemove = { viewModel.requestRemove(setOf(row.id)) },
                        )
                    }
                }
            }
        }

        if (pendingRemoveIds.isNotEmpty()) {
            val asksAboutStorage = rows.any { it.id in pendingRemoveIds && it.hasArtifact }
            AlertDialog(
                onDismissRequest = viewModel::dismissRemove,
                title = {
                    Text(
                        if (pendingRemoveIds.size == 1) {
                            stringResource(Res.string.remove_one_title)
                        } else {
                            stringResource(Res.string.remove_many_title, pendingRemoveIds.size)
                        }
                    )
                },
                text = {
                    Text(
                        stringResource(
                            if (asksAboutStorage) Res.string.remove_also_delete else Res.string.file_stays,
                        ),
                    )
                },
                confirmButton = {
                    if (asksAboutStorage) {
                        Column(horizontalAlignment = Alignment.End) {
                            DestructiveTextButton(
                                text = stringResource(Res.string.delete_file),
                                onClick = { viewModel.confirmRemove(deleteStoredFile = true) },
                                modifier = Modifier.testTag("history-confirm-remove-delete"),
                            )
                            TextButton(
                                onClick = { viewModel.confirmRemove(deleteStoredFile = false) },
                                modifier = Modifier.testTag("history-confirm-remove"),
                            ) {
                                Text(stringResource(Res.string.keep_file))
                            }
                        }
                    } else {
                        DestructiveTextButton(
                            text = stringResource(Res.string.remove),
                            onClick = { viewModel.confirmRemove(deleteStoredFile = false) },
                            modifier = Modifier.testTag("history-confirm-remove"),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRemove) {
                        Text(stringResource(if (asksAboutStorage) Res.string.cancel else Res.string.keep))
                    }
                },
            )
        }

        pendingDeleteId?.let { jobId ->
            val row = rows.firstOrNull { it.id == jobId }
            AlertDialog(
                onDismissRequest = viewModel::dismissDelete,
                title = { Text(stringResource(Res.string.delete_file_title)) },
                text = {
                    Text(
                        row?.artifacts
                            ?.joinToString(separator = "\n") { it.fileName }
                            ?: stringResource(Res.string.delete_file_fallback)
                    )
                },
                confirmButton = {
                    DestructiveTextButton(
                        text = stringResource(Res.string.delete),
                        onClick = { viewModel.confirmDelete() },
                        modifier = Modifier.testTag("history-confirm-delete"),
                    )
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDelete) {
                        Text(stringResource(Res.string.keep_file))
                    }
                },
            )
        }
    }
}

private fun selectionState(selected: Int, total: Int): ToggleableState = when {
    selected == 0 -> ToggleableState.Off
    selected == total -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

@Composable
private fun HistoryRowItem(
    row: HistoryRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onCopyError: () -> Unit,
    onOpenFile: () -> Unit,
    onRevealFile: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemove: () -> Unit,
) {
    val tone = row.state.statusTone()
    ObjectCard(accent = tone.colors().foreground) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.testTag("history-select-${row.id}"),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(label = row.stateLabel.resolve(), tone = tone, live = true)
                }
                row.artifacts.forEach { artifact ->
                    val removedLabel = if (artifact.removed) {
                        stringResource(Res.string.file_removed, artifact.fileName)
                    } else {
                        null
                    }
                    Text(
                        text = removedLabel ?: run {
                            listOfNotNull(artifact.fileName, artifact.sizeBytes?.let(::formatBytes))
                                .joinToString(" \u00b7 ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                row.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ActionRow {
                    if (row.canRetry) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("history-retry-${row.id}"),
                        ) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                    if (row.errorMessage != null) {
                        TextButton(
                            onClick = onCopyError,
                            modifier = Modifier.testTag("history-copy-${row.id}"),
                        ) {
                            Text(stringResource(Res.string.copy_error))
                        }
                    }
                    if (row.hasArtifact) {
                        TextButton(
                            onClick = onOpenFile,
                            modifier = Modifier.testTag("history-open-${row.id}"),
                        ) {
                            Text(stringResource(Res.string.open_file))
                        }
                        TextButton(
                            onClick = onRevealFile,
                            modifier = Modifier.testTag("history-reveal-${row.id}"),
                        ) {
                            Text(stringResource(Res.string.reveal_in_folder))
                        }
                        DestructiveTextButton(
                            text = stringResource(Res.string.delete_file),
                            onClick = onDeleteFile,
                            modifier = Modifier.testTag("history-delete-${row.id}"),
                        )
                    }
                    DestructiveTextButton(
                        text = stringResource(Res.string.remove),
                        onClick = onRemove,
                        modifier = Modifier.testTag("history-remove-${row.id}"),
                    )
                }
            }
        }
    }
}
