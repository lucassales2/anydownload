package com.anydownlod.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieFilePicker
import com.anydownlod.core.FileOpener
import com.anydownlod.core.FileRevealer
import com.anydownlod.core.FolderPicker
import com.anydownlod.core.ThumbnailLoader
import com.anydownlod.core.UrlOpener
import com.anydownlod.ui.add.AddFormViewModel
import com.anydownlod.ui.history.HistoryViewModel
import com.anydownlod.ui.preview.PreviewViewModel
import com.anydownlod.ui.queue.QueueViewModel
import com.anydownlod.ui.settings.SettingsViewModel
import com.anydownlod.ui.subscriptions.SubscriptionsViewModel
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
        SubscriptionsViewModel::class to { SubscriptionsViewModel(graph.subscriptions) },
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
