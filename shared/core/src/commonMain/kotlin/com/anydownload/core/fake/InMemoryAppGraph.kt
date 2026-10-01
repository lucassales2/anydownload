package com.anydownload.core.fake

import com.anydownload.core.AppGraph
import com.anydownload.core.DownloadEngine
import com.anydownload.core.MediaPreviewSource
import com.anydownload.core.SettingsRepository
import com.anydownload.core.SubscriptionRepository
import com.anydownload.core.ToolProbe
import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.music.SpotifyAuthService
import com.anydownload.core.music.SpotifyDownloadService

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
