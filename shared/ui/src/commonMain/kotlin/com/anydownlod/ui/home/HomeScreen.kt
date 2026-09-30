package com.anydownlod.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.ui.add.AddFormViewModel
import com.anydownlod.ui.add.UrlEntryRow
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.latest_download
import com.anydownlod.ui.generated.resources.subscribe
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.i18n.text
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.ObjectCard
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.StatusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * The link field above the library lists. Submitting checks that the field
 * holds exactly one compatible URL: a bad value stays on the field with the
 * validator message, and a compatible URL opens the metadata preview.
 * Subscribe uses the same field so the Subscriptions list can be filled
 * without the full options form.
 */
@Composable
fun PreviewLinkEntry(
    onPreviewSingleUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    form: AddFormViewModel = metroViewModel(),
) {
    val state by form.state.collectAsState()
    val status by form.status.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageInset, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UrlEntryRow(
            urlText = state.urlText,
            downloadEnabled = state.hasInput,
            onUrlChange = form::setUrl,
            onPaste = { form.applyPastedText(clipboard.getText()?.text) },
            onDownload = {
                val single = form.validateForPreview() ?: return@UrlEntryRow
                onPreviewSingleUrl(single)
            },
        )
        OutlinedButton(
            onClick = { form.subscribe() },
            enabled = state.canSubscribe,
            modifier = Modifier.testTag("add-subscribe-button"),
        ) {
            Text(text(Res.string.subscribe))
        }
        status?.let { current ->
            MessageStrip(
                text = current.message.resolve(),
                tone = if (current.isError) StatusTone.Negative else StatusTone.Positive,
            )
        }
    }
}

/** The most recent saved download. Choosing it opens that link's preview. */
@Composable
fun LatestDownloadCard(
    title: String,
    host: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ObjectCard(
        onClick = onOpen,
        modifier = modifier.padding(horizontal = PageInset).testTag("latest-download"),
    ) {
        Text(
            text = stringResource(Res.string.latest_download),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        host?.let { site ->
            Text(
                text = site,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
