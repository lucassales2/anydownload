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
import com.anydownlod.ui.add.AddFormPresenter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T-063: the Edit panel only enables what the extracted formats can satisfy as
 * a single file, marks the rest with a reason, and cannot be tricked into
 * selecting a disabled entry.
 */
@OptIn(ExperimentalTestApi::class)
class PreviewEditPanelTest {

    private fun presenter(): AddFormPresenter = AddFormPresenter(
        engine = InMemoryDownloadEngine(),
        subscriptions = InMemorySubscriptionRepository(),
        settingsRepository = InMemorySettingsRepository(),
    )

    private val mixedChoices = FormatChoices(
        videoHeights = listOf(720),
        audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
        formatsNeedingJs = 4,
    )

    @Test
    fun onlyPresentHeightsAreEnabledAndTheRestCarryTheReason() = runComposeUiTest {
        val editor = presenter()
        setContent { PreviewEditPanel(editor = editor, availableFormats = mixedChoices) }

        onNodeWithTag("preview-edit-quality-res-720").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-res-1080").assertIsNotEnabled()
        onNodeWithTag("preview-edit-quality-res-360").assertIsNotEnabled()
        onNodeWithText("Needs the media toolkit (not built yet)", substring = true).assertExists()
        onNodeWithText("Not available from this source", substring = true).assertExists()

        // Choosing a disabled entry is impossible: the state does not move.
        onNodeWithTag("preview-edit-quality-res-1080").performClick()
        assertEquals(QualityPreference.Best, editor.state.value.quality)
        onNodeWithTag("preview-edit-quality-res-720").performClick()
        assertEquals(QualityPreference.Resolution("720"), editor.state.value.quality)
    }

    @Test
    fun toolkitAudioContainersAreDisabledAndCannotBePicked() = runComposeUiTest {
        val editor = presenter()
        setContent { PreviewEditPanel(editor = editor, availableFormats = mixedChoices) }

        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithTag("preview-edit-format-m4a").assertIsEnabled()
        onNodeWithTag("preview-edit-format-opus").assertIsEnabled()
        onNodeWithTag("preview-edit-format-mp3").assertIsNotEnabled()
        onNodeWithTag("preview-edit-format-wav").assertIsNotEnabled()
        onNodeWithTag("preview-edit-format-flac").assertIsNotEnabled()

        onNodeWithTag("preview-edit-format-mp3").performClick()
        assertEquals(AudioContainer.M4A, editor.state.value.audioContainer)
        onNodeWithTag("preview-edit-format-opus").performClick()
        assertEquals(AudioContainer.OPUS, editor.state.value.audioContainer)
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
