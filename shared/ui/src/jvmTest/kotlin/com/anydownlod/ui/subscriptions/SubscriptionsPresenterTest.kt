package com.anydownlod.ui.subscriptions

import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionsPresenterTest {

    private fun repository() = InMemorySubscriptionRepository()

    private fun InMemorySubscriptionRepository.addFixture(
        name: String = "Fixture",
        options: DownloadOptions = DownloadOptions(),
    ) = add(
        sourceUrl = "https://example.com/channel/fixture",
        displayName = name,
        downloadOptions = options,
    )

    @Test
    fun rowsCarryTheTableFields() {
        val repository = repository()
        repository.add(
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture",
            downloadOptions = DownloadOptions(),
            checkIntervalMinutes = 30,
            titleFilterRegex = "episode",
            skipMembersOnly = true,
        )

        val row = SubscriptionsPresenter.rows(repository.subscriptions.value).single()

        assertEquals("Fixture", row.name)
        assertEquals("example.com", row.sourceHost)
        assertEquals("https://example.com/channel/fixture", row.sourceUrl)
        assertEquals(30, row.intervalMinutes)
        assertEquals("episode", row.titleFilter)
        assertTrue(row.skipMembersOnly)
        assertFalse(row.paused)
        assertNull(row.lastError)
        assertNull(row.lastCheckedAtEpochMillis)
    }

    @Test
    fun checkSelectedOnlyChecksTheSelection() {
        val repository = repository()
        val first = repository.addFixture("First")
        val second = repository.addFixture("Second")

        SubscriptionsPresenter(repository).checkSelected(setOf(first.id))

        val stored = repository.subscriptions.value
        assertNotNull(stored.first { it.id == first.id }.lastCheckedAtEpochMillis)
        assertNull(stored.first { it.id == second.id }.lastCheckedAtEpochMillis)
    }

    @Test
    fun updateRejectsInvalidRegexIntervalAndBlankName() {
        val repository = repository()
        val subscription = repository.addFixture()
        val presenter = SubscriptionsPresenter(repository)

        assertNotNull(presenter.update(subscription.id, "Name", "30", "[", false))
        assertNotNull(presenter.update(subscription.id, "Name", "0", "", false))
        assertNotNull(presenter.update(subscription.id, "  ", "30", "", false))

        assertEquals(60, repository.subscriptions.value.single().checkIntervalMinutes)
    }

    @Test
    fun updateEditsOnlyTheEditableFields() {
        val repository = repository()
        val options = DownloadOptions(quality = QualityPreference.Resolution("1080"))
        val subscription = repository.addFixture(options = options)
        val presenter = SubscriptionsPresenter(repository)

        assertNull(presenter.update(subscription.id, "New name", "45", "fixture", true))

        val stored = repository.subscriptions.value.single()
        assertEquals("New name", stored.displayName)
        assertEquals(45, stored.checkIntervalMinutes)
        assertEquals("fixture", stored.titleFilterRegex)
        assertTrue(stored.skipMembersOnly)
        assertEquals(QualityPreference.Resolution("1080"), stored.downloadOptions.quality)
    }

    @Test
    fun deleteRemovesOnlyThatSubscription() {
        val repository = repository()
        val first = repository.addFixture("First")
        val second = repository.addFixture("Second")
        val presenter = SubscriptionsPresenter(repository)

        assertTrue(presenter.delete(first.id))

        assertEquals(listOf(second.id), repository.subscriptions.value.map { it.id })
    }

    @Test
    fun pauseClearsNextCheckAndResumeRestoresIt() {
        val repository = repository()
        val subscription = repository.addFixture()
        val presenter = SubscriptionsPresenter(repository)

        assertTrue(presenter.pause(subscription.id))
        assertTrue(repository.subscriptions.value.single().paused)
        assertNull(repository.subscriptions.value.single().nextCheckAtEpochMillis)

        assertTrue(presenter.resume(subscription.id))
        assertFalse(repository.subscriptions.value.single().paused)
        assertNotNull(repository.subscriptions.value.single().nextCheckAtEpochMillis)
    }

    @Test
    fun titleFilterValidationOnlyCompilesThePattern() {
        assertTrue(SubscriptionsPresenter.isValidTitleFilter(""))
        assertTrue(SubscriptionsPresenter.isValidTitleFilter("^episode [0-9]+$"))
        assertFalse(SubscriptionsPresenter.isValidTitleFilter("["))
    }
}
