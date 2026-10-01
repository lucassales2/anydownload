package com.anydownload.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anydownload.core.domain.JobState
import com.anydownload.ui.add.AddFormViewModel
import com.anydownload.ui.history.HistoryViewModel
import com.anydownload.ui.preview.PreviewScreen
import com.anydownload.ui.theme.MessageStrip
import com.anydownload.ui.theme.StatusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Paste and preview on one side, the filtered download list on the other.
 * Narrow windows stack the same pieces.
 */
@Composable
fun AdaptiveHome(
    previewUrl: String?,
    onPreviewUrl: (String) -> Unit,
    onClearPreview: () -> Unit,
    startupWarning: String?,
    modifier: Modifier = Modifier,
    historyViewModel: HistoryViewModel = metroViewModel(),
) {
    val history by historyViewModel.state.collectAsState()
    val form = metroViewModel<AddFormViewModel>()
    val latest = history.rows
        .filter { it.state == JobState.COMPLETED }
        .maxByOrNull { it.finishedAtEpochMillis ?: 0L }
    val openLatest: (() -> Unit)? = latest?.let { row ->
        {
            form.setUrl(row.sourceUrl)
            onPreviewUrl(row.sourceUrl)
        }
    }

    Column(modifier.fillMaxSize()) {
        startupWarning?.let { warning ->
            MessageStrip(
                text = warning,
                tone = StatusTone.Negative,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 880.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(0.4f).fillMaxHeight()) {
                        PreviewLinkEntry(onPreviewSingleUrl = onPreviewUrl)
                        PreviewPane(
                            previewUrl = previewUrl,
                            onClearPreview = onClearPreview,
                            latestTitle = latest?.title,
                            latestHost = latest?.sourceHost,
                            onOpenLatest = openLatest,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DownloadLibrary(Modifier.weight(0.6f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PreviewLinkEntry(onPreviewSingleUrl = onPreviewUrl)
                    PreviewPane(
                        previewUrl = previewUrl,
                        onClearPreview = onClearPreview,
                        latestTitle = latest?.title,
                        latestHost = latest?.sourceHost,
                        onOpenLatest = openLatest,
                        modifier = Modifier
                            .heightIn(max = if (previewUrl != null) 420.dp else 160.dp)
                            .fillMaxWidth(),
                    )
                    DownloadLibrary(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(
    previewUrl: String?,
    onClearPreview: () -> Unit,
    latestTitle: String?,
    latestHost: String?,
    onOpenLatest: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (previewUrl != null) {
            PreviewScreen(
                url = previewUrl,
                onBack = onClearPreview,
            )
        } else if (latestTitle != null && onOpenLatest != null) {
            LatestDownloadCard(
                title = latestTitle,
                host = latestHost,
                onOpen = onOpenLatest,
            )
        }
    }
}
