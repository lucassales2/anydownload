package com.anydownload.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydownload.core.domain.JobState
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.cancel
import com.anydownload.ui.generated.resources.empty_all_body
import com.anydownload.ui.generated.resources.empty_all_title
import com.anydownload.ui.generated.resources.empty_failed_body
import com.anydownload.ui.generated.resources.empty_failed_title
import com.anydownload.ui.generated.resources.empty_history_body
import com.anydownload.ui.generated.resources.empty_history_title
import com.anydownload.ui.generated.resources.filter_all
import com.anydownload.ui.generated.resources.filter_complete
import com.anydownload.ui.generated.resources.filter_failed
import com.anydownload.ui.generated.resources.job_failed
import com.anydownload.ui.generated.resources.open_file
import com.anydownload.ui.generated.resources.phone_downloads
import com.anydownload.ui.generated.resources.preview_back
import com.anydownload.ui.generated.resources.retry
import com.anydownload.ui.generated.resources.start
import com.anydownload.ui.history.HistoryViewModel
import com.anydownload.ui.home.DownloadConfirmations
import com.anydownload.ui.home.DownloadFilter
import com.anydownload.ui.home.LibraryItem
import com.anydownload.ui.home.LibrarySort
import com.anydownload.ui.home.LibrarySortMenu
import com.anydownload.ui.home.libraryItems
import com.anydownload.ui.queue.QueueRow
import com.anydownload.ui.queue.QueueViewModel
import com.anydownload.ui.shell.EmptyStatePanel
import com.anydownload.ui.shell.formatBytes
import com.anydownload.ui.history.HistoryRow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * All / Complete / Failed, grouped by the minute, hour, or day a download
 * finished. The bottom button returns to the paste field.
 */
@Composable
internal fun PhoneDownloads(
    onBack: () -> Unit,
    onNewDownload: () -> Unit,
    queueViewModel: QueueViewModel = metroViewModel(),
    historyViewModel: HistoryViewModel = metroViewModel(),
) {
    val colors = phoneColors()
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }
    var sort by remember { mutableStateOf(LibrarySort.NEWEST) }
    val queue by queueViewModel.state.collectAsState()
    val history by historyViewModel.state.collectAsState()
    val rows = libraryItems(filter, queue.rows, history.rows, sort)
    val backLabel = stringResource(Res.string.preview_back)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(phoneCanvas(colors))
            .statusBarsPadding()
            .testTag("phone-downloads"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhoneIconWell(
                onClick = onBack,
                tag = "phone-back",
                background = colors.well,
            ) {
                Glyph(Modifier.size(18.dp).semantics { contentDescription = backLabel }) {
                    drawChevronLeft(colors.ink)
                }
            }
            Text(
                text = stringResource(Res.string.phone_downloads),
                modifier = Modifier.weight(1f),
                color = colors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            PhoneIconWell(onClick = null, background = colors.well) {
                Glyph(Modifier.size(18.dp)) { drawBell(colors.ink) }
            }
        }
        FilterBar(
            selected = filter,
            colors = colors,
            onSelect = { filter = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            LibrarySortMenu(sort = sort, onSort = { sort = it })
        }
        if (rows.isEmpty()) {
            val (title, body) = emptyCopy(filter)
            EmptyStatePanel(title = title, body = body, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("download-library"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.key }) { item ->
                    when (item) {
                        is LibraryItem.Heading -> Text(
                            text = item.label,
                            color = colors.muted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                        is LibraryItem.Active -> ActiveDownloadCard(
                            row = item.row,
                            colors = colors,
                            onStart = { queueViewModel.startSelected(setOf(item.row.id)) },
                            onCancel = { queueViewModel.requestCancelSelected(setOf(item.row.id)) },
                        )
                        is LibraryItem.Done -> FinishedDownloadCard(
                            row = item.row,
                            colors = colors,
                            onRetry = { historyViewModel.retry(item.row.id) },
                            onOpenFile = {
                                item.row.artifacts.firstOrNull { !it.removed }?.let {
                                    historyViewModel.openArtifact(it.source)
                                }
                            },
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            PhoneDownloadButton(
                onClick = onNewDownload,
                tag = "phone-new-download",
                colors = colors,
            )
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
private fun FilterBar(
    selected: DownloadFilter,
    colors: PhoneColors,
    onSelect: (DownloadFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.card)
            .padding(4.dp),
    ) {
        DownloadFilter.entries.forEach { filter ->
            val picked = filter == selected
            val label = when (filter) {
                DownloadFilter.ALL -> stringResource(Res.string.filter_all)
                DownloadFilter.COMPLETE -> stringResource(Res.string.filter_complete)
                DownloadFilter.FAILED -> stringResource(Res.string.filter_failed)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (picked) colors.accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(filter) }
                    .testTag("download-filter-${filter.name.lowercase()}")
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (picked) colors.onAccent else colors.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun emptyCopy(filter: DownloadFilter): Pair<String, String> = when (filter) {
    DownloadFilter.ALL -> stringResource(Res.string.empty_all_title) to stringResource(Res.string.empty_all_body)
    DownloadFilter.COMPLETE -> stringResource(Res.string.empty_history_title) to stringResource(Res.string.empty_history_body)
    DownloadFilter.FAILED -> stringResource(Res.string.empty_failed_title) to stringResource(Res.string.empty_failed_body)
}

@Composable
private fun ActiveDownloadCard(
    row: QueueRow,
    colors: PhoneColors,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val fraction = row.percent?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
    val sizeLabel = when {
        row.downloadedBytes != null && row.totalBytes != null ->
            "${formatBytes(row.downloadedBytes)} / ${formatBytes(row.totalBytes)}"
        row.downloadedBytes != null -> formatBytes(row.downloadedBytes)
        else -> null
    }
    val actionLabel = stringResource(if (row.canStart) Res.string.start else Res.string.cancel)
    DownloadRow(
        colors = colors,
        title = row.title,
        subtitle = row.sourceHost,
        thumbTitle = row.title,
        meta = sizeLabel,
        metaColor = colors.muted,
        accent = row.percent?.let { "${it.toInt()}%" },
        accentColor = colors.ink,
        bar = fraction,
        barColor = colors.accent,
        onClick = if (row.canStart) onStart else onCancel,
        trailing = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = actionLabel },
                contentAlignment = Alignment.Center,
            ) {
                if (row.indeterminate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = colors.accent,
                        trackColor = colors.track,
                        strokeWidth = 3.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { fraction ?: 0f },
                        modifier = Modifier.size(28.dp),
                        color = colors.accent,
                        trackColor = colors.track,
                        strokeWidth = 3.dp,
                    )
                }
            }
        },
    )
}

@Composable
private fun FinishedDownloadCard(
    row: HistoryRow,
    colors: PhoneColors,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val failed = row.state == JobState.FAILED
    val size = row.artifacts.firstOrNull { !it.removed }?.sizeBytes
    val retryLabel = stringResource(Res.string.retry)
    val openLabel = stringResource(Res.string.open_file)
    DownloadRow(
        colors = colors,
        title = row.title,
        subtitle = row.errorMessage ?: row.sourceHost,
        thumbTitle = row.title,
        meta = size?.let(::formatBytes),
        metaColor = colors.muted,
        accent = if (failed) stringResource(Res.string.job_failed) else null,
        accentColor = colors.failed,
        bar = if (failed) 1f else null,
        barColor = colors.failed,
        onClick = if (failed) onRetry else onOpenFile,
        trailing = {
            if (failed) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = retryLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Modifier.size(18.dp)) { drawRefresh(colors.muted) }
                }
            } else if (row.hasArtifact) {
                Box(Modifier.size(8.dp).semantics { contentDescription = openLabel })
            }
        },
    )
}

@Composable
private fun DownloadRow(
    colors: PhoneColors,
    title: String,
    subtitle: String?,
    thumbTitle: String,
    meta: String?,
    metaColor: Color,
    accent: String?,
    accentColor: Color,
    bar: Float?,
    barColor: Color,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(86.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(thumbPalette(thumbTitle))),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = colors.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { line ->
                Text(
                    text = line,
                    color = colors.muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (bar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(bar.coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(barColor),
                    )
                }
            }
            if (meta != null || accent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta != null) {
                        Text(text = meta, color = metaColor, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    if (accent != null) {
                        Text(text = accent, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        trailing()
    }
}

private fun thumbPalette(title: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFFD7E7FF), Color(0xFFB7D0F0)),
        listOf(Color(0xFFE6E8EE), Color(0xFFD5D8E0)),
        listOf(Color(0xFFE8EEF6), Color(0xFFD0D7E2)),
    )
    return palettes[title.hashCode().mod(palettes.size)]
}
