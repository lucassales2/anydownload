package com.anydownlod.core.engine

import com.anydownlod.core.platform.HttpRequest

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

    /**
     * Downloads [url] and streams progress; then the browser saves the file.
     * [headers] are the selected format's allowlisted request headers; MV3
     * `fetch`/`downloads` may refuse some of them and the browser owns the
     * progress it reports. [saveViaBlob] asks the extension to fetch the
     * media itself and hand a Blob URL to the browser downloader (matched
     * formats), because a direct `chrome.downloads` fetch of a signed media
     * URL is not reliable everywhere.
     */
    suspend fun download(
        url: String,
        jobId: String,
        headers: Map<String, String> = emptyMap(),
        saveViaBlob: Boolean = false,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): WebDownload

    /**
     * Fetches [url] and returns a bounded, redacted page: the FINAL URL after
     * the browser's redirects plus at most 512 KiB of the HTML text. The page
     * — never the extension — runs the shared Kotlin extractor on those
     * bytes; the extension performs no extraction in JavaScript.
     */
    suspend fun fetchPage(url: String): WebPage

    /**
     * Carries one extractor request (GET/POST, headers, body, range) through
     * the extension fetch path. The reply includes the effective request
     * header set the extension actually sent, so the caller can report the
     * difference (names only) without guessing about MV3's header rules.
     */
    suspend fun fetch(request: HttpRequest): WebFetch

    /** Aborts an in-flight [download] for [jobId]. */
    suspend fun cancelDownload(jobId: String)
}

/** One extractor-request outcome over the extension bridge. */
sealed interface WebFetch {
    data class Final(
        val statusCode: Int,
        val contentType: String?,
        val totalBytes: Long?,
        val contentRange: String?,
        val headers: Map<String, String>,
        val body: ByteArray,
        val finalUrl: String,
        /** Request headers the browser fetch actually kept. */
        val sentHeaders: Map<String, String>,
    ) : WebFetch

    data class Failed(val code: WebFailureCode, val message: String) : WebFetch
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