package com.anydownlod.android.engine

import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidRouteClassifierTest {

    private fun server(): HttpServer = HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun fixtureCheck(server: HttpServer): (String) -> UrlCheck = { url ->
        if (url.startsWith("http://127.0.0.1:${server.address.port}")) {
            UrlCheck.Allowed(url)
        } else {
            UrlPolicy.check(url)
        }
    }

    @Test
    fun directFileHeadRoutesToHttpEngine() {
        val server = server()
        server.createContext("/files/tiny.bin") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, 42)
            exchange.close()
        }
        try {
            val classifier = AndroidRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                AndroidRoute.DIRECT_FILE,
                classifier.route("http://127.0.0.1:${server.address.port}/files/tiny.bin"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun htmlWithoutMediaRoutesToChaquopy() {
        val server = server()
        server.createContext("/watch") { exchange ->
            val body = "<html><body><p>no media element</p></body></html>".toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        try {
            val classifier = AndroidRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                AndroidRoute.CHAQUOPY,
                classifier.route("http://127.0.0.1:${server.address.port}/watch"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun htmlFixtureWithOneMediaElementRoutesToHttpEngine() {
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
            val classifier = AndroidRouteClassifier(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000, urlCheck = fixtureCheck(server))
            assertEquals(
                AndroidRoute.DIRECT_FILE,
                classifier.route("http://127.0.0.1:${server.address.port}/watch"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun defaultPolicyNeverSendsLoopbackToPython() {
        val classifier = AndroidRouteClassifier(2_000, 2_000)
        assertEquals(AndroidRoute.DIRECT_FILE, classifier.route("http://127.0.0.1:9/x"))
    }
}