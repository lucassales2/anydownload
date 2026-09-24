package com.anydownlod.ui

import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.UnavailableMediaPreviewSource
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.fake.InMemoryToolProbe
import com.anydownlod.core.platform.IosFileStore
import com.anydownlod.core.platform.IosHttpTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

/**
 * The iOS app graph: the shared [HttpDownloadEngine] with in-process
 * NSURLSession transport and a sandbox [IosFileStore] rooted in Documents.
 * Non-direct URLs fail with the engine's typed "extractor not implemented"
 * error; there is no Python or CLI on iOS. Work is foreground-only.
 */
class IosAppGraph : AppGraph {

    private val sandboxRoot: String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)
            ?.takeIf { it.isNotEmpty() }
            ?: NSTemporaryDirectory()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val settingsRepository = InMemorySettingsRepository(
        AppSettings(downloadRoot = sandboxRoot)
    )

    override val engine: DownloadEngine = HttpDownloadEngine(
        transfer = IosHttpTransfer(),
        fileStore = IosFileStore(sandboxRoot),
        settings = settingsRepository,
        scope = scope,
    )

    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository()
    override val settings: SettingsRepository = settingsRepository
    override val toolProbe: ToolProbe = InMemoryToolProbe
    override val previews: MediaPreviewSource = UnavailableMediaPreviewSource
    override val cookieStore: CookieStore = CookieStore.Unavailable
}