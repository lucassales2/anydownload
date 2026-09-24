package com.anydownlod.ui

import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
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

    // D4: the shared Kotlin extractor owns matched URLs on iOS. One
    // NSURLSession transfer and registry serve downloads and previews.
    private val transfer = IosHttpTransfer()
    private val jsRuntime = com.anydownlod.core.jsc.QuickJsRuntime()
    private val extractorRegistry = ExtractorRegistry(
        listOf(YoutubeIE(ExtractorHttp(transfer), jsRuntime)),
    )

    override val engine: DownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = IosFileStore(sandboxRoot),
        settings = settingsRepository,
        scope = scope,
        registry = extractorRegistry,
    )

    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository()
    override val settings: SettingsRepository = settingsRepository
    override val toolProbe: ToolProbe = IosToolProbe(jsRuntime)
    override val previews: MediaPreviewSource = ExtractorMediaPreviewSource(extractorRegistry)
    override val cookieStore: CookieStore = CookieStore.Unavailable
}

/** Reports the embedded Zipline QuickJS runtime as the Settings row (T-071). */
private class IosToolProbe(
    private val jsRuntime: com.anydownlod.core.jsc.JsRuntime,
) : ToolProbe {
    override suspend fun probe(): com.anydownlod.core.domain.ToolStatus =
        com.anydownlod.core.domain.ToolStatus(
            jsRuntime = com.anydownlod.core.domain.ToolAvailability(
                available = jsRuntime.available,
                version = if (jsRuntime.available) "${jsRuntime.name} ${jsRuntime.version ?: ""} (embedded)".trim() else null,
            ),
        )
}