package com.anydownload.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anydownload.core.domain.ThemePreference
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.section_appearance
import com.anydownload.ui.generated.resources.theme
import com.anydownload.ui.i18n.labelResource
import com.anydownload.ui.i18n.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * Theme controls for Settings. Uses [Button] instead of FilterChip because
 * chip clicks are swallowed inside the settings verticalScroll in desktop
 * Compose UI tests.
 */
@Composable
internal fun AppearanceSection(theme: ThemePreference, onThemeChange: (ThemePreference) -> Unit) {
    SettingsSection(stringResource(Res.string.section_appearance)) {
        Text(stringResource(Res.string.theme), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { option ->
                Button(
                    onClick = { onThemeChange(option) },
                    modifier = Modifier.testTag("settings-theme-${option.wireName}"),
                ) {
                    Text(option.labelResource().resolve())
                }
            }
        }
    }
}
