package com.anydownlod.desktop.engine

import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlClassifier
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.extract.GenericExtraction
import com.anydownlod.core.extract.GenericExtractor
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Routes a submitted desktop URL by proving, with one bounded HEAD request and,
 * for HTML pages, one bounded GET of the page body, whether it is owned by the
 * shared HTTP engine.
 *
 * Decisions:
 * - The submitted URL and every redirect target are validated with [UrlPolicy].
 *   A URL that fails policy (userinfo, loopback, reserved destination) is
 *   routed to the shared engine so **no process ever sees it**; the engine
 *   refuses it before any request.
 * - A reachable 2xx HEAD whose Content-Type is not HTML -> [DesktopRoute
 *   .DIRECT_FILE].
 * - HTML, or a HEAD without Content-Type: a bounded GET fetches up to
 *   [HttpDownloadEngine.MAX_HTML_BYTES] of the page, and the shared generic
 *   extractor subset decides. Exactly one media URL that passes policy ->
 *   [DesktopRoute.DIRECT_FILE]; zero, several, or any I/O failure ->
 *   [DesktopRoute.YTDLP_CLI] (yt-dlp handles direct files, simple pages, and
 *   site URLs, so a probe failure never invents a download).
 *
 * The synchronous probes cost at most [connectTimeoutMillis] +
 * [readTimeoutMillis] per hop and at most one bounded page body per submit;
 * that is the price of routing before any engine starts work.
 */
class DesktopRouteClassifier(
    private val connectTimeoutMillis: Int = 3_000,
    private val readTimeoutMillis: Int = 3_000,
    private val chunkSize: Int = 64 * 1024,
    private val urlCheck: (String) -> UrlCheck = UrlPolicy::check,
) {
    private sealed interface Head {
        data class File(val contentType: String) : Head
        data class Redirect(val location: String) : Head
        data object HtmlOrUnknown : Head
        data object Error : Head
    }

    /** A bounded page read: the final URL after redirects plus its HTML prefix. */
    private data class ProbePage(val url: String, val html: String)

    fun route(url: String): DesktopRoute {
        if (urlCheck(url) !is UrlCheck.Allowed) return DesktopRoute.DIRECT_FILE
        var current = url
        try {
            var hops = 0
            while (true) {
                when (val head = head(current)) {
                    is Head.File -> return DesktopRoute.DIRECT_FILE

                    is Head.HtmlOrUnknown -> {
                        // The generic subset resolves one media URL -> the shared
                        // engine owns the job; otherwise the CLI keeps it.
                        val page = boundedPage(current) ?: return DesktopRoute.YTDLP_CLI
                        val extraction = GenericExtractor.extract(
                            pageUrl = page.url,
                            html = page.html,
                            // The classifier's own injectable check (UrlPolicy in
                            // production) is the one the extractor must use, so
                            // tests may allow exactly one fixture origin.
                            candidateCheck = urlCheck,
                        )
                        return when (extraction) {
                            is GenericExtraction.Direct -> DesktopRoute.DIRECT_FILE
                            is GenericExtraction.Failed -> DesktopRoute.YTDLP_CLI
                        }
                    }

                    is Head.Error -> return DesktopRoute.YTDLP_CLI

                    is Head.Redirect -> {
                        if (head.location.isBlank()) return DesktopRoute.YTDLP_CLI
                        hops++
                        if (hops > UrlPolicy.MAX_REDIRECTS) return DesktopRoute.YTDLP_CLI
                        val target = runCatching { URI(current).resolve(head.location).toString() }
                            .getOrNull() ?: return DesktopRoute.YTDLP_CLI
                        when (urlCheck(target)) {
                            is UrlCheck.Rejected -> return DesktopRoute.DIRECT_FILE
                            is UrlCheck.Allowed -> current = target
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            return DesktopRoute.YTDLP_CLI
        }
    }

    /**
     * Bounded GET of the page body for the extractor route decision. Follows
     * the same policy-checked redirect budget as the HEAD probe and never
     * reads more than [HttpDownloadEngine.MAX_HTML_BYTES]. Returns null on any
     * error so the CLI keeps the URL rather than inventing a download.
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
                UrlClassifier.classify(contentType) == UrlClassifier.Classification.NEEDS_EXTRACTOR ->
                    Head.HtmlOrUnknown

                contentType != null -> Head.File(contentType)
                // A HEAD without Content-Type is unknown, not proof of a
                // direct file; the CLI takes it.
                else -> Head.HtmlOrUnknown
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /**
         * Extension allowlist used only to split persisted jobs across engines
         * at startup, without any network. New submits use [route]. A direct
         * file without an extension resumes on the CLI, which handles direct
         * files too, so this never invents a download.
         */
        private val DIRECT_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "m4v", "avi",
            "mp3", "m4a", "aac", "flac", "wav", "opus", "ogg",
            "zip", "gz", "tgz", "tar", "pdf", "bin", "epub",
            "jpg", "jpeg", "png", "gif", "webp",
            "srt", "vtt", "ttml", "txt", "json",
        )

        fun resumeRoute(url: String): DesktopRoute {
            val path = url.substringAfter("://").substringBefore('?').substringBefore('#')
            val last = path.substringAfterLast('/')
            val extension = last.substringAfterLast('.', missingDelimiterValue = "")
            return if (last != extension && extension.lowercase() in DIRECT_EXTENSIONS) {
                DesktopRoute.DIRECT_FILE
            } else {
                DesktopRoute.YTDLP_CLI
            }
        }
    }
}