package com.anydownlod.core.validation

/**
 * Result of client-side source URL pre-validation.
 *
 * This is deliberately conservative: the local engine repeats its own checks
 * before spawning a process. The app only rejects input that cannot be a
 * usable HTTP(S) source so users get immediate feedback. A multi-line paste
 * is not one URL; use [BatchUrlValidator] for batches.
 */
sealed interface SourceUrlValidation {
    data class Valid(val url: String) : SourceUrlValidation
    data class Invalid(val reason: String) : SourceUrlValidation
}

object SourceUrlValidator {
    fun validate(raw: String): SourceUrlValidation {
        val candidate = raw.trim()
        if (candidate.isEmpty()) {
            return SourceUrlValidation.Invalid("Enter a source URL.")
        }
        if (candidate.any { it.isWhitespace() }) {
            return SourceUrlValidation.Invalid("The URL must not contain whitespace.")
        }

        val lower = candidate.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return SourceUrlValidation.Invalid("Only http:// and https:// sources are supported.")
        }

        val afterScheme = candidate.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty()) {
            return SourceUrlValidation.Invalid("The URL is missing a host.")
        }

        return SourceUrlValidation.Valid(candidate)
    }
}
