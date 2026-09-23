package com.anydownlod.ui.settings

import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.ThemePreference

/**
 * Validation and writes for the Settings screen. Every setter is validated
 * here so the composable can stay thin. Cookie methods only ever flip a
 * boolean; no API on this class accepts a file body.
 */
class SettingsPresenter(private val repository: SettingsRepository) {

    fun setDownloadRoot(path: String) {
        repository.update { it.copy(downloadRoot = path.trim()) }
    }

    fun setOutputTemplate(value: String): String? = setTemplate(value) { it.copy(outputTemplate = value) }

    fun setPlaylistTemplate(value: String): String? = setTemplate(value) { it.copy(playlistTemplate = value) }

    fun setChannelTemplate(value: String): String? = setTemplate(value) { it.copy(channelTemplate = value) }

    fun setChapterTemplate(value: String): String? = setTemplate(value) { it.copy(chapterTemplate = value) }

    fun setMaxConcurrentDownloads(text: String): String? {
        val value = text.trim().toIntOrNull()
        if (value == null || value < 1) return "Concurrent downloads must be 1 or more."
        repository.update { it.copy(maxConcurrentDownloads = value) }
        return null
    }

    /** Stored in seconds; the field is labeled in minutes. */
    fun setClearCompletedMinutes(text: String): String? {
        val minutes = text.trim().toIntOrNull()
        if (minutes == null || minutes < 0) return "Clear-completed minutes must be 0 or more."
        repository.update { it.copy(clearCompletedAfterSeconds = minutes * 60L) }
        return null
    }

    fun setTheme(theme: ThemePreference) {
        repository.update { it.copy(theme = theme) }
    }

    fun addPreset(name: String, options: Map<String, String> = emptyMap()): Preset? =
        repository.addPreset(name, options)

    fun removePreset(id: String): Boolean = repository.removePreset(id)

    fun movePresetUp(id: String): Boolean {
        val index = repository.settings.value.presets.indexOfFirst { it.id == id }
        if (index <= 0) return false
        return repository.reorderPresets(index, index - 1)
    }

    fun movePresetDown(id: String): Boolean {
        val presets = repository.settings.value.presets
        val index = presets.indexOfFirst { it.id == id }
        if (index == -1 || index >= presets.lastIndex) return false
        return repository.reorderPresets(index, index + 1)
    }

    fun setCookiesConfigured(configured: Boolean) {
        repository.setCookiesConfigured(configured)
    }

    /**
     * Restores the reviewed defaults for templates, queue, subscription
     * interval, and theme. Leaves the download root, cookie status, and presets
     * exactly as they were.
     */
    fun restoreDefaults() {
        repository.update { current ->
            current.copy(
                outputTemplate = AppSettingsDefaults.OUTPUT_TEMPLATE,
                playlistTemplate = AppSettingsDefaults.PLAYLIST_TEMPLATE,
                channelTemplate = AppSettingsDefaults.CHANNEL_TEMPLATE,
                chapterTemplate = AppSettingsDefaults.CHAPTER_TEMPLATE,
                maxConcurrentDownloads = AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS,
                clearCompletedAfterSeconds = AppSettingsDefaults.CLEAR_COMPLETED_AFTER_SECONDS,
                subscriptionIntervalMinutes = AppSettingsDefaults.SUBSCRIPTION_INTERVAL_MINUTES,
                theme = ThemePreference.SYSTEM,
            )
        }
    }

    private fun setTemplate(value: String, apply: (AppSettings) -> AppSettings): String? {
        validateTemplate(value)?.let { return it }
        repository.update(apply)
        return null
    }

    companion object {
        /** A string check only: no yt-dlp run, no filesystem lookup. */
        fun validateTemplate(value: String): String? {
            if (value.isBlank()) return "Enter a template."
            if (value.startsWith('/') || value.startsWith('\\') || Regex("^[A-Za-z]:").containsMatchIn(value)) {
                return "The template cannot be an absolute path."
            }
            val segments = value.split('/', '\\')
            if (segments.any { it == ".." }) return "The template cannot contain a \"..\" segment."
            if (segments.any { it.isEmpty() }) return "The template has an empty path segment."
            return null
        }
    }
}
