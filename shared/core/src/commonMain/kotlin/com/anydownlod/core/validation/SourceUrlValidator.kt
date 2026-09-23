package com.anydownlod.core.validation

/**
 * Result of client-side source URL pre-validation.
 *
 * This is deliberately conservative: the local engine repeats its own checks
 * before spawning a process. The app only rejects input that cannot be a
 * usable HTTP(S) source so users get immediate feedback. A multi-line paste
 * is not one URL; use [BatchUrlValidator] for batches.
 */
/** Why a source URL was rejected. The UI maps each case to a string resource. */
enum class SourceUrlError {
    Blank,
    Whitespace,
    UnsupportedScheme,
    MissingHost,
}

sealed interface SourceUrlValidation {
    data class Valid(val url: String) : SourceUrlValidation
    data class Invalid(val error: SourceUrlError) : SourceUrlValidation
}

object SourceUrlValidator {
    fun validate(raw: String): SourceUrlValidation {
        val candidate = raw.trim()
        if (candidate.isEmpty()) {
            return SourceUrlValidation.Invalid(SourceUrlError.Blank)
        }
        if (candidate.any { it.isWhitespace() }) {
            return SourceUrlValidation.Invalid(SourceUrlError.Whitespace)
        }

        val lower = candidate.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return SourceUrlValidation.Invalid(SourceUrlError.UnsupportedScheme)
        }

        val afterScheme = candidate.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty()) {
            return SourceUrlValidation.Invalid(SourceUrlError.MissingHost)
        }

        return SourceUrlValidation.Valid(candidate)
    }
}
