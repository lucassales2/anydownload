package com.anydownlod.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.ui.theme.SegmentedChoice

/** Human-readable label for a theme choice; the control is never color-only. */
internal fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "System"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

/**
 * Shell bar: product mark, the labeled theme control, and Settings.
 *
 * There is no server URL, login, or capability card here.
 */
@Composable
fun AppHeader(
    theme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tight = maxWidth < 640.dp
            if (tight) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductLockup(Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        SettingsButton(onOpenSettings)
                    }
                    ThemeControl(theme, onThemeChange)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProductLockup(Modifier.weight(1f))
                    ThemeControl(theme, onThemeChange)
                    SettingsButton(onOpenSettings)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProductLockup(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "A",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "AnyDownload",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThemeControl(theme: ThemePreference, onThemeChange: (ThemePreference) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Theme", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = ThemePreference.entries,
            selected = theme,
            optionLabel = { it.label() },
            onSelect = onThemeChange,
        )
    }
}

@Composable
private fun SettingsButton(onOpenSettings: () -> Unit) {
    FilledTonalButton(onClick = onOpenSettings) { Text("Settings") }
}
