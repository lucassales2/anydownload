package com.anydownlod.core.validation

/**
 * Decides whether clipboard text is one link this app can download.
 *
 * Compatibility is the same conservative http(s) check as [SourceUrlValidator].
 * A batch, a bare host, or any other scheme is not a single compatible link.
 */
object ClipboardLink {
    fun compatibleUrl(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.lineSequence().count { it.isNotBlank() } != 1) return null
        return when (val result = SourceUrlValidator.validate(text)) {
            is SourceUrlValidation.Valid -> result.url
            is SourceUrlValidation.Invalid -> null
        }
    }
}
