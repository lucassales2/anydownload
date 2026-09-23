package com.anydownlod.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.shell.EmptyStatePanel
import com.anydownlod.ui.shell.formatBytes

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
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rows) {
        selectedIds = selectedIds.intersect(rows.map { it.id }.toSet())
    }

    if (rows.isEmpty()) {
        EmptyStatePanel(
            title = "No finished downloads",
            body = "Finished downloads appear here.",
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TriStateCheckbox(
                state = when {
                    selectedIds.isEmpty() -> ToggleableState.Off
                    selectedIds.size == rows.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                },
                onClick = {
                    selectedIds = if (selectedIds.size == rows.size) emptySet() else rows.map { it.id }.toSet()
                },
                modifier = Modifier.testTag("history-select-all"),
            )
            Text("Select all")
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { presenter.retrySelected(selectedIds) },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("history-retry-selected"),
            ) {
                Text("Retry failed")
            }
            OutlinedButton(
                onClick = { pendingRemoveIds = selectedIds },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("history-remove-selected"),
            ) {
                Text("Remove selected")
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).testTag("history-list"),
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
                    onOpenFile = { row.artifacts.firstOrNull { !it.removed }?.let { graph.openFile(it.source) } },
                    onRevealFile = { row.artifacts.firstOrNull { !it.removed }?.let { graph.revealFile(it.source) } },
                    onDeleteFile = { pendingDeleteId = row.id },
                    onRemove = { pendingRemoveIds = setOf(row.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (pendingRemoveIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingRemoveIds = emptySet() },
            title = {
                Text(
                    if (pendingRemoveIds.size == 1) {
                        "Remove this entry from history?"
                    } else {
                        "Remove ${pendingRemoveIds.size} entries from history?"
                    }
                )
            },
            text = { Text("The file stays on disk.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presenter.removeSelected(pendingRemoveIds)
                        selectedIds = selectedIds - pendingRemoveIds
                        pendingRemoveIds = emptySet()
                    },
                    modifier = Modifier.testTag("history-confirm-remove"),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveIds = emptySet() }) {
                    Text("Keep")
                }
            },
        )
    }

    pendingDeleteId?.let { jobId ->
        val row = rows.firstOrNull { it.id == jobId }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this file from the download folder?") },
            text = {
                Text(
                    row?.artifacts
                        ?.joinToString(separator = "\n") { it.fileName }
                        ?: "This removes the downloaded file."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val result = presenter.deleteArtifacts(jobId)
                        statusMessage = when {
                            result.failures.isNotEmpty() ->
                                "Some files could not be deleted: ${result.failures.joinToString()}"
                            result.deletedCount == 0 -> "The file was already gone."
                            else -> "Deleted ${result.deletedCount} file(s)."
                        }
                        pendingDeleteId = null
                    },
                    modifier = Modifier.testTag("history-confirm-delete"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Keep file")
                }
            },
        )
    }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            Text(text = row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = row.stateLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            row.artifacts.forEach { artifact ->
                Text(
                    text = if (artifact.removed) {
                        "${artifact.fileName} (file removed)"
                    } else {
                        listOfNotNull(artifact.fileName, artifact.sizeBytes?.let(::formatBytes))
                            .joinToString(" \u00b7 ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            row.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (row.canRetry) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("history-retry-${row.id}"),
                ) {
                    Text("Retry")
                }
            }
            if (row.errorMessage != null) {
                TextButton(
                    onClick = onCopyError,
                    modifier = Modifier.testTag("history-copy-${row.id}"),
                ) {
                    Text("Copy error")
                }
            }
            if (row.hasArtifact) {
                TextButton(
                    onClick = onOpenFile,
                    modifier = Modifier.testTag("history-open-${row.id}"),
                ) {
                    Text("Open file")
                }
                TextButton(
                    onClick = onRevealFile,
                    modifier = Modifier.testTag("history-reveal-${row.id}"),
                ) {
                    Text("Reveal in folder")
                }
                TextButton(
                    onClick = onDeleteFile,
                    modifier = Modifier.testTag("history-delete-${row.id}"),
                ) {
                    Text("Delete file")
                }
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.testTag("history-remove-${row.id}"),
            ) {
                Text("Remove")
            }
        }
    }
}
