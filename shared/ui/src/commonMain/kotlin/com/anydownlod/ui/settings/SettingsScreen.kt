package com.anydownlod.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.PresetOptionKeys
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.domain.ToolAvailability
import com.anydownlod.core.domain.ToolStatus
import com.anydownlod.ui.shell.label

/**
 * Local preferences: Storage, Filenames, Queue, Cookies, Presets, Appearance,
 * and Tools. Nothing here reads a cookie file or spawns a process.
 */
@Composable
fun SettingsScreen(graph: AppGraph, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val settings by graph.settings.settings.collectAsState()
    val presenter = remember(graph.settings) { SettingsPresenter(graph.settings) }

    var tools by remember { mutableStateOf<ToolStatus?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmCookieDelete by remember { mutableStateOf(false) }
    var addingPreset by remember { mutableStateOf(false) }

    var outputTemplate by remember { mutableStateOf(settings.outputTemplate) }
    var outputError by remember { mutableStateOf<String?>(null) }
    var playlistTemplate by remember { mutableStateOf(settings.playlistTemplate) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    var channelTemplate by remember { mutableStateOf(settings.channelTemplate) }
    var channelError by remember { mutableStateOf<String?>(null) }
    var chapterTemplate by remember { mutableStateOf(settings.chapterTemplate) }
    var chapterError by remember { mutableStateOf<String?>(null) }
    var concurrencyText by remember { mutableStateOf(settings.maxConcurrentDownloads.toString()) }
    var concurrencyError by remember { mutableStateOf<String?>(null) }
    var clearMinutesText by remember {
        mutableStateOf((settings.clearCompletedAfterSeconds / 60).toString())
    }
    var clearMinutesError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(graph.toolProbe) {
        tools = graph.toolProbe.probe()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onClose, modifier = Modifier.testTag("settings-close")) {
                Text("Close")
            }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StorageSection(
                downloadRoot = settings.downloadRoot,
                onChooseFolder = {
                    val chosen = graph.pickFolder()
                    if (chosen != null) {
                        presenter.setDownloadRoot(chosen)
                        notice = "Download folder updated."
                    } else {
                        notice = "Folder selection is available on desktop."
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
                        notice = "Cookie import is only available on desktop."
                    } else {
                        val result = graph.cookieStore.import(picked)
                        if (result.success) {
                            presenter.setCookiesConfigured(true)
                            notice = "Cookie file imported."
                        } else {
                            notice = result.message ?: "That file could not be imported."
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

            AppearanceSection(
                theme = settings.theme,
                onThemeChange = presenter::setTheme,
            )

            ToolsSection(tools = tools)

            notice?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.testTag("settings-restore-defaults"),
            ) {
                Text("Restore defaults")
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore default settings?") },
            text = {
                Text(
                    "Templates, queue limits, subscription interval, and theme return to their defaults. " +
                        "The download folder, cookie status, and presets stay."
                )
            },
            confirmButton = {
                TextButton(
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
                        notice = "Defaults restored."
                    },
                    modifier = Modifier.testTag("settings-confirm-restore"),
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmCookieDelete) {
        AlertDialog(
            onDismissRequest = { confirmCookieDelete = false },
            title = { Text("Delete the cookie file?") },
            text = { Text("The stored cookie file is removed and the status returns to Not configured.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        graph.cookieStore.delete()
                        presenter.setCookiesConfigured(false)
                        confirmCookieDelete = false
                        notice = "Cookie status cleared."
                    },
                    modifier = Modifier.testTag("settings-confirm-cookie-delete"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCookieDelete = false }) {
                    Text("Keep")
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
                notice = "Preset added."
            },
        )
    }
}

@Composable
private fun StorageSection(downloadRoot: String, onChooseFolder: () -> Unit) {
    SettingsSection("Storage") {
        Text(
            text = downloadRoot.ifBlank {
                "No folder chosen. A folder is required before a real download."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onChooseFolder, modifier = Modifier.testTag("settings-choose-folder")) {
            Text("Choose folder")
        }
    }
}

@Composable
private fun FilenamesSection(
    outputTemplate: String,
    outputError: String?,
    onOutputChange: (String) -> Unit,
    playlistTemplate: String,
    playlistError: String?,
    onPlaylistChange: (String) -> Unit,
    channelTemplate: String,
    channelError: String?,
    onChannelChange: (String) -> Unit,
    chapterTemplate: String,
    chapterError: String?,
    onChapterChange: (String) -> Unit,
) {
    SettingsSection("Filenames") {
        Text(
            text = "Templates use tokens such as %(title)s. The filename prefix stays on the add form.",
            style = MaterialTheme.typography.bodySmall,
        )
        TemplateField("Output template", outputTemplate, outputError, "settings-template-output", onOutputChange)
        TemplateField("Playlist template", playlistTemplate, playlistError, "settings-template-playlist", onPlaylistChange)
        TemplateField("Channel template", channelTemplate, channelError, "settings-template-channel", onChannelChange)
        TemplateField("Chapter template", chapterTemplate, chapterError, "settings-template-chapter", onChapterChange)
    }
}

@Composable
private fun TemplateField(
    label: String,
    value: String,
    error: String?,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    val currentError = error
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        label = { Text(label) },
        isError = currentError != null,
        supportingText = currentError?.let { message -> { Text(message) } },
        singleLine = true,
    )
}

@Composable
private fun QueueSection(
    concurrencyText: String,
    concurrencyError: String?,
    onConcurrencyChange: (String) -> Unit,
    clearMinutesText: String,
    clearMinutesError: String?,
    onClearMinutesChange: (String) -> Unit,
) {
    SettingsSection("Queue") {
        NumberField(
            label = "Max concurrent downloads",
            value = concurrencyText,
            error = concurrencyError,
            testTag = "settings-concurrency",
            supporting = "Default 3.",
            onValueChange = onConcurrencyChange,
        )
        NumberField(
            label = "Clear completed after (minutes)",
            value = clearMinutesText,
            error = clearMinutesError,
            testTag = "settings-clear-completed",
            supporting = "0 keeps finished rows forever.",
            onValueChange = onClearMinutesChange,
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    testTag: String,
    supporting: String,
    onValueChange: (String) -> Unit,
) {
    val currentError = error
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        label = { Text(label) },
        isError = currentError != null,
        supportingText = { Text(currentError ?: supporting) },
        singleLine = true,
    )
}

@Composable
private fun CookiesSection(
    configured: Boolean,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsSection("Cookies") {
        Text(
            text = "Status: ${if (configured) "Configured" else "Not configured"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "A cookie file stays on this computer and can expose an account. " +
                "Importing it does not guarantee the site will allow the download. " +
                "Contents are never shown here.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImport, modifier = Modifier.testTag("settings-cookies-import")) {
                Text(if (configured) "Replace" else "Import")
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = configured,
                modifier = Modifier.testTag("settings-cookies-delete"),
            ) {
                Text("Delete")
            }
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
    SettingsSection("Presets") {
        Text(
            text = "Presets layer in this order; a later preset overrides an earlier one on the same key.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (presets.isEmpty()) {
            Text("No presets yet.", style = MaterialTheme.typography.bodyMedium)
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
                    Text("Up")
                }
                TextButton(
                    onClick = { onMoveDown(preset.id) },
                    enabled = index < presets.lastIndex,
                    modifier = Modifier.testTag("settings-preset-down-${preset.id}"),
                ) {
                    Text("Down")
                }
                TextButton(
                    onClick = { onRemove(preset.id) },
                    modifier = Modifier.testTag("settings-preset-remove-${preset.id}"),
                ) {
                    Text("Remove")
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.testTag("settings-add-preset")) {
            Text("Add preset")
        }
    }
}

private fun presetSummary(preset: Preset): String =
    if (preset.options.isEmpty()) {
        "No options"
    } else {
        preset.options.keys.joinToString { PresetOptionKeys.label(it) }
    }

@Composable
private fun AppearanceSection(theme: ThemePreference, onThemeChange: (ThemePreference) -> Unit) {
    SettingsSection("Appearance") {
        Text("Theme", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = option == theme,
                    onClick = { onThemeChange(option) },
                    label = { Text(option.label()) },
                    modifier = Modifier.testTag("settings-theme-${option.wireName}"),
                )
            }
        }
    }
}

@Composable
private fun ToolsSection(tools: ToolStatus?) {
    SettingsSection("Tools") {
        ToolRow("yt-dlp", tools?.ytDlp, "settings-tool-ytdlp")
        ToolRow("ffmpeg", tools?.ffmpeg, "settings-tool-ffmpeg")
        Text(
            text = "Versions come from the local PATH probe. A missing tool stops a real download.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ToolRow(label: String, availability: ToolAvailability?, testTag: String) {
    Row(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = when {
                availability == null -> "Not checked"
                availability.available -> availability.version ?: "Available"
                else -> "Not found"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun AddPresetDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Map<String, String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("settings-preset-name"),
                    label = { Text("Name") },
                    singleLine = true,
                )
                Text("Options", style = MaterialTheme.typography.labelLarge)
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
                        Text(PresetOptionKeys.label(key), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let { message ->
                    Text(
                        text = message,
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
                        error = "Enter a preset name."
                    } else {
                        onAdd(
                            name,
                            selected.filterValues { it }.keys.associateWith { "true" },
                        )
                    }
                },
                modifier = Modifier.testTag("settings-preset-save"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings-preset-cancel")) {
                Text("Cancel")
            }
        },
    )
}
