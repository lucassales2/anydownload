package com.anydownlod.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.home.HomeScreen
import com.anydownlod.ui.preview.PreviewScreen
import com.anydownlod.ui.settings.SettingsScreen
import com.anydownlod.ui.settings.SettingsViewModel
import com.anydownlod.ui.theme.AnyDownloadTheme
import com.anydownlod.ui.theme.LocalThemeChanger
import com.anydownlod.ui.theme.LocalThemePreference
import com.anydownlod.core.validation.SharedLink
import com.anydownlod.core.validation.SharedLinkResult
import com.anydownlod.ui.viewmodel.fallbackViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * Root composable called by the Android, iOS, desktop, and web hosts.
 *
 * The [graph] supplies the engine, repositories, and tool probe. Screens below
 * this root resolve Metro ViewModels and do not take the graph.
 *
 * The idle screen is the link field alone; the app never reads the clipboard
 * on its own. A single compatible link opens the metadata preview, and
 * Settings is reached from the header.
 */
@Composable
fun App(
    graph: AppGraph = remember { InMemoryAppGraph() },
    viewModelFactory: MetroViewModelFactory? = null,
) {
    val metroViewModelFactory = viewModelFactory
        ?: (graph as? ViewModelGraph)?.metroViewModelFactory
        ?: remember(graph) { fallbackViewModelFactory(graph) }

    var settingsOpen by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    // T-020: a shared or deep link is validated once and opens the preview;
    // a rejection is consumed silently (the Add field stays the manual path).
    val sharedInbox = graph.sharedLinkInbox
    val fallbackInbox = remember { MutableStateFlow<String?>(null) }
    val pendingSharedText by (sharedInbox?.pending ?: fallbackInbox).collectAsState()
    LaunchedEffect(pendingSharedText) {
        val pending = pendingSharedText ?: return@LaunchedEffect
        when (val result = SharedLink.extract(pending)) {
            is SharedLinkResult.Accepted -> previewUrl = result.url
            is SharedLinkResult.Rejected -> Unit
        }
        sharedInbox?.consume()
    }

    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        val settingsViewModel = metroViewModel<SettingsViewModel>()
        val settings by settingsViewModel.state.collectAsState()
        val darkTheme = when (settings.settings.theme) {
            ThemePreference.SYSTEM -> isSystemInDarkTheme()
            ThemePreference.LIGHT -> false
            ThemePreference.DARK -> true
        }
        CompositionLocalProvider(
            LocalThemePreference provides settings.settings.theme,
            LocalThemeChanger provides settingsViewModel::setTheme,
        ) {
            AnyDownloadTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val openPreview = previewUrl
                    when {
                        settingsOpen -> SettingsScreen(onClose = { settingsOpen = false })
                        openPreview != null -> PreviewScreen(
                            url = openPreview,
                            onBack = { previewUrl = null },
                        )
                        else -> HomeScreen(
                            startupWarning = graph.startupWarning,
                            onOpenSettings = { settingsOpen = true },
                            onPreviewSingleUrl = { previewUrl = it },
                        )
                    }
                }
            }
        }
    }
}
