package com.anydownlod.ui.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance

/**
 * Android sets this so a dark canvas can use light status-bar icons.
 * Other hosts leave it empty.
 */
val LocalLightStatusBarIcons = staticCompositionLocalOf<(Boolean) -> Unit> { { } }

/** Home and the download list. */
enum class PhonePage {
    Home,
    Downloads,
}

/**
 * Android phone shell. Wide desktop windows keep [com.anydownlod.ui.shell.AppShell].
 * [page] is hoisted so opening Settings returns to the same screen.
 */
@Composable
fun PhoneShell(
    page: PhonePage,
    onPageChange: (PhonePage) -> Unit,
    previewUrl: String?,
    onPreviewUrl: (String) -> Unit,
    onClearPreview: () -> Unit,
    onOpenSettings: () -> Unit,
    startupWarning: String?,
) {
    val lightStatusIcons = LocalLightStatusBarIcons.current
    val lightIcons = phoneColors().canvas.luminance() < 0.5f
    SideEffect { lightStatusIcons(lightIcons) }
    when (page) {
        PhonePage.Home -> PhoneHome(
            previewUrl = previewUrl,
            onPreviewUrl = onPreviewUrl,
            onClearPreview = onClearPreview,
            onOpenSettings = onOpenSettings,
            onViewAll = { onPageChange(PhonePage.Downloads) },
            onDownloadStarted = { onPageChange(PhonePage.Downloads) },
            startupWarning = startupWarning,
        )
        PhonePage.Downloads -> PhoneDownloads(
            onBack = { onPageChange(PhonePage.Home) },
            onNewDownload = { onPageChange(PhonePage.Home) },
        )
    }
}
