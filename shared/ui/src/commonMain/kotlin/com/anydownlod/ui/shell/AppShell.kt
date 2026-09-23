package com.anydownlod.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.add.AddForm
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.history.HistoryScreen
import com.anydownlod.ui.queue.QueueScreen
import com.anydownlod.ui.subscriptions.SubscriptionsScreen

/** The three list regions of the desktop shell. */
enum class ShellTab(val label: String) {
    DOWNLOADING("Downloading"),
    COMPLETED("Completed"),
    SUBSCRIPTIONS("Subscriptions"),
}

/**
 * One desktop window: header, add slot, and the three list tabs.
 *
 * [selectedTab] is hoisted so opening and closing Settings returns to the same
 * tab. The three list regions stay separate composables so a later milestone
 * can stack them for a phone layout.
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

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            theme = settings.theme,
            onThemeChange = { theme -> graph.settings.update { it.copy(theme = theme) } },
            onOpenSettings = onOpenSettings,
        )
        graph.startupWarning?.let { warning ->
            Text(
                text = warning,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AddForm(presenter = addForm, presets = settings.presets, cookiesConfigured = settings.cookiesConfigured)
        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            ShellTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) },
                )
            }
        }
        when (selectedTab) {
            ShellTab.DOWNLOADING -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                QueueScreen(graph = graph)
            }

            ShellTab.COMPLETED -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                HistoryScreen(graph = graph)
            }

            ShellTab.SUBSCRIPTIONS -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SubscriptionsScreen(graph = graph)
            }
        }
    }
}
