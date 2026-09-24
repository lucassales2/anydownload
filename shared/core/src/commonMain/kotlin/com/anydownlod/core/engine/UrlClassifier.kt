package com.anydownlod.core.engine

/**
 * Decides whether a 2xx response body is a direct file, an HTML page that
 * would need an extractor, or an HLS/DASH manifest.
 *
 * HTML is [Classification.NEEDS_EXTRACTOR] so the UI can show the per-host
 * “extractor not implemented” state instead of pretending a download
 * happened. The HLS/DASH content types are [Classification.MANIFEST]; a
 * `.m3u8`/`.mpd` URL tail is only a hint when the content type is missing.
 * Anything else is a direct file.
 */
object UrlClassifier {
    private val manifestTypes = listOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "application/dash+xml",
    )

    fun classify(contentType: String?, url: String? = null): Classification = when {
        contentType == null -> if (looksLikeManifest(url)) Classification.MANIFEST else Classification.DIRECT_FILE
        contentType.startsWith("text/html", ignoreCase = true) -> Classification.NEEDS_EXTRACTOR
        contentType.startsWith("application/xhtml+xml", ignoreCase = true) -> Classification.NEEDS_EXTRACTOR
        manifestTypes.any { contentType.startsWith(it, ignoreCase = true) } -> Classification.MANIFEST
        else -> Classification.DIRECT_FILE
    }

    /** `.m3u8`/`.mpd` tails are a hint only; the content type wins. */
    private fun looksLikeManifest(url: String?): Boolean {
        val path = url?.substringBefore('?')?.substringBefore('#')?.lowercase() ?: return false
        return path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    enum class Classification {
        DIRECT_FILE,
        NEEDS_EXTRACTOR,
        MANIFEST,
    }
}