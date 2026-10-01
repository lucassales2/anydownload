/*
 * Shared link intake — AnyDownload (T-020)
 *
 * A link arrives from a host share sheet, an OS deep link, or a desktop paste.
 * The text may carry a title or extra words around one URL (Android share
 * commonly does), so the intake extracts exactly one http(s) URL and validates
 * it with [SourceUrlValidator]. Zero URLs, several URLs, or an unsupported
 * scheme are typed rejections; the caller never guesses.
 */
package com.anydownload.core.validation

/** Why a shared text was not accepted. */
enum class SharedLinkReason {
    EMPTY,
    NO_URL,
    MULTIPLE_URLS,
}

sealed interface SharedLinkResult {
    data class Accepted(val url: String) : SharedLinkResult
    data class Rejected(val reason: SharedLinkReason) : SharedLinkResult
}

object SharedLink {

    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""")

    /**
     * The one compatible URL in [raw], or a typed rejection. A trailing
     * punctuation character that is not part of the URL is trimmed.
     */
    fun extract(raw: String?): SharedLinkResult {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return SharedLinkResult.Rejected(SharedLinkReason.EMPTY)
        val candidates = URL_PATTERN.findAll(text)
            .map { match -> match.value.trimEnd('.', ',', ';', ')', ']', '}') }
            .filter { it.isNotEmpty() }
            .toList()
        if (candidates.isEmpty()) return SharedLinkResult.Rejected(SharedLinkReason.NO_URL)
        val valid = candidates.mapNotNull { candidate ->
            (SourceUrlValidator.validate(candidate) as? SourceUrlValidation.Valid)?.url
        }
        return when {
            valid.isEmpty() -> SharedLinkResult.Rejected(SharedLinkReason.NO_URL)
            valid.size > 1 -> SharedLinkResult.Rejected(SharedLinkReason.MULTIPLE_URLS)
            else -> SharedLinkResult.Accepted(valid.single())
        }
    }
}
