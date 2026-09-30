/*
 * Desktop Metro graph — AnyDownload
 *
 * T-116: the desktop host assembly moves out of `DesktopApp.open` and into a
 * Metro `@DependencyGraph` in the host source set. The factory keeps the five
 * runtime inputs. One `ExtractorRegistry` (from SharedEngineBindings) feeds
 * previews, the HTTP engine, the CLI routing classifier, and Spotify. One
 * `DesktopSpotifyTokenStore` serves the library and the auth service.
 *
 * `apps/desktop` is the only module that starts processes; nothing here
 * changes what the engines do.
 */
package com.anydownlod.desktop

import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieFilePicker
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.FileOpener
import com.anydownlod.core.FileRevealer
import com.anydownlod.core.FolderPicker
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.ThumbnailLoader
import com.anydownlod.core.UrlOpener
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.di.SharedEngineBindings
import com.anydownlod.ui.add.AddFormBindings
import com.anydownlod.core.domain.Artifact
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
import com.anydownlod.core.music.AudioProviders
import com.anydownlod.core.music.LyricsFetcher
import com.anydownlod.core.music.SpotifyAuthService
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyLibraryClient
import com.anydownlod.core.music.SpotifyMetadataClients
import com.anydownlod.core.music.SpotifyTokenStore
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.JavaNetHttpTransfer
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.DesktopFfmpegToolkit
import com.anydownlod.desktop.engine.DesktopFileStore
import com.anydownlod.desktop.engine.DesktopPreviewSource
import com.anydownlod.desktop.engine.DesktopRoute
import com.anydownlod.desktop.engine.DesktopRouteClassifier
import com.anydownlod.desktop.engine.DesktopRoutingEngine
import com.anydownlod.desktop.engine.DesktopSpotifyListStore
import com.anydownlod.desktop.engine.DownloadPaths
import com.anydownlod.desktop.engine.PathToolProbe
import com.anydownlod.desktop.engine.ThumbnailBytes
import com.anydownlod.desktop.engine.YtDlpCliEngine
import com.anydownlod.desktop.store.DesktopCookieStore
import com.anydownlod.desktop.store.DesktopSpotifyTokenStore
import com.anydownlod.desktop.store.DesktopStore
import com.anydownlod.desktop.store.PersistedState
import com.anydownlod.desktop.store.PersistingSettingsRepository
import com.anydownlod.desktop.subscriptions.DesktopSubscriptionRepository
import com.anydownlod.desktop.subscriptions.SubscriptionScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class, AddFormBindings::class])
internal interface DesktopGraph : ViewModelGraph, AppGraph {

    override val engine: DownloadEngine
    override val subscriptions: SubscriptionRepository
    override val settings: SettingsRepository
    override val toolProbe: ToolProbe

    // Core AppGraph gives these six properties default getters. Metro does not
    // generate accessors for overrides of default properties, so the host
    // accessors below carry the desktop bindings and the concrete overrides
    // read them.
    val hostPreviews: MediaPreviewSource
    val hostStartupWarning: String?
    val hostCookieStore: CookieStore
    val hostToolkitCapabilities: ToolkitCapabilities
    val hostSpotify: SpotifyDownloadService
    val hostSpotifyAuth: SpotifyAuthService

    override val previews: MediaPreviewSource get() = hostPreviews
    override val startupWarning: String? get() = hostStartupWarning
    override val cookieStore: CookieStore get() = hostCookieStore
    override val toolkitCapabilities: ToolkitCapabilities get() = hostToolkitCapabilities
    override val spotify: SpotifyDownloadService? get() = hostSpotify
    override val spotifyAuth: SpotifyAuthService? get() = hostSpotifyAuth

    /** Host entry-point accessors: the store to save, the CLI scope for
     * shutdown, and the one registry so the preview-identity test compares
     * instances. */
    val store: DesktopStore
    val cliEngine: YtDlpCliEngine
    val scope: CoroutineScope
    val extractorRegistry: ExtractorRegistry
    val ioDispatcher: CoroutineDispatcher

    // These properties are the only colliding function-type keys, so they are
    // default getters over the abstract accessors instead of bindings.
    override val openUrl: (String) -> Unit
        get() = { url -> openInBrowser(url) }
    override val openFile: (Artifact) -> Unit
        get() = { artifact -> openArtifact(settings, artifact, reveal = false) }
    override val revealFile: (Artifact) -> Unit
        get() = { artifact -> openArtifact(settings, artifact, reveal = true) }
    override val pickFolder: () -> String?
        get() = { chooseDirectory() }
    override val pickCookieFile: () -> String?
        get() = { chooseCookieFile() }
    override val loadThumbnail: suspend (String) -> ByteArray?
        get() = { url -> withContext(ioDispatcher) { ThumbnailBytes.fetch(url) } }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides stateDirectory: Path,
            @Provides defaultDownloadRoot: DesktopDefaultDownloadRoot,
            @Provides processRunner: CliProcessRunner,
            @Provides resolveExecutable: (String) -> String?,
            @Provides ioDispatcher: CoroutineDispatcher,
        ): DesktopGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun httpTransfer(): HttpTransfer = JavaNetHttpTransfer()

    @Provides
    @SingleIn(AppScope::class)
    fun jsRuntime(): JsRuntime = QuickJsRuntime()

    @Provides
    @SingleIn(AppScope::class)
    fun scope(ioDispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + ioDispatcher)

    @Provides
    @SingleIn(AppScope::class)
    fun store(
        stateDirectory: Path,
        defaultDownloadRoot: DesktopDefaultDownloadRoot,
    ): DesktopStore = DesktopStore(stateDirectory, defaultDownloadRoot = defaultDownloadRoot.value)

    @Provides
    @SingleIn(AppScope::class)
    fun persisted(store: DesktopStore): PersistedState = store.load()

    @Provides
    @SingleIn(AppScope::class)
    fun settings(store: DesktopStore, persisted: PersistedState): SettingsRepository =
        PersistingSettingsRepository(
            delegate = InMemorySettingsRepository(persisted.settings),
            store = store,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun startupWarning(store: DesktopStore, persisted: PersistedState): String? = store.loadWarning

    @Provides
    @SingleIn(AppScope::class)
    fun classifier(registry: ExtractorRegistry): DesktopRouteClassifier =
        DesktopRouteClassifier(registry = registry)

    @Provides
    @SingleIn(AppScope::class)
    fun toolkit(
        processRunner: CliProcessRunner,
        resolveExecutable: (String) -> String?,
    ): DesktopFfmpegToolkit = DesktopFfmpegToolkit(
        runner = processRunner,
        resolveExecutable = resolveExecutable,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun toolkitCapabilities(toolkit: DesktopFfmpegToolkit): ToolkitCapabilities =
        toolkit.capabilities()

    @Provides
    @SingleIn(AppScope::class)
    fun toolProbe(
        processRunner: CliProcessRunner,
        resolveExecutable: (String) -> String?,
        jsRuntime: JsRuntime,
    ): ToolProbe = PathToolProbe(processRunner, resolveExecutable, jsRuntime = jsRuntime)

    /**
     * Both engines persist the merged list so one engine can never drop the
     * other's rows. The provider breaks the routing-engine cycle: the lambda
     * resolves the singleton only when a write happens, after construction.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun persistAll(
        store: DesktopStore,
        routing: Provider<DesktopRoutingEngine>,
    ): (List<DownloadJob>) -> Unit = { store.saveJobs(routing().jobs.value) }

    @Provides
    @SingleIn(AppScope::class)
    fun cliEngine(
        settings: SettingsRepository,
        store: DesktopStore,
        scope: CoroutineScope,
        processRunner: CliProcessRunner,
        resolveExecutable: (String) -> String?,
        ioDispatcher: CoroutineDispatcher,
        persist: (List<DownloadJob>) -> Unit,
        extractorRegistry: ExtractorRegistry,
        persisted: PersistedState,
    ): YtDlpCliEngine = YtDlpCliEngine(
        settingsRepository = settings,
        scope = scope,
        runner = processRunner,
        resolveExecutable = resolveExecutable,
        ioDispatcher = ioDispatcher,
        persist = persist,
        cookieFilePath = { store.cookieFilePath() },
        seedJobs = persisted.jobs.filter {
            DesktopRouteClassifier.resumeRoute(it.request.sourceUrl, extractorRegistry) ==
                DesktopRoute.YTDLP_CLI
        },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun httpEngine(
        transfer: HttpTransfer,
        settings: SettingsRepository,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
        persist: (List<DownloadJob>) -> Unit,
        extractorRegistry: ExtractorRegistry,
        toolkit: DesktopFfmpegToolkit,
        persisted: PersistedState,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
        settings = settings,
        scope = scope,
        ioDispatcher = ioDispatcher,
        persist = persist,
        registry = extractorRegistry,
        toolkit = toolkit,
        seedJobs = persisted.jobs.filter {
            DesktopRouteClassifier.resumeRoute(it.request.sourceUrl, extractorRegistry) !=
                DesktopRoute.YTDLP_CLI
        },
    )

    @Provides
    @SingleIn(AppScope::class)
    fun routingEngine(
        http: HttpDownloadEngine,
        cli: YtDlpCliEngine,
        classifier: DesktopRouteClassifier,
        scope: CoroutineScope,
    ): DesktopRoutingEngine = DesktopRoutingEngine(
        http = http,
        cli = cli,
        classify = { url -> classifier.route(url) },
        scope = scope,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun downloadEngine(routing: DesktopRoutingEngine): DownloadEngine = routing

    @Provides
    @SingleIn(AppScope::class)
    fun subscriptions(
        store: DesktopStore,
        engine: DownloadEngine,
        settings: SettingsRepository,
        scope: CoroutineScope,
        processRunner: CliProcessRunner,
        resolveExecutable: (String) -> String?,
        ioDispatcher: CoroutineDispatcher,
        persisted: PersistedState,
    ): SubscriptionRepository {
        val repository = DesktopSubscriptionRepository(
            delegate = InMemorySubscriptionRepository(seedSubscriptions = persisted.subscriptions),
            store = store,
            engine = engine,
            settings = settings,
            scope = scope,
            runner = processRunner,
            resolveExecutable = resolveExecutable,
            ioDispatcher = ioDispatcher,
        )
        SubscriptionScheduler(repository = repository, scope = scope).start()
        return repository
    }

    @Provides
    @SingleIn(AppScope::class)
    fun cookieStore(store: DesktopStore): CookieStore = DesktopCookieStore(store)

    @Provides
    @SingleIn(AppScope::class)
    fun previews(
        registry: ExtractorRegistry,
        processRunner: CliProcessRunner,
        resolveExecutable: (String) -> String?,
        settings: SettingsRepository,
    ): MediaPreviewSource {
        val workingDirectory = {
            val root = settings.settings.value.downloadRoot
            val candidate = root.takeIf { it.isNotBlank() }?.let { runCatching { Path.of(it) }.getOrNull() }
            if (candidate != null && Files.isDirectory(candidate)) candidate
            else Path.of(System.getProperty("java.io.tmpdir"))
        }
        return DesktopPreviewSource.create(
            registry = registry,
            runner = processRunner,
            resolveExecutable = resolveExecutable,
            workingDirectory = workingDirectory,
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyTokenStore(stateDirectory: Path): SpotifyTokenStore =
        DesktopSpotifyTokenStore(stateDirectory)

    @Provides
    @SingleIn(AppScope::class)
    fun spotify(
        extractorHttp: ExtractorHttp,
        engine: DownloadEngine,
        settings: SettingsRepository,
        extractorRegistry: ExtractorRegistry,
        tokenStore: SpotifyTokenStore,
    ): SpotifyDownloadService = SpotifyDownloadService(
        metadata = SpotifyMetadataClients.default(extractorHttp),
        matcher = AudioMatcher.withFallbacks(
            search = YoutubeSearch(extractorHttp),
            fallbacks = AudioProviders.fallbacks(
                extractorHttp,
                settings.settings.value.spotifyFallbackProviders,
            ),
            canDownload = { url ->
                extractorRegistry.suitableFor(url) != null || isDirectMediaUrl(url)
            },
        ),
        engine = engine,
        listStore = DesktopSpotifyListStore { settings.settings.value.downloadRoot },
        lyrics = LyricsFetcher.default(extractorHttp),
        library = SpotifyLibraryClient(
            http = extractorHttp,
            tokenStore = tokenStore,
        ),
    )

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyAuth(
        tokenStore: SpotifyTokenStore,
        extractorHttp: ExtractorHttp,
    ): SpotifyAuthService = SpotifyAuthService(
        tokenStore = tokenStore,
        http = extractorHttp,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyOptional(service: SpotifyDownloadService): SpotifyDownloadService? = service

    @Provides
    @SingleIn(AppScope::class)
    fun spotifyAuthOptional(auth: SpotifyAuthService): SpotifyAuthService? = auth

    @Provides
    @SingleIn(AppScope::class)
    fun urlOpener(): UrlOpener = UrlOpener { url -> openInBrowser(url) }

    @Provides
    @SingleIn(AppScope::class)
    fun fileOpener(settings: SettingsRepository): FileOpener =
        FileOpener { artifact -> openArtifact(settings, artifact, reveal = false) }

    @Provides
    @SingleIn(AppScope::class)
    fun fileRevealer(settings: SettingsRepository): FileRevealer =
        FileRevealer { artifact -> openArtifact(settings, artifact, reveal = true) }

    @Provides
    @SingleIn(AppScope::class)
    fun folderPicker(): FolderPicker = FolderPicker { chooseDirectory() }

    @Provides
    @SingleIn(AppScope::class)
    fun cookieFilePicker(): CookieFilePicker = CookieFilePicker { chooseCookieFile() }

    @Provides
    @SingleIn(AppScope::class)
    fun thumbnailLoader(ioDispatcher: CoroutineDispatcher): ThumbnailLoader =
        ThumbnailLoader { url -> withContext(ioDispatcher) { ThumbnailBytes.fetch(url) } }
}

/**
 * T-116: Metro treats parameter-less Kotlin function types as provider
 * intrinsics when `enableFunctionProviders` is on, so the factory cannot take
 * `defaultDownloadRoot: () -> String` directly. This wrapper carries the same
 * lambda and is the one factory input that changed shape.
 */
internal class DesktopDefaultDownloadRoot(val value: () -> String)

/** Media extensions the engine can download without an extractor. */
private val SPOTIFY_MEDIA_EXTENSIONS = setOf(
    "mp3", "m4a", "opus", "ogg", "wav", "flac", "mp4", "webm",
)

private fun isDirectMediaUrl(url: String): Boolean =
    url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase() in SPOTIFY_MEDIA_EXTENSIONS

private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

/** Opens or reveals a registered artifact, refusing paths outside the root. */
private fun openArtifact(settingsRepository: SettingsRepository, artifact: Artifact, reveal: Boolean) {
    val root = settingsRepository.settings.value.downloadRoot
    if (root.isBlank()) return
    val path = runCatching { DownloadPaths.artifactPath(Path.of(root), artifact.relativePath) }.getOrNull() ?: return
    if (!Files.isRegularFile(path)) return
    runCatching {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (reveal && desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            desktop.browseFileDirectory(path.toFile())
        } else {
            desktop.open(path.toFile())
        }
    }
}

private fun chooseDirectory(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Choose the download folder"
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private fun chooseCookieFile(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Choose a Netscape cookie file"
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}
