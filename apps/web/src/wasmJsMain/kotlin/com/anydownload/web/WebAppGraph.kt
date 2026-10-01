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
package com.anydownload.web

import com.anydownload.core.AppGraph
import com.anydownload.core.CookieFilePicker
import com.anydownload.core.CookieStore
import com.anydownload.core.DownloadEngine
import com.anydownload.core.FileOpener
import com.anydownload.core.FileRevealer
import com.anydownload.core.FolderPicker
import com.anydownload.core.ThumbnailLoader
import com.anydownload.core.UrlOpener
import com.anydownload.core.ExtractorMediaPreviewSource
import com.anydownload.core.MediaPreviewSource
import com.anydownload.core.SettingsRepository
import com.anydownload.core.SubscriptionRepository
import com.anydownload.core.ToolProbe
import com.anydownload.core.di.SharedEngineBindings
import com.anydownload.ui.add.AddFormBindings
import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.engine.WebExtensionEngine
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
import com.anydownload.core.extract.youtube.YoutubeSearch
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.fake.InMemorySubscriptionRepository
import com.anydownload.core.jsc.BrowserJsRuntime
import com.anydownload.core.jsc.JsRuntime
import com.anydownload.core.music.AudioMatcher
import com.anydownload.core.music.SpotifyDownloadService
import com.anydownload.core.music.SpotifyMetadataClients
import com.anydownload.core.persist.JobDocumentRestore
import com.anydownload.core.persist.JobDocumentStorageError
import com.anydownload.core.persist.JobDocumentStore
import com.anydownload.core.persist.PersistedSubscriptionRepository
import com.anydownload.core.persist.PersistingDownloadEngine
import com.anydownload.core.subscriptions.RegistrySubscriptionEntrySource
import com.anydownload.core.subscriptions.ScanningSubscriptionRepository
import com.anydownload.core.subscriptions.SubscriptionScanner
import com.anydownload.core.subscriptions.enqueueSubscriptionEntry
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.platform.WebExtensionTransfer
import com.anydownload.core.postprocess.ToolkitCapabilities
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

@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class, AddFormBindings::class])
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
    override val subscriptionsPauseOnSuspend: Boolean get() = true
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
    fun subscriptions(
        registry: ExtractorRegistry,
        scope: CoroutineScope,
        engine: Provider<DownloadEngine>,
    ): SubscriptionRepository {
        val storage = WebSubscriptionDocumentStorage()
        val delegate = InMemorySubscriptionRepository(
            seedSubscriptions = PersistedSubscriptionRepository.restore(storage),
        )
        val persisted = PersistedSubscriptionRepository(delegate, storage)
        val scanner = SubscriptionScanner(
            repository = persisted,
            entrySource = RegistrySubscriptionEntrySource(registry),
        ) { subscription, entry -> enqueueSubscriptionEntry(engine(), subscription, entry) }
        return ScanningSubscriptionRepository(persisted, scope, scanner)
    }

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

    @Provides
    @SingleIn(AppScope::class)
    fun urlOpener(): UrlOpener = UrlOpener { _ -> }

    @Provides
    @SingleIn(AppScope::class)
    fun fileOpener(): FileOpener = FileOpener { _ -> }

    @Provides
    @SingleIn(AppScope::class)
    fun fileRevealer(): FileRevealer = FileRevealer { _ -> }

    @Provides
    @SingleIn(AppScope::class)
    fun folderPicker(): FolderPicker = FolderPicker { null }

    // T-018 recorded web gap: the extension does not store or apply a
    // Netscape cookie file yet, and MV3 fetch cannot set Cookie. The page
    // sees only Not configured and never receives cookie bytes.
    @Provides
    @SingleIn(AppScope::class)
    fun cookieFilePicker(): CookieFilePicker = CookieFilePicker { null }

    @Provides
    @SingleIn(AppScope::class)
    fun thumbnailLoader(): ThumbnailLoader = ThumbnailLoader { null }

    @Provides
    @SingleIn(AppScope::class)
    fun cookieStore(): CookieStore = CookieStore.Unavailable

    @Provides
    @SingleIn(AppScope::class)
    fun toolkitCapabilities(): ToolkitCapabilities = ToolkitCapabilities.Unavailable

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyOptional(service: SpotifyDownloadService): SpotifyDownloadService? = service
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
    override suspend fun probe(): com.anydownload.core.domain.ToolStatus =
        com.anydownload.core.domain.ToolStatus(
            jsRuntime = com.anydownload.core.domain.ToolAvailability(
                available = jsRuntime.available,
                version = if (jsRuntime.available) "${jsRuntime.name} (page)" else null,
            ),
        )
}
