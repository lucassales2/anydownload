package com.anydownlod.ui.clipboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Reads the clipboard after permission is granted: once immediately, and again
 * each time the window is focused. [ClipboardWatch] ignores an unchanged copy.
 */
@Composable
internal fun ClipboardLinkWatcher(
    fieldText: String,
    alreadyHandledUrl: String,
    readText: () -> String?,
    onFill: (String) -> Unit,
    onSuggest: (String) -> Unit,
) {
    val watch = remember { ClipboardWatch() }
    val focused = LocalWindowInfo.current.isWindowFocused
    val currentField = rememberUpdatedState(fieldText)
    val currentHandled = rememberUpdatedState(alreadyHandledUrl)
    val currentRead = rememberUpdatedState(readText)
    var polledOnce by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (polledOnce && !focused) return@LaunchedEffect
        polledOnce = true
        when (val event = watch.poll(currentRead.value(), currentField.value, currentHandled.value)) {
            is ClipboardEvent.Fill -> onFill(event.url)
            is ClipboardEvent.Suggest -> onSuggest(event.url)
            ClipboardEvent.None -> Unit
        }
    }
}
