package com.anydownlod.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydownlod.core.CookieFilePicker
import com.anydownlod.core.CookieStore
import com.anydownlod.core.FolderPicker
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.domain.ToolStatus
import com.anydownlod.core.music.SpotifyAuthService
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.clear_minutes_error
import com.anydownlod.ui.generated.resources.concurrency_error
import com.anydownlod.ui.generated.resources.cookie_cleared
import com.anydownlod.ui.generated.resources.cookie_import_desktop_only
import com.anydownlod.ui.generated.resources.cookie_import_failed
import com.anydownlod.ui.generated.resources.cookie_imported
import com.anydownlod.ui.generated.resources.defaults_restored
import com.anydownlod.ui.generated.resources.download_folder_updated
import com.anydownlod.ui.generated.resources.folder_desktop_only
import com.anydownlod.ui.generated.resources.preset_added
import com.anydownlod.ui.generated.resources.template_absolute
import com.anydownlod.ui.generated.resources.template_empty_segment
import com.anydownlod.ui.generated.resources.template_parent
import com.anydownlod.ui.generated.resources.template_required
import com.anydownlod.ui.i18n.UiText
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Draft fields and dialogs for Settings, plus the saved preferences. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val outputTemplate: String = "",
    val outputError: UiText? = null,
    val playlistTemplate: String = "",
    val playlistError: UiText? = null,
    val channelTemplate: String = "",
    val channelError: UiText? = null,
    val chapterTemplate: String = "",
    val chapterError: UiText? = null,
    val concurrencyText: String = "",
    val concurrencyError: UiText? = null,
    val clearMinutesText: String = "",
    val clearMinutesError: UiText? = null,
    val tools: ToolStatus? = null,
    val notice: UiText? = null,
    val confirmRestore: Boolean = false,
    val confirmCookieDelete: Boolean = false,
    val addingPreset: Boolean = false,
    val spotifyAuth: SpotifyAuthService? = null,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val toolProbe: ToolProbe = object : ToolProbe {
        override suspend fun probe() = ToolStatus()
    },
    cookieStore: CookieStore = CookieStore.Unavailable,
    val spotifyAuth: SpotifyAuthService? = null,
    private val pickFolder: FolderPicker = FolderPicker { null },
    private val pickCookieFile: CookieFilePicker = CookieFilePicker { null },
) : ViewModel() {

    private val cookies = cookieStore
    private val current = repository.settings.value
    private val drafts = MutableStateFlow(draftsFrom(current))

    val state: StateFlow<SettingsUiState> = combine(
        repository.settings,
        drafts,
    ) { settings, draft -> draft.copy(settings = settings, spotifyAuth = spotifyAuth) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            drafts.value.copy(settings = current, spotifyAuth = spotifyAuth),
        )

    init {
        viewModelScope.launch {
            val probed = runCatching { toolProbe.probe() }.getOrNull()
            drafts.update { it.copy(tools = probed) }
        }
    }

    fun setNotice(message: UiText?) {
        drafts.update { it.copy(notice = message) }
    }

    fun setTheme(theme: ThemePreference) {
        repository.update { it.copy(theme = theme) }
    }

    fun chooseFolder() {
        val chosen = pickFolder()
        if (chosen != null) {
            setDownloadRoot(chosen)
            setNotice(UiText.of(Res.string.download_folder_updated))
        } else {
            setNotice(UiText.of(Res.string.folder_desktop_only))
        }
    }

    fun setDownloadRoot(path: String) {
        repository.update { it.copy(downloadRoot = path.trim()) }
    }

    fun setOutputTemplate(value: String): UiText? {
        val error = writeTemplate(value) { current -> current.copy(outputTemplate = value) }
        drafts.update { it.copy(outputTemplate = value, outputError = error) }
        return error
    }

    fun setPlaylistTemplate(value: String): UiText? {
        val error = writeTemplate(value) { current -> current.copy(playlistTemplate = value) }
        drafts.update { it.copy(playlistTemplate = value, playlistError = error) }
        return error
    }

    fun setChannelTemplate(value: String): UiText? {
        val error = writeTemplate(value) { current -> current.copy(channelTemplate = value) }
        drafts.update { it.copy(channelTemplate = value, channelError = error) }
        return error
    }

    fun setChapterTemplate(value: String): UiText? {
        val error = writeTemplate(value) { current -> current.copy(chapterTemplate = value) }
        drafts.update { it.copy(chapterTemplate = value, chapterError = error) }
        return error
    }

    fun setMaxConcurrentDownloads(text: String): UiText? {
        val error = writeConcurrency(text)
        drafts.update { it.copy(concurrencyText = text, concurrencyError = error) }
        return error
    }

    fun setClearCompletedMinutes(text: String): UiText? {
        val error = writeClearMinutes(text)
        drafts.update { it.copy(clearMinutesText = text, clearMinutesError = error) }
        return error
    }

    fun importCookies() {
        val picked = pickCookieFile()
        if (picked == null) {
            setNotice(UiText.of(Res.string.cookie_import_desktop_only))
            return
        }
        val result = cookies.import(picked)
        if (result.success) {
            setCookiesConfigured(true)
            setNotice(UiText.of(Res.string.cookie_imported))
        } else {
            setNotice(result.message?.let(UiText::raw) ?: UiText.of(Res.string.cookie_import_failed))
        }
    }

    fun requestCookieDelete() {
        drafts.update { it.copy(confirmCookieDelete = true) }
    }

    fun dismissCookieDelete() {
        drafts.update { it.copy(confirmCookieDelete = false) }
    }

    fun confirmCookieDelete() {
        cookies.delete()
        setCookiesConfigured(false)
        drafts.update { it.copy(confirmCookieDelete = false, notice = UiText.of(Res.string.cookie_cleared)) }
    }

    fun setCookiesConfigured(configured: Boolean) {
        repository.setCookiesConfigured(configured)
    }

    fun requestRestore() {
        drafts.update { it.copy(confirmRestore = true) }
    }

    fun dismissRestore() {
        drafts.update { it.copy(confirmRestore = false) }
    }

    fun confirmRestore() {
        restoreDefaults()
        drafts.value = draftsFrom(repository.settings.value).copy(
            notice = UiText.of(Res.string.defaults_restored),
            tools = drafts.value.tools,
        )
    }

    fun restoreDefaults() {
        repository.update { current ->
            current.copy(
                outputTemplate = AppSettingsDefaults.OUTPUT_TEMPLATE,
                playlistTemplate = AppSettingsDefaults.PLAYLIST_TEMPLATE,
                channelTemplate = AppSettingsDefaults.CHANNEL_TEMPLATE,
                chapterTemplate = AppSettingsDefaults.CHAPTER_TEMPLATE,
                maxConcurrentDownloads = AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS,
                clearCompletedAfterSeconds = AppSettingsDefaults.CLEAR_COMPLETED_AFTER_SECONDS,
                subscriptionIntervalMinutes = AppSettingsDefaults.SUBSCRIPTION_INTERVAL_MINUTES,
                theme = ThemePreference.SYSTEM,
            )
        }
    }

    fun beginAddPreset() {
        drafts.update { it.copy(addingPreset = true) }
    }

    fun dismissAddPreset() {
        drafts.update { it.copy(addingPreset = false) }
    }

    fun addPreset(name: String, options: Map<String, String> = emptyMap()): Preset? {
        val preset = repository.addPreset(name, options)
        drafts.update { it.copy(addingPreset = false, notice = UiText.of(Res.string.preset_added)) }
        return preset
    }

    fun removePreset(id: String): Boolean = repository.removePreset(id)

    fun movePresetUp(id: String): Boolean {
        val index = repository.settings.value.presets.indexOfFirst { it.id == id }
        if (index <= 0) return false
        return repository.reorderPresets(index, index - 1)
    }

    fun movePresetDown(id: String): Boolean {
        val presets = repository.settings.value.presets
        val index = presets.indexOfFirst { it.id == id }
        if (index == -1 || index >= presets.lastIndex) return false
        return repository.reorderPresets(index, index + 1)
    }

    private fun writeTemplate(value: String, apply: (AppSettings) -> AppSettings): UiText? {
        validateTemplate(value)?.let { return it }
        repository.update(apply)
        return null
    }

    private fun writeConcurrency(digits: String): UiText? {
        val value = digits.trim().toIntOrNull()
        if (value == null || value < 1) return UiText.of(Res.string.concurrency_error)
        repository.update { it.copy(maxConcurrentDownloads = value) }
        return null
    }

    private fun writeClearMinutes(digits: String): UiText? {
        val minutes = digits.trim().toIntOrNull()
        if (minutes == null || minutes < 0) return UiText.of(Res.string.clear_minutes_error)
        repository.update { it.copy(clearCompletedAfterSeconds = minutes * 60L) }
        return null
    }

    private fun draftsFrom(settings: AppSettings) = SettingsUiState(
        settings = settings,
        outputTemplate = settings.outputTemplate,
        playlistTemplate = settings.playlistTemplate,
        channelTemplate = settings.channelTemplate,
        chapterTemplate = settings.chapterTemplate,
        concurrencyText = settings.maxConcurrentDownloads.toString(),
        clearMinutesText = (settings.clearCompletedAfterSeconds / 60).toString(),
        spotifyAuth = spotifyAuth,
    )

    companion object {
        /** A string check only: no yt-dlp run, no filesystem lookup. */
        fun validateTemplate(value: String): UiText? {
            if (value.isBlank()) return UiText.of(Res.string.template_required)
            if (value.startsWith('/') || value.startsWith('\\') || Regex("^[A-Za-z]:").containsMatchIn(value)) {
                return UiText.of(Res.string.template_absolute)
            }
            val segments = value.split('/', '\\')
            if (segments.any { it == ".." }) return UiText.of(Res.string.template_parent)
            if (segments.any { it.isEmpty() }) return UiText.of(Res.string.template_empty_segment)
            return null
        }
    }
}
