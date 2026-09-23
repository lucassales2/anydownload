package com.anydownlod.core.fake

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemorySettingsRepositoryTest {

    @Test
    fun defaultsMatchReviewedMeTubeValues() {
        val settings = AppSettings()

        assertEquals("%(title)s.%(ext)s", settings.outputTemplate)
        assertEquals("%(playlist_title)s/%(title)s.%(ext)s", settings.playlistTemplate)
        assertEquals("%(channel)s/%(title)s.%(ext)s", settings.channelTemplate)
        assertEquals(
            "%(title)s - %(section_number)02d - %(section_title)s.%(ext)s",
            settings.chapterTemplate,
        )
        assertEquals(3, settings.maxConcurrentDownloads)
        assertEquals(0L, settings.clearCompletedAfterSeconds)
        assertEquals(60, settings.subscriptionIntervalMinutes)
        assertEquals(ThemePreference.SYSTEM, settings.theme)
        assertFalse(settings.cookiesConfigured)
        assertTrue(settings.presets.isEmpty())
    }

    @Test
    fun presetReorderPreservesEveryPreset() {
        val repository = InMemorySettingsRepository(idGenerator = defaultIdGenerator())
        repository.addPreset("A")
        repository.addPreset("B")
        repository.addPreset("C")

        assertTrue(repository.reorderPresets(0, 2))

        assertEquals(listOf("B", "C", "A"), repository.settings.value.presets.map { it.name })
    }

    @Test
    fun presetRemovalAndBadIndices() {
        val repository = InMemorySettingsRepository(idGenerator = defaultIdGenerator())
        val first = repository.addPreset("A")!!
        repository.addPreset("B")

        assertFalse(repository.reorderPresets(0, 9))
        assertTrue(repository.removePreset(first.id))
        assertEquals(listOf("B"), repository.settings.value.presets.map { it.name })
        assertFalse(repository.removePreset(first.id))
        assertNull(repository.addPreset("  "))
    }

    @Test
    fun cookieStateIsABooleanFlagWithNoPayload() {
        val repository = InMemorySettingsRepository()

        val updated = repository.setCookiesConfigured(true)

        assertTrue(updated.cookiesConfigured)
        // The only cookie information is the flag; the repository API has no
        // parameter and the model has no field that could carry cookie bytes.
        assertEquals(AppSettings(cookiesConfigured = true), repository.settings.value)
        assertFalse(repository.settings.value.toString().contains("SESSION="))
        assertFalse(repository.setCookiesConfigured(false).cookiesConfigured)
    }

    @Test
    fun presetOptionsKeepTheCallersOrderedAllowlist() {
        val repository = InMemorySettingsRepository()

        val preset = repository.addPreset(
            name = "Small audio",
            options = mapOf("audioContainer" to "mp3", "audioBitrate" to "128"),
        )

        assertEquals(listOf("audioContainer", "audioBitrate"), preset!!.options.keys.toList())
        assertFalse(preset.options.keys.any { it.contains("cookie", ignoreCase = true) })
    }

    @Test
    fun updateAppliesAnImmutableTransform() {
        val repository = InMemorySettingsRepository()

        val updated = repository.update { it.copy(maxConcurrentDownloads = 5) }

        assertEquals(5, updated.maxConcurrentDownloads)
        assertEquals(5, repository.settings.value.maxConcurrentDownloads)
    }
}
