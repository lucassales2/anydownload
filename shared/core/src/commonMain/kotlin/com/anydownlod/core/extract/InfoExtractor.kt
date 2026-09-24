/*
 * Extractor base and registry — AnyDownload
 *
 * Translation of the `InfoExtractor` contract in
 * `yt_dlp/extractor/common.py` (`_VALID_URL`, `suitable`, `_match_id`,
 * `_real_extract`) and the first-suitable dispatch from
 * `yt_dlp/extractor/_extractors.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24. Unlicense; see
 * shared/core/NOTICE.md. `common.py` is not vendored.
 */
package com.anydownlod.core.extract

/**
 * The base every translated extractor extends. `ieKey` is the upstream class
 * name without the `IE` suffix when the site is generic
 * (upstream `IE_NAME`/`_NETRC_MACHINE`-style key); [validUrl] mirrors
 * `_VALID_URL`, with the capture group named `id` feeding [matchId].
 */
abstract class InfoExtractor(
    val ieKey: String,
    protected val http: ExtractorHttp,
    private val validUrl: Regex? = null,
) {
    /** Human label; defaults to the key. */
    open val displayName: String = ieKey

    /** Upstream `suitable`: the URL matches this extractor's `_VALID_URL`. */
    open fun suitable(url: String): Boolean = validUrl?.containsMatchIn(url) == true

    /** Upstream `_match_id`: the named `id` group when the pattern has one. */
    open fun matchId(url: String): String? =
        validUrl?.find(url)?.groups?.get("id")?.value

    /** Upstream `_real_extract`; every failure is a typed [ExtractionError]. */
    abstract suspend fun extract(url: String): InfoDict
}

/**
 * Ordered first-match dispatch, mirroring `_extractors.py`. Site extractors
 * come first and `GenericIE` is registered last, so an unmatched URL can only
 * reach the generic fallback.
 */
class ExtractorRegistry(private val extractors: List<InfoExtractor>) {

    init {
        require(extractors.isNotEmpty()) { "an extractor registry needs at least one extractor" }
        val generic = extractors.filter { it.ieKey == GENERIC_KEY }
        require(generic.isEmpty() || extractors.last().ieKey == GENERIC_KEY) {
            "the generic extractor must be registered last so it is only the fallback"
        }
    }

    fun all(): List<InfoExtractor> = extractors

    /** The first extractor that declares the URL suitable, generic last. */
    fun suitableFor(url: String): InfoExtractor? = extractors.firstOrNull { it.suitable(url) }

    /** Upstream `extract_info` for one URL; unmatched URLs fail typed. */
    suspend fun extract(url: String): InfoDict {
        val extractor = suitableFor(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractor.extract(url)
    }

    companion object {
        /** The upstream generic class name; the fallback is always last. */
        const val GENERIC_KEY = "Generic"
    }
}
