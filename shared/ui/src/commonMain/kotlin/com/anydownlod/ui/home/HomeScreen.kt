package com.anydownlod.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.anydownlod.ui.add.AddFormViewModel
import com.anydownlod.ui.add.UrlEntryRow
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.shell.AppHeader
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.StatusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The idle screen: a header and one paste-link field. No clipboard checks, no
 * add-form option controls, and no queue chrome. Submitting checks that the
 * field holds exactly one compatible URL: a bad value stays on the field with
 * the validator message, and a compatible URL opens the metadata preview.
 */
@Composable
fun HomeScreen(
    startupWarning: String?,
    onOpenSettings: () -> Unit,
    onPreviewSingleUrl: (String) -> Unit,
    form: AddFormViewModel = metroViewModel(),
) {
    val state by form.state.collectAsState()
    val status by form.status.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(onOpenSettings = onOpenSettings)
        startupWarning?.let { warning ->
            MessageStrip(
                text = warning,
                tone = StatusTone.Negative,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                status?.let { current ->
                    MessageStrip(
                        text = current.message.resolve(),
                        tone = if (current.isError) StatusTone.Negative else StatusTone.Positive,
                    )
                }
            }
        }
    }
}
