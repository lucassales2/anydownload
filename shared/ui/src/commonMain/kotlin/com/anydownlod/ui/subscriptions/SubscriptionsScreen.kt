package com.anydownlod.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.shell.EmptyStatePanel
import com.anydownlod.ui.shell.formatUtcMinute

/**
 * Subscriptions table bound to [com.anydownlod.core.SubscriptionRepository].
 * Checks only record timestamps until T-035 wires real scans.
 */
@Composable
fun SubscriptionsScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    val subscriptions by graph.subscriptions.subscriptions.collectAsState()
    val rows = remember(subscriptions) { SubscriptionsPresenter.rows(subscriptions) }
    val presenter = remember(graph.subscriptions) { SubscriptionsPresenter(graph.subscriptions) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rows) {
        selectedIds = selectedIds.intersect(rows.map { it.id }.toSet())
    }

    if (rows.isEmpty()) {
        EmptyStatePanel(
            title = "No subscriptions",
            body = "Paste a channel or playlist URL in the add form above and choose Subscribe. Checks run while the app is open.",
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
                modifier = Modifier.testTag("subscriptions-select-all"),
            )
            Text("Select all")
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { presenter.checkAll() },
                modifier = Modifier.testTag("subscriptions-check-all"),
            ) {
                Text("Check all")
            }
            OutlinedButton(
                onClick = { presenter.checkSelected(selectedIds) },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.testTag("subscriptions-check-selected"),
            ) {
                Text("Check selected")
            }
        }

        SubscriptionHeader()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("subscriptions-list"),
        ) {
            items(rows, key = { it.id }) { row ->
                SubscriptionRowItem(
                    row = row,
                    selected = row.id in selectedIds,
                    onSelectedChange = { checked ->
                        selectedIds = if (checked) selectedIds + row.id else selectedIds - row.id
                    },
                    onCheck = { presenter.checkNow(row.id) },
                    onTogglePause = {
                        if (row.paused) presenter.resume(row.id) else presenter.pause(row.id)
                    },
                    onEdit = { editingId = row.id },
                    onDelete = { deletingId = row.id },
                )
                HorizontalDivider()
            }
        }
    }

    editingId?.let { id ->
        rows.firstOrNull { it.id == id }?.let { row ->
            EditSubscriptionDialog(
                row = row,
                onDismiss = { editingId = null },
                onSave = { name, interval, filter, members ->
                    val message = presenter.update(id, name, interval, filter, members)
                    if (message == null) editingId = null
                    message
                },
            )
        }
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete this subscription?") },
            text = { Text("Downloads already in the queue or history stay. Future checks stop.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presenter.delete(id)
                        deletingId = null
                    },
                    modifier = Modifier.testTag("subscriptions-confirm-delete"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text("Keep")
                }
            },
        )
    }
}

@Composable
private fun SubscriptionHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(48.dp))
        Text("Name", Modifier.weight(1.6f), style = MaterialTheme.typography.labelMedium)
        Text("Interval", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
        Text("Title filter", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Members", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
        Text("Last check", Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium)
        Text("Next check", Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium)
        Text("Status", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium)
        Text("Actions", Modifier.width(300.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SubscriptionRowItem(
    row: SubscriptionRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onTogglePause: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onSelectedChange,
            modifier = Modifier.testTag("subscriptions-select-${row.id}"),
        )
        Column(
            modifier = Modifier.weight(1.6f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.sourceHost ?: row.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${row.intervalMinutes} min",
            modifier = Modifier.width(64.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (row.titleFilter.isEmpty()) "All titles" else row.titleFilter,
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (row.skipMembersOnly) "Yes" else "No",
            modifier = Modifier.width(64.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = row.lastCheckedAtEpochMillis?.let(::formatUtcMinute) ?: "Never",
            modifier = Modifier.width(120.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = row.nextCheckAtEpochMillis?.let(::formatUtcMinute) ?: "\u2014",
            modifier = Modifier.width(120.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Column(modifier = Modifier.weight(0.8f).padding(top = 8.dp)) {
            when {
                row.paused -> Text("Paused", style = MaterialTheme.typography.bodySmall)
                row.lastError != null -> Text(
                    text = row.lastError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                else -> Text("Active", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            modifier = Modifier.width(300.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onCheck,
                modifier = Modifier.testTag("subscriptions-check-${row.id}"),
            ) {
                Text("Check")
            }
            TextButton(
                onClick = onTogglePause,
                modifier = Modifier.testTag("subscriptions-pause-${row.id}"),
            ) {
                Text(if (row.paused) "Resume" else "Pause")
            }
            TextButton(
                onClick = onEdit,
                modifier = Modifier.testTag("subscriptions-edit-${row.id}"),
            ) {
                Text("Edit")
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("subscriptions-delete-${row.id}"),
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun EditSubscriptionDialog(
    row: SubscriptionRow,
    onDismiss: () -> Unit,
    onSave: (name: String, intervalText: String, titleFilter: String, skipMembersOnly: Boolean) -> String?,
) {
    var name by remember(row.id) { mutableStateOf(row.name) }
    var intervalText by remember(row.id) { mutableStateOf(row.intervalMinutes.toString()) }
    var titleFilter by remember(row.id) { mutableStateOf(row.titleFilter) }
    var skipMembersOnly by remember(row.id) { mutableStateOf(row.skipMembersOnly) }
    var error by remember(row.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("subscriptions-edit-name"),
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("subscriptions-edit-interval"),
                    label = { Text("Check interval (minutes)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = titleFilter,
                    onValueChange = { titleFilter = it },
                    modifier = Modifier.fillMaxWidth().testTag("subscriptions-edit-filter"),
                    label = { Text("Title filter") },
                    supportingText = { Text("Regular expression; empty means every title.") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier
                        .toggleable(
                            value = skipMembersOnly,
                            role = Role.Checkbox,
                            onValueChange = { skipMembersOnly = it },
                        )
                        .testTag("subscriptions-edit-members"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = skipMembersOnly, onCheckedChange = null)
                    Text("Skip members-only items", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "Quality and format stay as they were when you subscribed. " +
                        "Delete and subscribe again to change them.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val message = onSave(name, intervalText, titleFilter, skipMembersOnly)
                    if (message != null) error = message
                },
                modifier = Modifier.testTag("subscriptions-edit-save"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("subscriptions-edit-cancel"),
            ) {
                Text("Cancel")
            }
        },
    )
}
