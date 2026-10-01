/*
 * Android Metro graph — AnyDownload
 *
 * T-117: the Android host assembly becomes a Metro `@DependencyGraph`. The
 * factory takes the `Context` and a `ChaquopyPort` (default [NoChaquopyPort]).
 * One `JavaNetHttpTransfer`, one `QuickJsRuntime`, and the shared
 * `ExtractorRegistry` feed the routing engine, previews, and the Spotify
 * helper. Unmatched URLs still route to Chaquopy; when the port is missing
 * they fail typed with the engine-unavailable error and no Python runs.
 *
 * `@DependencyGraph` stays out of `com.anydownlod.android.engine`; that
 * package is compiled by `:apps:android-engine-tests` on the JVM.
 */
package com.anydownlod.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anydownlod.android.engine.AndroidJobDocumentStorage
import com.anydownlod.android.engine.AndroidRoute
import com.anydownlod.android.engine.AndroidRouteClassifier
import com.anydownlod.android.engine.AndroidRoutingEngine
import com.anydownlod.android.engine.ChaquopyEngine
import com.anydownlod.android.engine.ChaquopyPort
import com.anydownlod.android.engine.cookies.AndroidCookieJarSource
import com.anydownlod.android.engine.cookies.AndroidCookieStore
import com.anydownlod.android.engine.AndroidSubscriptionDocumentStorage
import com.anydownlod.android.engine.media.AndroidMediaToolkit
import com.anydownlod.android.media.AndroidPlatformMuxer
import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieFilePicker
import com.anydownlod.core.CookieStore
import com.anydownlod.core.SharedLinkInbox
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.FileOpener
import com.anydownlod.core.FileRevealer
import com.anydownlod.core.FolderPicker
import com.anydownlod.core.ThumbnailLoader
import com.anydownlod.core.UrlOpener
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.cookies.CookieJarSource
import com.anydownlod.core.di.SharedEngineBindings
import com.anydownlod.ui.add.AddFormBindings
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.ToolAvailability
import com.anydownlod.core.domain.ToolStatus
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
import com.anydownlod.core.persist.JobDocumentStore
import com.anydownlod.core.persist.PersistedSubscriptionRepository
import com.anydownlod.core.persist.PersistingDownloadEngine
import com.anydownlod.core.subscriptions.RegistrySubscriptionEntrySource
import com.anydownlod.core.subscriptions.ScanningSubscriptionRepository
import com.anydownlod.core.subscriptions.SubscriptionScanner
import com.anydownlod.core.subscriptions.enqueueSubscriptionEntry
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.JavaNetFileStore
import com.anydownlod.core.platform.JavaNetHttpTransfer
import com.anydownlod.core.postprocess.ToolkitCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class, AddFormBindings::class])
internal interface AndroidAppGraph : ViewModelGraph, AppGraph {

    override val engine: DownloadEngine
    override val subscriptions: SubscriptionRepository
    override val settings: SettingsRepository
    override val toolProbe: ToolProbe

    // Core AppGraph gives these properties default getters. Metro does not
    // generate accessors for overrides of default properties, so the host
    // accessors carry the Android bindings and the concrete overrides read
    // them (the desktop graph hit the same Metro behavior in T-116).
    val hostPreviews: MediaPreviewSource
    val hostStartupWarning: String?
    val hostToolkitCapabilities: ToolkitCapabilities
    val hostSpotify: SpotifyDownloadService
    val hostOpenUrl: (String) -> Unit
    val hostSharedLinkInbox: SharedLinkInbox

    override val previews: MediaPreviewSource get() = hostPreviews
    override val startupWarning: String? get() = hostStartupWarning
    override val toolkitCapabilities: ToolkitCapabilities get() = hostToolkitCapabilities
    override val spotify: SpotifyDownloadService? get() = hostSpotify
    override val openUrl: (String) -> Unit get() = hostOpenUrl
    override val sharedLinkInbox: SharedLinkInbox? get() = hostSharedLinkInbox

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides context: Context,
            @Provides port: ChaquopyPort = NoChaquopyPort,
            @Provides cookiePicker: AndroidCookiePickerBridge = AndroidCookiePickerBridge.Unavailable,
            @Provides sharedLinkInbox: SharedLinkInbox = SharedLinkInbox(),
        ): AndroidAppGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun httpTransfer(): HttpTransfer = JavaNetHttpTransfer()

    @Provides
    @SingleIn(AppScope::class)
    fun jsRuntime(): JsRuntime = QuickJsRuntime()

    @Provides
    @SingleIn(AppScope::class)
    fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun settings(context: Context): SettingsRepository =
        InMemorySettingsRepository(AppSettings(downloadRoot = context.filesDir.absolutePath))

    @Provides
    @SingleIn(AppScope::class)
    fun classifier(registry: ExtractorRegistry): AndroidRouteClassifier =
        AndroidRouteClassifier(registry = registry)

    @Provides
    @SingleIn(AppScope::class)
    fun toolkit(): AndroidMediaToolkit = AndroidMediaToolkit(AndroidPlatformMuxer())

    @Provides
    @SingleIn(AppScope::class)
    fun toolProbe(port: ChaquopyPort, jsRuntime: JsRuntime): ToolProbe =
        AndroidToolProbe(port, jsRuntime)

    @Provides
    @SingleIn(AppScope::class)
    fun jobDocumentStore(): JobDocumentStore = JobDocumentStore()

    @Provides
    @SingleIn(AppScope::class)
    fun jobStorage(context: Context): AndroidJobDocumentStorage =
        AndroidJobDocumentStorage(
            File(context.getDir("state", Context.MODE_PRIVATE), "jobs.json"),
        )

    /**
     * One restore, split by route. A corrupt document yields empty seeds and
     * a startup warning; interrupted active rows come back from the document
     * as failed and retryable.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun restoredJobs(
        jobStorage: AndroidJobDocumentStorage,
        jobDocumentStore: JobDocumentStore,
        settings: SettingsRepository,
        registry: ExtractorRegistry,
    ): AndroidRestoredJobs {
        val restoreResult = try {
            jobDocumentStore.restore(jobStorage, settings.settings.value.clearCompletedAfterSeconds)
        } catch (failure: Exception) {
            null
        }
        val jobs = restoreResult?.jobs ?: emptyList()
        return AndroidRestoredJobs(
            httpJobs = jobs.filter {
                AndroidRouteClassifier.resumeRoute(it.request.sourceUrl, registry) != AndroidRoute.CHAQUOPY
            },
            chaquopyJobs = jobs.filter {
                AndroidRouteClassifier.resumeRoute(it.request.sourceUrl, registry) == AndroidRoute.CHAQUOPY
            },
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
     * Both child engines persist after a mutation. The provider resolves the
     * persisting engine only when a write happens, after construction, which
     * breaks the constructor cycle.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun persist(persisting: Provider<PersistingDownloadEngine>): (List<DownloadJob>) -> Unit =
        { persisting().persistNow() }

    @Provides
    @SingleIn(AppScope::class)
    fun httpEngine(
        context: Context,
        transfer: HttpTransfer,
        settings: SettingsRepository,
        scope: CoroutineScope,
        persist: (List<DownloadJob>) -> Unit,
        registry: ExtractorRegistry,
        toolkit: AndroidMediaToolkit,
        cookieJarSource: CookieJarSource,
        restored: AndroidRestoredJobs,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = JavaNetFileStore(Path.of(context.filesDir.absolutePath)),
        settings = settings,
        scope = scope,
        ioDispatcher = Dispatchers.Default,
        registry = registry,
        toolkit = toolkit,
        cookieJarSource = cookieJarSource,
        persist = persist,
        seedJobs = restored.httpJobs,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun chaquopyEngine(
        context: Context,
        port: ChaquopyPort,
        scope: CoroutineScope,
        persist: (List<DownloadJob>) -> Unit,
        restored: AndroidRestoredJobs,
    ): ChaquopyEngine = ChaquopyEngine(
        port = port,
        downloadRoot = { context.filesDir.absolutePath },
        scope = scope,
        ioDispatcher = Dispatchers.Default,
        persist = persist,
        seedJobs = restored.chaquopyJobs,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun routingEngine(
        http: HttpDownloadEngine,
        chaquopy: ChaquopyEngine,
        classifier: AndroidRouteClassifier,
        scope: CoroutineScope,
    ): AndroidRoutingEngine = AndroidRoutingEngine(
        http = http,
        chaquopy = chaquopy,
        classify = { url -> classifier.route(url) },
        scope = scope,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun downloadEngine(routing: AndroidRoutingEngine): DownloadEngine = routing

    @Provides
    @SingleIn(AppScope::class)
    fun persistingEngine(
        routing: AndroidRoutingEngine,
        http: HttpDownloadEngine,
        chaquopy: ChaquopyEngine,
        documentStore: JobDocumentStore,
        jobStorage: AndroidJobDocumentStorage,
        settings: SettingsRepository,
    ): PersistingDownloadEngine = PersistingDownloadEngine(
        delegate = routing,
        documentStore = documentStore,
        writeDocument = jobStorage::write,
        clearAfterSeconds = { settings.settings.value.clearCompletedAfterSeconds },
        // The routing flow merges asynchronously; the child engines' values
        // are synchronous after a mutation.
        jobsSnapshot = { chaquopy.jobs.value + http.jobs.value },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun subscriptions(
        context: Context,
        registry: ExtractorRegistry,
        scope: CoroutineScope,
        engine: Provider<DownloadEngine>,
    ): SubscriptionRepository {
        val storage = AndroidSubscriptionDocumentStorage(
            File(context.getDir("state", Context.MODE_PRIVATE), "subscriptions.json"),
        )
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
    fun startupWarning(restored: AndroidRestoredJobs): String? = restored.warning

    @Provides
    @SingleIn(AppScope::class)
    fun toolkitCapabilities(toolkit: AndroidMediaToolkit): ToolkitCapabilities =
        toolkit.capabilities()

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
    fun openUrl(context: Context): (String) -> Unit = { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun urlOpener(openUrl: (String) -> Unit): UrlOpener = UrlOpener { openUrl(it) }

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
    fun cookieStore(context: Context): AndroidCookieStore = AndroidCookieStore(
        stateDirectory = Path.of(context.filesDir.absolutePath),
        importDirectory = Path.of(context.cacheDir.absolutePath),
    )

    @Provides
    @SingleIn(AppScope::class)
    fun cookieStoreBinding(store: AndroidCookieStore): CookieStore = store

    @Provides
    @SingleIn(AppScope::class)
    fun cookieJarSource(store: AndroidCookieStore): CookieJarSource = AndroidCookieJarSource(store)

    @Provides
    @SingleIn(AppScope::class)
    fun cookieFilePicker(bridge: AndroidCookiePickerBridge): CookieFilePicker =
        CookieFilePicker { bridge.pick() }

    @Provides
    @SingleIn(AppScope::class)
    fun thumbnailLoader(): ThumbnailLoader = ThumbnailLoader { null }

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyOptional(service: SpotifyDownloadService): SpotifyDownloadService? = service
}

/** The restored rows, already split by route, plus the startup warning. */
internal class AndroidRestoredJobs(
    val httpJobs: List<DownloadJob>,
    val chaquopyJobs: List<DownloadJob>,
    val warning: String?,
)

/** Runtime absent by default; a build with Chaquopy wires its real port here. */
object NoChaquopyPort : ChaquopyPort {
    override val available: Boolean = false
    override suspend fun runDownload(request: DownloadRequest, downloadRoot: String) =
        ChaquopyPort.resultUnavailable()
}

/** Reports the embedded pinned yt-dlp availability as the Settings tool row. */
private class AndroidToolProbe(
    private val port: ChaquopyPort,
    private val jsRuntime: com.anydownlod.core.jsc.JsRuntime,
) : ToolProbe {
    override suspend fun probe(): ToolStatus = ToolStatus(
        ytDlp = ToolAvailability(available = port.available, version = "pinned yt-dlp ${ChaquopyPort.pinnedVersion}"),
        ffmpeg = ToolAvailability(available = false),
        jsRuntime = ToolAvailability(
            available = jsRuntime.available,
            version = if (jsRuntime.available) "${jsRuntime.name} ${jsRuntime.version ?: ""} (embedded)".trim() else null,
        ),
    )
}
