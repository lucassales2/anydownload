package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.Preset
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetLayeringTest {

    private fun preset(id: String, vararg options: Pair<String, String>) =
        Preset(id = id, name = id, options = options.toMap())

    @Test
    fun laterPresetsOverrideEarlierOnesOnTheSameKey() {
        val result = PresetLayering.apply(
            DownloadOptions(),
            listOf(
                preset("first", "splitByChapters" to "true"),
                preset("second", "splitByChapters" to "false", "writeMetadata" to "true"),
            ),
        )

        assertFalse(result.splitByChapters)
        assertTrue(result.writeMetadata)
    }

    @Test
    fun unknownKeysAreDropped() {
        val result = PresetLayering.apply(
            DownloadOptions(),
            listOf(preset("weird", "exec" to "rm -rf /", "configLocations" to "/tmp/x", "embedSubtitles" to "true")),
        )

        assertTrue(result.embedSubtitles)
    }

    @Test
    fun explicitFormSwitchesStayOn() {
        val result = PresetLayering.apply(
            DownloadOptions(writeMetadata = true),
            listOf(preset("off", "writeMetadata" to "false")),
        )

        assertTrue(result.writeMetadata)
    }

    @Test
    fun selectedPresetReachesTheArgumentList() {
        val request = DownloadRequest(
            sourceUrl = "https://example.com/watch?v=fixture",
            options = DownloadOptions(presetIds = listOf("p1")),
        )
        val effective = PresetLayering.apply(
            request.options,
            listOf(preset("p1", "sponsorBlockRemove" to "true")),
        )
        val args = YtDlpArguments.build(
            executable = "/usr/bin/yt-dlp",
            request = request.copy(options = effective),
            outputTemplatePath = Path.of("/tmp/%(title)s.%(ext)s"),
        )

        assertTrue(args.contains("--sponsorblock-remove"))
    }
}
