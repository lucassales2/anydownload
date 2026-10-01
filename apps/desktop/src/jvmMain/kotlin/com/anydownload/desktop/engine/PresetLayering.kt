package com.anydownload.desktop.engine

import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.Preset
import com.anydownload.core.format.PresetOverlay

/**
 * Overlays selected presets in the order the Settings list shows them.
 *
 * T-017 moved the behavior into the shared [PresetOverlay] so the desktop CLI
 * and the Kotlin engine resolve presets identically: a later preset overrides
 * an earlier one on the same key, an explicit form value wins, unknown keys
 * are dropped, and the safety pass runs after every layer.
 */
object PresetLayering {
    fun apply(options: DownloadOptions, selectedInOrder: List<Preset>): DownloadOptions =
        PresetOverlay.apply(options, selectedInOrder)
}
