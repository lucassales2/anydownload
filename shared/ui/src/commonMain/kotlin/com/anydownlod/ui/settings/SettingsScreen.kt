package com.anydownlod.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.domain.*
import com.anydownlod.core.music.SpotifyAuthService
import com.anydownlod.ui.generated.resources.*
import com.anydownlod.ui.i18n.UiText
import com.anydownlod.ui.i18n.presetOptionLabel
import com.anydownlod.ui.i18n.resolve
import com.anydownlod.ui.theme.*
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Local preferences: Storage, Filenames, Queue, Cookies, Presets, Appearance,
 * and Tools. Nothing here reads a cookie file or spawns a process.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = metroViewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val settings = uiState.settings

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
                onThemeChange = viewModel::setTheme,
            )

            StorageSection(
                downloadRoot = settings.downloadRoot,
                onChooseFolder = viewModel::chooseFolder,
            )

            FilenamesSection(
                outputTemplate = uiState.outputTemplate,
                outputError = uiState.outputError,
                onOutputChange = viewModel::setOutputTemplate,
                playlistTemplate = uiState.playlistTemplate,
                playlistError = uiState.playlistError,
                onPlaylistChange = viewModel::setPlaylistTemplate,
                channelTemplate = uiState.channelTemplate,
                channelError = uiState.channelError,
                onChannelChange = viewModel::setChannelTemplate,
                chapterTemplate = uiState.chapterTemplate,
                chapterError = uiState.chapterError,
                onChapterChange = viewModel::setChapterTemplate,
            )

            QueueSection(
                concurrencyText = uiState.concurrencyText,
                concurrencyError = uiState.concurrencyError,
                onConcurrencyChange = { value -> viewModel.setMaxConcurrentDownloads(value.filter(Char::isDigit)) },
                clearMinutesText = uiState.clearMinutesText,
                clearMinutesError = uiState.clearMinutesError,
                onClearMinutesChange = { value -> viewModel.setClearCompletedMinutes(value.filter(Char::isDigit)) },
            )

            CookiesSection(
                configured = settings.cookiesConfigured,
                onImport = viewModel::importCookies,
                onDelete = viewModel::requestCookieDelete,
            )

            SpotifySection(
                auth = uiState.spotifyAuth,
                onNotice = viewModel::setNotice,
            )

            PresetsSection(
                presets = settings.presets,
                onAdd = viewModel::beginAddPreset,
                onRemove = viewModel::removePreset,
                onMoveUp = viewModel::movePresetUp,
                onMoveDown = viewModel::movePresetDown,
            )

            ToolsSection(tools = uiState.tools)

            uiState.notice?.let { message ->
                MessageStrip(text = message.resolve(), tone = StatusTone.Information)
            }

            DestructiveOutlinedButton(
                text = stringResource(Res.string.restore_defaults),
                onClick = viewModel::requestRestore,
                modifier = Modifier.testTag("settings-restore-defaults"),
            )
        }
        }
    }

    if (uiState.confirmRestore) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestore,
            title = { Text(stringResource(Res.string.restore_defaults_title)) },
            text = { Text(stringResource(Res.string.restore_defaults_body)) },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(Res.string.restore),
                    onClick = viewModel::confirmRestore,
                    modifier = Modifier.testTag("settings-confirm-restore"),
                )
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestore) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (uiState.confirmCookieDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCookieDelete,
            title = { Text(stringResource(Res.string.delete_cookie_title)) },
            text = { Text(stringResource(Res.string.delete_cookie_body)) },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(Res.string.delete),
                    onClick = viewModel::confirmCookieDelete,
                    modifier = Modifier.testTag("settings-confirm-cookie-delete"),
                )
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCookieDelete) {
                    Text(stringResource(Res.string.keep))
                }
            },
        )
    }

    if (uiState.addingPreset) {
        AddPresetDialog(
            onDismiss = viewModel::dismissAddPreset,
            onAdd = { name, options -> viewModel.addPreset(name, options) },
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
private fun SpotifySection(auth: SpotifyAuthService?, onNotice: (UiText) -> Unit) {
    SettingsSection(stringResource(Res.string.section_spotify)) {
        if (auth == null) {
            Text(
                text = stringResource(Res.string.spotify_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SettingsSection
        }
        var loggedIn by remember(auth) { mutableStateOf(auth.isLoggedIn()) }
        var token by remember(auth) { mutableStateOf("") }
        if (loggedIn) {
            Text(
                text = stringResource(Res.string.spotify_logged_in),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = {
                    auth.logout()
                    loggedIn = false
                    onNotice(UiText.of(Res.string.spotify_logout_done))
                },
                modifier = Modifier.testTag("spotify-logout"),
            ) {
                Text(stringResource(Res.string.spotify_logout))
            }
        } else {
            Text(
                text = stringResource(Res.string.spotify_logged_out),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth().testTag("spotify-token-field"),
                label = { Text(stringResource(Res.string.spotify_token_hint)) },
                singleLine = true,
            )
            Button(
                onClick = {
                    auth.login(token)
                    token = ""
                    loggedIn = true
                    onNotice(UiText.of(Res.string.spotify_token_saved))
                },
                enabled = token.isNotBlank(),
                modifier = Modifier.testTag("spotify-login"),
            ) {
                Text(stringResource(Res.string.spotify_login))
            }
        }
    }
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
        JsRuntimeRow(tools?.jsRuntime, "settings-tool-jsruntime")
        Text(
            text = stringResource(Res.string.settings_kotlin_extractors),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("settings-tool-kotlin"),
        )
        Text(
            text = stringResource(Res.string.tools_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The embedded solver runtime: its version, or an honest "none" with the cost. */
@Composable
private fun JsRuntimeRow(availability: ToolAvailability?, testTag: String) {
    val value = when {
        availability == null -> stringResource(Res.string.tool_not_checked)
        availability.available -> availability.version ?: stringResource(Res.string.tool_available)
        else -> stringResource(Res.string.tool_js_none)
    }
    Text(
        text = stringResource(Res.string.settings_js_runtime, value),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(testTag),
    )
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
