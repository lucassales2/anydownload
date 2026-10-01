package com.anydownload.core.validation

import com.anydownload.core.extract.ExtractorRegistry

/**
 * Whether a URL is one a registered site extractor can handle.
 *
 * True only when [registry] matches the trimmed URL and the match is not the
 * generic fallback or a known-unsupported extractor. An unknown page, a
 * direct file, and a Spotify link stay false. Matching is the extractor
 * pattern; this does not fetch the URL.
 */
class SupportedUrlValidator(
    private val registry: ExtractorRegistry,
) {
    fun isSupported(raw: String): Boolean {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return false
        val match = registry.suitableFor(candidate) ?: return false
        if (match.ieKey == ExtractorRegistry.GENERIC_KEY) return false
        // The known-unsupported extractors (DRM, piracy, liability) match so
        // the engine can fail typed, but they are not downloadable support.
        return !match.ieKey.startsWith("Known")
    }
}
