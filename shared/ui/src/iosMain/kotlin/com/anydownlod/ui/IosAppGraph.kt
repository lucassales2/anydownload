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
package com.anydownlod.ui

import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.di.SharedEngineBindings
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.jsc.QuickJsRuntime
import com.anydownlod.core.music.AudioMatcher
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyMetadataClients
import com.anydownlod.core.persist.JobDocumentRestore
import com.anydownlod.core.persist.JobDocumentStore
import com.anydownlod.core.persist.PersistingDownloadEngine
import com.anydownlod.core.platform.IosFileStore
import com.anydownlod.core.platform.IosHttpTransfer
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.ui.media.IosMediaToolkit
import com.anydownlod.ui.persist.IosJobDocumentStorage
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

@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class])
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
        restored: IosRestoredQueue,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = IosFileStore(sandboxRoot.value),
        settings = settings,
        scope = scope,
        registry = registry,
        toolkit = toolkit,
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
    fun subscriptions(): SubscriptionRepository = InMemorySubscriptionRepository()

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
