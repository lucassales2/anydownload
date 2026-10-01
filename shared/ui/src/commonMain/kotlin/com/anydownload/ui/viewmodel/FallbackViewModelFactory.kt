package com.anydownload.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.anydownload.core.AppGraph
import com.anydownload.core.CookieFilePicker
import com.anydownload.core.FileOpener
import com.anydownload.core.FileRevealer
import com.anydownload.core.FolderPicker
import com.anydownload.core.ThumbnailLoader
import com.anydownload.core.UrlOpener
import com.anydownload.ui.add.AddFormViewModel
import com.anydownload.ui.history.HistoryViewModel
import com.anydownload.ui.preview.PreviewViewModel
import com.anydownload.ui.queue.QueueViewModel
import com.anydownload.ui.settings.SettingsViewModel
import com.anydownload.ui.subscriptions.SubscriptionsViewModel
import kotlin.reflect.KClass

/**
 * ViewModels for hosts and tests whose graph is not a [dev.zacsweers.metrox.viewmodel.ViewModelGraph].
 * Production graphs contribute the same classes through Metro.
 */
internal fun fallbackViewModelFactory(graph: AppGraph): AnyDownloadViewModelFactory {
    val providers: Map<KClass<out ViewModel>, () -> ViewModel> = mapOf(
        AddFormViewModel::class to {
            AddFormViewModel(graph.engine, graph.subscriptions, graph.settings)
        },
        PreviewViewModel::class to {
            PreviewViewModel(
                previews = graph.previews,
                thumbnails = ThumbnailLoader { url -> graph.loadThumbnail(url) },
                capabilities = graph.toolkitCapabilities,
                spotify = graph.spotify,
            )
        },
        SettingsViewModel::class to {
            SettingsViewModel(
                repository = graph.settings,
                toolProbe = graph.toolProbe,
                cookieStore = graph.cookieStore,
                browserCookieImport = graph.browserCookieImport,
                spotifyAuth = graph.spotifyAuth,
                pickFolder = FolderPicker { graph.pickFolder() },
                pickCookieFile = CookieFilePicker { graph.pickCookieFile() },
            )
        },
        HistoryViewModel::class to {
            HistoryViewModel(
                engine = graph.engine,
                openFile = FileOpener { graph.openFile(it) },
                revealFile = FileRevealer { graph.revealFile(it) },
            )
        },
        SubscriptionsViewModel::class to {
            SubscriptionsViewModel(graph.subscriptions, graph.subscriptionsPauseOnSuspend)
        },
        QueueViewModel::class to {
            QueueViewModel(graph.engine, UrlOpener { graph.openUrl(it) })
        },
    )
    return AnyDownloadViewModelFactory(
        viewModelProviders = providers,
        assistedFactoryProviders = emptyMap(),
        manualAssistedFactoryProviders = emptyMap(),
    )
}
