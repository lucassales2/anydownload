package com.anydownload.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anydownload.core.AppGraph
import com.anydownload.core.domain.ThemePreference
import com.anydownload.core.fake.InMemoryAppGraph
import com.anydownload.ui.phone.PhonePage
import com.anydownload.ui.phone.PhoneShell
import com.anydownload.ui.settings.SettingsScreen
import com.anydownload.ui.settings.SettingsViewModel
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.close
import com.anydownload.ui.shell.AppShell
import com.anydownload.ui.shell.ShellTab
import com.anydownload.ui.subscriptions.SubscriptionsScreen
import com.anydownload.ui.theme.AnyDownloadTheme
import com.anydownload.ui.theme.LocalThemeChanger
import com.anydownload.ui.theme.LocalThemePreference
import com.anydownload.core.validation.SharedLink
import com.anydownload.core.validation.SharedLinkResult
import com.anydownload.ui.viewmodel.fallbackViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Root composable called by the Android, iOS, desktop, and web hosts.
 *
 * The [graph] supplies the engine, repositories, and tool probe. Screens below
 * this root resolve Metro ViewModels and do not take the graph.
 *
 * The idle screen keeps the paste field and an inline preview beside the
 * All / Complete / Failed list. Narrow windows stack those pieces. The app
 * never reads the clipboard on its own. Settings and Subscriptions open from
 * the header.
 *
 * [phone] is the Android layout: a welcome screen, a paste-and-preview home,
 * and the All / Complete / Failed list. Desktop keeps the window shell.
 */
@Composable
fun App(
    graph: AppGraph = remember { InMemoryAppGraph() },
    viewModelFactory: MetroViewModelFactory? = null,
    phone: Boolean = false,
) {
    val metroViewModelFactory = viewModelFactory
        ?: (graph as? ViewModelGraph)?.metroViewModelFactory
        ?: remember(graph) { fallbackViewModelFactory(graph) }

    var settingsOpen by remember { mutableStateOf(false) }
    var subscriptionsOpen by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(ShellTab.DOWNLOADING) }
    var phonePage by remember { mutableStateOf(PhonePage.Home) }

    // T-020: a shared or deep link is validated once and opens the preview;
    // a rejection is consumed silently (the Add field stays the manual path).
    val sharedInbox = graph.sharedLinkInbox
    val fallbackInbox = remember { MutableStateFlow<String?>(null) }
    val pendingSharedText by (sharedInbox?.pending ?: fallbackInbox).collectAsState()
    LaunchedEffect(pendingSharedText) {
        val pending = pendingSharedText ?: return@LaunchedEffect
        when (val result = SharedLink.extract(pending)) {
            is SharedLinkResult.Accepted -> {
                previewUrl = result.url
                phonePage = PhonePage.Home
            }
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
                    when {
                        settingsOpen -> SettingsScreen(onClose = { settingsOpen = false })
                        phone -> PhoneShell(
                            page = phonePage,
                            onPageChange = { phonePage = it },
                            previewUrl = previewUrl,
                            onPreviewUrl = { previewUrl = it },
                            onClearPreview = { previewUrl = null },
                            onOpenSettings = { settingsOpen = true },
                            startupWarning = graph.startupWarning,
                        )
                        subscriptionsOpen -> Column(Modifier.fillMaxSize()) {
                            TextButton(
                                onClick = { subscriptionsOpen = false },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(Res.string.close))
                            }
                            SubscriptionsScreen(Modifier.weight(1f).fillMaxWidth())
                        }
                        else -> AppShell(
                            startupWarning = graph.startupWarning,
                            capabilities = graph.toolkitCapabilities,
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            onOpenSettings = { settingsOpen = true },
                            onOpenSubscriptions = { subscriptionsOpen = true },
                            onPreviewSingleUrl = { previewUrl = it },
                            onClearPreview = { previewUrl = null },
                            previewUrl = previewUrl,
                            previewLink = true,
                        )
                    }
                }
            }
        }
    }
}
