package com.anydownlod.desktop.engine

import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Routes against a local mock HTTP server. The shared engine would refuse the
 * loopback origin, so the classifier gets a fixture exception that allows
 * exactly this one test-server origin and delegates everything else to
 * [UrlPolicy] — strictly test-only, per the T-006 notes.
 */
class DesktopRouteClassifierTest {

    private fun server(): HttpServer = HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun fixtureCheck(server: HttpServer): (String) -> UrlCheck = { url ->
        if (url.startsWith("http://127.0.0.1:${server.address.port}")) {
            UrlCheck.Allowed(url)
        } else {
            UrlPolicy.check(url)
        }
    }

    @Test
    fun directFileHeadIsRoutedToTheHttpEngine() {
        val server = server()
        server.createContext("/files/tiny.bin") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, 42)
            exchange.close()
        }
        try {
            val classifier = DesktopRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            val route = classifier.route("http://127.0.0.1:${server.address.port}/files/tiny.bin")
            assertEquals(DesktopRoute.DIRECT_FILE, route)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun htmlWithoutMediaIsRoutedToTheCli() {
        val server = server()
        server.createContext("/watch") { exchange ->
            val body = "<html><body><p>no media element</p></body></html>".toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        try {
            val classifier = DesktopRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                DesktopRoute.YTDLP_CLI,
                classifier.route("http://127.0.0.1:${server.address.port}/watch"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun htmlFixtureWithOneMediaElementIsRoutedToTheHttpEngine() {
        val server = server()
        server.createContext("/watch") { exchange ->
            val body =
                """<html><body><video src="https://fixtures.example.net/clip.bin"></video></body></html>""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        try {
            val classifier = DesktopRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                DesktopRoute.DIRECT_FILE,
                classifier.route("http://127.0.0.1:${server.address.port}/watch"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun redirectToOneHopAwayDirectFileIsRoutedToTheHttpEngine() {
        val server = server()
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.set("Location", "/files/tiny.bin")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/files/tiny.bin") { exchange ->
            exchange.responseHeaders.set("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, 42)
            exchange.close()
        }
        try {
            val classifier = DesktopRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                DesktopRoute.DIRECT_FILE,
                classifier.route("http://127.0.0.1:${server.address.port}/start"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun errorStatusIsRoutedToTheCliAsFallback() {
        val server = server()
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        try {
            val classifier = DesktopRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                DesktopRoute.YTDLP_CLI,
                classifier.route("http://127.0.0.1:${server.address.port}/missing"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun defaultPolicyRefusesToSendLoopbackToTheCli() {
        // Without a fixture exception, the loopback URL is not allowed by the
        // policy, so it is routed to the shared engine which refuses it before
        // any request; it must never reach the yt-dlp process.
        val classifier = DesktopRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000)
        assertEquals(DesktopRoute.DIRECT_FILE, classifier.route("http://127.0.0.1:9/x"))
        assertEquals(DesktopRoute.DIRECT_FILE, classifier.route("https://user:pass@example.com/x"))
    }

    @Test
    fun resumeRouteSplitsPersistedJobsByUrlShape() {
        assertEquals(DesktopRoute.DIRECT_FILE, DesktopRouteClassifier.resumeRoute("https://example.com/files/tiny.bin"))
        assertEquals(DesktopRoute.DIRECT_FILE, DesktopRouteClassifier.resumeRoute("https://example.com/video.mp4?x=1"))
        assertEquals(DesktopRoute.DIRECT_FILE, DesktopRouteClassifier.resumeRoute("https://example.com/a/c.tar.gz"))
        assertEquals(DesktopRoute.DIRECT_FILE, DesktopRouteClassifier.resumeRoute("https://example.com/pod/song.mp3"))
        assertEquals(DesktopRoute.YTDLP_CLI, DesktopRouteClassifier.resumeRoute("https://example.com/watch?v=abc"))
        assertEquals(DesktopRoute.YTDLP_CLI, DesktopRouteClassifier.resumeRoute("https://example.com/podcast/episode"))
        assertEquals(DesktopRoute.YTDLP_CLI, DesktopRouteClassifier.resumeRoute("https://example.com/playlist?list=xyz"))
    }
}