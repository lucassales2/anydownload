package com.anydownlod.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.shell.AppShell
import com.anydownlod.ui.shell.ShellTab

/**
 * Root composable called by the Android, iOS, desktop, and web hosts.
 *
 * The [graph] supplies the engine, repositories, and tool probe. Non-desktop
 * hosts and desktop before T-032 use the in-memory graph, so the screens work
 * the same against fakes and against the desktop adapters.
 */
@Composable
fun App(graph: AppGraph = remember { InMemoryAppGraph() }) {
    val settings by graph.settings.settings.collectAsState()
    val addForm = remember(graph) {
        AddFormPresenter(
            engine = graph.engine,
            subscriptions = graph.subscriptions,
            settingsRepository = graph.settings,
        )
    }
    var selectedTab by remember { mutableStateOf(ShellTab.DOWNLOADING) }
    var settingsOpen by remember { mutableStateOf(false) }

    val darkTheme = when (settings.theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (settingsOpen) {
                SettingsScreen(graph = graph, onClose = { settingsOpen = false })
            } else {
                AppShell(
                    graph = graph,
                    addForm = addForm,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onOpenSettings = { settingsOpen = true },
                )
            }
        }
    }
}
