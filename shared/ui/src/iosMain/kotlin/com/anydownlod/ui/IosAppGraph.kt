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
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.persist.JobDocumentRestore
import com.anydownlod.core.persist.JobDocumentStore
import com.anydownlod.core.persist.PersistingDownloadEngine
import com.anydownlod.core.platform.IosFileStore
import com.anydownlod.core.platform.IosHttpTransfer
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.ui.media.IosMediaToolkit
import com.anydownlod.ui.persist.IosJobDocumentStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSApplicationSupportDirectory
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
    private val extractorRegistry = IosExtractors.registry(transfer, jsRuntime)

    // D5: AVFoundation passthrough remux. Capabilities stay M4A/Opus copy-only.
    private val toolkit = IosMediaToolkit()

    // D8: the shared jobs document under Application Support, separate from
    // the Documents download root. Foreground only; no background transfer.
    private val stateRoot: String =
        (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)
            ?.takeIf { it.isNotEmpty() }
            ?.let { "$it/AnyDownload" }
            ?: (NSTemporaryDirectory().trimEnd('/') + "/AnyDownload")

    private val jobStorage = IosJobDocumentStorage("$stateRoot/jobs.json")
    private val jobDocumentStore = JobDocumentStore()
    private val restoreResult: JobDocumentRestore? = try {
        jobDocumentStore.restore(jobStorage, settingsRepository.settings.value.clearCompletedAfterSeconds)
    } catch (failure: Exception) {
        null
    }

    /** Assigned in init; the engine's synchronous persist callback needs it. */
    private lateinit var persistingEngine: PersistingDownloadEngine

    private fun saveJobs() {
        if (::persistingEngine.isInitialized) persistingEngine.persistNow()
    }

    override val engine: DownloadEngine get() = persistingEngine

    override val startupWarning: String? = when {
        restoreResult == null ->
            "The saved queue could not be read. It will be replaced when the queue changes."

        restoreResult.interruptedActive > 0 ->
            "Marked ${restoreResult.interruptedActive} interrupted job(s) for retry."

        else -> null
    }

    init {
        val httpEngine = HttpDownloadEngine(
            transfer = transfer,
            fileStore = IosFileStore(sandboxRoot),
            settings = settingsRepository,
            scope = scope,
            registry = extractorRegistry,
            toolkit = toolkit,
            persist = { saveJobs() },
            seedJobs = restoreResult?.jobs ?: emptyList(),
        )
        persistingEngine = PersistingDownloadEngine(
            delegate = httpEngine,
            documentStore = jobDocumentStore,
            writeDocument = jobStorage::write,
            clearAfterSeconds = { settingsRepository.settings.value.clearCompletedAfterSeconds },
        )
        // Persist the normalized rows (interrupted jobs) right away, like desktop.
        persistingEngine.persistNow()
    }

    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository()
    override val settings: SettingsRepository = settingsRepository

    // D6: Spotify metadata, matching, and queueing through the shared engine.
    // The user library needs an on-device token store, which is a mobile gap.
    override val spotify: com.anydownlod.core.music.SpotifyDownloadService =
        com.anydownlod.core.music.SpotifyDownloadService(
            metadata = com.anydownlod.core.music.SpotifyMetadataClients.default(ExtractorHttp(transfer)),
            matcher = com.anydownlod.core.music.AudioMatcher.default(YoutubeSearch(ExtractorHttp(transfer))),
            engine = engine,
        )

    override val toolProbe: ToolProbe = IosToolProbe(jsRuntime)
    override val previews: MediaPreviewSource = ExtractorMediaPreviewSource(extractorRegistry)
    override val cookieStore: CookieStore = CookieStore.Unavailable
    override val toolkitCapabilities: ToolkitCapabilities = toolkit.capabilities()
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