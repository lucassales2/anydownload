package com.anydownlod.ui.home

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.cancel
import com.anydownlod.ui.generated.resources.cancel_body
import com.anydownlod.ui.generated.resources.cancel_download
import com.anydownlod.ui.generated.resources.cancel_many_title
import com.anydownlod.ui.generated.resources.cancel_one_title
import com.anydownlod.ui.generated.resources.delete
import com.anydownlod.ui.generated.resources.delete_file
import com.anydownlod.ui.generated.resources.delete_file_fallback
import com.anydownlod.ui.generated.resources.delete_file_title
import com.anydownlod.ui.generated.resources.empty_all_body
import com.anydownlod.ui.generated.resources.empty_all_title
import com.anydownlod.ui.generated.resources.empty_failed_body
import com.anydownlod.ui.generated.resources.empty_failed_title
import com.anydownlod.ui.generated.resources.empty_history_body
import com.anydownlod.ui.generated.resources.empty_history_title
import com.anydownlod.ui.generated.resources.eta
import com.anydownlod.ui.generated.resources.file_stays
import com.anydownlod.ui.generated.resources.remove_also_delete
import com.anydownlod.ui.generated.resources.filter_all
import com.anydownlod.ui.generated.resources.filter_complete
import com.anydownlod.ui.generated.resources.filter_failed
import com.anydownlod.ui.generated.resources.keep
import com.anydownlod.ui.generated.resources.keep_downloading
import com.anydownlod.ui.generated.resources.keep_file
import com.anydownlod.ui.generated.resources.open_file
import com.anydownlod.ui.generated.resources.open_source
import com.anydownlod.ui.generated.resources.remove
import com.anydownlod.ui.generated.resources.remove_many_title
import com.anydownlod.ui.generated.resources.remove_one_title
import com.anydownlod.ui.generated.resources.retry
import com.anydownlod.ui.generated.resources.sort_newest
import com.anydownlod.ui.generated.resources.sort_oldest
import com.anydownlod.ui.generated.resources.sort_title
import com.anydownlod.ui.generated.resources.start
import com.anydownlod.ui.history.HistoryRow
import com.anydownlod.ui.history.HistoryUiState
import com.anydownlod.ui.history.HistoryViewModel
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.queue.QueueRow
import com.anydownlod.ui.queue.QueueUiState
import com.anydownlod.ui.queue.QueueViewModel
import com.anydownlod.ui.shell.EmptyStatePanel
import com.anydownlod.ui.shell.formatBytes
import com.anydownlod.ui.shell.formatEta
import com.anydownlod.ui.shell.formatSpeed
import com.anydownlod.ui.theme.DestructiveOutlinedButton
import com.anydownlod.ui.theme.DestructiveTextButton
import com.anydownlod.ui.theme.ObjectCard
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.ProgressMeter
import com.anydownlod.ui.theme.SegmentedChoice
import com.anydownlod.ui.theme.StatusBadge
import com.anydownlod.ui.theme.colors
import com.anydownlod.ui.theme.statusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

internal enum class DownloadFilter {
    ALL,
    COMPLETE,
    FAILED,
}

/**
 * All / Complete / Failed cards for the home list. Active rows show progress,
 * speed, and time left. Finished rows group by the minute, hour, or day they
 * ended, and a sort switches newest, oldest, or title.
 */
@Composable
fun DownloadLibrary(
    modifier: Modifier = Modifier,
    queueViewModel: QueueViewModel = metroViewModel(),
    historyViewModel: HistoryViewModel = metroViewModel(),
) {
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }
    var sort by remember { mutableStateOf(LibrarySort.NEWEST) }
    val queue by queueViewModel.state.collectAsState()
    val history by historyViewModel.state.collectAsState()
    val libraryRows = libraryItems(filter, queue.rows, history.rows, sort)

    Column(modifier.fillMaxSize().padding(horizontal = PageInset, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChoice(
                options = DownloadFilter.entries,
                selected = filter,
                optionLabel = { it.label() },
                onSelect = { filter = it },
                optionTag = { "download-filter-${it.name.lowercase()}" },
                modifier = Modifier.weight(1f),
            )
            LibrarySortMenu(sort = sort, onSort = { sort = it })
        }
        if (libraryRows.isEmpty()) {
            val (title, body) = emptyCopy(filter)
            EmptyStatePanel(
                title = title,
                body = body,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("download-library"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(libraryRows, key = { it.key }) { item ->
                    when (item) {
                        is LibraryItem.Heading -> Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        is LibraryItem.Active -> ActiveCard(
                            row = item.row,
                            onStart = { queueViewModel.startSelected(setOf(item.row.id)) },
                            onCancel = { queueViewModel.requestCancelSelected(setOf(item.row.id)) },
                            onOpenSource = { queueViewModel.openSource(item.row.sourceUrl) },
                        )
                        is LibraryItem.Done -> DoneCard(
                            row = item.row,
                            onRetry = { historyViewModel.retry(item.row.id) },
                            onOpenFile = {
                                item.row.artifacts.firstOrNull { !it.removed }?.let {
                                    historyViewModel.openArtifact(it.source)
                                }
                            },
                            onDeleteFile = { historyViewModel.requestDelete(item.row.id) },
                            onRemove = { historyViewModel.requestRemove(setOf(item.row.id)) },
                        )
                    }
                }
            }
        }
    }

    DownloadConfirmations(
        queue = queue,
        history = history,
        onDismissCancel = queueViewModel::dismissCancel,
        onConfirmCancel = queueViewModel::confirmCancel,
        onDismissRemove = historyViewModel::dismissRemove,
        onConfirmRemove = { historyViewModel.confirmRemove(deleteStoredFile = false) },
        onConfirmRemoveAndDelete = { historyViewModel.confirmRemove(deleteStoredFile = true) },
        onDismissDelete = historyViewModel::dismissDelete,
        onConfirmDelete = historyViewModel::confirmDelete,
    )
}

@Composable
internal fun LibrarySortMenu(
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("download-sort")) {
            Text(sort.label())
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.testTag("download-sort-menu"),
        ) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                    leadingIcon = if (option == sort) {
                        { Text("✓") }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("download-sort-${option.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun LibrarySort.label(): String = when (this) {
    LibrarySort.NEWEST -> stringResource(Res.string.sort_newest)
    LibrarySort.OLDEST -> stringResource(Res.string.sort_oldest)
    LibrarySort.TITLE -> stringResource(Res.string.sort_title)
}

@Composable
private fun DownloadFilter.label(): String = when (this) {
    DownloadFilter.ALL -> stringResource(Res.string.filter_all)
    DownloadFilter.COMPLETE -> stringResource(Res.string.filter_complete)
    DownloadFilter.FAILED -> stringResource(Res.string.filter_failed)
}

@Composable
private fun emptyCopy(filter: DownloadFilter): Pair<String, String> = when (filter) {
    DownloadFilter.ALL -> stringResource(Res.string.empty_all_title) to stringResource(Res.string.empty_all_body)
    DownloadFilter.COMPLETE -> stringResource(Res.string.empty_history_title) to stringResource(Res.string.empty_history_body)
    DownloadFilter.FAILED -> stringResource(Res.string.empty_failed_title) to stringResource(Res.string.empty_failed_body)
}

@Composable
private fun ActiveCard(
    row: QueueRow,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val tone = row.state.statusTone()
    ObjectCard(accent = tone.colors().foreground) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        row.sourceHost?.let { host ->
            Text(
                text = host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressMeter(
            progress = if (row.indeterminate) null else ((row.percent ?: 0.0) / 100.0).toFloat(),
            modifier = Modifier.testTag("queue-progress-${row.id}"),
        )
        val etaLabel = row.etaSeconds?.let { stringResource(Res.string.eta, formatEta(it)) }
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
            etaLabel?.let { add(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(label = row.stateLabel.resolve(), tone = tone, live = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.canStart) {
                FilledTonalButton(onClick = onStart, modifier = Modifier.testTag("queue-start-${row.id}")) {
                    Text(stringResource(Res.string.start))
                }
            }
            DestructiveOutlinedButton(
                text = stringResource(Res.string.cancel),
                onClick = onCancel,
                modifier = Modifier.testTag("queue-cancel-${row.id}"),
            )
            TextButton(onClick = onOpenSource, modifier = Modifier.testTag("queue-open-${row.id}")) {
                Text(stringResource(Res.string.open_source))
            }
        }
    }
}

@Composable
private fun DoneCard(
    row: HistoryRow,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemove: () -> Unit,
) {
    val tone = row.state.statusTone()
    val size = row.artifacts.firstOrNull { !it.removed }?.sizeBytes
    ObjectCard(accent = tone.colors().foreground) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(label = row.stateLabel.resolve(), tone = tone)
        }
        val host = row.sourceHost
        val sizeLabel = size?.let(::formatBytes)
        val meta = listOfNotNull(host, sizeLabel).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.canRetry) {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("history-retry-${row.id}")) {
                    Text(stringResource(Res.string.retry))
                }
            }
            if (row.hasArtifact) {
                TextButton(onClick = onOpenFile, modifier = Modifier.testTag("history-open-${row.id}")) {
                    Text(stringResource(Res.string.open_file))
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

internal enum class LibrarySort {
    NEWEST,
    OLDEST,
    TITLE,
}

internal sealed interface LibraryItem {
    val key: String

    data class Heading(val label: String, val bucket: String) : LibraryItem {
        override val key: String = "heading-$bucket"
    }

    data class Active(val row: QueueRow) : LibraryItem {
        override val key: String = "active-${row.id}"
    }

    data class Done(val row: HistoryRow) : LibraryItem {
        override val key: String = "done-${row.id}"
    }
}

internal enum class TimeGrain {
    MINUTE,
    HOUR,
    DAY,
}

internal data class TimeGroup(
    val grain: TimeGrain,
    val label: String,
    val key: String,
)

/**
 * Finished rows for [filter], ordered by [sort]. Date sorts insert a heading
 * each time the time bucket changes: the minute for the last hour, the hour
 * for the last day, and the calendar day after that. [offsetMillis] shifts
 * those buckets onto local civil time.
 */
internal fun libraryItems(
    filter: DownloadFilter,
    active: List<QueueRow>,
    done: List<HistoryRow>,
    sort: LibrarySort = LibrarySort.NEWEST,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    offsetMillis: Int = localUtcOffsetMillis(nowEpochMillis),
): List<LibraryItem> {
    val finished = when (filter) {
        DownloadFilter.ALL -> done
        DownloadFilter.COMPLETE -> done.filter { it.state == JobState.COMPLETED }
        DownloadFilter.FAILED -> done.filter { it.state == JobState.FAILED }
    }
    val ordered = when (sort) {
        LibrarySort.NEWEST -> finished.sortedByDescending { it.finishedAtEpochMillis ?: Long.MIN_VALUE }
        LibrarySort.OLDEST -> finished.sortedBy { it.finishedAtEpochMillis ?: Long.MAX_VALUE }
        LibrarySort.TITLE -> finished.sortedWith(titleThenId())
    }
    val items = mutableListOf<LibraryItem>()
    if (filter == DownloadFilter.ALL) {
        val activeRows = if (sort == LibrarySort.TITLE) {
            active.sortedWith(
                compareBy<QueueRow, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id },
            )
        } else {
            active
        }
        activeRows.forEach { items += LibraryItem.Active(it) }
    }
    if (sort == LibrarySort.TITLE) {
        ordered.forEach { items += LibraryItem.Done(it) }
        return items
    }
    var lastBucket: String? = null
    ordered.forEach { row ->
        val finishedAt = row.finishedAtEpochMillis
        if (finishedAt != null) {
            val group = timeGroup(finishedAt, nowEpochMillis, offsetMillis)
            if (group.key != lastBucket) {
                items += LibraryItem.Heading(group.label, group.key)
                lastBucket = group.key
            }
        }
        items += LibraryItem.Done(row)
    }
    return items
}

private fun titleThenId(): Comparator<HistoryRow> =
    compareBy<HistoryRow, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id }

/** Age picks the bucket. Clock labels use [offsetMillis] so they match local time. */
internal fun timeGroup(epochMillis: Long, nowEpochMillis: Long, offsetMillis: Int = 0): TimeGroup {
    val age = nowEpochMillis - epochMillis
    val grain = when {
        age < HOUR_MS -> TimeGrain.MINUTE
        age < DAY_MS -> TimeGrain.HOUR
        else -> TimeGrain.DAY
    }
    val unit = when (grain) {
        TimeGrain.MINUTE -> MINUTE_MS
        TimeGrain.HOUR -> HOUR_MS
        TimeGrain.DAY -> DAY_MS
    }
    val zonedStart = floorEpoch(epochMillis + offsetMillis, unit)
    val zonedNow = nowEpochMillis + offsetMillis
    val label = when (grain) {
        TimeGrain.DAY -> utcDateLabel(zonedStart)
        else -> {
            val clock = clockLabel(zonedStart)
            if (dayIndex(zonedStart) == dayIndex(zonedNow)) clock else "${shortDate(zonedStart)}, $clock"
        }
    }
    return TimeGroup(grain, label, "${grain.name}-$zonedStart")
}

/** Calendar label for a UTC instant, or for an instant already shifted into local time. */
internal fun utcDateLabel(epochMillis: Long): String {
    val (year, month, day) = civilFromUnixDays(dayIndex(epochMillis))
    return "${monthNames[month - 1]} $day, $year"
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun shortDate(epochMillis: Long): String {
    val (_, month, day) = civilFromUnixDays(dayIndex(epochMillis))
    return "${monthNames[month - 1]} $day"
}

private fun clockLabel(epochMillis: Long): String {
    val secondOfDay = floorMod(epochMillis, DAY_MS) / 1000
    val hour = secondOfDay / 3600
    val minute = (secondOfDay % 3600) / 60
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun dayIndex(epochMillis: Long): Long = floorEpoch(epochMillis, DAY_MS) / DAY_MS

private fun floorEpoch(epochMillis: Long, unit: Long): Long = epochMillis - floorMod(epochMillis, unit)

private fun floorMod(value: Long, unit: Long): Long {
    val mod = value % unit
    return if (mod < 0) mod + unit else mod
}

@Composable
internal fun DownloadConfirmations(
    queue: QueueUiState,
    history: HistoryUiState,
    onDismissCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmRemoveAndDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    if (queue.pendingCancelIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissCancel,
            title = {
                Text(
                    if (queue.pendingCancelIds.size == 1) {
                        stringResource(Res.string.cancel_one_title)
                    } else {
                        stringResource(Res.string.cancel_many_title)
                    },
                )
            },
            text = { Text(stringResource(Res.string.cancel_body)) },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(Res.string.cancel_download),
                    onClick = onConfirmCancel,
                    modifier = Modifier.testTag("queue-confirm-cancel"),
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissCancel) {
                    Text(stringResource(Res.string.keep_downloading))
                }
            },
        )
    }

    if (history.pendingRemoveIds.isNotEmpty()) {
        val asksAboutStorage = history.rows.any { it.id in history.pendingRemoveIds && it.hasArtifact }
        AlertDialog(
            onDismissRequest = onDismissRemove,
            title = {
                Text(
                    if (history.pendingRemoveIds.size == 1) {
                        stringResource(Res.string.remove_one_title)
                    } else {
                        stringResource(Res.string.remove_many_title, history.pendingRemoveIds.size)
                    },
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
                            onClick = onConfirmRemoveAndDelete,
                            modifier = Modifier.testTag("history-confirm-remove-delete"),
                        )
                        TextButton(
                            onClick = onConfirmRemove,
                            modifier = Modifier.testTag("history-confirm-remove"),
                        ) {
                            Text(stringResource(Res.string.keep_file))
                        }
                    }
                } else {
                    DestructiveTextButton(
                        text = stringResource(Res.string.remove),
                        onClick = onConfirmRemove,
                        modifier = Modifier.testTag("history-confirm-remove"),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemove) {
                    Text(stringResource(if (asksAboutStorage) Res.string.cancel else Res.string.keep))
                }
            },
        )
    }

    history.pendingDeleteId?.let { jobId ->
        val row = history.rows.firstOrNull { it.id == jobId }
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(Res.string.delete_file_title)) },
            text = {
                Text(
                    row?.artifacts?.joinToString("\n") { it.fileName }
                        ?: stringResource(Res.string.delete_file_fallback),
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(Res.string.delete),
                    onClick = onConfirmDelete,
                    modifier = Modifier.testTag("history-confirm-delete"),
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(Res.string.keep_file))
                }
            },
        )
    }
}

private fun civilFromUnixDays(days: Long): Triple<Int, Int, Int> {
    var z = days + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = (z - era * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era.toInt() * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + if (mp < 10) 3 else -9
    return Triple(y + if (m <= 2) 1 else 0, m, d)
}
