package com.anydownlod.ui.add

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile

private val qualityOptions = listOf(
    QualityPreference.Best,
    QualityPreference.Worst,
    QualityPreference.Resolution("2160"),
    QualityPreference.Resolution("1440"),
    QualityPreference.Resolution("1080"),
    QualityPreference.Resolution("720"),
    QualityPreference.Resolution("480"),
    QualityPreference.Resolution("360"),
)

private val bitrateOptions = listOf("", "128", "192", "256", "320")

/**
 * The MeTube add form: always-visible fields, media-type-specific fields, and
 * a collapsed advanced section. State lives in [AddFormPresenter] so it
 * survives opening Settings and switching tabs.
 */
@Composable
fun AddForm(
    presenter: AddFormPresenter,
    presets: List<Preset>,
    cookiesConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by presenter.state.collectAsState()
    val status by presenter.status.collectAsState()

    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(
            modifier = Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.urlText,
                onValueChange = presenter::setUrl,
                modifier = Modifier.fillMaxWidth().testTag("add-url-field"),
                label = { Text("Source URLs") },
                supportingText = {
                    Text("One URL per line. A batch creates one job per valid line.")
                },
                minLines = 2,
                maxLines = 5,
            )

            ChoiceRow(
                label = "Media type",
                options = MediaType.entries,
                selected = state.mediaType,
                optionLabel = { it.displayName() },
                onSelect = presenter::setMediaType,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Auto-start", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = state.startPolicy == StartPolicy.AUTOMATIC,
                    onCheckedChange = {
                        presenter.setStartPolicy(if (it) StartPolicy.AUTOMATIC else StartPolicy.MANUAL)
                    },
                    modifier = Modifier.testTag("add-autostart-switch"),
                )
                Text(
                    text = if (state.startPolicy == StartPolicy.AUTOMATIC) {
                        "Start as soon as added"
                    } else {
                        "Wait for Start"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = state.filenamePrefix,
                onValueChange = presenter::setFilenamePrefix,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filename prefix") },
                singleLine = true,
            )

            OutlinedTextField(
                value = state.destinationFolder,
                onValueChange = presenter::setDestinationFolder,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Destination folder") },
                supportingText = {
                    Text(state.destinationError ?: "Relative to the download root. Blank uses the root.")
                },
                isError = state.destinationError != null,
                singleLine = true,
            )

            if (cookiesConfigured) {
                LabeledCheckbox(
                    label = "Use the configured cookie file",
                    checked = state.useCookies,
                    onCheckedChange = presenter::setUseCookies,
                )
            }

            when (state.mediaType) {
                MediaType.VIDEO -> VideoFields(state, presenter)
                MediaType.AUDIO -> AudioFields(state, presenter)
                MediaType.CAPTIONS -> CaptionsFields(state, presenter)
                MediaType.THUMBNAIL -> Text(
                    text = "Thumbnail only: the image is written without the media file.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AdvancedSection(state = state, presets = presets, presenter = presenter)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { presenter.submit() },
                    enabled = state.hasInput,
                    modifier = Modifier.testTag("add-download-button"),
                ) {
                    Text("Download")
                }
                OutlinedButton(
                    onClick = { presenter.subscribe() },
                    enabled = state.canSubscribe,
                    modifier = Modifier.testTag("add-subscribe-button"),
                ) {
                    Text("Subscribe")
                }
            }

            if (state.lineErrors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.lineErrors.forEach { error ->
                        Text(
                            text = "${error.line}: ${error.reason}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            status?.let { current ->
                Text(
                    text = current.message,
                    color = if (current.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun VideoFields(state: AddFormState, presenter: AddFormPresenter) {
    ChoiceRow(
        label = "Container profile",
        options = VideoContainerProfile.entries,
        selected = state.videoProfile,
        optionLabel = { it.displayName() },
        onSelect = presenter::setVideoProfile,
    )
    ChoiceRow(
        label = "Codec preference",
        options = VideoCodec.entries,
        selected = state.videoCodec,
        optionLabel = { it.displayName() },
        onSelect = presenter::setVideoCodec,
    )
    ChoiceRow(
        label = "Quality preference",
        options = qualityOptions,
        selected = state.quality,
        optionLabel = { it.displayName() },
        onSelect = presenter::setQuality,
    )
    Text(
        text = "Quality is a preference, not a promise that the file will be that height.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun AudioFields(state: AddFormState, presenter: AddFormPresenter) {
    ChoiceRow(
        label = "Container",
        options = AudioContainer.entries,
        selected = state.audioContainer,
        optionLabel = { it.displayName() },
        onSelect = presenter::setAudioContainer,
    )
    if (state.audioContainer.isLossy) {
        ChoiceRow(
            label = "Bitrate (kbps)",
            options = bitrateOptions,
            selected = state.audioBitrate,
            optionLabel = { if (it.isEmpty()) "Auto" else it },
            onSelect = presenter::setAudioBitrate,
        )
        Text(
            text = "Lossy containers are converted when the source does not already match.",
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(
            text = "${state.audioContainer.displayName()} is lossless; no bitrate is applied.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CaptionsFields(state: AddFormState, presenter: AddFormPresenter) {
    OutlinedTextField(
        value = state.captionLanguage,
        onValueChange = presenter::setCaptionLanguage,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Caption language") },
        supportingText = { Text("Language code, for example en or pt-BR.") },
        singleLine = true,
    )
    ChoiceRow(
        label = "Preference",
        options = CaptionPreference.entries,
        selected = state.captionPreference,
        optionLabel = { it.displayName() },
        onSelect = presenter::setCaptionPreference,
    )
    ChoiceRow(
        label = "Format",
        options = CaptionFormat.entries,
        selected = state.captionFormat,
        optionLabel = { it.displayName() },
        onSelect = presenter::setCaptionFormat,
    )
}

@Composable
private fun AdvancedSection(
    state: AddFormState,
    presets: List<Preset>,
    presenter: AddFormPresenter,
) {
    HorizontalDivider()
    TextButton(
        onClick = presenter::toggleAdvancedExpanded,
        modifier = Modifier.testTag("add-advanced-toggle"),
    ) {
        Text(if (state.advancedExpanded) "Hide advanced options" else "Advanced options")
    }
    if (!state.advancedExpanded) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.playlistItemLimit,
            onValueChange = presenter::setPlaylistItemLimit,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Playlist item limit") },
            supportingText = { Text(state.playlistLimitError ?: "0 means no extra limit.") },
            isError = state.playlistLimitError != null,
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.clipStart,
                onValueChange = presenter::setClipStart,
                modifier = Modifier.weight(1f),
                label = { Text("Clip start") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.clipEnd,
                onValueChange = presenter::setClipEnd,
                modifier = Modifier.weight(1f),
                label = { Text("Clip end") },
                singleLine = true,
            )
        }
        Text(
            text = state.clipError ?: "Clip times accept seconds or HH:MM:SS; one side alone is allowed.",
            color = if (state.clipError != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall,
        )

        LabeledCheckbox("Split by chapters", state.splitByChapters, presenter::setSplitByChapters)
        LabeledCheckbox("Remove SponsorBlock segments", state.sponsorBlockRemove, presenter::setSponsorBlockRemove)
        Text(
            text = "SponsorBlock removal needs marker data and may have no effect on some sources.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (state.mediaType == MediaType.VIDEO || state.mediaType == MediaType.AUDIO) {
            LabeledCheckbox("Embed subtitles", state.embedSubtitles, presenter::setEmbedSubtitles)
        }
        if (state.mediaType != MediaType.THUMBNAIL) {
            LabeledCheckbox("Write metadata", state.writeMetadata, presenter::setWriteMetadata)
            LabeledCheckbox("Write thumbnail sidecar", state.writeThumbnail, presenter::setWriteThumbnail)
        }

        if (presets.isEmpty()) {
            Text(
                text = "No presets configured yet. Add them in Settings.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Presets (applied in the order shown)", style = MaterialTheme.typography.labelLarge)
            presets.forEach { preset ->
                LabeledCheckbox(
                    label = preset.name,
                    checked = preset.id in state.selectedPresetIds,
                ) { checked -> presenter.setPresetSelected(preset.id, checked) }
            }
        }

        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().testTag("add-custom-json-field"),
            label = { Text("Custom yt-dlp options") },
            supportingText = { Text("Not available yet (Q-09). This field is disabled and never sent.") },
        )
    }
}

@Composable
private fun LabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

private fun MediaType.displayName(): String = when (this) {
    MediaType.VIDEO -> "Video"
    MediaType.AUDIO -> "Audio"
    MediaType.CAPTIONS -> "Captions"
    MediaType.THUMBNAIL -> "Thumbnail"
}

private fun VideoContainerProfile.displayName(): String = when (this) {
    VideoContainerProfile.AUTO -> "Auto"
    VideoContainerProfile.MP4 -> "MP4"
    VideoContainerProfile.IOS_COMPATIBLE -> "iOS compatible"
}

private fun VideoCodec.displayName(): String = when (this) {
    VideoCodec.AUTO -> "Auto"
    VideoCodec.H264 -> "H.264"
    VideoCodec.HEVC -> "HEVC"
    VideoCodec.AV1 -> "AV1"
    VideoCodec.VP9 -> "VP9"
}

private fun QualityPreference.displayName(): String = when (this) {
    QualityPreference.Best -> "Best"
    QualityPreference.Worst -> "Worst"
    is QualityPreference.Resolution -> "${token}p"
}

private fun AudioContainer.displayName(): String = when (this) {
    AudioContainer.M4A -> "M4A"
    AudioContainer.MP3 -> "MP3"
    AudioContainer.OPUS -> "Opus"
    AudioContainer.WAV -> "WAV"
    AudioContainer.FLAC -> "FLAC"
}

private fun CaptionPreference.displayName(): String = when (this) {
    CaptionPreference.MANUAL -> "Manual"
    CaptionPreference.AUTOMATIC -> "Automatic"
    CaptionPreference.EITHER -> "Either"
}

private fun CaptionFormat.displayName(): String = when (this) {
    CaptionFormat.SRT -> "SRT"
    CaptionFormat.TXT -> "TXT"
    CaptionFormat.VTT -> "VTT"
    CaptionFormat.TTML -> "TTML"
}
