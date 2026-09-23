package com.anydownlod.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.ThemePreference

/** Human-readable label for a theme choice; the control is never color-only. */
internal fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "System"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

/**
 * Shell header: app title, the labeled theme control, and Settings.
 *
 * There is no server URL, login, or capability card here.
 */
@Composable
fun AppHeader(
    theme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "AnyDownload",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(text = "Theme", style = MaterialTheme.typography.labelLarge)
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = option == theme,
                    onClick = { onThemeChange(option) },
                    label = { Text(option.label()) },
                )
            }
            Button(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}
