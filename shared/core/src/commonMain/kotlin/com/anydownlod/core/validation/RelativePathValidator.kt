package com.anydownlod.core.validation

/** Result of validating a destination folder relative to the download root. */
sealed interface RelativePathValidation {
    /** [path] is normalized with `/` separators; an empty path means the root. */
    data class Valid(val path: String) : RelativePathValidation

    data class Invalid(val reason: String) : RelativePathValidation
}

/**
 * Destination folder rules shared by the add form and the JSON store.
 *
 * A valid value is empty (the download root itself) or a relative path whose
 * segments are non-empty and are not `.` or `..`. Absolute paths (POSIX, a
 * Windows drive, or a UNC prefix) are rejected.
 */
object RelativePathValidator {
    private val drivePrefix = Regex("^[A-Za-z]:")

    fun validate(raw: String): RelativePathValidation {
        val candidate = raw.trim()
        if (candidate.isEmpty()) {
            return RelativePathValidation.Valid("")
        }
        if (candidate.startsWith('/') || candidate.startsWith('\\') || drivePrefix.containsMatchIn(candidate)) {
            return RelativePathValidation.Invalid("Choose a folder inside the download root, not an absolute path.")
        }

        val segments = candidate.split('/', '\\')
        if (segments.any { it.isEmpty() }) {
            return RelativePathValidation.Invalid("The folder path has an empty segment.")
        }
        if (segments.any { it == "." || it == ".." }) {
            return RelativePathValidation.Invalid("The folder path cannot use \".\" or \"..\".")
        }

        return RelativePathValidation.Valid(segments.joinToString("/"))
    }
}
