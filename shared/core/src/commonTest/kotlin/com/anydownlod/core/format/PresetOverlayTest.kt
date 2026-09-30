package com.anydownlod.core.format

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.PresetOptionKeys
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-017 typed preset overlay: order, form-wins, clearing, and safety. */
class PresetOverlayTest {

    private fun preset(id: String, vararg options: Pair<String, String>) =
        Preset(id = id, name = id, options = options.toMap())

    @Test
    fun aLaterPresetOverridesAnEarlierOneOnTheSameKey() {
        val options = DownloadOptions()
        val result = PresetOverlay.apply(
            options,
            listOf(
                preset("first", PresetOptionKeys.CAPTION_LANGUAGE to "en"),
                preset("second", PresetOptionKeys.CAPTION_LANGUAGE to "pt"),
            ),
        )

        assertEquals("pt", result.captionLanguage)
    }

    @Test
    fun anExplicitFormValueWinsOverPresets() {
        val options = DownloadOptions(
            captionLanguage = "de",
            quality = QualityPreference.Resolution("720"),
            embedSubtitles = true,
        )
        val result = PresetOverlay.apply(
            options,
            listOf(
                preset(
                    "p",
                    PresetOptionKeys.CAPTION_LANGUAGE to "en",
                    PresetOptionKeys.QUALITY to "1080",
                    PresetOptionKeys.EMBED_SUBTITLES to "false",
                ),
            ),
        )

        assertEquals("de", result.captionLanguage)
        assertEquals("720", result.quality.token)
        assertTrue(result.embedSubtitles)
    }

    @Test
    fun aLaterBlankValueClearsAPresetValue() {
        val result = PresetOverlay.apply(
            DownloadOptions(),
            listOf(
                preset("set", PresetOptionKeys.AUDIO_BITRATE to "128"),
                preset("clear", PresetOptionKeys.AUDIO_BITRATE to ""),
            ),
        )

        assertEquals(null, result.audioBitrate)
    }

    @Test
    fun typedEnumsApplyWhenTheFormIsNeutral() {
        val result = PresetOverlay.apply(
            DownloadOptions(),
            listOf(
                preset(
                    "typed",
                    PresetOptionKeys.AUDIO_CONTAINER to "opus",
                    PresetOptionKeys.CAPTION_FORMAT to "vtt",
                    PresetOptionKeys.VIDEO_CODEC to "h264",
                    PresetOptionKeys.PLAYLIST_ITEM_LIMIT to "25",
                    PresetOptionKeys.PLAYLIST_ITEMS to "1-5",
                ),
            ),
        )

        assertEquals(AudioContainer.OPUS, result.audioContainer)
        assertEquals(CaptionFormat.VTT, result.captionFormat)
        assertEquals(VideoCodec.H264, result.videoCodec)
        assertEquals(25, result.playlistItemLimit)
        assertEquals("1-5", result.playlistItems)
    }

    @Test
    fun unknownKeysAreDropped() {
        val result = PresetOverlay.apply(
            DownloadOptions(),
            listOf(
                preset(
                    "unsafe",
                    "proxy" to "http://127.0.0.1:1",
                    "exec" to "sh -c",
                    "customYtDlpJson" to "{}",
                    PresetOptionKeys.WRITE_METADATA to "true",
                ),
            ),
        )

        assertEquals("", result.customYtDlpJson)
        assertTrue(result.writeMetadata)
    }

    @Test
    fun theSafetyPassClearsOnlyTheMandatoryProtections() {
        val result = PresetOverlay.enforceSafety(
            DownloadOptions(
                customYtDlpJson = "{\"exec\":\"rm\"}",
                destinationFolder = "../outside",
                clipStart = "20",
                clipEnd = "10",
                playlistItems = "1,,2",
            ),
        )

        assertEquals("", result.customYtDlpJson)
        assertEquals(null, result.destinationFolder)
        // Invalid clip/playlist values stay so the engine fails the job typed.
        assertEquals("20", result.clipStart)
        assertEquals("1,,2", result.playlistItems)
    }

    @Test
    fun aSafeFolderSurvivesTheSafetyPass() {
        val result = PresetOverlay.enforceSafety(DownloadOptions(destinationFolder = "shows/season 1"))
        assertEquals("shows/season 1", result.destinationFolder)
    }
}
