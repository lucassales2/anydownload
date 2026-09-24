package com.anydownlod.core.extract.harness

/**
 * Redaction for recorded fixtures. Every fixture that enters the repository
 * goes through here first: signed `googlevideo` URLs, visitor data, PO
 * tokens, signatures, and cookies are replaced with synthetic placeholders.
 * Values are never kept; only the JSON shape and non-sensitive fields survive.
 *
 * Review checklist for a new fixture:
 *  1. The response has been through [FixtureRedactor.redact].
 *  2. No `googlevideo.com` host, `visitorData`, `poToken`, `signatureCipher`,
 *     `sig`, `n`, `expire`, or cookie value remains (grep the file).
 *  3. Media URLs point at `*.example` / `*.example.net` style hosts.
 */
object FixtureRedactor {

    private const val REDACTED = "REDACTED"
    private const val SYNTHETIC_GOOGLEVIDEO = "https://rr1---sn-example.googlevideo.example/videoplayback?redacted=1"

    private val googlevideoUrl = Regex(
        """https?://[A-Za-z0-9.\-]*googlevideo\.com[^"\s\\]*""",
        RegexOption.IGNORE_CASE,
    )

    private val sensitiveQueryParam = Regex(
        """(?i)([?&](?:sig|signature|n|t|token|cpn|expire|expires|expireat|ei|ip|ipbits|sparams|key|mn|mm|ms|mv|mvi|pl|mh|met|ratebypass|requiressl|pcm2|dur|visitor[^=&]*)=)[^&"\s\\]*""",
    )

    private val sensitiveJsonValue = Regex(
        """"(?:visitorData|visitorId|poToken|pot|signatureCipher|signature|decipheringKey|dataSyncId|sessionToken|token|authorization)"\s*:\s*"(?:[^"\\]|\\.)*"""",
        RegexOption.IGNORE_CASE,
    )

    fun redact(text: String): String {
        var output = googlevideoUrl.replace(text, SYNTHETIC_GOOGLEVIDEO)
        output = sensitiveQueryParam.replace(output) { match -> "${match.groupValues[1]}$REDACTED" }
        output = sensitiveJsonValue.replace(output) { match ->
            val key = match.value.substringBefore(':').trimEnd()
            "$key: \"$REDACTED\""
        }
        return output
    }
}
