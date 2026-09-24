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
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.add_batch_hint
import com.anydownlod.ui.generated.resources.add_source
import com.anydownlod.ui.generated.resources.advanced_options
import com.anydownlod.ui.generated.resources.auto
import com.anydownlod.ui.generated.resources.auto_start
import com.anydownlod.ui.generated.resources.bitrate_kbps
import com.anydownlod.ui.generated.resources.caption_automatic
import com.anydownlod.ui.generated.resources.caption_either
import com.anydownlod.ui.generated.resources.caption_language
import com.anydownlod.ui.generated.resources.caption_language_hint
import com.anydownlod.ui.generated.resources.caption_manual
import com.anydownlod.ui.generated.resources.clip_end
import com.anydownlod.ui.generated.resources.clip_hint
import com.anydownlod.ui.generated.resources.clip_start
import com.anydownlod.ui.generated.resources.codec_preference
import com.anydownlod.ui.generated.resources.container
import com.anydownlod.ui.generated.resources.container_profile
import com.anydownlod.ui.generated.resources.custom_options
import com.anydownlod.ui.generated.resources.custom_options_hint
import com.anydownlod.ui.generated.resources.delivery
import com.anydownlod.ui.generated.resources.destination_folder
import com.anydownlod.ui.generated.resources.destination_hint
import com.anydownlod.ui.generated.resources.download
import com.anydownlod.ui.generated.resources.embed_subtitles
import com.anydownlod.ui.generated.resources.filename_prefix
import com.anydownlod.ui.generated.resources.format
import com.anydownlod.ui.generated.resources.hide_advanced
import com.anydownlod.ui.generated.resources.line_error
import com.anydownlod.ui.generated.resources.lossless_hint
import com.anydownlod.ui.generated.resources.lossy_hint
import com.anydownlod.ui.generated.resources.media_audio
import com.anydownlod.ui.generated.resources.media_captions
import com.anydownlod.ui.generated.resources.media_thumbnail
import com.anydownlod.ui.generated.resources.media_type
import com.anydownlod.ui.generated.resources.media_video
import com.anydownlod.ui.generated.resources.new_download
import com.anydownlod.ui.generated.resources.no_presets_add
import com.anydownlod.ui.generated.resources.paste
import com.anydownlod.ui.generated.resources.playlist_limit
import com.anydownlod.ui.generated.resources.playlist_limit_hint
import com.anydownlod.ui.generated.resources.preference
import com.anydownlod.ui.generated.resources.presets_order
import com.anydownlod.ui.generated.resources.profile_ios
import com.anydownlod.ui.generated.resources.quality_best
import com.anydownlod.ui.generated.resources.quality_hint
import com.anydownlod.ui.generated.resources.quality_preference
import com.anydownlod.ui.generated.resources.quality_resolution
import com.anydownlod.ui.generated.resources.quality_worst
import com.anydownlod.ui.generated.resources.sponsorblock_hint
import com.anydownlod.ui.generated.resources.sponsorblock_remove
import com.anydownlod.ui.generated.resources.split_by_chapters
import com.anydownlod.ui.generated.resources.start_immediately
import com.anydownlod.ui.generated.resources.subscribe
import com.anydownlod.ui.generated.resources.thumbnail_only
import com.anydownlod.ui.generated.resources.url_placeholder
import com.anydownlod.ui.generated.resources.use_cookie_file
import com.anydownlod.ui.generated.resources.wait_for_start
import com.anydownlod.ui.generated.resources.write_metadata
import com.anydownlod.ui.generated.resources.write_thumbnail
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.i18n.text
import com.anydownlod.ui.theme.AppTextField
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.PageInset
import com.anydownlod.ui.theme.SegmentedChoice
import com.anydownlod.ui.theme.StatusTone
import org.jetbrains.compose.resources.stringResource

internal val qualityOptions = listOf(
    QualityPreference.Best,
    QualityPreference.Worst,
    QualityPreference.Resolution("2160"),
    QualityPreference.Resolution("1440"),
    QualityPreference.Resolution("1080"),
    QualityPreference.Resolution("720"),
    QualityPreference.Resolution("480"),
    QualityPreference.Resolution("360"),
)

internal val bitrateOptions = listOf("", "128", "192", "256", "320")

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
    onPreviewSingleUrl: (String) -> Unit,
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
                        text = text(Res.string.new_download),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = text(Res.string.add_source), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { presenter.subscribe() },
                    enabled = state.canSubscribe,
                    modifier = Modifier.testTag("add-subscribe-button"),
                ) {
                    Text(text(Res.string.subscribe))
                }
            }

            UrlEntryRow(
                urlText = state.urlText,
                downloadEnabled = state.hasInput,
                onUrlChange = presenter::setUrl,
                onPaste = { presenter.applyPastedText(clipboard.getText()?.text) },
                onDownload = {
                    val single = presenter.singleSourceUrl()
                    if (single != null) onPreviewSingleUrl(single) else presenter.submit()
                },
            )
            Text(
                text = text(Res.string.add_batch_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            ChoiceRow(
                label = text(Res.string.media_type),
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
                    text = text(Res.string.thumbnail_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(text = text(Res.string.delivery), style = MaterialTheme.typography.titleSmall)
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
                            text = stringResource(Res.string.line_error, error.line, error.reason.resolve()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            status?.let { current ->
                MessageStrip(
                    text = current.message.resolve(),
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
            label = text(Res.string.use_cookie_file),
            checked = state.useCookies,
            onCheckedChange = presenter::setUseCookies,
        )
    }
}

@Composable
private fun AutoStartRow(state: AddFormState, presenter: AddFormPresenter) {
    val automatic = state.startPolicy == StartPolicy.AUTOMATIC
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text(Res.string.auto_start), style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (automatic) {
                    text(Res.string.start_immediately)
                } else {
                    text(Res.string.wait_for_start)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = automatic,
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
        label = { Text(text(Res.string.filename_prefix)) },
        singleLine = true,
    )
}

@Composable
private fun FolderField(state: AddFormState, presenter: AddFormPresenter, modifier: Modifier) {
    AppTextField(
        value = state.destinationFolder,
        onValueChange = presenter::setDestinationFolder,
        modifier = modifier,
        label = { Text(text(Res.string.destination_folder)) },
        supportingText = {
            Text(state.destinationError?.resolve() ?: text(Res.string.destination_hint))
        },
        isError = state.destinationError != null,
        singleLine = true,
    )
}

/**
 * URL field with Paste and Download beside it: type or paste a link, then
 * start the download from the same row. This is the only control on the idle
 * home screen; the add-form options use it too.
 */
@Composable
internal fun UrlEntryRow(
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
            placeholder = { Text(text(Res.string.url_placeholder)) },
            minLines = 1,
            maxLines = 4,
            colors = fieldColors,
        )
        FilledTonalButton(
            onClick = onPaste,
            modifier = Modifier.testTag("add-paste-button"),
        ) {
            Text(text(Res.string.paste))
        }
        Button(
            onClick = onDownload,
            enabled = downloadEnabled,
            modifier = Modifier.testTag("add-download-button"),
        ) {
            Text(text(Res.string.download))
        }
    }
}

@Composable
private fun VideoFields(state: AddFormState, presenter: AddFormPresenter) {
    ChoiceRow(
        label = text(Res.string.container_profile),
        options = VideoContainerProfile.entries,
        selected = state.videoProfile,
        optionLabel = { it.displayName() },
        onSelect = presenter::setVideoProfile,
    )
    ChoiceRow(
        label = text(Res.string.codec_preference),
        options = VideoCodec.entries,
        selected = state.videoCodec,
        optionLabel = { it.displayName() },
        onSelect = presenter::setVideoCodec,
    )
    ChoiceRow(
        label = text(Res.string.quality_preference),
        options = qualityOptions,
        selected = state.quality,
        optionLabel = { it.displayName() },
        onSelect = presenter::setQuality,
    )
    Text(
        text = text(Res.string.quality_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AudioFields(state: AddFormState, presenter: AddFormPresenter) {
    ChoiceRow(
        label = text(Res.string.container),
        options = AudioContainer.entries,
        selected = state.audioContainer,
        optionLabel = { it.displayName() },
        onSelect = presenter::setAudioContainer,
    )
    if (state.audioContainer.isLossy) {
        ChoiceRow(
            label = text(Res.string.bitrate_kbps),
            options = bitrateOptions,
            selected = state.audioBitrate,
            optionLabel = { if (it.isEmpty()) text(Res.string.auto) else it },
            onSelect = presenter::setAudioBitrate,
        )
        Text(
            text = text(Res.string.lossy_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = text(Res.string.lossless_hint, state.audioContainer.displayName()),
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
        label = { Text(text(Res.string.caption_language)) },
        supportingText = { Text(text(Res.string.caption_language_hint)) },
        singleLine = true,
    )
    ChoiceRow(
        label = text(Res.string.preference),
        options = CaptionPreference.entries,
        selected = state.captionPreference,
        optionLabel = { it.displayName() },
        onSelect = presenter::setCaptionPreference,
    )
    ChoiceRow(
        label = text(Res.string.format),
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
        Text(if (state.advancedExpanded) text(Res.string.hide_advanced) else text(Res.string.advanced_options))
    }
    if (!state.advancedExpanded) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(
            value = state.playlistItemLimit,
            onValueChange = presenter::setPlaylistItemLimit,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text(Res.string.playlist_limit)) },
            supportingText = {
                Text(state.playlistLimitError?.resolve() ?: text(Res.string.playlist_limit_hint))
            },
            isError = state.playlistLimitError != null,
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = state.clipStart,
                onValueChange = presenter::setClipStart,
                modifier = Modifier.weight(1f),
                label = { Text(text(Res.string.clip_start)) },
                singleLine = true,
            )
            AppTextField(
                value = state.clipEnd,
                onValueChange = presenter::setClipEnd,
                modifier = Modifier.weight(1f),
                label = { Text(text(Res.string.clip_end)) },
                singleLine = true,
            )
        }
        Text(
            text = state.clipError?.resolve() ?: text(Res.string.clip_hint),
            color = if (state.clipError != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall,
        )

        LabeledCheckbox(text(Res.string.split_by_chapters), state.splitByChapters, presenter::setSplitByChapters)
        LabeledCheckbox(text(Res.string.sponsorblock_remove), state.sponsorBlockRemove, presenter::setSponsorBlockRemove)
        Text(
            text = text(Res.string.sponsorblock_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.mediaType == MediaType.VIDEO || state.mediaType == MediaType.AUDIO) {
            LabeledCheckbox(text(Res.string.embed_subtitles), state.embedSubtitles, presenter::setEmbedSubtitles)
        }
        if (state.mediaType != MediaType.THUMBNAIL) {
            LabeledCheckbox(text(Res.string.write_metadata), state.writeMetadata, presenter::setWriteMetadata)
            LabeledCheckbox(text(Res.string.write_thumbnail), state.writeThumbnail, presenter::setWriteThumbnail)
        }

        if (presets.isEmpty()) {
            Text(
                text = text(Res.string.no_presets_add),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(text(Res.string.presets_order), style = MaterialTheme.typography.labelLarge)
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
            label = { Text(text(Res.string.custom_options)) },
            supportingText = { Text(text(Res.string.custom_options_hint)) },
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
internal fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: (T) -> String? = { null },
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            optionTag = optionTag,
        )
    }
}

@Composable
internal fun MediaType.displayName(): String = when (this) {
    MediaType.VIDEO -> text(Res.string.media_video)
    MediaType.AUDIO -> text(Res.string.media_audio)
    MediaType.CAPTIONS -> text(Res.string.media_captions)
    MediaType.THUMBNAIL -> text(Res.string.media_thumbnail)
}

@Composable
internal fun VideoContainerProfile.displayName(): String = when (this) {
    VideoContainerProfile.AUTO -> text(Res.string.auto)
    VideoContainerProfile.MP4 -> "MP4"
    VideoContainerProfile.IOS_COMPATIBLE -> text(Res.string.profile_ios)
}

@Composable
private fun VideoCodec.displayName(): String = when (this) {
    VideoCodec.AUTO -> text(Res.string.auto)
    VideoCodec.H264 -> "H.264"
    VideoCodec.HEVC -> "HEVC"
    VideoCodec.AV1 -> "AV1"
    VideoCodec.VP9 -> "VP9"
}

@Composable
internal fun QualityPreference.displayName(): String = when (this) {
    QualityPreference.Best -> text(Res.string.quality_best)
    QualityPreference.Worst -> text(Res.string.quality_worst)
    is QualityPreference.Resolution -> text(Res.string.quality_resolution, token)
}

@Composable
internal fun AudioContainer.displayName(): String = when (this) {
    AudioContainer.M4A -> "M4A"
    AudioContainer.MP3 -> "MP3"
    AudioContainer.OPUS -> "Opus"
    AudioContainer.WAV -> "WAV"
    AudioContainer.FLAC -> "FLAC"
}

@Composable
private fun CaptionPreference.displayName(): String = when (this) {
    CaptionPreference.MANUAL -> text(Res.string.caption_manual)
    CaptionPreference.AUTOMATIC -> text(Res.string.caption_automatic)
    CaptionPreference.EITHER -> text(Res.string.caption_either)
}

@Composable
private fun CaptionFormat.displayName(): String = when (this) {
    CaptionFormat.SRT -> "SRT"
    CaptionFormat.TXT -> "TXT"
    CaptionFormat.VTT -> "VTT"
    CaptionFormat.TTML -> "TTML"
}
