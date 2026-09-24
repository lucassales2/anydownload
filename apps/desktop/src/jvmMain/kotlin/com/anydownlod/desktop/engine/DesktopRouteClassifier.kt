package com.anydownlod.desktop.engine

import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlClassifier
import com.anydownlod.core.engine.UrlPolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Routes a submitted desktop URL by proving, with one bounded HEAD request,
 * whether it responds as a direct file.
 *
 * Decisions:
 * - The submitted URL and every redirect target are validated with [UrlPolicy].
 *   A URL that fails policy (userinfo, loopback, reserved destination) is
 *   routed to the shared engine so **no process ever sees it**; the engine
 *   refuses it before any request.
 * - A reachable 2xx HEAD whose Content-Type is not HTML -> [DesktopRoute
 *   .DIRECT_FILE].
 * - HTML, a HEAD without Content-Type, connection errors, timeouts, and a
 *   redirect budget overflow -> [DesktopRoute.YTDLP_CLI] (yt-dlp handles both
 *   direct files and site URLs, so a probe failure never invents a download).
 *
 * The synchronous HEAD costs at most [connectTimeoutMillis] +
 * [readTimeoutMillis] per submit and is the price of routing before any
 * engine starts work.
 */
class DesktopRouteClassifier(
    private val connectTimeoutMillis: Int = 3_000,
    private val readTimeoutMillis: Int = 3_000,
    private val urlCheck: (String) -> UrlCheck = UrlPolicy::check,
) {
    private sealed interface Head {
        data class File(val contentType: String) : Head
        data class Redirect(val location: String) : Head
        data object HtmlOrUnknown : Head
        data object Error : Head
    }

    fun route(url: String): DesktopRoute {
        if (urlCheck(url) !is UrlCheck.Allowed) return DesktopRoute.DIRECT_FILE
        var current = url
        try {
            var hops = 0
            while (true) {
                when (val head = head(current)) {
                    is Head.File -> return DesktopRoute.DIRECT_FILE
                    is Head.HtmlOrUnknown -> return DesktopRoute.YTDLP_CLI
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