package com.anydownlod.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.Preset
import com.anydownlod.ui.add.AddForm
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.files_stay_on_device
import com.anydownlod.ui.generated.resources.library
import com.anydownlod.ui.history.HistoryScreen
import com.anydownlod.ui.i18n.labelResource
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.queue.QueueScreen
import com.anydownlod.ui.subscriptions.SubscriptionsScreen
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.StatusTone
import org.jetbrains.compose.resources.stringResource

/** The three list regions of the desktop shell. */
enum class ShellTab {
    DOWNLOADING,
    COMPLETED,
    SUBSCRIPTIONS,
}

/**
 * Desktop window: shell bar, library navigation, the add composer, and one list.
 *
 * Wide windows use a navigation rail. Narrow windows keep the same three
 * destinations in a horizontal strip so a later phone layout can restack
 * these composables. [selectedTab] is hoisted so opening and closing Settings
 * returns to the same list.
 */
@Composable
fun AppShell(
    graph: AppGraph,
    addForm: AddFormPresenter,
    selectedTab: ShellTab,
    onTabSelected: (ShellTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings by graph.settings.settings.collectAsState()
    val jobs by graph.engine.jobs.collectAsState()
    val openDownloads = jobs.count { !it.state.isTerminal && it.state != JobState.UNKNOWN }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            theme = settings.theme,
            onThemeChange = { theme -> graph.settings.update { it.copy(theme = theme) } },
            onOpenSettings = onOpenSettings,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 880.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LibraryRail(
                        selectedTab = selectedTab,
                        openDownloads = openDownloads,
                        onTabSelected = onTabSelected,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Workspace(
                        graph = graph,
                        addForm = addForm,
                        presets = settings.presets,
                        cookiesConfigured = settings.cookiesConfigured,
                        selectedTab = selectedTab,
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CompactLibrary(
                        selectedTab = selectedTab,
                        openDownloads = openDownloads,
                        onTabSelected = onTabSelected,
                    )
                    Workspace(
                        graph = graph,
                        addForm = addForm,
                        presets = settings.presets,
                        cookiesConfigured = settings.cookiesConfigured,
                        selectedTab = selectedTab,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRail(
    selectedTab: ShellTab,
    openDownloads: Int,
    onTabSelected: (ShellTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.library),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        ShellTab.entries.forEach { tab ->
            Destination(
                tab = tab,
                selected = tab == selectedTab,
                badge = if (tab == ShellTab.DOWNLOADING && openDownloads > 0) openDownloads else null,
                expand = true,
                onClick = { onTabSelected(tab) },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.files_stay_on_device),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CompactLibrary(
    selectedTab: ShellTab,
    openDownloads: Int,
    onTabSelected: (ShellTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShellTab.entries.forEach { tab ->
            Destination(
                tab = tab,
                selected = tab == selectedTab,
                badge = if (tab == ShellTab.DOWNLOADING && openDownloads > 0) openDownloads else null,
                expand = false,
                onClick = { onTabSelected(tab) },
            )
        }
    }
}

@Composable
private fun Destination(
    tab: ShellTab,
    selected: Boolean,
    badge: Int?,
    expand: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .then(if (expand) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = tab.labelResource().resolve(),
            color = labelColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (expand) Modifier.weight(1f) else Modifier,
        )
        if (badge != null) {
            Text(
                text = badge.toString(),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Workspace(
    graph: AppGraph,
    addForm: AddFormPresenter,
    presets: List<Preset>,
    cookiesConfigured: Boolean,
    selectedTab: ShellTab,
) {
    Column(Modifier.fillMaxSize()) {
        graph.startupWarning?.let { warning ->
            MessageStrip(
                text = warning,
                tone = StatusTone.Negative,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        AddForm(
            presenter = addForm,
            presets = presets,
            cookiesConfigured = cookiesConfigured,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (selectedTab) {
                ShellTab.DOWNLOADING -> QueueScreen(graph = graph)
                ShellTab.COMPLETED -> HistoryScreen(graph = graph)
                ShellTab.SUBSCRIPTIONS -> SubscriptionsScreen(graph = graph)
            }
        }
    }
}
