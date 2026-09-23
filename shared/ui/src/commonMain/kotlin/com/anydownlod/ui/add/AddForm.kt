package com.anydownlod.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.anydownlod.ui.theme.AppTextField
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.SegmentedChoice
import com.anydownlod.ui.theme.StatusTone

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
 * The add composer: a source field with the primary action beside it, format
 * choices, and a delivery group. State lives in [AddFormPresenter] so it
 * survives opening Settings and switching lists.
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
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageInset, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "New download",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = "Add a source", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { presenter.subscribe() },
                    enabled = state.canSubscribe,
                    modifier = Modifier.testTag("add-subscribe-button"),
                ) {
                    Text("Subscribe")
                }
            }

            UrlEntryRow(
                urlText = state.urlText,
                downloadEnabled = state.hasInput,
                onUrlChange = presenter::setUrl,
                onPaste = { presenter.applyPastedText(clipboard.getText()?.text) },
                onDownload = { presenter.submit() },
            )
            Text(
                text = "One URL per line. A batch creates one job per valid line.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            ChoiceRow(
                label = "Media type",
                options = MediaType.entries,
                selected = state.mediaType,
                optionLabel = { it.displayName() },
                onSelect = presenter::setMediaType,
            )

            when (state.mediaType) {
                MediaType.VIDEO -> VideoFields(state, presenter)
                MediaType.AUDIO -> AudioFields(state, presenter)
                MediaType.CAPTIONS -> CaptionsFields(state, presenter)
                MediaType.THUMBNAIL -> Text(
                    text = "Thumbnail only: the image is written without the media file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(text = "Delivery", style = MaterialTheme.typography.titleSmall)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DeliveryFields(state, presenter, cookiesConfigured)
                }
            }

            AdvancedSection(state = state, presets = presets, presenter = presenter)

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
                MessageStrip(
                    text = current.message,
                    tone = if (current.isError) StatusTone.Negative else StatusTone.Positive,
                )
            }
        }
    }
}

@Composable
private fun DeliveryFields(
    state: AddFormState,
    presenter: AddFormPresenter,
    cookiesConfigured: Boolean,
) {
    AutoStartRow(state, presenter)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 520.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PrefixField(state, presenter, Modifier.weight(1f))
                FolderField(state, presenter, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrefixField(state, presenter, Modifier.fillMaxWidth())
                FolderField(state, presenter, Modifier.fillMaxWidth())
            }
        }
    }
    if (cookiesConfigured) {
        LabeledCheckbox(
            label = "Use the configured cookie file",
            checked = state.useCookies,
            onCheckedChange = presenter::setUseCookies,
        )
    }
}

@Composable
private fun AutoStartRow(state: AddFormState, presenter: AddFormPresenter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Auto-start", style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (state.startPolicy == StartPolicy.AUTOMATIC) {
                    "Start as soon as added"
                } else {
                    "Wait for Start"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = state.startPolicy == StartPolicy.AUTOMATIC,
            onCheckedChange = {
                presenter.setStartPolicy(if (it) StartPolicy.AUTOMATIC else StartPolicy.MANUAL)
            },
            modifier = Modifier.testTag("add-autostart-switch"),
        )
    }
}

@Composable
private fun PrefixField(state: AddFormState, presenter: AddFormPresenter, modifier: Modifier) {
    AppTextField(
        value = state.filenamePrefix,
        onValueChange = presenter::setFilenamePrefix,
        modifier = modifier,
        label = { Text("Filename prefix") },
        singleLine = true,
    )
}

@Composable
private fun FolderField(state: AddFormState, presenter: AddFormPresenter, modifier: Modifier) {
    AppTextField(
        value = state.destinationFolder,
        onValueChange = presenter::setDestinationFolder,
        modifier = modifier,
        label = { Text("Destination folder") },
        supportingText = {
            Text(state.destinationError ?: "Relative to the download root. Blank uses the root.")
        },
        isError = state.destinationError != null,
        singleLine = true,
    )
}

/**
 * URL field with Paste and Download beside it: type or paste a link, then
 * start the download from the same row.
 */
@Composable
private fun UrlEntryRow(
    urlText: String,
    downloadEnabled: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextField(
            value = urlText,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f).testTag("add-url-field"),
            placeholder = { Text("Paste a source URL") },
            minLines = 1,
            maxLines = 4,
            colors = fieldColors,
        )
        FilledTonalButton(
            onClick = onPaste,
            modifier = Modifier.testTag("add-paste-button"),
        ) {
            Text("Paste")
        }
        Button(
            onClick = onDownload,
            enabled = downloadEnabled,
            modifier = Modifier.testTag("add-download-button"),
        ) {
            Text("Download")
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = "${state.audioContainer.displayName()} is lossless; no bitrate is applied.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CaptionsFields(state: AddFormState, presenter: AddFormPresenter) {
    AppTextField(
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    TextButton(
        onClick = presenter::toggleAdvancedExpanded,
        modifier = Modifier.testTag("add-advanced-toggle"),
    ) {
        Text(if (state.advancedExpanded) "Hide advanced options" else "Advanced options")
    }
    if (!state.advancedExpanded) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.playlistItemLimit,
            onValueChange = presenter::setPlaylistItemLimit,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Playlist item limit") },
            supportingText = { Text(state.playlistLimitError ?: "0 means no extra limit.") },
            isError = state.playlistLimitError != null,
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = state.clipStart,
                onValueChange = presenter::setClipStart,
                modifier = Modifier.weight(1f),
                label = { Text("Clip start") },
                singleLine = true,
            )
            AppTextField(
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        AppTextField(
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
        ).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedChoice(
            options = options,
            selected = selected,
            optionLabel = optionLabel,
            onSelect = onSelect,
        )
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
