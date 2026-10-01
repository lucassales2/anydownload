package com.anydownload.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anydownload.core.FormatChoices
import com.anydownload.core.domain.AudioContainer
import com.anydownload.core.domain.MediaType
import com.anydownload.core.domain.QualityPreference
import com.anydownload.core.domain.VideoContainerProfile
import com.anydownload.core.postprocess.ToolkitCapabilities
import com.anydownload.ui.add.AddFormViewModel
import com.anydownload.ui.add.ChoiceRow
import com.anydownload.ui.add.bitrateOptions
import com.anydownload.ui.add.displayName
import com.anydownload.ui.add.isLossy
import com.anydownload.ui.add.qualityOptions
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.auto
import com.anydownload.ui.generated.resources.format
import com.anydownload.ui.generated.resources.media_type
import com.anydownload.ui.generated.resources.preview_cannot_merge
import com.anydownload.ui.generated.resources.preview_cannot_write
import com.anydownload.ui.generated.resources.preview_not_available_source
import com.anydownload.ui.generated.resources.quality_preference
import com.anydownload.ui.i18n.text
import org.jetbrains.compose.resources.stringResource

/**
 * T-054/T-063/T-078: the collapsible download editor inside the metadata
 * preview. When the Kotlin extractor supplied [availableFormats], a quality is
 * enabled when a single-file format satisfies it or when the host can merge a
 * video-only stream at that height with an audio-only stream. Audio
 * containers are enabled only when the source carries them natively or the
 * host toolkit can write them. Everything else is disabled with a reason that
 * names the host gap, not a build gap. Captions, clips, cookies, destination,
 * and custom yt-dlp JSON never appear here.
 */
@Composable
internal fun PreviewEditPanel(
    editor: AddFormViewModel,
    availableFormats: FormatChoices? = null,
    capabilities: ToolkitCapabilities = ToolkitCapabilities.Unavailable,
    modifier: Modifier = Modifier,
) {
    val state by editor.state.collectAsState()
    val cannotMerge = stringResource(Res.string.preview_cannot_merge)
    val notAvailable = stringResource(Res.string.preview_not_available_source)
    val cannotWrite = buildMap {
        for (container in listOf(AudioContainer.MP3, AudioContainer.WAV, AudioContainer.FLAC)) {
            put(container, stringResource(Res.string.preview_cannot_write, container.wireName.uppercase()))
        }
    }

    val mergeReady = capabilities.canMerge && availableFormats?.hasSplitStreams == true

    // Defaults from the extracted formats: best single-file video, or the best
    // native audio when the source only offers split streams in this stage.
    LaunchedEffect(availableFormats) {
        if (availableFormats != null &&
            !availableFormats.hasSingleFileVideo &&
            availableFormats.audioContainers.isNotEmpty()
        ) {
            editor.setMediaType(MediaType.AUDIO)
            availableFormats.preferredAudioContainer?.let(editor::setAudioContainer)
        }
    }

    val qualityEnabled: (QualityPreference) -> Boolean = { quality ->
        when {
            availableFormats == null -> true
            quality is QualityPreference.Resolution -> {
                val height = quality.token.toIntOrNull()
                height != null && (
                    height in availableFormats.videoHeights ||
                        (mergeReady && height in availableFormats.mergeableVideoHeights)
                    )
            }

            else -> availableFormats.hasSingleFileVideo || mergeReady
        }
    }
    val qualityReason: (QualityPreference) -> String? = { quality ->
        when {
            qualityEnabled(quality) -> null
            availableFormats == null -> null
            availableFormats.hasSplitStreams && !capabilities.canMerge -> cannotMerge
            else -> notAvailable
        }
    }
    val containerEnabled: (AudioContainer) -> Boolean = { container ->
        when {
            availableFormats == null -> true
            container == AudioContainer.M4A || container == AudioContainer.OPUS ->
                container in availableFormats.audioContainers || container in capabilities.audioContainers

            else -> container in capabilities.audioContainers
        }
    }
    val containerReason: (AudioContainer) -> String? = { container ->
        if (containerEnabled(container)) {
            null
        } else {
            cannotWrite[container] ?: notAvailable
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("preview-edit-panel"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChoiceRow(
                label = text(Res.string.media_type),
                options = listOf(MediaType.VIDEO, MediaType.AUDIO),
                selected = state.mediaType,
                optionLabel = { it.displayName() },
                onSelect = editor::setMediaType,
                modifier = Modifier.testTag("preview-edit-media"),
                optionTag = { media ->
                    when (media) {
                        MediaType.VIDEO -> "preview-edit-media-video"
                        MediaType.AUDIO -> "preview-edit-media-audio"
                        MediaType.CAPTIONS, MediaType.THUMBNAIL -> null
                    }
                },
            )
            when (state.mediaType) {
                MediaType.VIDEO -> {
                    ChoiceRow(
                        label = text(Res.string.quality_preference),
                        options = qualityOptions,
                        selected = state.quality,
                        optionLabel = { it.displayName() },
                        onSelect = editor::setQuality,
                        modifier = Modifier.testTag("preview-edit-quality"),
                        optionTag = { quality ->
                            when (quality) {
                                QualityPreference.Best -> "preview-edit-quality-best"
                                QualityPreference.Worst -> "preview-edit-quality-worst"
                                is QualityPreference.Resolution -> "preview-edit-quality-res-${quality.token}"
                            }
                        },
                        optionEnabled = qualityEnabled,
                        optionReason = qualityReason,
                    )
                    ChoiceRow(
                        label = text(Res.string.format),
                        options = VideoContainerProfile.entries,
                        selected = state.videoProfile,
                        optionLabel = { it.displayName() },
                        onSelect = editor::setVideoProfile,
                        modifier = Modifier.testTag("preview-edit-format"),
                        optionTag = { profile -> "preview-edit-format-${profile.name.lowercase()}" },
                    )
                }

                MediaType.AUDIO -> {
                    ChoiceRow(
                        label = text(Res.string.format),
                        options = AudioContainer.entries,
                        selected = state.audioContainer,
                        optionLabel = { it.displayName() },
                        onSelect = editor::setAudioContainer,
                        modifier = Modifier.testTag("preview-edit-format"),
                        optionTag = { container -> "preview-edit-format-${container.name.lowercase()}" },
                        optionEnabled = containerEnabled,
                        optionReason = containerReason,
                    )
                    if (state.audioContainer.isLossy) {
                        ChoiceRow(
                            label = text(Res.string.quality_preference),
                            options = bitrateOptions,
                            selected = state.audioBitrate,
                            optionLabel = { if (it.isEmpty()) text(Res.string.auto) else it },
                            onSelect = editor::setAudioBitrate,
                            modifier = Modifier.testTag("preview-edit-quality"),
                            optionTag = { bitrate -> "preview-edit-quality-${if (bitrate.isEmpty()) "auto" else bitrate}" },
                        )
                    }
                }

                MediaType.CAPTIONS, MediaType.THUMBNAIL -> Unit
            }
        }
    }
}
