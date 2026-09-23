package com.anydownlod.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.JobState

/** Horizontal inset shared by the composer, lists, and page titles. */
val PageInset = 20.dp

/** Semantic status color, independent of the brand blue. */
enum class StatusTone {
    Positive,
    Critical,
    Negative,
    Information,
    Neutral,
}

data class ToneColors(val background: Color, val foreground: Color)

fun JobState.statusTone(): StatusTone = when (this) {
    JobState.COMPLETED -> StatusTone.Positive
    JobState.FAILED -> StatusTone.Negative
    JobState.CANCELLED, JobState.UNKNOWN -> StatusTone.Neutral
    JobState.PENDING, JobState.SCHEDULED -> StatusTone.Critical
    JobState.QUEUED, JobState.RESOLVING, JobState.DOWNLOADING, JobState.POSTPROCESSING ->
        StatusTone.Information
}

@Composable
fun StatusTone.colors(): ToneColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) darkTone(this) else lightTone(this)
}

private fun lightTone(tone: StatusTone): ToneColors = when (tone) {
    StatusTone.Positive -> ToneColors(Color(0xFFE3F6EB), Color(0xFF0E6B3A))
    StatusTone.Critical -> ToneColors(Color(0xFFFFF4D6), Color(0xFF7A4E00))
    StatusTone.Negative -> ToneColors(Color(0xFFFDECEC), Color(0xFFB42318))
    StatusTone.Information -> ToneColors(Color(0xFFE7F1FF), Color(0xFF084A94))
    StatusTone.Neutral -> ToneColors(Color(0xFFEEEFF3), Color(0xFF3A3A3C))
}

private fun darkTone(tone: StatusTone): ToneColors = when (tone) {
    StatusTone.Positive -> ToneColors(Color(0xFF0E2A1C), Color(0xFF8EEDB8))
    StatusTone.Critical -> ToneColors(Color(0xFF3A2A0C), Color(0xFFFFD27A))
    StatusTone.Negative -> ToneColors(Color(0xFF3A1616), Color(0xFFFFB4AB))
    StatusTone.Information -> ToneColors(Color(0xFF0E2744), Color(0xFFA9CFFF))
    StatusTone.Neutral -> ToneColors(Color(0xFF3A3A3C), Color(0xFFE5E5EA))
}

@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val colors = tone.colors()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100),
        color = colors.background,
    ) {
        Text(
            text = label,
            color = colors.foreground,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .then(
                    if (live) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
fun MessageStrip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.background,
    ) {
        Text(
            text = text,
            color = colors.foreground,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
fun PageHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun KpiTile(
    value: String,
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = colors.background) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = colors.foreground)
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.foreground)
        }
    }
}

/**
 * Flat grouped surface. [accent] is a status rail along the leading edge,
 * used by job and subscription cards.
 */
@Composable
fun ObjectCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (accent == null) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        } else {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
                Column(
                    modifier = Modifier.weight(1f).padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun SelectionBar(
    selection: ToggleableState,
    onToggleAll: () -> Unit,
    selectAllTag: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TriStateCheckbox(
                state = selection,
                onClick = onToggleAll,
                modifier = Modifier.testTag(selectAllTag),
            )
            Text(text = "Select all", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: (T) -> String? = { null },
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val picked = option == selected
                val tag = optionTag(option)
                FilterChip(
                    selected = picked,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                    modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = picked,
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(10.dp),
        colors = colors,
    )
}

@Composable
fun DestructiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text)
    }
}

@Composable
fun DestructiveOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text)
    }
}

@Composable
fun ProgressMeter(progress: Float?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val bar = modifier.fillMaxWidth().height(6.dp)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = bar,
            color = color,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = bar,
            color = color,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
