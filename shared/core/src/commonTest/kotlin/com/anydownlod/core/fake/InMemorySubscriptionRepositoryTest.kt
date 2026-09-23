package com.anydownlod.core.fake

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemorySubscriptionRepositoryTest {

    private var clock = 10_000L

    private fun repository() = InMemorySubscriptionRepository(
        now = { clock++ },
        idGenerator = defaultIdGenerator(),
    )

    private fun InMemorySubscriptionRepository.addFixture(
        displayName: String = "Fixture",
        downloadOptions: DownloadOptions = DownloadOptions(),
    ) = add(
        sourceUrl = "https://example.com/channel/fixture",
        displayName = displayName,
        downloadOptions = downloadOptions,
    )

    @Test
    fun checkNowRecordsTimestampsAndDoesNotEnqueue() {
        val repository = repository()
        val subscription = repository.addFixture()

        assertNull(subscription.lastCheckedAtEpochMillis)
        assertTrue(repository.checkNow(subscription.id))

        val stored = repository.subscriptions.value.single()
        assertNotNull(stored.lastCheckedAtEpochMillis)
        assertNotNull(stored.nextCheckAtEpochMillis)
        assertTrue(stored.nextCheckAtEpochMillis > stored.lastCheckedAtEpochMillis)
    }

    @Test
    fun capturedOptionsDoNotChangeWhenTheFormOrSettingsChangeLater() {
        val repository = repository()
        val formOptions = DownloadOptions(quality = QualityPreference.Resolution("1080"))
        val subscription = repository.addFixture(downloadOptions = formOptions)

        // A later add-form edit and a later Settings change must not leak in.
        val editedFormOptions = formOptions.copy(
            mediaType = MediaType.AUDIO,
            quality = QualityPreference.Best,
        )
        val settings = InMemorySettingsRepository()
        settings.update { it.copy(subscriptionIntervalMinutes = 120) }

        val stored = repository.subscriptions.value.single()
        assertEquals(MediaType.VIDEO, stored.downloadOptions.mediaType)
        assertEquals(QualityPreference.Resolution("1080"), stored.downloadOptions.quality)
        assertEquals(subscription.downloadOptions, stored.downloadOptions)
        assertNotEquals(editedFormOptions, stored.downloadOptions)
        assertEquals(60, stored.checkIntervalMinutes)
    }

    @Test
    fun updateChangesOnlyEditableFields() {
        val repository = repository()
        val options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3)
        val subscription = repository.addFixture(displayName = "Old name", downloadOptions = options)

        assertTrue(repository.update(subscription.id, "New name", 30, "fixture", true))

        val stored = repository.subscriptions.value.single()
        assertEquals("New name", stored.displayName)
        assertEquals(30, stored.checkIntervalMinutes)
        assertEquals("fixture", stored.titleFilterRegex)
        assertTrue(stored.skipMembersOnly)
        assertEquals(options, stored.downloadOptions)
    }

    @Test
    fun pauseClearsNextCheckAndResumeRestoresIt() {
        val repository = repository()
        val subscription = repository.addFixture()
        assertNotNull(repository.subscriptions.value.single().nextCheckAtEpochMillis)

        assertTrue(repository.pause(subscription.id))
        val paused = repository.subscriptions.value.single()
        assertTrue(paused.paused)
        assertNull(paused.nextCheckAtEpochMillis)

        assertTrue(repository.resume(subscription.id))
        val resumed = repository.subscriptions.value.single()
        assertFalse(resumed.paused)
        assertNotNull(resumed.nextCheckAtEpochMillis)
    }

    @Test
    fun checkAllSkipsPausedSubscriptions() {
        val repository = repository()
        val first = repository.addFixture(displayName = "First")
        val second = repository.addFixture(displayName = "Second")
        repository.pause(second.id)

        repository.checkAll()

        val stored = repository.subscriptions.value
        assertNotNull(stored.first { it.id == first.id }.lastCheckedAtEpochMillis)
        assertNull(stored.first { it.id == second.id }.lastCheckedAtEpochMillis)
    }

    @Test
    fun deleteRemovesOnlyThatSubscription() {
        val repository = repository()
        val first = repository.addFixture(displayName = "First")
        val second = repository.addFixture(displayName = "Second")

        assertTrue(repository.delete(first.id))
        assertEquals(listOf(second.id), repository.subscriptions.value.map { it.id })
        assertFalse(repository.delete(first.id))
    }

    @Test
    fun unknownIdsReturnFalse() {
        val repository = repository()

        assertFalse(repository.update("missing", "name", 30, "", false))
        assertFalse(repository.pause("missing"))
        assertFalse(repository.resume("missing"))
        assertFalse(repository.delete("missing"))
        assertFalse(repository.checkNow("missing"))
    }
}
