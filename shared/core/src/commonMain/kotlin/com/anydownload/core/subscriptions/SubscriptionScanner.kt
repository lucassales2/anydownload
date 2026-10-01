/*
 * Subscription scanner — AnyDownload (T-019)
 *
 * Translation of the subscription check in `yt_dlp/YoutubeDL.py`
 * (`--download-archive` interplay and the first-run/seen-id policy) and
 * MeTube's subscription loop at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30. Unlicense;
 * see shared/core/NOTICE.md.
 *
 * Scans run only while the app is open. The default first check marks the
 * current listing seen and downloads nothing; an explicit `downloadExisting`
 * choice enqueues the current backlog once. Later checks enqueue unseen items
 * only. Seen ids are capped oldest-first, a failed scan keeps the previous
 * seen ids, and overlapping checks for one subscription coalesce.
 */
package com.anydownload.core.subscriptions

import com.anydownload.core.SubscriptionRepository
import com.anydownload.core.domain.JobError
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** One current item of a scanned channel or playlist. */
data class SubscriptionEntry(
    val id: String,
    val url: String? = null,
    val title: String? = null,
    val membersOnly: Boolean = false,
)

/** Lists a subscription source's current items; implemented by each host. */
fun interface SubscriptionEntrySource {
    suspend fun entries(sourceUrl: String): List<SubscriptionEntry>
}

class SubscriptionScanner(
    private val repository: SubscriptionRepository,
    private val entrySource: SubscriptionEntrySource,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Per-scan entry cap (the reviewed default is 50). */
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    /** Enqueues one download for the subscription's captured options. */
    private val enqueue: (Subscription, SubscriptionEntry) -> Unit,
) {

    private val lock = Mutex()
    private val inFlight = mutableSetOf<String>()

    /** Checks one unpaused subscription. False when paused, unknown, or overlapping. */
    suspend fun check(id: String): Boolean {
        val subscription = repository.subscriptions.value.firstOrNull { it.id == id } ?: return false
        if (subscription.paused) return false
        if (!begin(id)) return false
        try {
            val entries = try {
                entrySource.entries(subscription.sourceUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                repository.recordCheck(
                    id = id,
                    seenIds = subscription.seenIds,
                    error = JobError(
                        code = JobErrorCode.EXTRACTION_FAILURE,
                        message = "The subscription scan failed.",
                        retryable = true,
                    ),
                    nextCheckAtEpochMillis = nextCheckAt(subscription, failed = true),
                )
                return false
            }

            val listed = entries.take(maxEntries)
            val firstCheck = subscription.seenIds.isEmpty()
            val filter = compileFilter(subscription.titleFilterRegex)
            val downloads = mutableListOf<SubscriptionEntry>()
            for (entry in listed) {
                if (entry.id in subscription.seenIds) continue
                // The default first check only marks items seen.
                if (firstCheck && !subscription.downloadExisting) continue
                if (entry.membersOnly && subscription.skipMembersOnly) continue
                val title = entry.title.orEmpty().take(MAX_TITLE_LENGTH)
                if (filter != null && !filter.containsMatchIn(title)) continue
                downloads += entry
            }

            val updatedSeen = (subscription.seenIds + listed.map { it.id }.filterNot { it in subscription.seenIds })
                .takeLast(MAX_SEEN_IDS)
            repository.recordCheck(
                id = id,
                seenIds = updatedSeen,
                error = null,
                nextCheckAtEpochMillis = nextCheckAt(subscription, failed = false),
            )
            downloads.forEach { entry -> enqueue(subscription, entry) }
            return true
        } finally {
            end(id)
        }
    }

    /** Checks every unpaused subscription; returns how many ran. */
    suspend fun checkAll(): Int = checkSelected(
        repository.subscriptions.value.filterNot { it.paused }.map { it.id },
    )

    /**
     * T-019 downtime policy: the ids that are due at [nowEpochMillis]. An app
     * that was closed past a due time runs each overdue subscription once on
     * the next tick; there is no catch-up storm.
     */
    fun dueIds(nowEpochMillis: Long = now()): List<String> =
        repository.subscriptions.value
            .filter { !it.paused && (it.nextCheckAtEpochMillis == null || it.nextCheckAtEpochMillis <= nowEpochMillis) }
            .map { it.id }

    /** Checks the currently due subscriptions; returns how many ran. */
    suspend fun checkDue(nowEpochMillis: Long = now()): Int = checkSelected(dueIds(nowEpochMillis))

    /** Checks the given ids; returns how many ran. */
    suspend fun checkSelected(ids: List<String>): Int {
        var ran = 0
        for (id in ids) {
            if (check(id)) ran++
        }
        return ran
    }

    private suspend fun begin(id: String): Boolean = lock.withLock { inFlight.add(id) }

    private suspend fun end(id: String) {
        lock.withLock { inFlight.remove(id) }
    }

    private fun intervalMillis(subscription: Subscription): Long =
        subscription.checkIntervalMinutes.coerceAtLeast(1) * 60_000L

    /**
     * The next check time: the interval on success; after a failure the
     * previous error doubles the wait up to four intervals, plus a stable
     * per-subscription jitter of up to a quarter interval so many rows do not
     * all fire at once.
     */
    private fun nextCheckAt(subscription: Subscription, failed: Boolean): Long {
        val base = intervalMillis(subscription)
        val backedOff = if (failed && subscription.lastError != null) minOf(base * 2, base * 4) else base
        return now() + backedOff + jitterMillis(subscription.id, base)
    }

    private fun jitterMillis(id: String, base: Long): Long {
        val window = base / 4
        if (window <= 0) return 0
        val hash = id.fold(0L) { acc, character -> (acc * 31 + character.code) and 0x7fff_ffffL }
        return hash % (window + 1)
    }

    /** A bounded, valid regex or null (invalid or too long filters are ignored). */
    private fun compileFilter(pattern: String): Regex? {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_FILTER_LENGTH) return null
        return runCatching { Regex(trimmed) }.getOrNull()
    }

    companion object {
        /** Upstream's reviewed scan cap and seen-id cap. */
        const val DEFAULT_MAX_ENTRIES: Int = 50
        const val MAX_SEEN_IDS: Int = 50_000

        /** A pathological title filter is ignored instead of hanging the scan. */
        const val MAX_FILTER_LENGTH: Int = 200

        /** A pathological title is truncated before the filter runs. */
        const val MAX_TITLE_LENGTH: Int = 500
    }
}
