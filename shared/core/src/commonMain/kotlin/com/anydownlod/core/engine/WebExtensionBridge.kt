package com.anydownlod.core.engine

/**
 * The web page's only outward-facing seam: a Manifest V3 browser extension
 * with host permissions does the fetch and the save. The page never issues a
 * fetch to an arbitrary origin — the [WebExtensionEngine] drives redirect
 * policy and classification with the same [UrlPolicy]/[UrlClassifier] rules
 * as every other host, and the extension performs single final fetches.
 */
interface WebExtensionBridge {
    /** True when the extension content script is present in this page. */
    val available: Boolean

    /**
     * Classifies [url] without saving anything. Implementations follow the
     * browser's redirect handling and report the FINAL response; the engine
     * re-validates [WebProbe.Final.finalUrl] with [UrlPolicy], which is the
     * web platform's best-effort version of per-hop validation (intermediate
     * hops are hidden by the browser, a recorded web limitation).
     */
    suspend fun probe(url: String): WebProbe

    /** Downloads [url] and streams progress; then the browser saves the file. */
    suspend fun download(url: String, jobId: String, onProgress: (downloaded: Long, total: Long?) -> Unit): WebDownload

    /**
     * Fetches [url] and returns a bounded, redacted page: the FINAL URL after
     * the browser's redirects plus at most 512 KiB of the HTML text. The page
     * — never the extension — runs the shared Kotlin extractor on those
     * bytes; the extension performs no extraction in JavaScript.
     */
    suspend fun fetchPage(url: String): WebPage

    /** Aborts an in-flight [download] for [jobId]. */
    suspend fun cancelDownload(jobId: String)
}

/** One bounded page-read outcome. */
sealed interface WebPage {
    data class Final(val finalUrl: String, val html: String) : WebPage

    data class Failed(val code: WebFailureCode, val message: String) : WebPage
}

/** One classification outcome. */
sealed interface WebProbe {
    data class Final(
        val statusCode: Int,
        val contentType: String?,
        val totalBytes: Long?,
        val finalUrl: String,
    ) : WebProbe

    data class Failed(
        val code: WebFailureCode,
        val message: String,
    ) : WebProbe
}

/** One download outcome, already redacted by the caller upstream. */
sealed interface WebDownload {
    data class Completed(
        val fileName: String,
        val sizeBytes: Long?,
    ) : WebDownload

    data class Failed(
        val code: WebFailureCode,
        val message: String,
        val retryable: Boolean = true,
    ) : WebDownload

    /** The transfer was interrupted (extension aborted, tab closed, cancel). */
    data class Aborted(val reason: String) : WebDownload
}

/** Safe, short failure codes from the extension; never raw output. */
enum class WebFailureCode {
    PERMISSION,
    BLOCKED_DESTINATION,
    NETWORK,
    TIMEOUT,
    OTHER,
}