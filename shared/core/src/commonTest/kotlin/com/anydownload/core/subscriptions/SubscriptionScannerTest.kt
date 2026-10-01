package com.anydownload.core.subscriptions

import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.JobError
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.Subscription
import com.anydownload.core.fake.InMemoryDownloadEngine
import com.anydownload.core.fake.InMemorySubscriptionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T-019 subscription scanner: first-check policy, dedupe, filter, and caps. */
class SubscriptionScannerTest {

    private var now = 1_000_000L

    private fun subscription(
        id: String = "sub-1",
        seenIds: List<String> = emptyList(),
        downloadExisting: Boolean = false,
        titleFilterRegex: String = "",
        skipMembersOnly: Boolean = false,
        paused: Boolean = false,
    ) = Subscription(
        id = id,
        sourceUrl = "https://fixture.example/channel",
        displayName = "Fixture Channel",
        paused = paused,
        titleFilterRegex = titleFilterRegex,
        skipMembersOnly = skipMembersOnly,
        downloadExisting = downloadExisting,
        downloadOptions = DownloadOptions(),
        seenIds = seenIds,
    )

    private class Source(private var entries: List<SubscriptionEntry>) : SubscriptionEntrySource {
        var calls = 0
        var fail = false
        fun set(entries: List<SubscriptionEntry>) {
            this.entries = entries
        }

        override suspend fun entries(sourceUrl: String): List<SubscriptionEntry> {
            calls++
            if (fail) error("fixture failure")
            return entries
        }
    }

    private fun entry(id: String, title: String = "Fixture $id", membersOnly: Boolean = false) =
        SubscriptionEntry(id = id, url = "https://fixture.example/watch?v=$id", title = title, membersOnly = membersOnly)

    @Test
    fun theFirstCheckMarksSeenAndDownloadsNothing() = runTest {
        val repository = InMemorySubscriptionRepository(seedSubscriptions = listOf(subscription()), now = { now })
        val source = Source(listOf(entry("a"), entry("b")))
        val enqueued = mutableListOf<String>()
        val scanner = SubscriptionScanner(repository, source) { _, item -> enqueued += item.id }

        assertTrue(scanner.check("sub-1"))

        assertEquals(listOf("a", "b"), repository.subscriptions.value.single().seenIds)
        assertTrue(enqueued.isEmpty(), "the default first check downloads nothing")
    }

    @Test
    fun anExplicitBacklogChoiceDownloadsCurrentItemsOnce() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(downloadExisting = true)),
            now = { now },
        )
        val source = Source(listOf(entry("a"), entry("b")))
        val enqueued = mutableListOf<String>()
        val scanner = SubscriptionScanner(repository, source) { _, item -> enqueued += item.id }

        scanner.check("sub-1")
        assertEquals(listOf("a", "b"), enqueued)

        // A second check sees the same items already in seenIds.
        enqueued.clear()
        scanner.check("sub-1")
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun laterChecksDownloadOnlyNewItems() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(seenIds = listOf("a"))),
            now = { now },
        )
        val source = Source(listOf(entry("a"), entry("b"), entry("c")))
        val enqueued = mutableListOf<String>()
        val scanner = SubscriptionScanner(repository, source) { _, item -> enqueued += item.id }

        scanner.check("sub-1")

        assertEquals(listOf("b", "c"), enqueued)
        assertEquals(listOf("a", "b", "c"), repository.subscriptions.value.single().seenIds)
    }

    @Test
    fun theTitleFilterAndMembersOnlySkipAreApplied() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(
                subscription(seenIds = listOf("seen"), titleFilterRegex = "^Keep", skipMembersOnly = true),
            ),
            now = { now },
        )
        val source = Source(
            listOf(
                entry("a", title = "Keep this"),
                entry("b", title = "Drop this"),
                entry("c", title = "Keep member", membersOnly = true),
            ),
        )
        val enqueued = mutableListOf<String>()
        val scanner = SubscriptionScanner(repository, source) { _, item -> enqueued += item.id }

        scanner.check("sub-1")

        assertEquals(listOf("a"), enqueued)
        assertEquals(listOf("seen", "a", "b", "c"), repository.subscriptions.value.single().seenIds)
    }

    @Test
    fun aFailedScanKeepsSeenIdsAndRecordsAnError() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(seenIds = listOf("a"))),
            now = { now },
        )
        val source = Source(emptyList()).apply { fail = true }
        val scanner = SubscriptionScanner(repository, source) { _, _ -> }

        assertFalse(scanner.check("sub-1"))

        val subscription = repository.subscriptions.value.single()
        assertEquals(listOf("a"), subscription.seenIds)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, subscription.lastError?.code)
        assertNotNull(subscription.nextCheckAtEpochMillis)
    }

    @Test
    fun theScanningRepositoryRunsChecksInTheScope() = runTest {
        val delegate = InMemorySubscriptionRepository(seedSubscriptions = listOf(subscription()), now = { now })
        val source = Source(listOf(entry("a")))
        val scanner = SubscriptionScanner(delegate, source) { _, _ -> }
        val repository = ScanningSubscriptionRepository(delegate, this, scanner)

        assertTrue(repository.checkNow("sub-1"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("a"), delegate.subscriptions.value.single().seenIds)
    }

    @Test
    fun unseenEntriesBecomeChildJobsWithTheCapturedOptions() = runTest {
        val engine = InMemoryDownloadEngine()
        val delegate = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(seenIds = listOf("seen"))),
            now = { now },
        )
        val source = Source(listOf(entry("a")))
        val scanner = SubscriptionScanner(delegate, source) { subscription, item ->
            enqueueSubscriptionEntry(engine, subscription, item)
        }

        scanner.check("sub-1")

        val job = engine.jobs.value.single()
        assertEquals("https://fixture.example/watch?v=a", job.request.sourceUrl)
        assertEquals("subscription:sub-1:a", job.request.idempotencyKey)
        assertEquals("sub-1", job.request.parentBatchId)
    }

    @Test
    fun theScanCapBoundsOneCheck() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(downloadExisting = true)),
            now = { now },
        )
        val source = Source((1..60).map { entry("e$it") })
        val enqueued = mutableListOf<String>()
        val scanner = SubscriptionScanner(repository, source) { _, item -> enqueued += item.id }

        scanner.check("sub-1")

        assertEquals(SubscriptionScanner.DEFAULT_MAX_ENTRIES, enqueued.size)
        assertEquals(SubscriptionScanner.DEFAULT_MAX_ENTRIES, repository.subscriptions.value.single().seenIds.size)
    }

    @Test
    fun aFailedScanBacksOffAndAddsJitter() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(
                subscription(seenIds = listOf("a")).copy(
                    lastError = JobError(JobErrorCode.NETWORK_FAILURE, "fixture", retryable = true),
                ),
            ),
            now = { now },
        )
        val source = Source(emptyList()).apply { fail = true }
        val scanner = SubscriptionScanner(repository, source, now = { now }) { _, _ -> }

        scanner.check("sub-1")

        val next = repository.subscriptions.value.single().nextCheckAtEpochMillis!!
        // base 60m doubled to 120m plus up to a quarter-interval jitter.
        assertTrue(next >= now + 120 * 60_000L, "next=$next now=$now")
        assertTrue(next <= now + 135 * 60_000L, "next=$next now=$now")
    }

    @Test
    fun dueIdsHonorPauseAndOverdueTimes() {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(
                subscription(id = "due").copy(nextCheckAtEpochMillis = now - 1),
                subscription(id = "future").copy(nextCheckAtEpochMillis = now + 60_000),
                subscription(id = "paused", paused = true).copy(nextCheckAtEpochMillis = now - 1),
            ),
            now = { now },
        )
        val scanner = SubscriptionScanner(repository, Source(emptyList())) { _, _ -> }

        assertEquals(listOf("due"), scanner.dueIds(now))
    }

    @Test
    fun overlappingSubscriptionsScanIndependently() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(id = "a"), subscription(id = "b")),
            now = { now },
        )
        val source = Source(listOf(entry("x")))
        val scanner = SubscriptionScanner(repository, source) { _, _ -> }

        assertEquals(2, scanner.checkAll())
        assertTrue(repository.subscriptions.value.all { it.seenIds == listOf("x") })
    }

    @Test
    fun aRestartWithPersistedSeenIdsDoesNotRedownload() = runTest {
        // The same ids restored from storage, as after a restart.
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(seenIds = listOf("a", "b"))),
            now = { now },
        )
        val source = Source(listOf(entry("a"), entry("b")))
        val enqueued = mutableListOf<String>()
        val scanner = SubscriptionScanner(repository, source) { _, item -> enqueued += item.id }

        scanner.check("sub-1")

        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun pausedSubscriptionsAreSkipped() = runTest {
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(paused = true)),
            now = { now },
        )
        val source = Source(listOf(entry("a")))
        val scanner = SubscriptionScanner(repository, source) { _, _ -> }

        assertFalse(scanner.check("sub-1"))
        assertEquals(0, source.calls)
        assertEquals(0, scanner.checkAll())
    }

    @Test
    fun seenIdsAreCappedOldestFirst() = runTest {
        val old = (1..SubscriptionScanner.MAX_SEEN_IDS).map { "old$it" }
        val repository = InMemorySubscriptionRepository(
            seedSubscriptions = listOf(subscription(seenIds = old)),
            now = { now },
        )
        val source = Source(listOf(entry("new")))
        val scanner = SubscriptionScanner(repository, source) { _, _ -> }

        scanner.check("sub-1")

        val seen = repository.subscriptions.value.single().seenIds
        assertEquals(SubscriptionScanner.MAX_SEEN_IDS, seen.size)
        assertFalse(seen.contains("old1"))
        assertTrue(seen.contains("new"))
    }
}
