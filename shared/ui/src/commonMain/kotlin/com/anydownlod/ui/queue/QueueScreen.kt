package com.anydownlod.ui.queue

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.shell.EmptyStatePanel
import com.anydownlod.ui.shell.formatBytes
import com.anydownlod.ui.shell.formatDueTime
import com.anydownlod.ui.shell.formatEta
import com.anydownlod.ui.shell.formatSpeed
import com.anydownlod.ui.theme.ActionRow
import com.anydownlod.ui.theme.DestructiveOutlinedButton
import com.anydownlod.ui.theme.DestructiveTextButton
import com.anydownlod.ui.theme.KpiTile
import com.anydownlod.ui.theme.ObjectCard
import com.anydownlod.ui.theme.PageHeading
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.ProgressMeter
import com.anydownlod.ui.theme.SelectionBar
import com.anydownlod.ui.theme.StatusBadge
import com.anydownlod.ui.theme.StatusTone
import com.anydownlod.ui.theme.colors
import com.anydownlod.ui.theme.statusTone

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

    val working = rows.count { it.state.isActive }
    val notStarted = rows.size - working

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = PageInset),
        ) {
            PageHeading(
                title = "In progress",
                subtitle = "Active and waiting downloads.",
            )
            if (rows.isEmpty()) {
                EmptyStatePanel(
                    title = "Nothing is downloading",
                    body = "Add a URL above to start a download.",
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        value = working.toString(),
                        label = "Working",
                        tone = StatusTone.Information,
                    )
                    KpiTile(
                        value = notStarted.toString(),
                        label = "Not started",
                        tone = StatusTone.Critical,
                    )
                }
                SelectionBar(
                    selection = selectionState(selectedIds.size, rows.size),
                    onToggleAll = {
                        selectedIds = if (selectedIds.size == rows.size) emptySet() else rows.map { it.id }.toSet()
                    },
                    selectAllTag = "queue-select-all",
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { presenter.startSelected(selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("queue-start-selected"),
                    ) {
                        Text("Start selected")
                    }
                    DestructiveOutlinedButton(
                        text = "Cancel selected",
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
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("queue-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
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
                    }
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
                    DestructiveTextButton(
                        text = "Cancel download",
                        onClick = {
                            presenter.cancelSelected(pendingCancelIds)
                            selectedIds = selectedIds - pendingCancelIds
                            pendingCancelIds = emptySet()
                        },
                        modifier = Modifier.testTag("queue-confirm-cancel"),
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingCancelIds = emptySet() }) {
                        Text("Keep downloading")
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
private fun QueueRowItem(
    row: QueueRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenSource: () -> Unit,
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
                modifier = Modifier.testTag("queue-select-${row.id}"),
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
                    StatusBadge(label = row.stateLabel, tone = tone, live = true)
                }
                row.sourceHost?.let { host ->
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                row.phase?.let { phase ->
                    Text(text = "Phase: $phase", style = MaterialTheme.typography.bodySmall)
                }
                ProgressMeter(
                    progress = if (row.indeterminate) {
                        null
                    } else {
                        ((row.percent ?: 0.0) / 100.0).toFloat()
                    },
                    modifier = Modifier.testTag("queue-progress-${row.id}"),
                )
                val details = buildList {
                    row.downloadedBytes?.let { downloaded ->
                        add(
                            if (row.totalBytes != null) {
                                "${formatBytes(downloaded)} / ${formatBytes(row.totalBytes)}"
                            } else {
                                formatBytes(downloaded)
                            },
                        )
                    }
                    row.speedBytesPerSecond?.let { add(formatSpeed(it)) }
                    row.etaSeconds?.let { add("ETA ${formatEta(it)}") }
                    if (!row.indeterminate) add("${(row.percent ?: 0.0).toInt()}%")
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" \u00b7 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                row.scheduledAtEpochMillis?.let { due ->
                    Text(
                        text = "Waiting until the source is available (${formatDueTime(due)}).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ActionRow {
                    if (row.canStart) {
                        FilledTonalButton(
                            onClick = onStart,
                            modifier = Modifier.testTag("queue-start-${row.id}"),
                        ) {
                            Text("Start")
                        }
                    }
                    DestructiveOutlinedButton(
                        text = "Cancel",
                        onClick = onCancel,
                        modifier = Modifier.testTag("queue-cancel-${row.id}"),
                    )
                    TextButton(
                        onClick = onOpenSource,
                        modifier = Modifier.testTag("queue-open-${row.id}"),
                    ) {
                        Text("Open source")
                    }
                }
            }
        }
    }
}
