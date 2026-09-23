package com.anydownlod.core

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.Preset
import kotlinx.coroutines.flow.StateFlow

/** Local preferences, presets, theme, and cookie status. */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /** Applies an immutable update and returns the new value. */
    fun update(transform: (AppSettings) -> AppSettings): AppSettings

    /** Adds a named preset. Returns null when the name is blank. */
    fun addPreset(name: String, options: Map<String, String> = emptyMap()): Preset?

    fun removePreset(id: String): Boolean

    /** Moves the preset at [fromIndex] to [toIndex]. False for bad indices. */
    fun reorderPresets(fromIndex: Int, toIndex: Int): Boolean

    /**
     * Flips the cookie status. There is no parameter that can carry cookie
     * contents; the desktop store keeps the file path outside this type.
     */
    fun setCookiesConfigured(configured: Boolean): AppSettings
}
