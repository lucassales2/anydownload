package com.anydownlod.web

import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.engine.WebExtensionEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.extract.youtube.YoutubeSearch
import com.anydownlod.core.music.AudioMatcher
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyMetadataClients
import com.anydownlod.core.platform.WebExtensionTransfer
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.fake.InMemoryToolProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The web graph: the shared UI plus [WebExtensionEngine] over the
 * [WindowExtensionBridge]. The page never fetches arbitrary origins — the
 * extension does. Without the extension, submissions fail with an honest
 * “extension required” error.
 */
class WebAppGraph : AppGraph {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bridge = WindowExtensionBridge()

    // D4: matched URLs go through the Kotlin extractor over the extension
    // request port; the browser downloader saves the selected format. The
    // page's own JavaScript runs the bundled solver (T-072).
    internal val browserJsRuntime = com.anydownlod.core.jsc.BrowserJsRuntime()
    private val extensionHttp = ExtractorHttp(WebExtensionTransfer(bridge))
    private val extractorRegistry = ExtractorRegistry(
        listOf(YoutubeIE(extensionHttp, browserJsRuntime), TwitterIE(extensionHttp)),
    )

    override val engine: DownloadEngine = WebExtensionEngine(
        bridge = bridge,
        scope = scope,
        registry = extractorRegistry,
    )

    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository()
    override val settings: SettingsRepository = InMemorySettingsRepository()

    // D6: Spotify metadata, matching, and queueing through the extension.
    // The page itself never fetches; the extension carries every request. The
    // user library has no on-device token store on web, so it is a gap.
    override val spotify: SpotifyDownloadService = SpotifyDownloadService(
        metadata = SpotifyMetadataClients.default(extensionHttp),
        matcher = AudioMatcher.default(YoutubeSearch(extensionHttp)),
        engine = engine,
    )

    override val toolProbe: ToolProbe = WebToolProbe(browserJsRuntime)
    override val previews: MediaPreviewSource = ExtractorMediaPreviewSource(extractorRegistry)
    override val cookieStore: CookieStore = CookieStore.Unavailable
}

/** Reports the page's own JavaScript runtime as the Settings row (T-072). */
private class WebToolProbe(
    private val jsRuntime: com.anydownlod.core.jsc.JsRuntime,
) : ToolProbe {
    override suspend fun probe(): com.anydownlod.core.domain.ToolStatus =
        com.anydownlod.core.domain.ToolStatus(
            jsRuntime = com.anydownlod.core.domain.ToolAvailability(
                available = jsRuntime.available,
                version = if (jsRuntime.available) "${jsRuntime.name} (page)" else null,
            ),
        )
}