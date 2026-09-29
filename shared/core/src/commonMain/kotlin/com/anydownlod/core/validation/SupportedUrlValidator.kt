package com.anydownlod.core.validation

import com.anydownlod.core.extract.ExtractorRegistry

/**
 * Whether a URL is one a registered site extractor can handle.
 *
 * True only when [registry] matches the trimmed URL and the match is not the
 * generic fallback. An unknown page, a direct file, and a Spotify link stay
 * false. Matching is the extractor pattern; this does not fetch the URL.
 */
class SupportedUrlValidator(
    private val registry: ExtractorRegistry,
) {
    fun isSupported(raw: String): Boolean {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return false
        val match = registry.suitableFor(candidate) ?: return false
        return match.ieKey != ExtractorRegistry.GENERIC_KEY
    }
}
