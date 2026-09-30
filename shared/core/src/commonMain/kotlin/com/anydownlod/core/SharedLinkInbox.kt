/*
 * Shared-link inbox — AnyDownload (T-020)
 *
 * The host writes an incoming share/deep-link text here; the UI observes it
 * once and consumes it. The inbox is deliberately tiny and carries only the
 * raw shared text until the UI validates it with [com.anydownlod.core.validation.SharedLink].
 */
package com.anydownlod.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedLinkInbox {
    private val _pending = MutableStateFlow<String?>(null)

    /** The last offered text, or null once consumed. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Offers a new shared text; it replaces any unconsumed one. */
    fun offer(text: String?) {
        _pending.value = text?.takeIf { it.isNotBlank() }
    }

    /** Returns and clears the pending text. */
    fun consume(): String? {
        val value = _pending.value
        _pending.value = null
        return value
    }
}
