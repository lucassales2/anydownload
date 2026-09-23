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
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.DownloadPaths
import com.anydownlod.desktop.engine.ExecutableOnPath
import com.anydownlod.desktop.engine.JavaCliProcessRunner
import com.anydownlod.desktop.engine.PathToolProbe
import com.anydownlod.desktop.engine.YtDlpCliEngine
import com.anydownlod.desktop.store.DesktopCookieStore
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
        state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
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
            val engine = YtDlpCliEngine(
                settingsRepository = settings,
                scope = scope,
                runner = processRunner,
                resolveExecutable = resolveExecutable,
                ioDispatcher = ioDispatcher,
                persist = { jobs -> store.saveJobs(jobs) },
                cookieFilePath = { store.cookieFilePath() },
                seedJobs = persisted.jobs,
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
            return DesktopApp(
                store = store,
                graph = desktopGraph(
                    downloadEngine = engine,
                    subscriptionRepository = subscriptions,
                    settingsRepository = settings,
                    toolProbe = PathToolProbe(processRunner, resolveExecutable),
                    cookieStore = cookieStore,
                    startupWarning = store.loadWarning,
                ),
                shutdownEngine = {
                    engine.shutdown()
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
): AppGraph = object : AppGraph {
    override val engine: DownloadEngine = downloadEngine
    override val subscriptions: SubscriptionRepository = subscriptionRepository
    override val settings: SettingsRepository = settingsRepository
    override val toolProbe: ToolProbe = toolProbe
    override val cookieStore: CookieStore = cookieStore
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
