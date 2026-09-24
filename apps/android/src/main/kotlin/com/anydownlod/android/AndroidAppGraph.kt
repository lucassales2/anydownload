package com.anydownlod.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anydownlod.android.engine.AndroidRouteClassifier
import com.anydownlod.android.engine.AndroidRoutingEngine
import com.anydownlod.android.engine.ChaquopyEngine
import com.anydownlod.android.engine.ChaquopyPort
import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.UnavailableMediaPreviewSource
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.ToolAvailability
import com.anydownlod.core.domain.ToolStatus
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.platform.JavaNetFileStore
import com.anydownlod.core.platform.JavaNetHttpTransfer
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The Android app graph: shared HTTP engine for direct files and the
 * Chaquopy adapter (pinned yt-dlp, `apps/android` only) for everything else.
 * Files land under app-scoped storage ([Context.filesDir]); the HTTP engine
 * streams in chunks and never loads a whole media file into memory.
 *
 * When [port] is unavailable, site URLs fail with an honest
 * “engine unavailable” error and no Python runs, so Add never pretends a
 * site URL started. Direct files work regardless.
 */
class AndroidAppGraph(
    context: Context,
    private val port: ChaquopyPort = NoChaquopyPort,
) : AppGraph {

    private val downloadRoot: String = context.filesDir.absolutePath
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val settingsRepository: SettingsRepository =
        InMemorySettingsRepository(AppSettings(downloadRoot = downloadRoot))

    // D4: the shared Kotlin extractor owns matched URLs; Chaquopy keeps the
    // rest. One transfer and registry serve routing, downloads, and previews.
    private val transfer = JavaNetHttpTransfer()
    private val jsRuntime = com.anydownlod.core.jsc.QuickJsRuntime()
    private val extractorRegistry = ExtractorRegistry(
        listOf(YoutubeIE(ExtractorHttp(transfer), jsRuntime)),
    )
    private val classifier = AndroidRouteClassifier(registry = extractorRegistry)

    override val engine: DownloadEngine = AndroidRoutingEngine(
        http = HttpDownloadEngine(
            transfer = transfer,
            fileStore = JavaNetFileStore(Path.of(downloadRoot)),
            settings = settingsRepository,
            scope = scope,
            ioDispatcher = Dispatchers.Default,
            registry = extractorRegistry,
        ),
        chaquopy = ChaquopyEngine(
            port = port,
            downloadRoot = { downloadRoot },
            scope = scope,
            ioDispatcher = Dispatchers.Default,
        ),
        classify = { url -> classifier.route(url) },
        scope = scope,
    )

    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository()
    override val settings: SettingsRepository = settingsRepository

    override val toolProbe: ToolProbe = AndroidToolProbe(port, jsRuntime)
    override val previews: MediaPreviewSource = ExtractorMediaPreviewSource(extractorRegistry)
    override val cookieStore: CookieStore = CookieStore.Unavailable

    override val openUrl: (String) -> Unit = { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

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