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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anydownlod.core.AppGraph
import com.anydownlod.core.CompositeMediaPreviewSource
import com.anydownlod.core.SpotifyMediaPreviewSource
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.home.HomeScreen
import com.anydownlod.ui.preview.PreviewScreen
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.theme.AnyDownloadTheme
import kotlinx.coroutines.launch

/**
 * Root composable called by the Android, iOS, desktop, and web hosts.
 *
 * The [graph] supplies the engine, repositories, and tool probe. Non-desktop
 * hosts and desktop before T-032 use the in-memory graph, so the screens work
 * the same against fakes and against the desktop adapters.
 *
 * The idle screen is the link field alone; the app never reads the clipboard
 * on its own. A single compatible link opens the metadata preview, and
 * Settings is reached from the header.
 */
@Composable
fun App(
    graph: AppGraph = remember { InMemoryAppGraph() },
) {
    val settings by graph.settings.settings.collectAsState()
    val addForm = remember(graph) {
        AddFormPresenter(
            engine = graph.engine,
            subscriptions = graph.subscriptions,
            settingsRepository = graph.settings,
        )
    }
    val previewSource = remember(graph) {
        val spotify = graph.spotify
        if (spotify != null) {
            CompositeMediaPreviewSource(SpotifyMediaPreviewSource(spotify), graph.previews)
        } else {
            graph.previews
        }
    }
    val scope = rememberCoroutineScope()
    var settingsOpen by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

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
                    source = previewSource,
                    loadThumbnail = graph.loadThumbnail,
                    onBack = { previewUrl = null },
                    onDownload = { preview, selectedMediaIds ->
                        val spotifyPreview = preview?.spotify
                        val service = graph.spotify
                        if (spotifyPreview != null && service != null) {
                            scope.launch {
                                service.queue(
                                    preview = spotifyPreview,
                                    options = addForm.currentOptions(),
                                    capabilities = graph.toolkitCapabilities,
                                )
                            }
                        } else {
                            addForm.submit(selectedMediaIds)
                        }
                        previewUrl = null
                    },
                    editor = addForm,
                    capabilities = graph.toolkitCapabilities,
                )
                else -> HomeScreen(
                    graph = graph,
                    addForm = addForm,
                    onOpenSettings = { settingsOpen = true },
                    onPreviewSingleUrl = { previewUrl = it },
                )
            }
        }
    }
}