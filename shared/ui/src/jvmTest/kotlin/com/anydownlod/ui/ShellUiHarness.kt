package com.anydownlod.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anydownlod.core.AppGraph
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.shell.AppShell
import com.anydownlod.ui.shell.ShellTab
import com.anydownlod.ui.theme.AnyDownloadTheme

/**
 * Renders the shell with its own tab and Settings state, wrapped the same way
 * [App] wraps it (theme plus a full-size surface). T-052 removed the queue
 * chrome from the idle home screen, so shell click-through tests compose
 * [AppShell] here instead of going through [App].
 */
@Composable
internal fun ShellUiHarness(graph: AppGraph) {
    val addForm = remember(graph) {
        AddFormPresenter(
            engine = graph.engine,
            subscriptions = graph.subscriptions,
            settingsRepository = graph.settings,
        )
    }
    var selectedTab by remember { mutableStateOf(ShellTab.DOWNLOADING) }
    var settingsOpen by remember { mutableStateOf(false) }
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
                )
            }
        }
    }
}