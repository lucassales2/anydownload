package com.anydownlod.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.ClipboardAccess
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.clipboard_allow
import com.anydownlod.ui.generated.resources.clipboard_not_now
import com.anydownlod.ui.generated.resources.clipboard_section
import com.anydownlod.ui.generated.resources.clipboard_setting_body
import com.anydownlod.ui.generated.resources.clipboard_status_allowed
import com.anydownlod.ui.generated.resources.clipboard_status_denied
import com.anydownlod.ui.generated.resources.clipboard_status_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClipboardSection(
    access: ClipboardAccess,
    onChange: (ClipboardAccess) -> Unit,
) {
    SettingsSection(stringResource(Res.string.clipboard_section)) {
        val status = when (access) {
            ClipboardAccess.ALLOWED -> Res.string.clipboard_status_allowed
            ClipboardAccess.DENIED -> Res.string.clipboard_status_denied
            ClipboardAccess.UNKNOWN -> Res.string.clipboard_status_unknown
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(status), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.clipboard_setting_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onChange(ClipboardAccess.ALLOWED) },
                    modifier = Modifier.testTag("settings-clipboard-allow"),
                ) {
                    Text(stringResource(Res.string.clipboard_allow))
                }
                Button(
                    onClick = { onChange(ClipboardAccess.DENIED) },
                    modifier = Modifier.testTag("settings-clipboard-deny"),
                ) {
                    Text(stringResource(Res.string.clipboard_not_now))
                }
            }
        }
    }
}
