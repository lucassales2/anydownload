package com.anydownlod.desktop.subscriptions

import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.FakeCliProcess
import com.anydownlod.desktop.store.DesktopStore
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSubscriptionRepositoryTest {

    private fun repository(
        scanLines: List<String>,
        engine: DownloadEngine = InMemoryDownloadEngine(),
        delegate: InMemorySubscriptionRepository = InMemorySubscriptionRepository(),
        exitCode: Int = 0,
    ): Triple<DesktopSubscriptionRepository, DownloadEngine, CoroutineScope> {
        val store = DesktopStore(Files.createTempDirectory("anydownlod-subs-"), now = { 1L })
        val settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = DesktopSubscriptionRepository(
            delegate = delegate,
            store = store,
            engine = engine,
            settings = settings,
            scope = scope,
            runner = CliProcessRunner { _, _ -> FakeCliProcess(scanLines, exitCode = exitCode) },
            resolveExecutable = { "/fake/yt-dlp" },
            ioDispatcher = Dispatchers.Default,
            now = { 1_000L },
        )
        return Triple(repository, engine, scope)
    }

    @Test
    fun firstCheckMarksSeenAndEnqueuesNothing() = runBlocking {
        val delegate = InMemorySubscriptionRepository()
        val subscription = delegate.add(
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture",
            downloadOptions = DownloadOptions(),
        )
        val engine = InMemoryDownloadEngine()
        val (repository, _, scope) = repository(
            scanLines = listOf(
                "ENTRY|a|https://example.com/watch?v=a|Alpha|",
                "ENTRY|b|https://example.com/watch?v=b|Beta|",
            ),
            engine = engine,
            delegate = delegate,
        )
        try {
            repository.runCheck(subscription.id)

            assertTrue(engine.jobs.value.isEmpty())
            val stored = delegate.subscriptions.value.single()
            assertEquals(listOf("a", "b"), stored.seenIds)
            assertNotNull(stored.lastCheckedAtEpochMillis)
            assertNull(stored.lastError)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun laterCheckEnqueuesOnlyUnseenFilteredNonMembers() = runBlocking {
        val delegate = InMemorySubscriptionRepository()
        val subscription = delegate.add(
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture",
            downloadOptions = DownloadOptions(),
            checkIntervalMinutes = 30,
            titleFilterRegex = "keep",
            skipMembersOnly = true,
        )
        delegate.updateSubscription(subscription.id) {
            it.copy(lastCheckedAtEpochMillis = 1L, seenIds = listOf("old"))
        }
        val engine = InMemoryDownloadEngine()
        val (repository, _, scope) = repository(
            scanLines = listOf(
                "ENTRY|old|https://example.com/watch?v=old|keep this old|",
                "ENTRY|new1|https://example.com/watch?v=new1|keep this new|",
                "ENTRY|new2|https://example.com/watch?v=new2|drop this title|",
                "ENTRY|new3|https://example.com/watch?v=new3|keep members|subscriber_only",
            ),
            engine = engine,
            delegate = delegate,
        )
        try {
            repository.runCheck(subscription.id)

            assertEquals(
                listOf("https://example.com/watch?v=new1"),
                engine.jobs.value.map { it.request.sourceUrl },
            )
            val stored = delegate.subscriptions.value.single()
            assertEquals(listOf("old", "new1", "new2", "new3"), stored.seenIds)
            assertEquals(1_000L + 30 * 60_000L, stored.nextCheckAtEpochMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun pausedSubscriptionsAreSkippedByCheckAll() = runBlocking {
        val delegate = InMemorySubscriptionRepository()
        val paused = delegate.add(
            sourceUrl = "https://example.com/channel/paused",
            displayName = "Paused",
            downloadOptions = DownloadOptions(),
        )
        delegate.pause(paused.id)
        val (repository, _, scope) = repository(
            scanLines = listOf("ENTRY|a|https://example.com/watch?v=a|Alpha|"),
            delegate = delegate,
        )
        try {
            repository.checkAll()
            delay(300)

            assertNull(delegate.subscriptions.value.first { it.id == paused.id }.lastCheckedAtEpochMillis)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun scanFailureRecordsAnErrorWithoutWipingSeenIds() = runBlocking {
        val delegate = InMemorySubscriptionRepository()
        val subscription = delegate.add(
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture",
            downloadOptions = DownloadOptions(),
        )
        delegate.updateSubscription(subscription.id) { it.copy(seenIds = listOf("old")) }
        val (repository, _, scope) = repository(scanLines = emptyList(), delegate = delegate, exitCode = 1)
        try {
            repository.runCheck(subscription.id)

            val stored = delegate.subscriptions.value.single()
            assertNotNull(stored.lastError)
            assertEquals(listOf("old"), stored.seenIds)
        } finally {
            scope.cancel()
        }
    }
}
