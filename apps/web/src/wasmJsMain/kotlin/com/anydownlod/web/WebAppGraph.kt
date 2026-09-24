package com.anydownlod.web

import com.anydownlod.core.AppGraph
import com.anydownlod.core.CookieStore
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.UnavailableMediaPreviewSource
import com.anydownlod.core.engine.WebExtensionEngine
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

    override val engine: DownloadEngine = WebExtensionEngine(
        bridge = WindowExtensionBridge(),
        scope = scope,
    )

    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository()
    override val settings: SettingsRepository = InMemorySettingsRepository()
    override val toolProbe: ToolProbe = InMemoryToolProbe
    override val previews: MediaPreviewSource = UnavailableMediaPreviewSource
    override val cookieStore: CookieStore = CookieStore.Unavailable
}