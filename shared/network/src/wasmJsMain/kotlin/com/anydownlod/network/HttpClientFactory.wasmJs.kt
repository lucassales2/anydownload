package com.anydownlod.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun defaultHttpClient(): HttpClient = HttpClient(Js) {
    applyAnyDownloadDefaults()
}
