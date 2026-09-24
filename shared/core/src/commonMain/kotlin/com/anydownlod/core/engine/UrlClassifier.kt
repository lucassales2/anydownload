package com.anydownlod.core.engine

/**
 * Decides whether a 2xx response body is a direct file or an HTML page that
 * would need an extractor.
 *
 * T-038 only fetches direct files. HTML is reported as [Classification
 * .NEEDS_EXTRACTOR] so the UI can show the per-host “extractor not
 * implemented” state instead of pretending a download happened. A missing
 * content type is treated as a direct file: unknown, but not HTML.
 */
object UrlClassifier {
    fun classify(contentType: String?): Classification = when {
        contentType == null -> Classification.DIRECT_FILE
        contentType.startsWith("text/html", ignoreCase = true) -> Classification.NEEDS_EXTRACTOR
        contentType.startsWith("application/xhtml+xml", ignoreCase = true) -> Classification.NEEDS_EXTRACTOR
        else -> Classification.DIRECT_FILE
    }

    enum class Classification {
        DIRECT_FILE,
        NEEDS_EXTRACTOR,
    }
}