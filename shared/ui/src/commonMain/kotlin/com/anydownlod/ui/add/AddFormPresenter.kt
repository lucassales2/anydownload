package com.anydownlod.ui.add

import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.Subscription
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.core.music.SpotifyQueryParser
import com.anydownlod.core.validation.BatchUrlValidator
import com.anydownlod.core.validation.ClipboardLink
import com.anydownlod.core.validation.SourceUrlValidation
import com.anydownlod.core.validation.SourceUrlValidator
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.added_jobs
import com.anydownlod.ui.generated.resources.already_queued
import com.anydownlod.ui.generated.resources.clipboard_empty
import com.anydownlod.ui.generated.resources.clipboard_link_found
import com.anydownlod.ui.generated.resources.no_valid_urls
import com.anydownlod.ui.generated.resources.nothing_to_add
import com.anydownlod.ui.generated.resources.paste_one_url
import com.anydownlod.ui.generated.resources.rejected_lines
import com.anydownlod.ui.generated.resources.subscribe_needs_url
import com.anydownlod.ui.generated.resources.subscribe_not_batch
import com.anydownlod.ui.generated.resources.subscribed
import com.anydownlod.ui.generated.resources.url_error_one_only
import com.anydownlod.ui.i18n.UiText
import com.anydownlod.ui.i18n.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/** What one Download click did. */
data class AddSubmitReport(
    val acceptedUrls: List<String>,
    val rejected: List<AddLineError>,
    val started: Int,
    val pending: Int,
)

/** What one Subscribe click did. */
data class AddSubscribeReport(
    val subscription: Subscription?,
    val rejectedReason: UiText?,
) {
    val accepted: Boolean get() = subscription != null
}

/**
 * State and behavior of the add form. The composable stays thin; this class is
 * where the field rules, batch split, idempotency keys, and repository calls
 * are tested.
 *
 * A click builds one batch key and each accepted line gets
 * `"<batchId>-<lineIndex>"`. The accepted lines are removed from the field
 * immediately, so the second click of a double click has nothing left to
 * submit; pasting the same URL again is an intentional new download and gets a
 * new key.
 */
class AddFormPresenter(
    private val engine: DownloadEngine,
    private val subscriptions: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val idGenerator: () -> String = { "add-${Random.nextLong().toULong().toString(16)}" },
) {
    private val _state = MutableStateFlow(AddFormState())
    val state: StateFlow<AddFormState> = _state.asStateFlow()

    private val _status = MutableStateFlow<AddStatus?>(null)
    val status: StateFlow<AddStatus?> = _status.asStateFlow()

    private val submittedJobIds = mutableSetOf<String>()

    fun setUrl(value: String) = mutate { it.copy(urlText = value, lineErrors = emptyList()) }

    /**
     * Replaces the URL field with clipboard text. A blank clipboard leaves the
     * field as it is and reports that there was nothing to paste.
     */
    fun applyPastedText(text: String?) {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) {
            _status.value = AddStatus(UiText.of(Res.string.clipboard_empty), isError = true)
            return
        }
        setUrl(value)
    }

    /**
     * Puts a clipboard link into an empty field. Returns the URL when it was
     * applied. A non-empty field is left alone so typed text is not replaced.
     */
    fun applyDetectedLink(url: String): String? {
        val compatible = ClipboardLink.compatibleUrl(url) ?: return null
        if (_state.value.urlText.isNotBlank()) return null
        setUrl(compatible)
        _status.value = AddStatus(UiText.of(Res.string.clipboard_link_found))
        return compatible
    }

    /**
     * The one previewable input in the field: an http(s) URL, a Spotify link,
     * or Spotify search text. Null for a batch, empty, or an input error. A
     * batch keeps the direct download path; a single input opens the preview.
     */
    fun singleSourceUrl(): String? {
        val current = _state.value
        if (current.inputError() != null) return null
        val lines = current.urlText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size != 1) return null
        val single = lines.single()
        if (SpotifyQueryParser.isSpotifyInput(single)) return single
        return (SourceUrlValidator.validate(single) as? SourceUrlValidation.Valid)?.url
    }

    /**
     * T-053 idle-field submit: accepts exactly one compatible HTTP(S) URL or
     * one Spotify query. Returns that input (the caller then opens the
     * metadata preview), or records the validator message on the status line
     * and returns null. This never starts a job and never clears the field.
     */
    fun validateForPreview(): String? {
        val current = _state.value
        val lines = current.urlText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size > 1) {
            _status.value = AddStatus(UiText.of(Res.string.url_error_one_only), isError = true)
            return null
        }
        val single = lines.singleOrNull().orEmpty()
        if (SpotifyQueryParser.isSpotifyInput(single)) return single
        return when (val validation = SourceUrlValidator.validate(single)) {
            is SourceUrlValidation.Valid -> validation.url
            is SourceUrlValidation.Invalid -> {
                _status.value = AddStatus(validation.error.toUiText(), isError = true)
                null
            }
        }
    }

    /** The current form options; the Spotify queue path uses them. */
    fun currentOptions(): DownloadOptions =
        _state.value.toDownloadOptions(settingsRepository.settings.value)

    fun setMediaType(value: MediaType) = mutate { it.copy(mediaType = value, lineErrors = emptyList()) }

    fun setStartPolicy(value: StartPolicy) = mutate { it.copy(startPolicy = value) }

    fun setFilenamePrefix(value: String) = mutate { it.copy(filenamePrefix = value) }

    fun setDestinationFolder(value: String) = mutate { it.copy(destinationFolder = value) }

    fun setVideoProfile(value: VideoContainerProfile) = mutate { it.copy(videoProfile = value) }

    fun setVideoCodec(value: VideoCodec) = mutate { it.copy(videoCodec = value) }

    fun setQuality(value: QualityPreference) = mutate { it.copy(quality = value) }

    fun setAudioContainer(value: AudioContainer) = mutate { current ->
        current.copy(
            audioContainer = value,
            audioBitrate = if (value.isLossy) current.audioBitrate else "",
        )
    }

    fun setAudioBitrate(value: String) = mutate { it.copy(audioBitrate = value.filter(Char::isDigit)) }

    fun setCaptionLanguage(value: String) = mutate { it.copy(captionLanguage = value) }

    fun setCaptionPreference(value: CaptionPreference) = mutate { it.copy(captionPreference = value) }

    fun setCaptionFormat(value: CaptionFormat) = mutate { it.copy(captionFormat = value) }

    fun toggleAdvancedExpanded() = mutate { it.copy(advancedExpanded = !it.advancedExpanded) }

    fun setPlaylistItemLimit(value: String) =
        mutate { it.copy(playlistItemLimit = value.filter(Char::isDigit)) }

    fun setClipStart(value: String) = mutate { it.copy(clipStart = value) }

    fun setClipEnd(value: String) = mutate { it.copy(clipEnd = value) }

    fun setSplitByChapters(value: Boolean) = mutate { it.copy(splitByChapters = value) }

    fun setSponsorBlockRemove(value: Boolean) = mutate { it.copy(sponsorBlockRemove = value) }

    fun setEmbedSubtitles(value: Boolean) = mutate { it.copy(embedSubtitles = value) }

    fun setWriteMetadata(value: Boolean) = mutate { it.copy(writeMetadata = value) }

    fun setWriteThumbnail(value: Boolean) = mutate { it.copy(writeThumbnail = value) }

    fun setUseCookies(value: Boolean) = mutate { it.copy(useCookies = value) }

    fun setPresetSelected(id: String, selected: Boolean) = mutate { current ->
        current.copy(
            selectedPresetIds = if (selected) {
                if (id in current.selectedPresetIds) current.selectedPresetIds else current.selectedPresetIds + id
            } else {
                current.selectedPresetIds.filterNot { it == id }
            },
        )
    }

    /**
     * Submits every valid line through the engine and clears only the accepted
     * lines. Returns null when the form has an inline error or no valid URL.
     *
     * [selectedMediaIds] is the multi-media preview selection (an X status);
     * it rides on every request of this submission and stays empty for other
     * sources and for the batch field.
     */
    fun submit(selectedMediaIds: List<String> = emptyList()): AddSubmitReport? {
        val current = _state.value
        current.inputError()?.let { error ->
            _status.value = AddStatus(error, isError = true)
            return null
        }

        val batch = BatchUrlValidator.validate(current.urlText)
        val rejected = batch.invalid.map { it.error.toAddLineError(it.raw) }
        if (batch.validUrls.isEmpty()) {
            _state.update { it.copy(lineErrors = rejected) }
            _status.value = AddStatus(
                message = UiText.of(
                    if (rejected.isEmpty()) Res.string.paste_one_url else Res.string.no_valid_urls,
                ),
                isError = true,
            )
            return null
        }

        val options = current.toDownloadOptions(settingsRepository.settings.value)
        val batchKey = "add-${idGenerator()}"
        var started = 0
        var pending = 0
        var duplicate = 0
        batch.validUrls.forEachIndexed { index, url ->
            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = url,
                    options = options,
                    idempotencyKey = "$batchKey-$index",
                    selectedMediaIds = selectedMediaIds,
                )
            )
            if (submittedJobIds.add(job.id)) {
                if (job.request.options.startPolicy == StartPolicy.AUTOMATIC) started++ else pending++
            } else {
                duplicate++
            }
        }

        val rejectedLines = rejected.map { it.line }.toSet()
        val remainingText = current.urlText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it in rejectedLines }
            .joinToString("\n")

        _state.update { it.copy(urlText = remainingText, lineErrors = rejected) }
        _status.value = AddStatus(
            message = submitMessage(started = started, pending = pending, duplicate = duplicate, rejected = rejected.size),
        )
        return AddSubmitReport(
            acceptedUrls = batch.validUrls,
            rejected = rejected,
            started = started,
            pending = pending,
        )
    }

    /**
     * Subscribes to a single channel or playlist URL with the current options.
     * A pasted batch is refused; the row itself is created by T-030's screen.
     */
    fun subscribe(): AddSubscribeReport {
        val current = _state.value
        val lines = current.urlText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) {
            return rejectSubscribe(UiText.of(Res.string.subscribe_needs_url))
        }
        if (lines.size > 1) {
            return rejectSubscribe(UiText.of(Res.string.subscribe_not_batch))
        }

        val validated = SourceUrlValidator.validate(lines.single())
        if (validated is SourceUrlValidation.Invalid) {
            return rejectSubscribe(validated.error.toUiText())
        }
        current.inputError()?.let { return rejectSubscribe(it) }

        val url = (validated as SourceUrlValidation.Valid).url
        val settings = settingsRepository.settings.value
        val subscription = subscriptions.add(
            sourceUrl = url,
            displayName = hostOf(url) ?: url,
            downloadOptions = current.toDownloadOptions(settings),
            checkIntervalMinutes = settings.subscriptionIntervalMinutes,
        )
        _status.value = AddStatus(UiText.of(Res.string.subscribed, subscription.displayName))
        return AddSubscribeReport(subscription = subscription, rejectedReason = null)
    }

    private fun rejectSubscribe(reason: UiText): AddSubscribeReport {
        _status.value = AddStatus(reason, isError = true)
        return AddSubscribeReport(subscription = null, rejectedReason = reason)
    }

    private fun mutate(transform: (AddFormState) -> AddFormState) {
        _state.update(transform)
        _status.value = null
    }
}

private fun AddFormState.inputError(): UiText? = destinationError ?: playlistLimitError ?: clipError

private fun submitMessage(started: Int, pending: Int, duplicate: Int, rejected: Int): UiText {
    val added = started + pending
    val parts = mutableListOf<UiText>()
    if (added > 0) {
        parts += UiText.quantity(Res.plurals.added_jobs, added, added, started, pending)
    }
    if (duplicate > 0) parts += UiText.of(Res.string.already_queued, duplicate)
    if (rejected > 0) parts += UiText.quantity(Res.plurals.rejected_lines, rejected, rejected)
    if (parts.isEmpty()) parts += UiText.of(Res.string.nothing_to_add)
    return UiText.combined(parts)
}

private fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return null
    return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').ifEmpty { null }
}
