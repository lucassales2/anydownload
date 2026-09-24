package com.anydownlod.android.engine

import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlClassifier
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.extract.GenericExtraction
import com.anydownlod.core.extract.GenericExtractor
import com.anydownlod.core.extract.ExtractorRegistry
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Routes a submitted Android URL with one bounded HEAD request and, for HTML
 * pages, one bounded GET of the page body.
 *
 * - Every hop is validated with [UrlPolicy]; a URL that fails policy routes
 *   to the shared engine, which refuses it before any request — a blocked
 *   destination never reaches Python.
 * - A reachable 2xx HEAD whose Content-Type is not HTML -> [AndroidRoute
 *   .DIRECT_FILE].
 * - HTML, or a HEAD without Content-Type: a bounded GET (≤
 *   [HttpDownloadEngine.MAX_HTML_BYTES]) feeds the shared generic extractor
 *   subset. Exactly one media URL that passes policy -> [AndroidRoute
 *   .DIRECT_FILE]; zero, several, or any I/O failure -> [AndroidRoute
 *   .CHAQUOPY] (pinned yt-dlp handles direct files, simple pages, and site
 *   URLs, so a probe failure never invents a download).
 */
class AndroidRouteClassifier(
    private val connectTimeoutMillis: Int = 3_000,
    private val readTimeoutMillis: Int = 3_000,
    private val chunkSize: Int = 64 * 1024,
    private val urlCheck: (String) -> UrlCheck = UrlPolicy::check,
    /**
     * The Kotlin registry. A matched URL becomes [AndroidRoute.KOTLIN] before
     * any probe request, so no HEAD, no page GET, and no Python.
     */
    private val registry: ExtractorRegistry? = null,
) {
    private sealed interface Head {
        data class File(val contentType: String) : Head
        data class Redirect(val location: String) : Head
        data object HtmlOrUnknown : Head
        data object Error : Head
    }

    /** A bounded page read: the final URL after redirects plus its HTML prefix. */
    private data class ProbePage(val url: String, val html: String)

    fun route(url: String): AndroidRoute {
        if (registry?.suitableFor(url) != null) return AndroidRoute.KOTLIN
        if (urlCheck(url) !is UrlCheck.Allowed) return AndroidRoute.DIRECT_FILE
        var current = url
        try {
            var hops = 0
            while (true) {
                when (val head = head(current)) {
                    is Head.File -> return AndroidRoute.DIRECT_FILE

                    is Head.HtmlOrUnknown -> {
                        // The generic subset resolves one media URL -> the
                        // shared engine owns the job; otherwise Chaquopy keeps it.
                        val page = boundedPage(current) ?: return AndroidRoute.CHAQUOPY
                        val extraction = GenericExtractor.extract(
                            pageUrl = page.url,
                            html = page.html,
                            // The classifier's own injectable check (UrlPolicy in
                            // production) is the one the extractor must use, so
                            // tests may allow exactly one fixture origin.
                            candidateCheck = urlCheck,
                        )
                        return when (extraction) {
                            is GenericExtraction.Direct -> AndroidRoute.DIRECT_FILE
                            is GenericExtraction.Failed -> AndroidRoute.CHAQUOPY
                        }
                    }

                    is Head.Error -> return AndroidRoute.CHAQUOPY

                    is Head.Redirect -> {
                        if (head.location.isBlank()) return AndroidRoute.CHAQUOPY
                        hops++
                        if (hops > UrlPolicy.MAX_REDIRECTS) return AndroidRoute.CHAQUOPY
                        val target = runCatching { URI(current).resolve(head.location).toString() }
                            .getOrNull() ?: return AndroidRoute.CHAQUOPY
                        when (urlCheck(target)) {
                            is UrlCheck.Rejected -> return AndroidRoute.DIRECT_FILE
                            is UrlCheck.Allowed -> current = target
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            return AndroidRoute.CHAQUOPY
        }
    }

    /**
     * Bounded GET of the page body for the extractor route decision. Follows
     * the same policy-checked redirect budget as the HEAD probe and never
     * reads more than [HttpDownloadEngine.MAX_HTML_BYTES]. Returns null on any
     * error so Chaquopy keeps the URL rather than inventing a download.
     */
    private fun boundedPage(startUrl: String): ProbePage? {
        var current = startUrl
        var hops = 0
        try {
            while (true) {
                val connection = URL(current).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = connectTimeoutMillis
                    connection.readTimeout = readTimeoutMillis
                    val status = connection.responseCode
                    if (status in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank()) return null
                        hops++
                        if (hops > UrlPolicy.MAX_REDIRECTS) return null
                        val target = runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: return null
                        when (urlCheck(target)) {
                            is UrlCheck.Rejected -> return null
                            is UrlCheck.Allowed -> current = target
                        }
                        continue
                    }
                    if (status !in 200..299) return null

                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(chunkSize)
                    val input = connection.inputStream
                    try {
                        var remaining = HttpDownloadEngine.MAX_HTML_BYTES
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count == -1) break
                            if (count > 0) {
                                out.write(buffer, 0, count)
                                remaining -= count
                            }
                        }
                    } finally {
                        runCatching { input.close() }
                    }
                    return ProbePage(current, out.toString(Charsets.UTF_8))
                } finally {
                    connection.disconnect()
                }
            }
        } catch (failure: Throwable) {
            return null
        }
    }

    private fun head(url: String): Head {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            val status = connection.responseCode
            val location = connection.getHeaderField("Location")
            val contentType = connection.contentType
            return when {
                status in 300..399 -> Head.Redirect(location ?: "")
                status !in 200..299 -> Head.Error
                UrlClassifier.classify(contentType, url) == UrlClassifier.Classification.NEEDS_EXTRACTOR ->
                    Head.HtmlOrUnknown

                contentType != null -> Head.File(contentType)
                // A HEAD without Content-Type is unknown, not proof of a file.
                else -> Head.HtmlOrUnknown
            }
        } finally {
            connection.disconnect()
        }
    }
}