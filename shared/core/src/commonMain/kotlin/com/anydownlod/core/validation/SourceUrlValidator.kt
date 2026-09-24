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
    /** The authority embeds credentials (userinfo), which would leak into rendered URLs. */
    Userinfo,
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
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) {
            return SourceUrlValidation.Invalid(SourceUrlError.MissingHost)
        }
        // userinfo would carry credentials in a URL the app renders; reject it.
        if (authority.indexOf('@') >= 0) {
            return SourceUrlValidation.Invalid(SourceUrlError.Userinfo)
        }

        return SourceUrlValidation.Valid(candidate)
    }
}
