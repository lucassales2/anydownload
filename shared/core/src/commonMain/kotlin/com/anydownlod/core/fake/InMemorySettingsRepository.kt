package com.anydownlod.core.fake

import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [SettingsRepository]. The cookie flag is just a boolean: this
 * class has no field and no parameter that can carry cookie contents.
 */
class InMemorySettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    private val idGenerator: () -> String = defaultIdGenerator(),
) : SettingsRepository {

    private val _settings = MutableStateFlow(initialSettings)
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(_settings.value)
        _settings.value = updated
        return updated
    }

    override fun addPreset(name: String, options: Map<String, String>): Preset? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val preset = Preset(
            id = "preset-${idGenerator()}",
            name = trimmed,
            options = options.toMap(),
        )
        update { current -> current.copy(presets = current.presets + preset) }
        return preset
    }

    override fun removePreset(id: String): Boolean {
        val presets = _settings.value.presets
        if (presets.none { it.id == id }) return false
        update { current -> current.copy(presets = current.presets.filterNot { it.id == id }) }
        return true
    }

    override fun reorderPresets(fromIndex: Int, toIndex: Int): Boolean {
        val presets = _settings.value.presets
        if (fromIndex !in presets.indices || toIndex !in presets.indices) return false
        if (fromIndex == toIndex) return true
        val reordered = presets.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        update { current -> current.copy(presets = reordered) }
        return true
    }

    override fun setCookiesConfigured(configured: Boolean): AppSettings =
        update { current -> current.copy(cookiesConfigured = configured) }
}
