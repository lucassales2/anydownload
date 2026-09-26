package com.anydownlod.core.fake

import com.anydownlod.core.AppGraph
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.music.SpotifyAuthService
import com.anydownlod.core.music.SpotifyDownloadService

/**
 * The default [AppGraph]: three in-memory fakes and a probe that reports both
 * tools missing. Android, iOS, web, and desktop-before-T-032 pass this.
 */
class InMemoryAppGraph(
    override val engine: DownloadEngine = InMemoryDownloadEngine(),
    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository(),
    override val settings: SettingsRepository = InMemorySettingsRepository(),
    override val toolProbe: ToolProbe = InMemoryToolProbe,
    override val previews: MediaPreviewSource = FixedMediaPreviewSource(),
    override val spotify: SpotifyDownloadService? = null,
    override val spotifyAuth: SpotifyAuthService? = null,
) : AppGraph {

    companion object {
        /** Wires the fakes with one sample job per state for UI work. */
        fun seeded(
            jobs: List<DownloadJob> = InMemoryDownloadEngine.sampleJobs(),
        ): InMemoryAppGraph = InMemoryAppGraph(engine = InMemoryDownloadEngine(seedJobs = jobs))
    }
}
