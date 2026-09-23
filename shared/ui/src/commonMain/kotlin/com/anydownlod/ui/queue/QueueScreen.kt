package com.anydownlod.ui.queue

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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.shell.EmptyStatePanel
import com.anydownlod.ui.shell.formatBytes
import com.anydownlod.ui.shell.formatDueTime
import com.anydownlod.ui.shell.formatEta
import com.anydownlod.ui.shell.formatSpeed

/**
 * Downloading list bound to the engine flow: pending, waiting, queued,
 * resolving, downloading, and post-processing rows.
 */
@Composable
fun QueueScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    val jobs by graph.engine.jobs.collectAsState()
    val rows = remember(jobs) { QueuePresenter.rows(jobs) }
    val presenter = remember(graph.engine) { QueuePresenter(graph.engine) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingCancelIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(rows) {
        selectedIds = selectedIds.intersect(rows.map { it.id }.toSet())
    }

    if (rows.isEmpty()) {
        EmptyStatePanel(
            title = "Nothing is downloading",
            body = "Add a URL above to start a download.",
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
                modifier = Modifier.testTag("queue-select-all"),
            )
            Text("Select all")
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { presenter.startSelected(selectedIds) },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("queue-start-selected"),
            ) {
                Text("Start selected")
            }
            OutlinedButton(
                onClick = {
                    if (rows.any { it.id in selectedIds && it.cancelNeedsConfirm }) {
                        pendingCancelIds = selectedIds
                    } else {
                        presenter.cancelSelected(selectedIds)
                        selectedIds = emptySet()
                    }
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("queue-cancel-selected"),
            ) {
                Text("Cancel selected")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).testTag("queue-list"),
        ) {
            items(rows, key = { it.id }) { row ->
                QueueRowItem(
                    row = row,
                    selected = row.id in selectedIds,
                    onSelectedChange = { checked ->
                        selectedIds = if (checked) selectedIds + row.id else selectedIds - row.id
                    },
                    onStart = { presenter.start(row.id) },
                    onCancel = {
                        if (row.cancelNeedsConfirm) {
                            pendingCancelIds = setOf(row.id)
                        } else {
                            presenter.cancel(row.id)
                        }
                    },
                    onOpenSource = { graph.openUrl(row.sourceUrl) },
                )
                HorizontalDivider()
            }
        }
    }

    if (pendingCancelIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingCancelIds = emptySet() },
            title = {
                Text(
                    if (pendingCancelIds.size == 1) {
                        "Cancel this download?"
                    } else {
                        "Cancel selected downloads?"
                    }
                )
            },
            text = { Text("This stops the download. It does not delete a finished file.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presenter.cancelSelected(pendingCancelIds)
                        selectedIds = selectedIds - pendingCancelIds
                        pendingCancelIds = emptySet()
                    },
                    modifier = Modifier.testTag("queue-confirm-cancel"),
                ) {
                    Text("Cancel download")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelIds = emptySet() }) {
                    Text("Keep downloading")
                }
            },
        )
    }
}

@Composable
private fun QueueRowItem(
    row: QueueRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onSelectedChange,
            modifier = Modifier.testTag("queue-select-${row.id}"),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = listOfNotNull(row.sourceHost, row.stateLabel).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            row.phase?.let { phase ->
                Text(text = "Phase: $phase", style = MaterialTheme.typography.bodySmall)
            }

            val progressModifier = Modifier.fillMaxWidth().testTag("queue-progress-${row.id}")
            if (row.indeterminate) {
                LinearProgressIndicator(modifier = progressModifier)
            } else {
                LinearProgressIndicator(
                    progress = { ((row.percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = progressModifier,
                )
            }

            val details = buildList {
                row.downloadedBytes?.let { downloaded ->
                    add(
                        if (row.totalBytes != null) {
                            "${formatBytes(downloaded)} / ${formatBytes(row.totalBytes)}"
                        } else {
                            formatBytes(downloaded)
                        }
                    )
                }
                row.speedBytesPerSecond?.let { add(formatSpeed(it)) }
                row.etaSeconds?.let { add("ETA ${formatEta(it)}") }
                if (!row.indeterminate) add("${(row.percent ?: 0.0).toInt()}%")
            }
            if (details.isNotEmpty()) {
                Text(text = details.joinToString(" \u00b7 "), style = MaterialTheme.typography.bodySmall)
            }
            row.scheduledAtEpochMillis?.let { due ->
                Text(
                    text = "Waiting until the source is available (${formatDueTime(due)}).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (row.canStart) {
                OutlinedButton(
                    onClick = onStart,
                    modifier = Modifier.testTag("queue-start-${row.id}"),
                ) {
                    Text("Start")
                }
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("queue-cancel-${row.id}"),
            ) {
                Text("Cancel")
            }
            TextButton(
                onClick = onOpenSource,
                modifier = Modifier.testTag("queue-open-${row.id}"),
            ) {
                Text("Open source")
            }
        }
    }
}
