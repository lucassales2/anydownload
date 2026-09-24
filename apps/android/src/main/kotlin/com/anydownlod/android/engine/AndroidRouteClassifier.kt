package com.anydownlod.android.engine

import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlClassifier
import com.anydownlod.core.engine.UrlPolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Routes a submitted Android URL with one bounded HEAD request.
 *
 * - Every hop is validated with [UrlPolicy]; a URL that fails policy routes
 *   to the shared engine, which refuses it before any request — a blocked
 *   destination never reaches Python.
 * - A reachable 2xx HEAD whose Content-Type is not HTML -> [AndroidRoute
 *   .DIRECT_FILE].
 * - HTML, a HEAD without Content-Type, errors, timeouts, and redirect-budget
 *   overflow -> [AndroidRoute.CHAQUOPY] (pinned yt-dlp handles both direct
 *   files and site URLs, so a probe failure never invents a download).
 */
class AndroidRouteClassifier(
    private val connectTimeoutMillis: Int = 3_000,
    private val readTimeoutMillis: Int = 3_000,
    private val urlCheck: (String) -> UrlCheck = UrlPolicy::check,
) {
    fun route(url: String): AndroidRoute {
        if (urlCheck(url) !is UrlCheck.Allowed) return AndroidRoute.DIRECT_FILE
        var current = url
        try {
            var hops = 0
            while (true) {
                when (val head = head(current)) {
                    is Head.File -> return AndroidRoute.DIRECT_FILE
                    is Head.HtmlOrUnknown -> return AndroidRoute.CHAQUOPY
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
                // A HEAD without Content-Type is unknown, not proof of a file.
                else -> Head.HtmlOrUnknown
            }
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface Head {
        data class File(val contentType: String) : Head
        data class Redirect(val location: String) : Head
        data object HtmlOrUnknown : Head
        data object Error : Head
    }
}