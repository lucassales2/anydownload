package com.anydownlod.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.ui.add.AddFormPresenter
import com.anydownlod.ui.add.ChoiceRow
import com.anydownlod.ui.add.bitrateOptions
import com.anydownlod.ui.add.displayName
import com.anydownlod.ui.add.isLossy
import com.anydownlod.ui.add.qualityOptions
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.auto
import com.anydownlod.ui.generated.resources.format
import com.anydownlod.ui.generated.resources.media_type
import com.anydownlod.ui.generated.resources.quality_preference
import com.anydownlod.ui.i18n.text

/**
 * T-054: the collapsible download editor inside the metadata preview. It is a
 * strict allowlist: media type (video or audio), quality, and format. Captions,
 * clips, cookies, destination, and custom yt-dlp JSON never appear here. The
 * choices write through the same [AddFormPresenter] state that Download uses.
 */
@Composable
internal fun PreviewEditPanel(
    editor: AddFormPresenter,
    modifier: Modifier = Modifier,
) {
    val state by editor.state.collectAsState()
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