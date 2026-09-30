package com.anydownlod.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.settings.SettingsViewModel
import com.anydownlod.ui.shell.AppShell
import com.anydownlod.ui.shell.ShellTab
import com.anydownlod.ui.theme.AnyDownloadTheme
import com.anydownlod.ui.theme.LocalThemeChanger
import com.anydownlod.ui.theme.LocalThemePreference
import com.anydownlod.ui.viewmodel.fallbackViewModelFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metrox.viewmodel.metroViewModel

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
    var selectedTab by remember { mutableStateOf(ShellTab.DOWNLOADING) }
    var settingsOpen by remember { mutableStateOf(false) }

    val metroViewModelFactory = viewModelFactory
        ?: (graph as? ViewModelGraph)?.metroViewModelFactory
        ?: remember(graph) { fallbackViewModelFactory(graph) }

    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        val settingsViewModel = metroViewModel<SettingsViewModel>()
        val settings by settingsViewModel.state.collectAsState()
        CompositionLocalProvider(
            LocalThemePreference provides settings.settings.theme,
            LocalThemeChanger provides settingsViewModel::setTheme,
        ) {
            AnyDownloadTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (settingsOpen) {
                        SettingsScreen(onClose = { settingsOpen = false })
                    } else {
                        AppShell(
                            startupWarning = graph.startupWarning,
                            capabilities = graph.toolkitCapabilities,
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
}
