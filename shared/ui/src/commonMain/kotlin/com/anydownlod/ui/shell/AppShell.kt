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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.ui.add.AddForm
import com.anydownlod.ui.home.AdaptiveHome
import com.anydownlod.ui.home.PreviewLinkEntry
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.files_stay_on_device
import com.anydownlod.ui.generated.resources.library
import com.anydownlod.ui.history.HistoryScreen
import com.anydownlod.ui.i18n.labelResource
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.queue.QueueScreen
import com.anydownlod.ui.queue.QueueViewModel
import com.anydownlod.ui.settings.SettingsViewModel
import com.anydownlod.ui.subscriptions.SubscriptionsScreen
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.StatusTone
import dev.zacsweers.metrox.viewmodel.metroViewModel
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
 * returns to the same list. The Downloading destination shows how many jobs
 * are actively working. [previewLink] keeps the paste-and-preview field
 * instead of the full options form.
 */
@Composable
fun AppShell(
    startupWarning: String?,
    capabilities: ToolkitCapabilities,
    selectedTab: ShellTab,
    onTabSelected: (ShellTab) -> Unit,
    onOpenSettings: () -> Unit,
    onPreviewSingleUrl: (String) -> Unit,
    onCopyUrls: ((String) -> Unit)? = null,
    previewLink: Boolean = false,
    previewUrl: String? = null,
    onClearPreview: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
) {
    val settings by metroViewModel<SettingsViewModel>().state.collectAsState()
    val queue by metroViewModel<QueueViewModel>().state.collectAsState()
    val activeDownloads = queue.working
    val saved = settings.settings

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            onOpenSettings = onOpenSettings,
            onOpenSubscriptions = if (previewLink) onOpenSubscriptions else null,
        )
        if (previewLink) {
            AdaptiveHome(
                previewUrl = previewUrl,
                onPreviewUrl = onPreviewSingleUrl,
                onClearPreview = onClearPreview,
                startupWarning = startupWarning,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return@Column
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 880.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LibraryRail(
                        selectedTab = selectedTab,
                        openDownloads = activeDownloads,
                        onTabSelected = onTabSelected,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Workspace(
                        startupWarning = startupWarning,
                        capabilities = capabilities,
                        presets = saved.presets,
                        cookiesConfigured = saved.cookiesConfigured,
                        selectedTab = selectedTab,
                        onPreviewSingleUrl = onPreviewSingleUrl,
                        onCopyUrls = onCopyUrls,
                        previewLink = previewLink,
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CompactLibrary(
                        selectedTab = selectedTab,
                        openDownloads = activeDownloads,
                        onTabSelected = onTabSelected,
                    )
                    Workspace(
                        startupWarning = startupWarning,
                        capabilities = capabilities,
                        presets = saved.presets,
                        cookiesConfigured = saved.cookiesConfigured,
                        selectedTab = selectedTab,
                        onPreviewSingleUrl = onPreviewSingleUrl,
                        onCopyUrls = onCopyUrls,
                        previewLink = previewLink,
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
            .testTag("shell-tab-${tab.name}")
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
    startupWarning: String?,
    capabilities: ToolkitCapabilities,
    presets: List<com.anydownlod.core.domain.Preset>,
    cookiesConfigured: Boolean,
    selectedTab: ShellTab,
    onPreviewSingleUrl: (String) -> Unit,
    onCopyUrls: ((String) -> Unit)? = null,
    previewLink: Boolean = false,
) {
    Column(Modifier.fillMaxSize()) {
        startupWarning?.let { warning ->
            MessageStrip(
                text = warning,
                tone = StatusTone.Negative,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (previewLink) {
            PreviewLinkEntry(onPreviewSingleUrl = onPreviewSingleUrl)
        } else {
            AddForm(
                presets = presets,
                cookiesConfigured = cookiesConfigured,
                onPreviewSingleUrl = onPreviewSingleUrl,
                capabilities = capabilities,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (selectedTab) {
                ShellTab.DOWNLOADING -> QueueScreen(onCopyUrls = onCopyUrls)
                ShellTab.COMPLETED -> HistoryScreen(onCopyUrls = onCopyUrls)
                ShellTab.SUBSCRIPTIONS -> SubscriptionsScreen()
            }
        }
    }
}

