package com.anydownlod.ui.subscriptions

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.shell.EmptyStatePanel
import com.anydownlod.ui.shell.formatUtcMinute
import com.anydownlod.ui.theme.ActionRow
import com.anydownlod.ui.theme.AppTextField
import com.anydownlod.ui.theme.DestructiveTextButton
import com.anydownlod.ui.theme.KpiTile
import com.anydownlod.ui.theme.ObjectCard
import com.anydownlod.ui.theme.PageHeading
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.SelectionBar
import com.anydownlod.ui.theme.StatusBadge
import com.anydownlod.ui.theme.StatusTone
import com.anydownlod.ui.theme.colors

/**
 * Subscriptions bound to [com.anydownlod.core.SubscriptionRepository].
 * Checks only record timestamps until a real scan is wired.
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

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = PageInset)) {
            PageHeading(
                title = "Followed sources",
                subtitle = "Channels and playlists on a schedule.",
            )
            if (rows.isEmpty()) {
                EmptyStatePanel(
                    title = "No subscriptions",
                    body = "Paste a channel or playlist URL in the add form above and choose Subscribe. Checks run while the app is open.",
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        value = rows.count { !it.paused }.toString(),
                        label = "Running",
                        tone = StatusTone.Positive,
                    )
                    KpiTile(
                        value = rows.count { it.paused }.toString(),
                        label = "Paused sources",
                        tone = StatusTone.Neutral,
                    )
                }
                SelectionBar(
                    selection = selectionState(selectedIds.size, rows.size),
                    onToggleAll = {
                        selectedIds = if (selectedIds.size == rows.size) emptySet() else rows.map { it.id }.toSet()
                    },
                    selectAllTag = "subscriptions-select-all",
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                ) {
                    FilledTonalButton(
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
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("subscriptions-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
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
                    }
                }
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
                DestructiveTextButton(
                    text = "Delete",
                    onClick = {
                        presenter.delete(id)
                        deletingId = null
                    },
                    modifier = Modifier.testTag("subscriptions-confirm-delete"),
                )
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text("Keep")
                }
            },
        )
    }
}

private fun selectionState(selected: Int, total: Int): ToggleableState = when {
    selected == 0 -> ToggleableState.Off
    selected == total -> ToggleableState.On
    else -> ToggleableState.Indeterminate
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
    val tone = when {
        row.paused -> StatusTone.Neutral
        row.lastError != null -> StatusTone.Negative
        else -> StatusTone.Positive
    }
    val badge = when {
        row.paused -> "Paused"
        row.lastError != null -> "Check failed"
        else -> "Active"
    }
    ObjectCard(accent = tone.colors().foreground) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.testTag("subscriptions-select-${row.id}"),
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
                        text = row.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(label = badge, tone = tone)
                }
                Text(
                    text = row.sourceHost ?: row.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Every ${row.intervalMinutes} min  ·  Last check ${
                        row.lastCheckedAtEpochMillis?.let(::formatUtcMinute) ?: "Never"
                    }  ·  Next ${row.nextCheckAtEpochMillis?.let(::formatUtcMinute) ?: "\u2014"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Title filter: ${if (row.titleFilter.isEmpty()) "All titles" else row.titleFilter}" +
                        "  ·  " +
                        if (row.skipMembersOnly) "Skips members-only" else "Includes members-only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!row.paused && row.lastError != null) {
                    Text(
                        text = row.lastError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ActionRow {
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
                    DestructiveTextButton(
                        text = "Delete",
                        onClick = onDelete,
                        modifier = Modifier.testTag("subscriptions-delete-${row.id}"),
                    )
                }
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
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("subscriptions-edit-name"),
                    label = { Text("Name") },
                    singleLine = true,
                )
                AppTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth().testTag("subscriptions-edit-interval"),
                    label = { Text("Check interval (minutes)") },
                    singleLine = true,
                )
                AppTextField(
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
