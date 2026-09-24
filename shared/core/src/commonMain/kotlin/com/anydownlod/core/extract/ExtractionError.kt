/*
 * Extractor core — AnyDownload
 *
 * Typed extraction failures mirroring upstream `ExtractorError` subclasses in
 * `yt_dlp/extractor/common.py` at the tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24. Messages are
 * short and redacted: they never carry page bytes, signed URLs, or cookies.
 */
package com.anydownlod.core.extract

/** The typed failures an extractor may raise. */
sealed class ExtractionError(message: String) : Exception(message) {

    /** No registered extractor matches the URL. */
    class UnsupportedUrl(
        message: String = "No extractor matches this URL.",
    ) : ExtractionError(message)

    /** Removed, private, geo-restricted, or otherwise unplayable. */
    class Unavailable(
        message: String = "This source is unavailable.",
    ) : ExtractionError(message)

    class LoginRequired(
        message: String = "This source needs a sign-in.",
    ) : ExtractionError(message)

    class AgeRestricted(
        message: String = "This video is age-restricted.",
    ) : ExtractionError(message)

    class GeoRestricted(
        val countries: List<String> = emptyList(),
    ) : ExtractionError("This source is not available in your region.")

    class NoFormats(
        message: String = "No downloadable format was found.",
    ) : ExtractionError(message)

    /** A response or document could not be parsed. */
    class Malformed(reason: String) : ExtractionError(reason)
}
