package com.anydownlod.ui.clipboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.clipboard_allow
import com.anydownlod.ui.generated.resources.clipboard_not_now
import com.anydownlod.ui.generated.resources.clipboard_permission_body
import com.anydownlod.ui.generated.resources.clipboard_permission_title
import org.jetbrains.compose.resources.stringResource

/**
 * The one-time in-app ask. Agreeing is what lets the app read the clipboard,
 * which is also when the operating system shows its own paste prompt.
 */
@Composable
internal fun ClipboardPermissionDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text(stringResource(Res.string.clipboard_permission_title)) },
        text = { Text(stringResource(Res.string.clipboard_permission_body)) },
        confirmButton = {
            TextButton(onClick = onAllow, modifier = Modifier.testTag("clipboard-allow")) {
                Text(stringResource(Res.string.clipboard_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny, modifier = Modifier.testTag("clipboard-not-now")) {
                Text(stringResource(Res.string.clipboard_not_now))
            }
        },
    )
}
