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
            val classifier = AndroidRouteClassifier(2_000, 2_000, fixtureCheck(server))
            assertEquals(
                AndroidRoute.DIRECT_FILE,
                classifier.route("http://127.0.0.1:${server.address.port}/files/tiny.bin"),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun htmlPageRoutesToChaquopy() {
        val server = server()
        server.createContext("/watch") { exchange ->
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, 12)
            exchange.close()
        }
        try {
            val classifier = AndroidRouteClassifier(2_000, 2_000, fixtureCheck(server))
            assertEquals(
                AndroidRoute.CHAQUOPY,
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