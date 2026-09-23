package com.anydownlod.ui.settings

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.fake.InMemorySettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsPresenterTest {

    private fun repository() = InMemorySettingsRepository()

    @Test
    fun templateValidationRejectsAbsoluteAndParentSegments() {
        assertNotNull(SettingsPresenter.validateTemplate("/tmp/%(title)s.%(ext)s"))
        assertNotNull(SettingsPresenter.validateTemplate("..\\video\\%(title)s.%(ext)s"))
        assertNotNull(SettingsPresenter.validateTemplate("C:/videos/%(title)s.%(ext)s"))
        assertNotNull(SettingsPresenter.validateTemplate("%(title)s//%(ext)s"))
        assertNull(SettingsPresenter.validateTemplate("%(title)s.%(ext)s"))
        assertNull(SettingsPresenter.validateTemplate("%(playlist_title)s/%(title)s.%(ext)s"))
    }

    @Test
    fun templateSetterRejectsWithoutWriting() {
        val repository = repository()
        val presenter = SettingsPresenter(repository)

        assertNotNull(presenter.setOutputTemplate("../%(title)s.%(ext)s"))
        assertEquals(AppSettingsDefaults.OUTPUT_TEMPLATE, repository.settings.value.outputTemplate)

        assertNull(presenter.setOutputTemplate("%(title)s-%(id)s.%(ext)s"))
        assertEquals("%(title)s-%(id)s.%(ext)s", repository.settings.value.outputTemplate)
    }

    @Test
    fun concurrencyBelowOneIsRejected() {
        val repository = repository()
        val presenter = SettingsPresenter(repository)

        assertNotNull(presenter.setMaxConcurrentDownloads("0"))
        assertNotNull(presenter.setMaxConcurrentDownloads("abc"))
        assertEquals(3, repository.settings.value.maxConcurrentDownloads)

        assertNull(presenter.setMaxConcurrentDownloads("2"))
        assertEquals(2, repository.settings.value.maxConcurrentDownloads)
    }

    @Test
    fun clearCompletedUsesMinutesInTheUiAndSecondsOnTheModel() {
        val repository = repository()
        val presenter = SettingsPresenter(repository)

        assertNull(presenter.setClearCompletedMinutes("5"))
        assertEquals(300L, repository.settings.value.clearCompletedAfterSeconds)

        assertNotNull(presenter.setClearCompletedMinutes("-1"))
        assertEquals(300L, repository.settings.value.clearCompletedAfterSeconds)
    }

    @Test
    fun cookieStateNeverTakesAFileBody() {
        val repository = repository()
        val presenter = SettingsPresenter(repository)

        presenter.setCookiesConfigured(true)

        assertTrue(repository.settings.value.cookiesConfigured)
        // The flag is the only cookie information this API can carry.
        assertEquals(AppSettings(cookiesConfigured = true), repository.settings.value)

        presenter.setCookiesConfigured(false)
        assertFalse(repository.settings.value.cookiesConfigured)
    }

    @Test
    fun restoreDefaultsKeepsRootCookieStateAndPresets() {
        val repository = repository()
        val presenter = SettingsPresenter(repository)
        presenter.setDownloadRoot("/tmp/downloads")
        presenter.setCookiesConfigured(true)
        presenter.addPreset("Keep me")
        presenter.setOutputTemplate("%(id)s.%(ext)s")
        presenter.setMaxConcurrentDownloads("7")
        presenter.setTheme(ThemePreference.DARK)

        presenter.restoreDefaults()

        val settings = repository.settings.value
        assertEquals(AppSettingsDefaults.OUTPUT_TEMPLATE, settings.outputTemplate)
        assertEquals(AppSettingsDefaults.PLAYLIST_TEMPLATE, settings.playlistTemplate)
        assertEquals(AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS, settings.maxConcurrentDownloads)
        assertEquals(AppSettingsDefaults.CLEAR_COMPLETED_AFTER_SECONDS, settings.clearCompletedAfterSeconds)
        assertEquals(ThemePreference.SYSTEM, settings.theme)
        assertEquals("/tmp/downloads", settings.downloadRoot)
        assertTrue(settings.cookiesConfigured)
        assertEquals(listOf("Keep me"), settings.presets.map { it.name })
    }

    @Test
    fun presetsAddMoveAndRemove() {
        val repository = repository()
        val presenter = SettingsPresenter(repository)
        val first = presenter.addPreset("First", mapOf("writeMetadata" to "true"))!!
        val second = presenter.addPreset("Second")!!

        assertTrue(presenter.movePresetDown(first.id))
        assertEquals(listOf("Second", "First"), repository.settings.value.presets.map { it.name })

        assertTrue(presenter.movePresetUp(first.id))
        assertEquals(listOf("First", "Second"), repository.settings.value.presets.map { it.name })
        assertFalse(presenter.movePresetUp(first.id))

        assertTrue(presenter.removePreset(second.id))
        assertEquals(listOf("First"), repository.settings.value.presets.map { it.name })
    }
}
