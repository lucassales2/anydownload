package com.anydownlod.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.MediaPreview
import kotlinx.coroutines.CancellationException
import com.anydownlod.core.MediaPreviewResult
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.PreviewFailure
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.download
import com.anydownlod.ui.generated.resources.preview_back
import com.anydownlod.ui.generated.resources.preview_edit
import com.anydownlod.ui.generated.resources.preview_edit_hide
import com.anydownlod.ui.generated.resources.preview_failed
import com.anydownlod.ui.generated.resources.preview_formats_needing_js
import com.anydownlod.ui.generated.resources.preview_heading
import com.anydownlod.ui.generated.resources.preview_loading
import com.anydownlod.ui.generated.resources.preview_playlist_count
import com.anydownlod.ui.generated.resources.preview_timed_out
import com.anydownlod.ui.generated.resources.preview_unavailable
import com.anydownlod.ui.generated.resources.preview_uploaded
import com.anydownlod.ui.generated.resources.preview_views
import com.anydownlod.ui.i18n.text
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.StatusBadge
import com.anydownlod.ui.theme.StatusTone
import org.jetbrains.compose.resources.stringResource

private sealed interface PreviewPhase {
    data object Loading : PreviewPhase
    data class Ready(val preview: MediaPreview, val thumbnail: ByteArray?) : PreviewPhase
    data class Failed(val failure: PreviewFailure) : PreviewPhase
}

/**
 * Loads title, thumbnail, and related metadata, then starts the download only
 * after the user confirms. A failed lookup still offers Download.
 *
 * [editor] is the same presenter the home field uses: the collapsible Edit
 * panel writes its choices, and Download submits them with the request.
 */
@Composable
fun PreviewScreen(
    url: String,
    source: MediaPreviewSource,
    loadThumbnail: suspend (String) -> ByteArray?,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    editor: AddFormPresenter? = null,
) {
    var phase by remember(url) { mutableStateOf<PreviewPhase>(PreviewPhase.Loading) }
    var editExpanded by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        phase = PreviewPhase.Loading
        phase = try {
            when (val result = source.load(url)) {
                is MediaPreviewResult.Failed -> PreviewPhase.Failed(result.failure)
                is MediaPreviewResult.Ready -> {
                    val bytes = result.preview.thumbnailUrl?.let { thumbnailUrl ->
                        runCatching { loadThumbnail(thumbnailUrl) }.getOrNull()
                    }
                    PreviewPhase.Ready(result.preview, bytes)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PreviewPhase.Failed(PreviewFailure.Failed)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = PageInset, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.preview_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("preview-back")) {
                Text(stringResource(Res.string.preview_back))
            }
        }

        when (val current = phase) {
            PreviewPhase.Loading -> LoadingBody()
            is PreviewPhase.Failed -> FailedBody(current.failure)
            is PreviewPhase.Ready -> ReadyBody(current.preview, current.thumbnail)
        }

        if (phase !is PreviewPhase.Loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadButton(onDownload)
                if (editor != null) {
                    OutlinedButton(
                        onClick = { editExpanded = !editExpanded },
                        modifier = Modifier.testTag("preview-edit-toggle"),
                    ) {
                        Text(
                            if (editExpanded) {
                                text(Res.string.preview_edit_hide)
                            } else {
                                text(Res.string.preview_edit)
                            },
                        )
                    }
                }
            }
            if (editExpanded && editor != null) {
                PreviewEditPanel(
                    editor = editor,
                    availableFormats = (phase as? PreviewPhase.Ready)?.preview?.availableFormats,
                )
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag("preview-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(Res.string.preview_loading),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FailedBody(failure: PreviewFailure) {
    val message = when (failure) {
        PreviewFailure.Unavailable -> stringResource(Res.string.preview_unavailable)
        PreviewFailure.TimedOut -> stringResource(Res.string.preview_timed_out)
        PreviewFailure.Failed -> stringResource(Res.string.preview_failed)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 640.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReadyBody(preview: MediaPreview, thumbnail: ByteArray?) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Thumbnail(thumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("preview-title"),
                )
                preview.channel?.let { channel ->
                    Text(text = channel, style = MaterialTheme.typography.titleMedium)
                }
                MetaRow(preview)
                preview.extractor?.let { extractor ->
                    StatusBadge(label = extractor, tone = StatusTone.Information)
                }
            }
        }
        if (preview.playlist) {
            val count = preview.entryCount
            Text(
                text = if (count != null) {
                    stringResource(Res.string.preview_playlist_count, formatCount(count.toLong()))
                } else {
                    stringResource(Res.string.preview_playlist_count, "—")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val formatsNeedingJs = preview.availableFormats?.formatsNeedingJs ?: 0
        if (formatsNeedingJs > 0) {
            Text(
                text = stringResource(
                    Res.string.preview_formats_needing_js,
                    formatCount(formatsNeedingJs.toLong()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("preview-js-formats"),
            )
        }
        preview.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaRow(preview: MediaPreview) {
    val views = preview.viewCount?.let { stringResource(Res.string.preview_views, formatCount(it)) }
    val uploaded = preview.uploadDate?.let { stringResource(Res.string.preview_uploaded, it) }
    val parts = listOfNotNull(
        preview.durationSeconds?.let(::formatDuration),
        views,
        uploaded,
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Thumbnail(bytes: ByteArray?) {
    val bitmap = remember(bytes) { bytes?.let(::decodeThumbnail) }
    Box(
        modifier = Modifier
            .size(width = 240.dp, height = 135.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag("preview-thumbnail"),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.height(135.dp))
        }
    }
}

@Composable
private fun DownloadButton(onDownload: () -> Unit) {
    Button(onClick = onDownload, modifier = Modifier.testTag("preview-download")) {
        Text(stringResource(Res.string.download))
    }
}
