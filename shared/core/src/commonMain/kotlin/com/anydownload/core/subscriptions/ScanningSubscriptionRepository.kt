/*
 * Scanning subscription repository — AnyDownload (T-019)
 *
 * The shared decorator for hosts that scan with the Kotlin extractor
 * registry: `checkNow`/`checkAll`/`checkSelected` launch a [SubscriptionScanner]
 * run in the host scope while the app is open. The desktop host keeps its
 * yt-dlp flat scan; Android, iOS, and web use this decorator once their
 * graphs wire it. Nothing runs after the host scope is cancelled (app
 * suspension or close).
 */
package com.anydownload.core.subscriptions

import com.anydownload.core.DownloadEngine
import com.anydownload.core.SubscriptionRepository
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.Subscription
import com.anydownload.core.extract.ExtractorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lists a tab source's current entries through the shared extractor registry. */
class RegistrySubscriptionEntrySource(private val registry: ExtractorRegistry) : SubscriptionEntrySource {
    override suspend fun entries(sourceUrl: String): List<SubscriptionEntry> {
        val extractor = registry.suitableFor(sourceUrl) ?: return emptyList()
        return extractor.extract(sourceUrl).entries.mapNotNull { entry ->
            val id = entry.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SubscriptionEntry(id = id, url = entry.url, title = entry.title)
        }
    }
}

/**
 * Submits one child job per unseen entry with the subscription's captured
 * options. The idempotency key makes a repeated scan land on the same job.
 */
fun enqueueSubscriptionEntry(
    engine: DownloadEngine,
    subscription: Subscription,
    entry: SubscriptionEntry,
) {
    engine.submit(
        DownloadRequest(
            sourceUrl = entry.url?.takeIf { it.isNotBlank() } ?: subscription.sourceUrl,
            options = subscription.downloadOptions,
            idempotencyKey = "subscription:${subscription.id}:${entry.id}",
            parentBatchId = subscription.id,
        ),
    )
}

/**
 * A [SubscriptionRepository] whose checks run the shared scanner in [scope].
 * CRUD and status stay on the delegate.
 */
class ScanningSubscriptionRepository(
    private val delegate: SubscriptionRepository,
    private val scope: CoroutineScope,
    private val scanner: SubscriptionScanner,
) : SubscriptionRepository by delegate {

    override fun checkNow(id: String): Boolean {
        if (delegate.subscriptions.value.none { it.id == id }) return false
        scope.launch { scanner.check(id) }
        return true
    }

    override fun checkAll() {
        scope.launch { scanner.checkAll() }
    }

    override fun checkSelected(ids: List<String>) {
        scope.launch { scanner.checkSelected(ids) }
    }
}
