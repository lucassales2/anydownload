package com.anydownlod.core.fake

import com.anydownlod.core.AppGraph
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.DownloadJob

/**
 * The default [AppGraph]: three in-memory fakes and a probe that reports both
 * tools missing. Android, iOS, web, and desktop-before-T-032 pass this.
 */
class InMemoryAppGraph(
    override val engine: DownloadEngine = InMemoryDownloadEngine(),
    override val subscriptions: SubscriptionRepository = InMemorySubscriptionRepository(),
    override val settings: SettingsRepository = InMemorySettingsRepository(),
    override val toolProbe: ToolProbe = InMemoryToolProbe,
) : AppGraph {

    companion object {
        /** Wires the fakes with one sample job per state for UI work. */
        fun seeded(
            jobs: List<DownloadJob> = InMemoryDownloadEngine.sampleJobs(),
        ): InMemoryAppGraph = InMemoryAppGraph(engine = InMemoryDownloadEngine(seedJobs = jobs))
    }
}
