package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.PresetOptionKeys

/**
 * Overlays selected presets in the order the Settings list shows them.
 *
 * Within the preset layer a later preset overrides an earlier one on the same
 * key. A switch the add form already turned on stays on, and a key outside
 * [PresetOptionKeys] is dropped and never becomes an argument. D1 presets can
 * only enable the boolean switches; they cannot lower an explicit form value.
 */
object PresetLayering {
    fun apply(options: DownloadOptions, selectedInOrder: List<Preset>): DownloadOptions {
        val layered = mutableMapOf<String, Boolean>()
        selectedInOrder.forEach { preset ->
            preset.options.forEach { (key, value) ->
                if (key in PresetOptionKeys.all) {
                    layered[key] = value.equals("true", ignoreCase = true)
                }
            }
        }
        return options.copy(
            embedSubtitles = options.embedSubtitles || layered[PresetOptionKeys.EMBED_SUBTITLES] == true,
            writeMetadata = options.writeMetadata || layered[PresetOptionKeys.WRITE_METADATA] == true,
            writeThumbnail = options.writeThumbnail || layered[PresetOptionKeys.WRITE_THUMBNAIL] == true,
            splitByChapters = options.splitByChapters || layered[PresetOptionKeys.SPLIT_BY_CHAPTERS] == true,
            sponsorBlockRemove = options.sponsorBlockRemove || layered[PresetOptionKeys.SPONSORBLOCK_REMOVE] == true,
        )
    }
}
