package com.anydownlod.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.section_appearance
import com.anydownlod.ui.generated.resources.theme
import com.anydownlod.ui.i18n.labelResource
import com.anydownlod.ui.i18n.resolve
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
