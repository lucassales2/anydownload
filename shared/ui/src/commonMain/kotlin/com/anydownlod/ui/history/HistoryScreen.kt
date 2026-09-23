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
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.copy_error
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

/**
 * Completed list: successes, failures, cancellations, and unknown future
 * states. Removing history, deleting a file, and retrying stay three
 * differently-named actions with their own confirms.
 */
@Composable
fun HistoryScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    val jobs by graph.engine.jobs.collectAsState()
    val rows = remember(jobs) { HistoryPresenter.rows(jobs) }
    val presenter = remember(graph.engine) { HistoryPresenter(graph.engine) }
    val clipboard = LocalClipboardManager.current
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingRemoveIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<UiText?>(null) }
    var statusTone by remember { mutableStateOf(StatusTone.Information) }

    LaunchedEffect(rows) {
        selectedIds = selectedIds.intersect(rows.map { it.id }.toSet())
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
                    onToggleAll = {
                        selectedIds = if (selectedIds.size == rows.size) emptySet() else rows.map { it.id }.toSet()
                    },
                    selectAllTag = "history-select-all",
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { presenter.retrySelected(selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("history-retry-selected"),
                    ) {
                        Text(stringResource(Res.string.retry_failed))
                    }
                    DestructiveOutlinedButton(
                        text = stringResource(Res.string.remove_selected),
                        onClick = { pendingRemoveIds = selectedIds },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("history-remove-selected"),
                    )
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
                            onSelectedChange = { checked ->
                                selectedIds = if (checked) selectedIds + row.id else selectedIds - row.id
                            },
                            onRetry = { presenter.retry(row.id) },
                            onCopyError = {
                                row.errorMessage?.let { message ->
                                    clipboard.setText(AnnotatedString(message))
                                }
                            },
                            onOpenFile = {
                                row.artifacts.firstOrNull { !it.removed }?.let { graph.openFile(it.source) }
                            },
                            onRevealFile = {
                                row.artifacts.firstOrNull { !it.removed }?.let { graph.revealFile(it.source) }
                            },
                            onDeleteFile = { pendingDeleteId = row.id },
                            onRemove = { pendingRemoveIds = setOf(row.id) },
                        )
                    }
                }
            }
        }

        if (pendingRemoveIds.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { pendingRemoveIds = emptySet() },
                title = {
                    Text(
                        if (pendingRemoveIds.size == 1) {
                            stringResource(Res.string.remove_one_title)
                        } else {
                            stringResource(Res.string.remove_many_title, pendingRemoveIds.size)
                        }
                    )
                },
                text = { Text(stringResource(Res.string.file_stays)) },
                confirmButton = {
                    DestructiveTextButton(
                        text = stringResource(Res.string.remove),
                        onClick = {
                            presenter.removeSelected(pendingRemoveIds)
                            selectedIds = selectedIds - pendingRemoveIds
                            pendingRemoveIds = emptySet()
                        },
                        modifier = Modifier.testTag("history-confirm-remove"),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemoveIds = emptySet() }) {
                        Text(stringResource(Res.string.keep))
                    }
                },
            )
        }

        pendingDeleteId?.let { jobId ->
            val row = rows.firstOrNull { it.id == jobId }
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
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
                        onClick = {
                            val result = presenter.deleteArtifacts(jobId)
                            when {
                                result.failures.isNotEmpty() -> {
                                    statusMessage = UiText.of(
                                        Res.string.delete_failures,
                                        result.failures.joinToString(),
                                    )
                                    statusTone = StatusTone.Negative
                                }
                                result.deletedCount == 0 -> {
                                    statusMessage = UiText.of(Res.string.file_already_gone)
                                    statusTone = StatusTone.Information
                                }
                                else -> {
                                    statusMessage = UiText.quantity(
                                        Res.plurals.deleted_files,
                                        result.deletedCount,
                                        result.deletedCount,
                                    )
                                    statusTone = StatusTone.Information
                                }
                            }
                            pendingDeleteId = null
                        },
                        modifier = Modifier.testTag("history-confirm-delete"),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
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
