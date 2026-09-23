package com.anydownlod.core.validation

/** One line of a pasted batch, already trimmed and validated on its own. */
sealed interface BatchUrlEntry {
    val raw: String

    data class Valid(override val raw: String, val url: String) : BatchUrlEntry

    data class Invalid(override val raw: String, val error: SourceUrlError) : BatchUrlEntry
}

data class BatchUrlValidation(val entries: List<BatchUrlEntry>) {
    val validUrls: List<String> get() = entries.filterIsInstance<BatchUrlEntry.Valid>().map { it.url }
    val invalid: List<BatchUrlEntry.Invalid> get() = entries.filterIsInstance<BatchUrlEntry.Invalid>()
    val isEmpty: Boolean get() = entries.isEmpty()
    val hasInvalid: Boolean get() = invalid.isNotEmpty()
}

/**
 * Splits a paste on newlines first and validates each line with
 * [SourceUrlValidator]. Blank lines are dropped; a bad line is reported on its
 * own and never discards the good lines.
 */
object BatchUrlValidator {
    fun validate(raw: String): BatchUrlValidation {
        val entries = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                when (val result = SourceUrlValidator.validate(line)) {
                    is SourceUrlValidation.Valid -> BatchUrlEntry.Valid(line, result.url)
                    is SourceUrlValidation.Invalid -> BatchUrlEntry.Invalid(line, result.error)
                }
            }
            .toList()
        return BatchUrlValidation(entries)
    }
}
