package com.anydownlod.ui.clipboard

import com.anydownlod.core.validation.ClipboardLink

/** What one clipboard poll should do with the add form. */
sealed interface ClipboardEvent {
    data object None : ClipboardEvent

    /** The field is empty, so the link can be filled in. */
    data class Fill(val url: String) : ClipboardEvent

    /** The field already has different text, so the link is only offered. */
    data class Suggest(val url: String) : ClipboardEvent
}

/**
 * Remembers the last clipboard text so the same link is not offered again
 * on every focus change.
 */
class ClipboardWatch {
    private var hasPolled: Boolean = false
    private var seen: String? = null

    fun poll(raw: String?, fieldText: String, alreadyHandledUrl: String = ""): ClipboardEvent {
        if (hasPolled && raw == seen) return ClipboardEvent.None
        hasPolled = true
        seen = raw
        val url = ClipboardLink.compatibleUrl(raw) ?: return ClipboardEvent.None
        if (url == alreadyHandledUrl) return ClipboardEvent.None
        val current = fieldText.trim()
        return when {
            current.isEmpty() -> ClipboardEvent.Fill(url)
            current == url -> ClipboardEvent.None
            else -> ClipboardEvent.Suggest(url)
        }
    }
}
