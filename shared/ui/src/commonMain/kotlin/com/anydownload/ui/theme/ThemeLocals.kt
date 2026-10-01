package com.anydownload.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.anydownload.core.domain.ThemePreference

/** The saved appearance choice. [AnyDownloadTheme] turns this into a color scheme. */
val LocalThemePreference = staticCompositionLocalOf { ThemePreference.SYSTEM }

/** Persists a new appearance choice. The header reads this instead of a repository. */
val LocalThemeChanger = staticCompositionLocalOf<(ThemePreference) -> Unit> { { } }
