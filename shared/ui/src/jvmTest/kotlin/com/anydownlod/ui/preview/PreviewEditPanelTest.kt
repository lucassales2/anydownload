package com.anydownlod.ui.preview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.FormatChoices
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.ui.add.AddFormPresenter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T-063/T-078: the Edit panel enables a quality when a single-file format
 * satisfies it or when the host can merge a split pair, and enables an audio
 * container only when the source carries it or the host can write it. The
 * rest carry a reason that names the host gap, and a disabled entry cannot be
 * picked.
 */
@OptIn(ExperimentalTestApi::class)
class PreviewEditPanelTest {

    private fun presenter(): AddFormPresenter = AddFormPresenter(
        engine = InMemoryDownloadEngine(),
        subscriptions = InMemorySubscriptionRepository(),
        settingsRepository = InMemorySettingsRepository(),
    )

    /** One progressive 720 and native M4A/Opus; no separate video stream. */
    private val singleFileChoices = FormatChoices(
        videoHeights = listOf(720),
        audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
        formatsNeedingJs = 4,
    )

    /** The same progressive 720 plus a 1080 video-only stream and audio. */
    private val splitStreamChoices = FormatChoices(
        videoHeights = listOf(720),
        audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
        formatsNeedingJs = 4,
        mergeableVideoHeights = listOf(1080),
        hasAudioOnly = true,
    )

    private val mergeCapable = ToolkitCapabilities(
        canMerge = true,
        audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
    )

    @Test
    fun onlyPresentHeightsAreEnabledAndTheRestCarryTheSourceReason() = runComposeUiTest {
        val editor = presenter()
        setContent { PreviewEditPanel(editor = editor, availableFormats = singleFileChoices) }

        onNodeWithTag("preview-edit-quality-res-720").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-res-1080").assertIsNotEnabled()
        onNodeWithTag("preview-edit-quality-res-360").assertIsNotEnabled()
        onNodeWithText("Not available from this source", substring = true).assertExists()

        // Choosing a disabled entry is impossible: the state does not move.
        onNodeWithTag("preview-edit-quality-res-1080").performClick()
        assertEquals(QualityPreference.Best, editor.state.value.quality)
        onNodeWithTag("preview-edit-quality-res-720").performClick()
        assertEquals(QualityPreference.Resolution("720"), editor.state.value.quality)
    }

    @Test
    fun mergeCapableHostEnablesASplitOnlyHeight() = runComposeUiTest {
        val editor = presenter()
        setContent {
            PreviewEditPanel(
                editor = editor,
                availableFormats = splitStreamChoices,
                capabilities = mergeCapable,
            )
        }

        onNodeWithTag("preview-edit-quality-res-720").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-res-1080").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-res-2160").assertIsNotEnabled()
        onNodeWithText("Not available from this source", substring = true).assertExists()

        onNodeWithTag("preview-edit-quality-res-1080").performClick()
        assertEquals(QualityPreference.Resolution("1080"), editor.state.value.quality)
    }

    @Test
    fun emptyCapabilityKeepsASplitOnlyHeightDisabledWithTheHostReason() = runComposeUiTest {
        val editor = presenter()
        setContent {
            PreviewEditPanel(
                editor = editor,
                availableFormats = splitStreamChoices,
                capabilities = ToolkitCapabilities.Unavailable,
            )
        }

        onNodeWithTag("preview-edit-quality-res-1080").assertIsNotEnabled()
        onNodeWithText("This host cannot merge video and audio", substring = true).assertExists()

        onNodeWithTag("preview-edit-quality-res-1080").performClick()
        assertEquals(QualityPreference.Best, editor.state.value.quality)
    }

    @Test
    fun toolkitAudioContainersAreDisabledWithTheHostReason() = runComposeUiTest {
        val editor = presenter()
        setContent { PreviewEditPanel(editor = editor, availableFormats = singleFileChoices) }

        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithTag("preview-edit-format-m4a").assertIsEnabled()
        onNodeWithTag("preview-edit-format-opus").assertIsEnabled()
        onNodeWithTag("preview-edit-format-mp3").assertIsNotEnabled()
        onNodeWithTag("preview-edit-format-wav").assertIsNotEnabled()
        onNodeWithTag("preview-edit-format-flac").assertIsNotEnabled()
        onNodeWithText("This host cannot write MP3", substring = true).assertExists()

        onNodeWithTag("preview-edit-format-mp3").performClick()
        assertEquals(AudioContainer.M4A, editor.state.value.audioContainer)
        onNodeWithTag("preview-edit-format-opus").performClick()
        assertEquals(AudioContainer.OPUS, editor.state.value.audioContainer)
    }

    @Test
    fun aCapabilityOnlyContainerIsEnabledWithoutASourceStream() = runComposeUiTest {
        val editor = presenter()
        val noNativeAudio = FormatChoices(videoHeights = listOf(720), formatsNeedingJs = 1)
        setContent {
            PreviewEditPanel(
                editor = editor,
                availableFormats = noNativeAudio,
                capabilities = ToolkitCapabilities(
                    canMerge = true,
                    audioContainers = setOf(AudioContainer.M4A),
                ),
            )
        }

        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithTag("preview-edit-format-m4a").assertIsEnabled()
        onNodeWithTag("preview-edit-format-opus").assertIsNotEnabled()
        onNodeWithTag("preview-edit-format-mp3").assertIsNotEnabled()
    }

    @Test
    fun capabilityContainersEnableMp3WavAndFlac() = runComposeUiTest {
        val editor = presenter()
        val noNativeAudio = FormatChoices(videoHeights = listOf(720), formatsNeedingJs = 1)
        setContent {
            PreviewEditPanel(
                editor = editor,
                availableFormats = noNativeAudio,
                capabilities = ToolkitCapabilities(
                    canMerge = true,
                    audioContainers = setOf(
                        AudioContainer.M4A,
                        AudioContainer.OPUS,
                        AudioContainer.MP3,
                        AudioContainer.WAV,
                        AudioContainer.FLAC,
                    ),
                ),
            )
        }

        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithTag("preview-edit-format-mp3").assertIsEnabled()
        onNodeWithTag("preview-edit-format-wav").assertIsEnabled()
        onNodeWithTag("preview-edit-format-flac").assertIsEnabled()
        onNodeWithTag("preview-edit-format-mp3").performClick()
        assertEquals(AudioContainer.MP3, editor.state.value.audioContainer)
    }

    @Test
    fun anAudioOnlySourceDefaultsToNativeAudio() = runComposeUiTest {
        val editor = presenter()
        val audioOnly = FormatChoices(
            videoHeights = emptyList(),
            audioContainers = setOf(AudioContainer.M4A),
            formatsNeedingJs = 27,
        )
        setContent { PreviewEditPanel(editor = editor, availableFormats = audioOnly) }

        assertEquals(MediaType.AUDIO, editor.state.value.mediaType)
        assertEquals(AudioContainer.M4A, editor.state.value.audioContainer)
    }

    @Test
    fun withoutExtractedFormatsTheStaticChoicesStayEnabled() = runComposeUiTest {
        val editor = presenter()
        setContent { PreviewEditPanel(editor = editor, availableFormats = null) }

        onNodeWithTag("preview-edit-quality-res-2160").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-best").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-res-1080").performClick()
        assertEquals(QualityPreference.Resolution("1080"), editor.state.value.quality)
        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithTag("preview-edit-format-mp3").performClick()
        assertEquals(AudioContainer.MP3, editor.state.value.audioContainer)
    }
}
