/*
 * Web Metro graph — AnyDownload
 *
 * T-119: the web host assembly becomes a Metro `@DependencyGraph` in the
 * wasm source set. One `WindowExtensionBridge`, one `BrowserJsRuntime`, one
 * `WebExtensionTransfer`, and one `WebExtensionEngine`. The page never
 * fetches an arbitrary origin; the extension carries every request through
 * the shared `ExtractorHttp`. The jobs document stays in localStorage and the
 * KSP-style storage error is still exposed for the host.
 */
package com.anydownlod.web

import com.anydownlod.core.AppGraph
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.di.SharedEngineBindings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.engine.WebExtensionEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.jsc.BrowserJsRuntime
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.music.AudioMatcher
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyMetadataClients
import com.anydownlod.core.persist.JobDocumentRestore
import com.anydownlod.core.persist.JobDocumentStorageError
import com.anydownlod.core.persist.JobDocumentStore
import com.anydownlod.core.persist.PersistingDownloadEngine
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.WebExtensionTransfer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class])
internal interface WebAppGraph : ViewModelGraph, AppGraph {

    override val engine: DownloadEngine
    override val subscriptions: SubscriptionRepository
    override val settings: SettingsRepository
    override val toolProbe: ToolProbe

    // Core AppGraph gives these properties default getters; the host
    // accessors carry the web bindings and the concrete overrides read them
    // (same Metro behavior the other host graphs recorded).
    val hostPreviews: MediaPreviewSource
    val hostStartupWarning: String?
    val hostSpotify: SpotifyDownloadService

    override val previews: MediaPreviewSource get() = hostPreviews
    override val startupWarning: String? get() = hostStartupWarning
    override val spotify: SpotifyDownloadService? get() = hostSpotify

    /** The page runtime the T-072 `?solverHook=1` hook installs on. */
    val browserJsRuntime: BrowserJsRuntime

    /** The typed web persistence state, exposed for the host. */
    val jobStorageError: StateFlow<JobDocumentStorageError?>

    @Provides
    @SingleIn(AppScope::class)
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun bridge(): WindowExtensionBridge = WindowExtensionBridge()

    @Provides
    @SingleIn(AppScope::class)
    fun browserJsRuntime(): BrowserJsRuntime = BrowserJsRuntime()

    @Provides
    @SingleIn(AppScope::class)
    fun jsRuntime(browserJsRuntime: BrowserJsRuntime): JsRuntime = browserJsRuntime

    @Provides
    @SingleIn(AppScope::class)
    fun extensionTransfer(bridge: WindowExtensionBridge): HttpTransfer =
        WebExtensionTransfer(bridge)

    @Provides
    @SingleIn(AppScope::class)
    fun jobStorage(): WebJobDocumentStorage = WebJobDocumentStorage()

    @Provides
    @SingleIn(AppScope::class)
    fun jobDocumentStore(): JobDocumentStore = JobDocumentStore()

    /** One localStorage restore; a refused read yields empty seeds and a warning. */
    @Provides
    @SingleIn(AppScope::class)
    fun restoredQueue(
        jobStorage: WebJobDocumentStorage,
        jobDocumentStore: JobDocumentStore,
    ): WebRestoredQueue {
        val restoreResult: JobDocumentRestore? = try {
            jobDocumentStore.restore(jobStorage, clearAfterSeconds = 0)
        } catch (failure: Exception) {
            null
        }
        return WebRestoredQueue(
            jobs = restoreResult?.jobs ?: emptyList(),
            warning = when {
                restoreResult == null ->
                    "The saved queue could not be read. It will be replaced when the queue changes."

                restoreResult.interruptedActive > 0 ->
                    "Marked ${restoreResult.interruptedActive} interrupted job(s) for retry."

                else -> null
            },
        )
    }

    /**
     * The engine persists after a mutation; the lambda resolves the
     * persisting engine only when a write happens, breaking the cycle.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun persist(persisting: Provider<PersistingDownloadEngine>): (List<DownloadJob>) -> Unit =
        { persisting().persistNow() }

    @Provides
    @SingleIn(AppScope::class)
    fun delegateEngine(
        bridge: WindowExtensionBridge,
        scope: CoroutineScope,
        registry: ExtractorRegistry,
        persist: (List<DownloadJob>) -> Unit,
        restored: WebRestoredQueue,
    ): WebExtensionEngine = WebExtensionEngine(
        bridge = bridge,
        scope = scope,
        registry = registry,
        persist = persist,
        seedJobs = restored.jobs,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun persistingEngine(
        delegate: WebExtensionEngine,
        documentStore: JobDocumentStore,
        jobStorage: WebJobDocumentStorage,
    ): PersistingDownloadEngine = PersistingDownloadEngine(
        delegate = delegate,
        documentStore = documentStore,
        writeDocument = jobStorage::write,
        clearAfterSeconds = { 0L },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun downloadEngine(persisting: PersistingDownloadEngine): DownloadEngine = persisting

    @Provides
    @SingleIn(AppScope::class)
    fun jobStorageError(
        persisting: PersistingDownloadEngine,
    ): StateFlow<JobDocumentStorageError?> = persisting.storageError

    @Provides
    @SingleIn(AppScope::class)
    fun subscriptions(): SubscriptionRepository = InMemorySubscriptionRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun settings(): SettingsRepository = InMemorySettingsRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun previews(registry: ExtractorRegistry): MediaPreviewSource =
        ExtractorMediaPreviewSource(registry)

    @Provides
    @SingleIn(AppScope::class)
    fun startupWarning(restored: WebRestoredQueue): String? = restored.warning

    @Provides
    @SingleIn(AppScope::class)
    fun toolProbe(jsRuntime: JsRuntime): ToolProbe = WebToolProbe(jsRuntime)

    @Provides
    @SingleIn(AppScope::class)
    fun spotify(
        extractorHttp: ExtractorHttp,
        engine: DownloadEngine,
    ): SpotifyDownloadService = SpotifyDownloadService(
        metadata = SpotifyMetadataClients.default(extractorHttp),
        matcher = AudioMatcher.default(YoutubeSearch(extractorHttp)),
        engine = engine,
    )
}

/** The one restored queue plus the startup warning. */
internal class WebRestoredQueue(
    val jobs: List<DownloadJob>,
    val warning: String?,
)

/** Reports the page's own JavaScript runtime as the Settings row (T-072). */
private class WebToolProbe(
    private val jsRuntime: JsRuntime,
) : ToolProbe {
    override suspend fun probe(): com.anydownlod.core.domain.ToolStatus =
        com.anydownlod.core.domain.ToolStatus(
            jsRuntime = com.anydownlod.core.domain.ToolAvailability(
                available = jsRuntime.available,
                version = if (jsRuntime.available) "${jsRuntime.name} (page)" else null,
            ),
        )
}
