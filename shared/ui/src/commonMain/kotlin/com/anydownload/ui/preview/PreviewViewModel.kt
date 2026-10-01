package com.anydownload.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydownload.core.CompositeMediaPreviewSource
import com.anydownload.core.MediaPreview
import com.anydownload.core.MediaPreviewResult
import com.anydownload.core.MediaPreviewSource
import com.anydownload.core.PreviewFailure
import com.anydownload.core.SpotifyMediaPreviewSource
import com.anydownload.core.ThumbnailLoader
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.music.SpotifyDownloadService
import com.anydownload.core.music.SpotifyPreview
import com.anydownload.core.postprocess.ToolkitCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PreviewPhase {
    data object Loading : PreviewPhase
    data class Ready(val preview: MediaPreview, val thumbnail: ByteArray?) : PreviewPhase
    data class Failed(val failure: PreviewFailure) : PreviewPhase
}

data class PreviewUiState(
    val phase: PreviewPhase = PreviewPhase.Loading,
    val selectedMediaIds: Set<String> = emptySet(),
    val editExpanded: Boolean = false,
    val capabilities: ToolkitCapabilities = ToolkitCapabilities.Unavailable,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class PreviewViewModel(
    previews: MediaPreviewSource,
    private val thumbnails: ThumbnailLoader,
    private val capabilities: ToolkitCapabilities,
    private val spotify: SpotifyDownloadService? = null,
) : ViewModel() {

    private val source: MediaPreviewSource = if (spotify != null) {
        CompositeMediaPreviewSource(SpotifyMediaPreviewSource(spotify), previews)
    } else {
        previews
    }

    private val _state = MutableStateFlow(PreviewUiState(capabilities = capabilities))
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Loads [url], cancelling a lookup already in flight. Pass the caller's
     * scope (the screen's [androidx.compose.runtime.LaunchedEffect]) so the
     * composition waits for the result; the default is [viewModelScope].
     */
    fun open(url: String, scope: CoroutineScope = viewModelScope) {
        loadJob?.cancel()
        _state.value = PreviewUiState(capabilities = capabilities)
        loadJob = scope.launch {
            val phase = try {
                when (val result = source.load(url)) {
                    is MediaPreviewResult.Failed -> PreviewPhase.Failed(result.failure)
                    is MediaPreviewResult.Ready -> {
                        val bytes = result.preview.thumbnailUrl?.let { thumbnailUrl ->
                            runCatching { thumbnails(thumbnailUrl) }.getOrNull()
                        }
                        val first = result.preview.videos.firstOrNull()?.let { setOf(it.mediaId) } ?: emptySet()
                        _state.value = _state.value.copy(selectedMediaIds = first)
                        PreviewPhase.Ready(result.preview, bytes)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PreviewPhase.Failed(PreviewFailure.Failed)
            }
            _state.value = _state.value.copy(phase = phase)
        }
    }

    fun toggleVideo(mediaId: String) {
        val selected = _state.value.selectedMediaIds
        _state.value = _state.value.copy(
            selectedMediaIds = if (mediaId in selected) selected - mediaId else selected + mediaId,
        )
    }

    fun toggleEdit() {
        _state.value = _state.value.copy(editExpanded = !_state.value.editExpanded)
    }

    fun queueSpotify(preview: SpotifyPreview, options: DownloadOptions) {
        val service = spotify ?: return
        viewModelScope.launch {
            service.queue(preview = preview, options = options, capabilities = capabilities)
        }
    }
}
