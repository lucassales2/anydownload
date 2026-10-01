package com.anydownload.core.persist

import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.JobError
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.QualityPreference
import com.anydownload.core.domain.Subscription
import com.anydownload.core.fake.InMemorySubscriptionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-019 subscription persistence: round-trip, mutation writes, and restore. */
class SubscriptionDocumentCodecTest {

    private class MemoryStorage : SubscriptionDocumentStorage {
        var text: String? = null
        var writes = 0
        override fun read(): String? = text
        override fun write(document: String): JobDocumentStorageError? {
            text = document
            writes++
            return null
        }
    }

    private val subscription = Subscription(
        id = "sub-1",
        sourceUrl = "https://fixture.example/channel",
        displayName = "Fixture Channel",
        paused = false,
        checkIntervalMinutes = 30,
        titleFilterRegex = "^Keep",
        skipMembersOnly = true,
        downloadExisting = true,
        downloadOptions = DownloadOptions(
            quality = QualityPreference.Resolution("720"),
            useCookies = true,
        ),
        lastCheckedAtEpochMillis = 1_000L,
        nextCheckAtEpochMillis = 2_000L,
        lastError = JobError(JobErrorCode.NETWORK_FAILURE, "fixture", retryable = true),
        seenIds = listOf("a", "b"),
    )

    @Test
    fun roundTripKeepsEveryField() {
        val decoded = SubscriptionDocumentCodec.decode(SubscriptionDocumentCodec.encode(listOf(subscription))).single()

        assertEquals(subscription.id, decoded.id)
        assertEquals(subscription.sourceUrl, decoded.sourceUrl)
        assertEquals(subscription.displayName, decoded.displayName)
        assertEquals(subscription.checkIntervalMinutes, decoded.checkIntervalMinutes)
        assertEquals(subscription.titleFilterRegex, decoded.titleFilterRegex)
        assertTrue(decoded.skipMembersOnly)
        assertTrue(decoded.downloadExisting)
        assertEquals("720", decoded.downloadOptions.quality.token)
        assertTrue(decoded.downloadOptions.useCookies)
        assertEquals(listOf("a", "b"), decoded.seenIds)
        assertEquals(JobErrorCode.NETWORK_FAILURE, decoded.lastError?.code)
        assertEquals(2_000L, decoded.nextCheckAtEpochMillis)
    }

    @Test
    fun theRepositoryPersistsEveryMutationAndRestores() {
        val storage = MemoryStorage()
        val delegate = InMemorySubscriptionRepository()
        val repository = PersistedSubscriptionRepository(delegate, storage)

        val added = repository.add(
            sourceUrl = "https://fixture.example/channel",
            displayName = "Fixture",
            downloadOptions = DownloadOptions(),
            checkIntervalMinutes = 15,
            titleFilterRegex = "",
            skipMembersOnly = false,
        )
        assertTrue(storage.writes >= 1)

        repository.update(added.id, "Renamed", 15, "^Keep", true)
        repository.recordCheck(added.id, listOf("x"), null, 9_000L)

        val restored = PersistedSubscriptionRepository.restore(storage).single()
        assertEquals("Renamed", restored.displayName)
        assertEquals("^Keep", restored.titleFilterRegex)
        assertTrue(restored.skipMembersOnly)
        assertEquals(listOf("x"), restored.seenIds)
        assertEquals(9_000L, restored.nextCheckAtEpochMillis)

        repository.delete(added.id)
        assertTrue(PersistedSubscriptionRepository.restore(storage).isEmpty())
    }

    @Test
    fun aCorruptOrMissingDocumentRestoresEmpty() {
        val storage = MemoryStorage()
        assertEquals(emptyList(), PersistedSubscriptionRepository.restore(storage))

        storage.text = "{not json"
        assertFalse(PersistedSubscriptionRepository.restore(storage).isNotEmpty())
    }
}
