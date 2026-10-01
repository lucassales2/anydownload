/*
 * iOS Metro graph — AnyDownload
 *
 * T-118: the iOS host assembly becomes a Metro `@DependencyGraph` in the
 * shared UI module's Apple source set. One `IosHttpTransfer`, one
 * `QuickJsRuntime`, and the shared `ExtractorRegistry` serve downloads,
 * previews, the startup split, and the Spotify helper. The file store stays
 * rooted in Documents; the jobs document stays under Application Support.
 * Work is foreground-only and non-direct URLs fail with the engine's typed
 * "extractor not implemented" error; there is no Python or CLI on iOS.
 */
package com.anydownload.ui

import com.anydownload.core.AppGraph
import com.anydownload.core.CookieFilePicker
import com.anydownload.core.CookieStore
import com.anydownload.core.SharedLinkInbox
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
import com.anydownload.core.cookies.CookieJarSource
import com.anydownload.ui.add.AddFormBindings
import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.engine.HttpDownloadEngine
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
import com.anydownload.core.extract.youtube.YoutubeSearch
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.fake.InMemorySubscriptionRepository
import com.anydownload.core.jsc.JsRuntime
import com.anydownload.core.jsc.QuickJsRuntime
import com.anydownload.core.music.AudioMatcher
import com.anydownload.core.music.SpotifyDownloadService
import com.anydownload.core.music.SpotifyMetadataClients
import com.anydownload.core.persist.JobDocumentRestore
import com.anydownload.core.persist.JobDocumentStore
import com.anydownload.core.persist.PersistedSubscriptionRepository
import com.anydownload.core.persist.PersistingDownloadEngine
import com.anydownload.core.subscriptions.RegistrySubscriptionEntrySource
import com.anydownload.core.subscriptions.ScanningSubscriptionRepository
import com.anydownload.core.subscriptions.SubscriptionScanner
import com.anydownload.core.subscriptions.enqueueSubscriptionEntry
import com.anydownload.core.platform.IosFileStore
import com.anydownload.core.platform.IosHttpTransfer
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.postprocess.ToolkitCapabilities
import com.anydownload.ui.media.IosMediaToolkit
import com.anydownload.ui.cookies.IosCookieJarSource
import com.anydownload.ui.cookies.IosCookiePicker
import com.anydownload.ui.cookies.IosCookieStore
import com.anydownload.ui.persist.IosJobDocumentStorage
import com.anydownload.ui.persist.IosSubscriptionDocumentStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class, AddFormBindings::class])
internal interface IosAppGraph : ViewModelGraph, AppGraph {

    override val engine: DownloadEngine
    override val subscriptions: SubscriptionRepository
    override val settings: SettingsRepository
    override val toolProbe: ToolProbe

    // Core AppGraph gives these properties default getters. Metro does not
    // generate accessors for overrides of default properties, so the host
    // accessors carry the iOS bindings and the concrete overrides read them
    // (same Metro behavior the desktop and Android hosts recorded).
    val hostPreviews: MediaPreviewSource
    val hostStartupWarning: String?
    val hostToolkitCapabilities: ToolkitCapabilities
    val hostSpotify: SpotifyDownloadService

    override val previews: MediaPreviewSource get() = hostPreviews
    override val startupWarning: String? get() = hostStartupWarning
    override val subscriptionsPauseOnSuspend: Boolean get() = true
    override val sharedLinkInbox: SharedLinkInbox get() = IosSharedLinkInbox.inbox
    override val toolkitCapabilities: ToolkitCapabilities get() = hostToolkitCapabilities
    override val spotify: SpotifyDownloadService? get() = hostSpotify

    @Provides
    @SingleIn(AppScope::class)
    fun sandboxRoot(): IosSandboxRoot =
        IosSandboxRoot(
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                .firstOrNull() as? String)
                ?.takeIf { it.isNotEmpty() }
                ?: NSTemporaryDirectory(),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun httpTransfer(): HttpTransfer = IosHttpTransfer()

    @Provides
    @SingleIn(AppScope::class)
    fun jsRuntime(): JsRuntime = QuickJsRuntime()

    @Provides
    @SingleIn(AppScope::class)
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun settings(sandboxRoot: IosSandboxRoot): SettingsRepository =
        InMemorySettingsRepository(AppSettings(downloadRoot = sandboxRoot.value))

    @Provides
    @SingleIn(AppScope::class)
    fun toolkit(): IosMediaToolkit = IosMediaToolkit()

    @Provides
    @SingleIn(AppScope::class)
    fun jobDocumentStore(): JobDocumentStore = JobDocumentStore()

    @Provides
    @SingleIn(AppScope::class)
    fun jobStorage(): IosJobDocumentStorage {
        val stateRoot = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { "$it/AnyDownload" }
            ?: (NSTemporaryDirectory().trimEnd('/') + "/AnyDownload")
        return IosJobDocumentStorage("$stateRoot/jobs.json")
    }

    /**
     * One restore under Application Support. A corrupt document yields empty
     * seeds and a startup warning; interrupted active rows come back from the
     * document as failed and retryable.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun restoredQueue(
        jobStorage: IosJobDocumentStorage,
        jobDocumentStore: JobDocumentStore,
        settings: SettingsRepository,
    ): IosRestoredQueue {
        val restoreResult: JobDocumentRestore? = try {
            jobDocumentStore.restore(jobStorage, settings.settings.value.clearCompletedAfterSeconds)
        } catch (failure: Exception) {
            null
        }
        return IosRestoredQueue(
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
    fun httpEngine(
        sandboxRoot: IosSandboxRoot,
        transfer: HttpTransfer,
        settings: SettingsRepository,
        scope: CoroutineScope,
        persist: (List<DownloadJob>) -> Unit,
        registry: ExtractorRegistry,
        toolkit: IosMediaToolkit,
        cookieJarSource: CookieJarSource,
        restored: IosRestoredQueue,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = IosFileStore(sandboxRoot.value),
        settings = settings,
        scope = scope,
        registry = registry,
        toolkit = toolkit,
        cookieJarSource = cookieJarSource,
        persist = persist,
        seedJobs = restored.jobs,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun persistingEngine(
        httpEngine: HttpDownloadEngine,
        documentStore: JobDocumentStore,
        jobStorage: IosJobDocumentStorage,
        settings: SettingsRepository,
    ): PersistingDownloadEngine = PersistingDownloadEngine(
        delegate = httpEngine,
        documentStore = documentStore,
        writeDocument = jobStorage::write,
        clearAfterSeconds = { settings.settings.value.clearCompletedAfterSeconds },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun downloadEngine(persisting: PersistingDownloadEngine): DownloadEngine = persisting

    @Provides
    @SingleIn(AppScope::class)
    fun subscriptions(
        registry: ExtractorRegistry,
        scope: CoroutineScope,
        engine: Provider<DownloadEngine>,
    ): SubscriptionRepository {
        val stateRoot = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { "$it/AnyDownload" }
            ?: (NSTemporaryDirectory().trimEnd('/') + "/AnyDownload")
        val storage = IosSubscriptionDocumentStorage("$stateRoot/subscriptions.json")
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
    fun previews(registry: ExtractorRegistry): MediaPreviewSource =
        ExtractorMediaPreviewSource(registry)

    @Provides
    @SingleIn(AppScope::class)
    fun startupWarning(restored: IosRestoredQueue): String? = restored.warning

    @Provides
    @SingleIn(AppScope::class)
    fun toolkitCapabilities(toolkit: IosMediaToolkit): ToolkitCapabilities =
        toolkit.capabilities()

    @Provides
    @SingleIn(AppScope::class)
    fun toolProbe(jsRuntime: JsRuntime): ToolProbe = IosToolProbe(jsRuntime)

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

    @Provides
    @SingleIn(AppScope::class)
    fun cookieStore(): IosCookieStore {
        val stateRoot = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { "$it/AnyDownload" }
            ?: (NSTemporaryDirectory().trimEnd('/') + "/AnyDownload")
        return IosCookieStore(
            stateDirectory = stateRoot,
            importDirectory = NSTemporaryDirectory(),
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun cookieStoreBinding(store: IosCookieStore): CookieStore = store

    @Provides
    @SingleIn(AppScope::class)
    fun cookieJarSource(store: IosCookieStore): CookieJarSource = IosCookieJarSource(store)

    @Provides
    @SingleIn(AppScope::class)
    fun cookiePicker(): IosCookiePicker = IosCookiePicker()

    @Provides
    @SingleIn(AppScope::class)
    fun cookieFilePicker(picker: IosCookiePicker): CookieFilePicker =
        CookieFilePicker { picker.pick() }

    @Provides
    @SingleIn(AppScope::class)
    fun thumbnailLoader(): ThumbnailLoader = ThumbnailLoader { null }

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyOptional(service: SpotifyDownloadService): SpotifyDownloadService? = service
}

/** Documents-rooted sandbox path, wrapped so it is one unique binding. */
internal class IosSandboxRoot(val value: String)

/** The one restored queue plus the startup warning. */
internal class IosRestoredQueue(
    val jobs: List<DownloadJob>,
    val warning: String?,
)

/** Reports the embedded Zipline QuickJS runtime as the Settings row (T-071). */
private class IosToolProbe(
  private val jsRuntime: com.anydownload.core.jsc.JsRuntime,
) : ToolProbe {
    override suspend fun probe(): com.anydownload.core.domain.ToolStatus =
        com.anydownload.core.domain.ToolStatus(
            jsRuntime = com.anydownload.core.domain.ToolAvailability(
                available = jsRuntime.available,
                version = if (jsRuntime.available) "${jsRuntime.name} ${jsRuntime.version ?: ""} (embedded)".trim() else null,
            ),
        )
}
