package com.anydownlod.ui.i18n

import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.PresetOptionKeys
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.validation.RelativePathError
import com.anydownlod.core.validation.SourceUrlError
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.active
import com.anydownlod.ui.generated.resources.check_failed
import com.anydownlod.ui.generated.resources.embed_subtitles
import com.anydownlod.ui.generated.resources.job_cancelled
import com.anydownlod.ui.generated.resources.job_completed
import com.anydownlod.ui.generated.resources.job_downloading
import com.anydownlod.ui.generated.resources.job_failed
import com.anydownlod.ui.generated.resources.job_pending
import com.anydownlod.ui.generated.resources.job_postprocessing
import com.anydownlod.ui.generated.resources.job_queued
import com.anydownlod.ui.generated.resources.job_resolving
import com.anydownlod.ui.generated.resources.job_waiting
import com.anydownlod.ui.generated.resources.paused
import com.anydownlod.ui.generated.resources.path_error_absolute
import com.anydownlod.ui.generated.resources.path_error_dot
import com.anydownlod.ui.generated.resources.path_error_empty_segment
import com.anydownlod.ui.generated.resources.sponsorblock_remove
import com.anydownlod.ui.generated.resources.split_by_chapters
import com.anydownlod.ui.generated.resources.tab_completed
import com.anydownlod.ui.generated.resources.tab_downloading
import com.anydownlod.ui.generated.resources.tab_subscriptions
import com.anydownlod.ui.generated.resources.theme_dark
import com.anydownlod.ui.generated.resources.theme_light
import com.anydownlod.ui.generated.resources.theme_system
import com.anydownlod.ui.generated.resources.url_error_blank
import com.anydownlod.ui.generated.resources.url_error_host
import com.anydownlod.ui.generated.resources.url_error_scheme
import com.anydownlod.ui.generated.resources.url_error_userinfo
import com.anydownlod.ui.generated.resources.url_error_whitespace
import com.anydownlod.ui.generated.resources.write_metadata
import com.anydownlod.ui.generated.resources.write_thumbnail
import com.anydownlod.ui.shell.ShellTab

fun SourceUrlError.toUiText(): UiText = UiText.of(
    when (this) {
        SourceUrlError.Blank -> Res.string.url_error_blank
        SourceUrlError.Whitespace -> Res.string.url_error_whitespace
        SourceUrlError.UnsupportedScheme -> Res.string.url_error_scheme
        SourceUrlError.MissingHost -> Res.string.url_error_host
        SourceUrlError.Userinfo -> Res.string.url_error_userinfo
    },
)

fun RelativePathError.toUiText(): UiText = UiText.of(
    when (this) {
        RelativePathError.Absolute -> Res.string.path_error_absolute
        RelativePathError.EmptySegment -> Res.string.path_error_empty_segment
        RelativePathError.DotSegment -> Res.string.path_error_dot
    },
)

fun JobState.labelResource(): UiText = when (this) {
    JobState.RESOLVING -> UiText.of(Res.string.job_resolving)
    JobState.PENDING -> UiText.of(Res.string.job_pending)
    JobState.SCHEDULED -> UiText.of(Res.string.job_waiting)
    JobState.QUEUED -> UiText.of(Res.string.job_queued)
    JobState.DOWNLOADING -> UiText.of(Res.string.job_downloading)
    JobState.POSTPROCESSING -> UiText.of(Res.string.job_postprocessing)
    JobState.COMPLETED -> UiText.of(Res.string.job_completed)
    JobState.FAILED -> UiText.of(Res.string.job_failed)
    JobState.CANCELLED -> UiText.of(Res.string.job_cancelled)
    JobState.UNKNOWN -> UiText.raw(wireName)
}

fun ThemePreference.labelResource(): UiText = UiText.of(
    when (this) {
        ThemePreference.SYSTEM -> Res.string.theme_system
        ThemePreference.LIGHT -> Res.string.theme_light
        ThemePreference.DARK -> Res.string.theme_dark
    },
)

fun ShellTab.labelResource(): UiText = UiText.of(
    when (this) {
        ShellTab.DOWNLOADING -> Res.string.tab_downloading
        ShellTab.COMPLETED -> Res.string.tab_completed
        ShellTab.SUBSCRIPTIONS -> Res.string.tab_subscriptions
    },
)

fun presetOptionLabel(key: String): UiText = when (key) {
    PresetOptionKeys.EMBED_SUBTITLES -> UiText.of(Res.string.embed_subtitles)
    PresetOptionKeys.WRITE_METADATA -> UiText.of(Res.string.write_metadata)
    PresetOptionKeys.WRITE_THUMBNAIL -> UiText.of(Res.string.write_thumbnail)
    PresetOptionKeys.SPLIT_BY_CHAPTERS -> UiText.of(Res.string.split_by_chapters)
    PresetOptionKeys.SPONSORBLOCK_REMOVE -> UiText.of(Res.string.sponsorblock_remove)
    else -> UiText.raw(key)
}

fun subscriptionStatusLabel(paused: Boolean, hasError: Boolean): UiText = when {
    paused -> UiText.of(Res.string.paused)
    hasError -> UiText.of(Res.string.check_failed)
    else -> UiText.of(Res.string.active)
}
