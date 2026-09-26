package com.anydownlod.ui.preview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.FormatChoices
import com.anydownlod.core.MediaPreview
import com.anydownlod.core.MediaPreviewResult
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.MediaPreviewVideo
import com.anydownlod.core.PreviewFailure
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.ui.App
import com.anydownlod.ui.add.AddFormPresenter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T-095: a status preview lists its videos grouped by stable media id, starts
 * with the first selected, and the Download action carries exactly the
 * selected ids. Photos never appear, and the existing Edit panel keeps
 * choosing quality. In-memory fakes only; no network and no media request.
 */
@OptIn(ExperimentalTestApi::class)
class StatusVideoSelectionUiTest {

    private val statusUrl = "https://x.com/fixture/status/9999999999999999999"

    private class TwoVideoSource : MediaPreviewSource {
        override suspend fun load(url: String): MediaPreviewResult = MediaPreviewResult.Ready(
            MediaPreview(
                pageUrl = url,
                title = "Fixture Poster - Two videos",
                channel = "Fixture Poster",
                videos = listOf(
                    MediaPreviewVideo(
                        mediaId = "3333333333333333333",
                        title = "Fixture Poster - Two videos #1",
                        durationSeconds = 5,
                    ),
                    MediaPreviewVideo(
                        mediaId = "4444444444444444444",
                        title = "Fixture Poster - Two videos #2",
                        durationSeconds = 7,
                    ),
                ),
                availableFormats = FormatChoices(videoHeights = listOf(360)),
            ),
        )
    }

    private class NoVideoSource : MediaPreviewSource {
        override suspend fun load(url: String): MediaPreviewResult = MediaPreviewResult.Ready(
            MediaPreview(pageUrl = url, title = "Photo only"),
        )
    }

    private class PhotoOnlyFailureSource : MediaPreviewSource {
        override suspend fun load(url: String): MediaPreviewResult =
            MediaPreviewResult.Failed(PreviewFailure.Failed)
    }

    private fun presenter(): AddFormPresenter = AddFormPresenter(
        engine = InMemoryDownloadEngine(),
        subscriptions = InMemorySubscriptionRepository(),
        settingsRepository = InMemorySettingsRepository(),
    )

    @Test
    fun theFirstVideoIsPreselectedAndDownloadCarriesTheSelection() = runComposeUiTest {
        var downloaded: List<String>? = null
        setContent {
            PreviewScreen(
                url = statusUrl,
                source = TwoVideoSource(),
                loadThumbnail = { null },
                onBack = {},
                onDownload = { _, ids -> downloaded = ids },
                editor = presenter(),
            )
        }

        onNodeWithTag("preview-videos").assertExists()
        onNodeWithTag("preview-video-3333333333333333333").assertExists()
        onNodeWithTag("preview-video-4444444444444444444").assertExists()
        onNodeWithTag("select-video-3333333333333333333").assertIsOn()
        onNodeWithTag("select-video-4444444444444444444").assertIsOff()

        // Toggling the second video adds its stable id.
        onNodeWithTag("select-video-4444444444444444444").performClick()
        onNodeWithTag("select-video-4444444444444444444").assertIsOn()
        onNodeWithTag("preview-download").assertIsEnabled().performClick()
        assertEquals(listOf("3333333333333333333", "4444444444444444444"), downloaded)

        // Toggling the first off leaves only the second.
        onNodeWithTag("select-video-3333333333333333333").performClick()
        onNodeWithTag("preview-download").assertIsEnabled().performClick()
        assertEquals(listOf("4444444444444444444"), downloaded)

        // With nothing selected the Download action cannot submit.
        onNodeWithTag("select-video-4444444444444444444").performClick()
        onNodeWithTag("preview-download").assertIsNotEnabled()
    }

    @Test
    fun theEditPanelKeepsChoosingQualityWhileVideosToggle() = runComposeUiTest {
        val editor = presenter()
        setContent {
            PreviewScreen(
                url = statusUrl,
                source = TwoVideoSource(),
                loadThumbnail = { null },
                onBack = {},
                onDownload = { _, _ -> },
                editor = editor,
            )
        }

        onNodeWithTag("preview-edit-toggle").performClick()
        onNodeWithTag("preview-edit-quality-res-360").assertIsEnabled()
        onNodeWithTag("preview-edit-quality-res-1080").assertIsNotEnabled()

        onNodeWithTag("select-video-4444444444444444444").performClick()
        onNodeWithTag("preview-edit-quality-res-360").performClick()
        assertEquals(QualityPreference.Resolution("360"), editor.state.value.quality)
    }

    @Test
    fun aPreviewWithoutVideosHasNoSelectionSection() = runComposeUiTest {
        setContent {
            PreviewScreen(
                url = statusUrl,
                source = NoVideoSource(),
                loadThumbnail = { null },
                onBack = {},
                onDownload = { _, _ -> },
            )
        }

        onNodeWithTag("preview-videos").assertDoesNotExist()
        onNodeWithTag("preview-download").assertIsEnabled()
    }

    @Test
    fun aPhotoOnlyFailureHasNoVideoRows() = runComposeUiTest {
        setContent {
            PreviewScreen(
                url = statusUrl,
                source = PhotoOnlyFailureSource(),
                loadThumbnail = { null },
                onBack = {},
                onDownload = { _, _ -> },
            )
        }

        onNodeWithTag("preview-videos").assertDoesNotExist()
    }

    @Test
    fun thePreviewSelectionReachesTheEngineRequest() = runComposeUiTest {
        val engine = InMemoryDownloadEngine()
        val graph = InMemoryAppGraph(engine = engine, previews = TwoVideoSource())
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput(statusUrl)
        onNodeWithTag("add-download-button").performClick()
        onNodeWithTag("preview-videos").assertExists()
        onNodeWithTag("select-video-4444444444444444444").performClick()
        onNodeWithTag("preview-download").performClick()
        waitForIdle()

        val job = engine.jobs.value.single()
        assertEquals(statusUrl, job.request.sourceUrl)
        assertEquals(
            listOf("3333333333333333333", "4444444444444444444"),
            job.request.selectedMediaIds,
        )
    }
}
