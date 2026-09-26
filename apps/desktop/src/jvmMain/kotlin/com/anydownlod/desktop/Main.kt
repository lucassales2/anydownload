package com.anydownlod.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.CompositeMediaPreviewSource
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.music.AudioMatcher
import com.anydownlod.core.music.AudioProviders
import com.anydownlod.core.music.LyricsFetcher
import com.anydownlod.core.music.SpotifyAuthService
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyLibraryClient
import com.anydownlod.core.music.SpotifyMetadataClients
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.platform.JavaNetHttpTransfer
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.DesktopFfmpegToolkit
import com.anydownlod.desktop.engine.DesktopFileStore
import com.anydownlod.desktop.engine.DesktopRoute
import com.anydownlod.desktop.engine.DesktopRouteClassifier
import com.anydownlod.desktop.engine.DesktopPreviewSource
import com.anydownlod.desktop.engine.DesktopRoutingEngine
import com.anydownlod.desktop.engine.DesktopSpotifyListStore
import com.anydownlod.desktop.engine.DownloadPaths
import com.anydownlod.desktop.engine.ExecutableOnPath
import com.anydownlod.desktop.engine.JavaCliProcessRunner
import com.anydownlod.desktop.engine.PathToolProbe
import com.anydownlod.desktop.engine.ThumbnailBytes
import com.anydownlod.desktop.engine.YtDlpCliEngine
import com.anydownlod.desktop.store.DesktopCookieStore
import com.anydownlod.desktop.store.DesktopSpotifyTokenStore
import com.anydownlod.desktop.store.DesktopStore
import com.anydownlod.desktop.store.PersistingSettingsRepository
import com.anydownlod.desktop.store.defaultDownloadRootPath
import com.anydownlod.desktop.store.defaultStateDirectory
import com.anydownlod.desktop.subscriptions.DesktopSubscriptionRepository
import com.anydownlod.desktop.subscriptions.SubscriptionScheduler
import com.anydownlod.ui.App
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

fun main() = application {
    val desktop = remember { DesktopApp.open() }
    Window(
        onCloseRequest = {
            desktop.close()
            exitApplication()
        },
        title = "AnyDownload",
        state = rememberWindowState(size = DpSize(1280.dp, 860.dp)),
    ) {
        App(desktop.graph)
    }
}

/**
 * The desktop host: JSON store, yt-dlp engine, and the graph the shared
 * screens consume. `apps/desktop` is the only module that starts processes.
 */
internal class DesktopApp(
    val store: DesktopStore,
    val graph: AppGraph,
    private val shutdownEngine: () -> Unit,
) {
    /** Destroys any live process, then persists the final state. */
    fun close() {
        shutdownEngine()
        store.saveAll(
            jobs = graph.engine.jobs.value,
            subscriptions = graph.subscriptions.subscriptions.value,
            settings = graph.settings.settings.value,
        )
    }

    companion object {
        fun open(
            stateDirectory: Path = defaultStateDirectory(),
            defaultDownloadRoot: () -> String = { defaultDownloadRootPath() },
            processRunner: CliProcessRunner = JavaCliProcessRunner,
            resolveExecutable: (String) -> String? = ExecutableOnPath::find,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): DesktopApp {
            val store = DesktopStore(stateDirectory, defaultDownloadRoot = defaultDownloadRoot)
            val persisted = store.load()
            val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
            val settings = PersistingSettingsRepository(
                delegate = InMemorySettingsRepository(persisted.settings),
                store = store,
            )

            // Each engine owns the jobs it creates; the router merges their
            // flows and routes every action to the owning engine. Both engines
            // persist the merged list so one engine can never drop the other's
            // rows in jobs.json.
            var routing: DesktopRoutingEngine? = null
            val persistAll: (List<DownloadJob>) -> Unit = { _ -> routing?.let { store.saveJobs(it.jobs.value) } }

            // D4: the shared Kotlin extractor owns matched URLs on desktop; the
            // installed CLI keeps every other URL. One transfer and registry
            // serve routing, downloads, and the startup job split.
            val transfer = com.anydownlod.core.platform.JavaNetHttpTransfer()
            val extractorHttp = ExtractorHttp(transfer)
            val jsRuntime = com.anydownlod.core.jsc.QuickJsRuntime()
            val extractorRegistry = ExtractorRegistry(
                listOf(YoutubeIE(extractorHttp, jsRuntime), TwitterIE(extractorHttp)),
            )
            val classifier = DesktopRouteClassifier(registry = extractorRegistry)
            val toolkit = DesktopFfmpegToolkit(runner = processRunner, resolveExecutable = resolveExecutable)

            val cliEngine = YtDlpCliEngine(
                settingsRepository = settings,
                scope = scope,
                runner = processRunner,
                resolveExecutable = resolveExecutable,
                ioDispatcher = ioDispatcher,
                persist = persistAll,
                cookieFilePath = { store.cookieFilePath() },
                seedJobs = persisted.jobs.filter {
                    DesktopRouteClassifier.resumeRoute(it.request.sourceUrl, extractorRegistry) ==
                        DesktopRoute.YTDLP_CLI
                },
            )
            val httpEngine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = ioDispatcher,
                persist = persistAll,
                registry = extractorRegistry,
                toolkit = toolkit,
                seedJobs = persisted.jobs.filter {
                    DesktopRouteClassifier.resumeRoute(it.request.sourceUrl, extractorRegistry) !=
                        DesktopRoute.YTDLP_CLI
                },
            )
            routing = DesktopRoutingEngine(
                http = httpEngine,
                cli = cliEngine,
                classify = { url -> classifier.route(url) },
                scope = scope,
            )
            val engine: DownloadEngine = routing
            val spotify = SpotifyDownloadService(
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
                    tokenStore = DesktopSpotifyTokenStore(stateDirectory),
                ),
            )
            val spotifyAuth = SpotifyAuthService(
                tokenStore = DesktopSpotifyTokenStore(stateDirectory),
                http = extractorHttp,
            )

            val subscriptions = DesktopSubscriptionRepository(
                delegate = InMemorySubscriptionRepository(seedSubscriptions = persisted.subscriptions),
                store = store,
                engine = engine,
                settings = settings,
                scope = scope,
                runner = processRunner,
                resolveExecutable = resolveExecutable,
                ioDispatcher = ioDispatcher,
            )
            SubscriptionScheduler(repository = subscriptions, scope = scope).start()
            val cookieStore = DesktopCookieStore(store)
            // Persist the normalized rows (interrupted jobs, default root).
            store.saveAll(
                jobs = engine.jobs.value,
                subscriptions = subscriptions.subscriptions.value,
                settings = settings.settings.value,
            )
            val previewDirectory = {
                val root = settings.settings.value.downloadRoot
                val candidate = root.takeIf { it.isNotBlank() }?.let { runCatching { Path.of(it) }.getOrNull() }
                if (candidate != null && Files.isDirectory(candidate)) candidate
                else Path.of(System.getProperty("java.io.tmpdir"))
            }
            return DesktopApp(
                store = store,
                graph = desktopGraph(
                    downloadEngine = engine,
                    subscriptionRepository = subscriptions,
                    settingsRepository = settings,
                    toolProbe = PathToolProbe(processRunner, resolveExecutable, jsRuntime = jsRuntime),
                    cookieStore = cookieStore,
                    startupWarning = store.loadWarning,
                    previews = DesktopPreviewSource.create(
                        runner = processRunner,
                        resolveExecutable = resolveExecutable,
                        workingDirectory = previewDirectory,
                        jsRuntime = jsRuntime,
                    ),
                    toolkitCapabilities = toolkit.capabilities(),
                    spotify = spotify,
                    spotifyAuth = spotifyAuth,
                    loadThumbnail = { url -> withContext(ioDispatcher) { ThumbnailBytes.fetch(url) } },
                ),
                shutdownEngine = {
                    cliEngine.shutdown()
                    scope.cancel()
                },
            )
        }
    }
}

private fun desktopGraph(
    downloadEngine: DownloadEngine,
    subscriptionRepository: SubscriptionRepository,
    settingsRepository: SettingsRepository,
    toolProbe: ToolProbe,
    cookieStore: CookieStore,
    startupWarning: String?,
    previews: MediaPreviewSource,
    toolkitCapabilities: ToolkitCapabilities,
    spotify: SpotifyDownloadService?,
    spotifyAuth: SpotifyAuthService?,
    loadThumbnail: suspend (String) -> ByteArray?,
): AppGraph = object : AppGraph {
    override val engine: DownloadEngine = downloadEngine
    override val subscriptions: SubscriptionRepository = subscriptionRepository
    override val settings: SettingsRepository = settingsRepository
    override val toolProbe: ToolProbe = toolProbe
    override val cookieStore: CookieStore = cookieStore
    override val previews: MediaPreviewSource = previews
    override val toolkitCapabilities: ToolkitCapabilities = toolkitCapabilities
    override val spotify: SpotifyDownloadService? = spotify
    override val spotifyAuth: SpotifyAuthService? = spotifyAuth
    override val loadThumbnail: suspend (String) -> ByteArray? = loadThumbnail
    override val openUrl: (String) -> Unit = { url -> openInBrowser(url) }
    override val openFile: (Artifact) -> Unit = { artifact ->
        openArtifact(settingsRepository, artifact, reveal = false)
    }
    override val revealFile: (Artifact) -> Unit = { artifact ->
        openArtifact(settingsRepository, artifact, reveal = true)
    }
    override val pickFolder: () -> String? = { chooseDirectory() }
    override val pickCookieFile: () -> String? = { chooseCookieFile() }
    override val startupWarning: String? = startupWarning
}

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
