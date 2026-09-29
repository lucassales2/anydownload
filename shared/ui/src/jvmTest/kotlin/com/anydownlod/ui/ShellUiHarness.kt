package com.anydownlod.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.queue.QueueViewModel
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.shell.AppShell
import com.anydownlod.ui.shell.ShellTab
import com.anydownlod.ui.theme.AnyDownloadTheme
import com.anydownlod.ui.viewmodel.AnyDownloadViewModelFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * Renders the shell with its own tab and Settings state, wrapped the same way
 * [App] wraps it (theme plus a full-size surface). T-052 removed the queue
 * chrome from the idle home screen, so shell click-through tests compose
 * [AppShell] here instead of going through [App].
 */
@Composable
internal fun ShellUiHarness(
    graph: AppGraph,
    viewModelFactory: MetroViewModelFactory? = null,
    onCopyUrls: ((String) -> Unit)? = null,
) {
    val addForm = remember(graph) {
        AddFormPresenter(
            engine = graph.engine,
            subscriptions = graph.subscriptions,
            settingsRepository = graph.settings,
        )
    }
    var selectedTab by remember { mutableStateOf(ShellTab.DOWNLOADING) }
    var settingsOpen by remember { mutableStateOf(false) }

    // T-121/T-122: empty test factory map unless the caller supplies one;
    // QueueViewModel resolves against the harness graph's engine.
    val metroViewModelFactory = viewModelFactory
        ?: (graph as? ViewModelGraph)?.metroViewModelFactory
        ?: remember(graph) {
            AnyDownloadViewModelFactory(
                viewModelProviders = mapOf(QueueViewModel::class to { QueueViewModel(graph.engine) }),
                assistedFactoryProviders = emptyMap(),
                manualAssistedFactoryProviders = emptyMap(),
            )
        }

    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        AnyDownloadTheme(darkTheme = false) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (settingsOpen) {
                    SettingsScreen(graph = graph, onClose = { settingsOpen = false })
                } else {
                    AppShell(
                        graph = graph,
                        addForm = addForm,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onOpenSettings = { settingsOpen = true },
                        onPreviewSingleUrl = {},
                        onCopyUrls = onCopyUrls,
                    )
                }
            }
        }
    }
}