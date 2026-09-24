package com.anydownlod.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.ClipboardAccess
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.clipboard.ClipboardLinkWatcher
import com.anydownlod.ui.clipboard.ClipboardPermissionDialog
import com.anydownlod.ui.preview.PreviewScreen
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.shell.AppShell
import com.anydownlod.ui.shell.ShellTab
import com.anydownlod.ui.theme.AnyDownloadTheme

/**
 * Root composable called by the Android, iOS, desktop, and web hosts.
 *
 * The [graph] supplies the engine, repositories, and tool probe. Non-desktop
 * hosts and desktop before T-032 use the in-memory graph, so the screens work
 * the same against fakes and against the desktop adapters.
 *
 * [offerClipboardCheck] is on for the real hosts. UI tests leave it off so the
 * one-time clipboard prompt does not cover the shell.
 */
@Composable
fun App(
    graph: AppGraph = remember { InMemoryAppGraph() },
    offerClipboardCheck: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    val settings by graph.settings.settings.collectAsState()
    val addForm = remember(graph) {
        AddFormPresenter(
            engine = graph.engine,
            subscriptions = graph.subscriptions,
            settingsRepository = graph.settings,
        )
    }
    val formState by addForm.state.collectAsState()
    var selectedTab by remember { mutableStateOf(ShellTab.DOWNLOADING) }
    var settingsOpen by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }

    val darkTheme = when (settings.theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    AnyDownloadTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val openPreview = previewUrl
            when {
                settingsOpen -> SettingsScreen(graph = graph, onClose = { settingsOpen = false })
                openPreview != null -> PreviewScreen(
                    url = openPreview,
                    source = graph.previews,
                    loadThumbnail = graph.loadThumbnail,
                    onBack = { previewUrl = null },
                    onDownload = {
                        addForm.submit()
                        previewUrl = null
                    },
                )
                else -> AppShell(
                    graph = graph,
                    addForm = addForm,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onOpenSettings = { settingsOpen = true },
                    onPreviewSingleUrl = { previewUrl = it },
                    clipboardSuggestion = clipboardSuggestion,
                    onUseClipboardSuggestion = {
                        val url = clipboardSuggestion ?: return@AppShell
                        addForm.setUrl(url)
                        clipboardSuggestion = null
                    },
                    onPreviewClipboardSuggestion = {
                        val url = clipboardSuggestion ?: return@AppShell
                        addForm.setUrl(url)
                        clipboardSuggestion = null
                        previewUrl = url
                    },
                )
            }
            if (offerClipboardCheck && settings.clipboardAccess == ClipboardAccess.UNKNOWN) {
                ClipboardPermissionDialog(
                    onAllow = {
                        graph.settings.update { it.copy(clipboardAccess = ClipboardAccess.ALLOWED) }
                    },
                    onDeny = {
                        graph.settings.update { it.copy(clipboardAccess = ClipboardAccess.DENIED) }
                    },
                )
            }
            if (offerClipboardCheck && settings.clipboardAccess == ClipboardAccess.ALLOWED && openPreview == null && !settingsOpen) {
                ClipboardLinkWatcher(
                    fieldText = formState.urlText,
                    alreadyHandledUrl = settings.handledClipboardUrl,
                    readText = { clipboard.getText()?.text },
                    onFill = { url ->
                        graph.settings.update { it.copy(handledClipboardUrl = url) }
                        val applied = addForm.applyDetectedLink(url)
                        if (applied != null) previewUrl = applied
                    },
                    onSuggest = { url ->
                        graph.settings.update { it.copy(handledClipboardUrl = url) }
                        clipboardSuggestion = url
                    },
                )
            }
        }
    }
}
