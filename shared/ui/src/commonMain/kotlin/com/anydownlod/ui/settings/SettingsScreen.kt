package com.anydownlod.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.ui.theme.AppTextField
import com.anydownlod.ui.theme.DestructiveOutlinedButton
import com.anydownlod.ui.theme.DestructiveTextButton
import com.anydownlod.ui.theme.MessageStrip
import com.anydownlod.ui.theme.ObjectCard
import com.anydownlod.ui.theme.StatusTone
import com.anydownlod.ui.theme.colors
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.PresetOptionKeys
import com.anydownlod.core.domain.ToolAvailability
import com.anydownlod.core.domain.ToolStatus
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.add_preset
import com.anydownlod.ui.generated.resources.cancel
import com.anydownlod.ui.generated.resources.channel_template
import com.anydownlod.ui.generated.resources.chapter_template
import com.anydownlod.ui.generated.resources.choose_folder
import com.anydownlod.ui.generated.resources.clear_completed
import com.anydownlod.ui.generated.resources.clear_completed_hint
import com.anydownlod.ui.generated.resources.close
import com.anydownlod.ui.generated.resources.cookie_cleared
import com.anydownlod.ui.generated.resources.cookie_configured
import com.anydownlod.ui.generated.resources.cookie_import_desktop_only
import com.anydownlod.ui.generated.resources.cookie_import_failed
import com.anydownlod.ui.generated.resources.cookie_imported
import com.anydownlod.ui.generated.resources.cookie_not_configured
import com.anydownlod.ui.generated.resources.cookie_status
import com.anydownlod.ui.generated.resources.cookie_warning
import com.anydownlod.ui.generated.resources.default_concurrent
import com.anydownlod.ui.generated.resources.defaults_restored
import com.anydownlod.ui.generated.resources.delete
import com.anydownlod.ui.generated.resources.delete_cookie_body
import com.anydownlod.ui.generated.resources.delete_cookie_title
import com.anydownlod.ui.generated.resources.down
import com.anydownlod.ui.generated.resources.download_folder_updated
import com.anydownlod.ui.generated.resources.folder_desktop_only
import com.anydownlod.ui.generated.resources.import_file
import com.anydownlod.ui.generated.resources.keep
import com.anydownlod.ui.generated.resources.max_concurrent
import com.anydownlod.ui.generated.resources.name
import com.anydownlod.ui.generated.resources.no_folder
import com.anydownlod.ui.generated.resources.no_presets_yet
import com.anydownlod.ui.generated.resources.options
import com.anydownlod.ui.generated.resources.output_template
import com.anydownlod.ui.generated.resources.playlist_template
import com.anydownlod.ui.generated.resources.preset_added
import com.anydownlod.ui.generated.resources.preset_name_required
import com.anydownlod.ui.generated.resources.preset_no_options
import com.anydownlod.ui.generated.resources.presets_layer_hint
import com.anydownlod.ui.generated.resources.remove
import com.anydownlod.ui.generated.resources.replace_file
import com.anydownlod.ui.generated.resources.restore
import com.anydownlod.ui.generated.resources.restore_defaults
import com.anydownlod.ui.generated.resources.restore_defaults_body
import com.anydownlod.ui.generated.resources.restore_defaults_title
import com.anydownlod.ui.generated.resources.save
import com.anydownlod.ui.generated.resources.section_cookies
import com.anydownlod.ui.generated.resources.section_filenames
import com.anydownlod.ui.generated.resources.section_presets
import com.anydownlod.ui.generated.resources.section_queue
import com.anydownlod.ui.generated.resources.section_storage
import com.anydownlod.ui.generated.resources.section_tools
import com.anydownlod.ui.generated.resources.settings
import com.anydownlod.ui.generated.resources.settings_subtitle
import com.anydownlod.ui.generated.resources.templates_hint
import com.anydownlod.ui.generated.resources.theme
import com.anydownlod.ui.generated.resources.tool_available
import com.anydownlod.ui.generated.resources.tool_not_checked
import com.anydownlod.ui.generated.resources.tool_not_found
import com.anydownlod.ui.generated.resources.tools_hint
import com.anydownlod.ui.generated.resources.up
import com.anydownlod.ui.i18n.UiText
import com.anydownlod.ui.i18n.labelResource
import com.anydownlod.ui.i18n.presetOptionLabel
import com.anydownlod.ui.i18n.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * Local preferences: Storage, Filenames, Queue, Cookies, Presets, Appearance,
 * and Tools. Nothing here reads a cookie file or spawns a process.
 */
@Composable
fun SettingsScreen(graph: AppGraph, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val settings by graph.settings.settings.collectAsState()
    val presenter = remember(graph.settings) { SettingsPresenter(graph.settings) }

    var tools by remember { mutableStateOf<ToolStatus?>(null) }
    var notice by remember { mutableStateOf<UiText?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmCookieDelete by remember { mutableStateOf(false) }
    var addingPreset by remember { mutableStateOf(false) }

    var outputTemplate by remember { mutableStateOf(settings.outputTemplate) }
    var outputError by remember { mutableStateOf<UiText?>(null) }
    var playlistTemplate by remember { mutableStateOf(settings.playlistTemplate) }
    var playlistError by remember { mutableStateOf<UiText?>(null) }
    var channelTemplate by remember { mutableStateOf(settings.channelTemplate) }
    var channelError by remember { mutableStateOf<UiText?>(null) }
    var chapterTemplate by remember { mutableStateOf(settings.chapterTemplate) }
    var chapterError by remember { mutableStateOf<UiText?>(null) }
    var concurrencyText by remember { mutableStateOf(settings.maxConcurrentDownloads.toString()) }
    var concurrencyError by remember { mutableStateOf<UiText?>(null) }
    var clearMinutesText by remember {
        mutableStateOf((settings.clearCompletedAfterSeconds / 60).toString())
    }
    var clearMinutesError by remember { mutableStateOf<UiText?>(null) }

    LaunchedEffect(graph.toolProbe) {
        tools = graph.toolProbe.probe()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(Res.string.settings), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(Res.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onClose, modifier = Modifier.testTag("settings-close")) {
                Text(stringResource(Res.string.close))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppearanceSection(
                theme = settings.theme,
                onThemeChange = presenter::setTheme,
            )

            StorageSection(
                downloadRoot = settings.downloadRoot,
                onChooseFolder = {
                    val chosen = graph.pickFolder()
                    if (chosen != null) {
                        presenter.setDownloadRoot(chosen)
                        notice = UiText.of(Res.string.download_folder_updated)
                    } else {
                        notice = UiText.of(Res.string.folder_desktop_only)
                    }
                },
            )

            FilenamesSection(
                outputTemplate = outputTemplate,
                outputError = outputError,
                onOutputChange = { value ->
                    outputTemplate = value
                    outputError = presenter.setOutputTemplate(value)
                },
                playlistTemplate = playlistTemplate,
                playlistError = playlistError,
                onPlaylistChange = { value ->
                    playlistTemplate = value
                    playlistError = presenter.setPlaylistTemplate(value)
                },
                channelTemplate = channelTemplate,
                channelError = channelError,
                onChannelChange = { value ->
                    channelTemplate = value
                    channelError = presenter.setChannelTemplate(value)
                },
                chapterTemplate = chapterTemplate,
                chapterError = chapterError,
                onChapterChange = { value ->
                    chapterTemplate = value
                    chapterError = presenter.setChapterTemplate(value)
                },
            )

            QueueSection(
                concurrencyText = concurrencyText,
                concurrencyError = concurrencyError,
                onConcurrencyChange = { value ->
                    val digits = value.filter(Char::isDigit)
                    concurrencyText = digits
                    concurrencyError = presenter.setMaxConcurrentDownloads(digits)
                },
                clearMinutesText = clearMinutesText,
                clearMinutesError = clearMinutesError,
                onClearMinutesChange = { value ->
                    val digits = value.filter(Char::isDigit)
                    clearMinutesText = digits
                    clearMinutesError = presenter.setClearCompletedMinutes(digits)
                },
            )

            CookiesSection(
                configured = settings.cookiesConfigured,
                onImport = {
                    val picked = graph.pickCookieFile()
                    if (picked == null) {
                        notice = UiText.of(Res.string.cookie_import_desktop_only)
                    } else {
                        val result = graph.cookieStore.import(picked)
                        if (result.success) {
                            presenter.setCookiesConfigured(true)
                            notice = UiText.of(Res.string.cookie_imported)
                        } else {
                            notice = result.message?.let(UiText::raw)
                                ?: UiText.of(Res.string.cookie_import_failed)
                        }
                    }
                },
                onDelete = { confirmCookieDelete = true },
            )

            PresetsSection(
                presets = settings.presets,
                onAdd = { addingPreset = true },
                onRemove = { presenter.removePreset(it) },
                onMoveUp = { presenter.movePresetUp(it) },
                onMoveDown = { presenter.movePresetDown(it) },
            )

            ToolsSection(tools = tools)

            notice?.let { message ->
                MessageStrip(text = message.resolve(), tone = StatusTone.Information)
            }

            DestructiveOutlinedButton(
                text = stringResource(Res.string.restore_defaults),
                onClick = { confirmRestore = true },
                modifier = Modifier.testTag("settings-restore-defaults"),
            )
        }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(Res.string.restore_defaults_title)) },
            text = { Text(stringResource(Res.string.restore_defaults_body)) },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(Res.string.restore),
                    onClick = {
                        presenter.restoreDefaults()
                        outputTemplate = AppSettingsDefaults.OUTPUT_TEMPLATE
                        outputError = null
                        playlistTemplate = AppSettingsDefaults.PLAYLIST_TEMPLATE
                        playlistError = null
                        channelTemplate = AppSettingsDefaults.CHANNEL_TEMPLATE
                        channelError = null
                        chapterTemplate = AppSettingsDefaults.CHAPTER_TEMPLATE
                        chapterError = null
                        concurrencyText = AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS.toString()
                        concurrencyError = null
                        clearMinutesText = "0"
                        clearMinutesError = null
                        confirmRestore = false
                        notice = UiText.of(Res.string.defaults_restored)
                    },
                    modifier = Modifier.testTag("settings-confirm-restore"),
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (confirmCookieDelete) {
        AlertDialog(
            onDismissRequest = { confirmCookieDelete = false },
            title = { Text(stringResource(Res.string.delete_cookie_title)) },
            text = { Text(stringResource(Res.string.delete_cookie_body)) },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(Res.string.delete),
                    onClick = {
                        graph.cookieStore.delete()
                        presenter.setCookiesConfigured(false)
                        confirmCookieDelete = false
                        notice = UiText.of(Res.string.cookie_cleared)
                    },
                    modifier = Modifier.testTag("settings-confirm-cookie-delete"),
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmCookieDelete = false }) {
                    Text(stringResource(Res.string.keep))
                }
            },
        )
    }

    if (addingPreset) {
        AddPresetDialog(
            onDismiss = { addingPreset = false },
            onAdd = { name, options ->
                presenter.addPreset(name, options)
                addingPreset = false
                notice = UiText.of(Res.string.preset_added)
            },
        )
    }
}

@Composable
private fun StorageSection(downloadRoot: String, onChooseFolder: () -> Unit) {
    SettingsSection(stringResource(Res.string.section_storage)) {
        Text(
            text = downloadRoot.ifBlank { stringResource(Res.string.no_folder) },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onChooseFolder, modifier = Modifier.testTag("settings-choose-folder")) {
            Text(stringResource(Res.string.choose_folder))
        }
    }
}

@Composable
private fun FilenamesSection(
    outputTemplate: String,
    outputError: UiText?,
    onOutputChange: (String) -> Unit,
    playlistTemplate: String,
    playlistError: UiText?,
    onPlaylistChange: (String) -> Unit,
    channelTemplate: String,
    channelError: UiText?,
    onChannelChange: (String) -> Unit,
    chapterTemplate: String,
    chapterError: UiText?,
    onChapterChange: (String) -> Unit,
) {
    SettingsSection(stringResource(Res.string.section_filenames)) {
        Text(
            text = stringResource(Res.string.templates_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        TemplateField(
            label = stringResource(Res.string.output_template),
            value = outputTemplate,
            error = outputError,
            testTag = "settings-template-output",
            onValueChange = onOutputChange,
        )
        TemplateField(
            label = stringResource(Res.string.playlist_template),
            value = playlistTemplate,
            error = playlistError,
            testTag = "settings-template-playlist",
            onValueChange = onPlaylistChange,
        )
        TemplateField(
            label = stringResource(Res.string.channel_template),
            value = channelTemplate,
            error = channelError,
            testTag = "settings-template-channel",
            onValueChange = onChannelChange,
        )
        TemplateField(
            label = stringResource(Res.string.chapter_template),
            value = chapterTemplate,
            error = chapterError,
            testTag = "settings-template-chapter",
            onValueChange = onChapterChange,
        )
    }
}

@Composable
private fun TemplateField(
    label: String,
    value: String,
    error: UiText?,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    val currentError = error
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        label = { Text(label) },
        isError = currentError != null,
        supportingText = currentError?.let { message -> { Text(message.resolve()) } },
        singleLine = true,
    )
}

@Composable
private fun QueueSection(
    concurrencyText: String,
    concurrencyError: UiText?,
    onConcurrencyChange: (String) -> Unit,
    clearMinutesText: String,
    clearMinutesError: UiText?,
    onClearMinutesChange: (String) -> Unit,
) {
    SettingsSection(stringResource(Res.string.section_queue)) {
        NumberField(
            label = stringResource(Res.string.max_concurrent),
            value = concurrencyText,
            error = concurrencyError,
            testTag = "settings-concurrency",
            supporting = stringResource(Res.string.default_concurrent),
            onValueChange = onConcurrencyChange,
        )
        NumberField(
            label = stringResource(Res.string.clear_completed),
            value = clearMinutesText,
            error = clearMinutesError,
            testTag = "settings-clear-completed",
            supporting = stringResource(Res.string.clear_completed_hint),
            onValueChange = onClearMinutesChange,
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: UiText?,
    testTag: String,
    supporting: String,
    onValueChange: (String) -> Unit,
) {
    val currentError = error
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        label = { Text(label) },
        isError = currentError != null,
        supportingText = {
            Text(currentError?.resolve() ?: supporting)
        },
        singleLine = true,
    )
}

@Composable
private fun CookiesSection(
    configured: Boolean,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusLabel = if (configured) {
        stringResource(Res.string.cookie_configured)
    } else {
        stringResource(Res.string.cookie_not_configured)
    }
    SettingsSection(stringResource(Res.string.section_cookies)) {
        Text(
            text = stringResource(Res.string.cookie_status, statusLabel),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(Res.string.cookie_warning),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImport, modifier = Modifier.testTag("settings-cookies-import")) {
                Text(
                    if (configured) {
                        stringResource(Res.string.replace_file)
                    } else {
                        stringResource(Res.string.import_file)
                    }
                )
            }
            DestructiveOutlinedButton(
                text = stringResource(Res.string.delete),
                onClick = onDelete,
                enabled = configured,
                modifier = Modifier.testTag("settings-cookies-delete"),
            )
        }
    }
}

@Composable
private fun PresetsSection(
    presets: List<Preset>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
) {
    SettingsSection(stringResource(Res.string.section_presets)) {
        Text(
            text = stringResource(Res.string.presets_layer_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        if (presets.isEmpty()) {
            Text(stringResource(Res.string.no_presets_yet), style = MaterialTheme.typography.bodyMedium)
        }
        presets.forEachIndexed { index, preset ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = presetSummary(preset),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = { onMoveUp(preset.id) },
                    enabled = index > 0,
                    modifier = Modifier.testTag("settings-preset-up-${preset.id}"),
                ) {
                    Text(stringResource(Res.string.up))
                }
                TextButton(
                    onClick = { onMoveDown(preset.id) },
                    enabled = index < presets.lastIndex,
                    modifier = Modifier.testTag("settings-preset-down-${preset.id}"),
                ) {
                    Text(stringResource(Res.string.down))
                }
                TextButton(
                    onClick = { onRemove(preset.id) },
                    modifier = Modifier.testTag("settings-preset-remove-${preset.id}"),
                ) {
                    Text(stringResource(Res.string.remove))
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.testTag("settings-add-preset")) {
            Text(stringResource(Res.string.add_preset))
        }
    }
}

@Composable
private fun presetSummary(preset: Preset): String =
    if (preset.options.isEmpty()) {
        stringResource(Res.string.preset_no_options)
    } else {
        preset.options.keys.map { presetOptionLabel(it).resolve() }.joinToString()
    }

@Composable
private fun ToolsSection(tools: ToolStatus?) {
    SettingsSection(stringResource(Res.string.section_tools)) {
        ToolRow("yt-dlp", tools?.ytDlp, "settings-tool-ytdlp")
        ToolRow("ffmpeg", tools?.ffmpeg, "settings-tool-ffmpeg")
        Text(
            text = stringResource(Res.string.tools_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ToolRow(label: String, availability: ToolAvailability?, testTag: String) {
    val tone = when {
        availability == null -> null
        availability.available -> StatusTone.Positive
        else -> StatusTone.Negative
    }
    val color = tone?.colors()?.foreground ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = when {
                availability == null -> stringResource(Res.string.tool_not_checked)
                availability.available -> availability.version ?: stringResource(Res.string.tool_available)
                else -> stringResource(Res.string.tool_not_found)
            },
            color = color,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        ObjectCard(contentPadding = 16.dp, content = content)
    }
}

@Composable
private fun AddPresetDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Map<String, String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_preset)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("settings-preset-name"),
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true,
                )
                Text(stringResource(Res.string.options), style = MaterialTheme.typography.labelLarge)
                PresetOptionKeys.all.forEach { key ->
                    Row(
                        modifier = Modifier
                            .toggleable(
                                value = selected[key] == true,
                                role = Role.Checkbox,
                                onValueChange = { checked -> selected[key] = checked },
                            )
                            .testTag("settings-preset-option-$key"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected[key] == true, onCheckedChange = null)
                        Text(
                            presetOptionLabel(key).resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                error?.let { message ->
                    Text(
                        text = message.resolve(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = UiText.of(Res.string.preset_name_required)
                    } else {
                        onAdd(
                            name,
                            selected.filterValues { it }.keys.associateWith { "true" },
                        )
                    }
                },
                modifier = Modifier.testTag("settings-preset-save"),
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings-preset-cancel")) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
