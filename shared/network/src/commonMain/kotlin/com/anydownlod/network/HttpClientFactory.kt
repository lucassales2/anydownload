package com.anydownlod.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Base URL used when no server is configured. The user-managed server story
 * and configuration UI are unresolved (open questions Q-02/Q-03), so this
 * default is a development placeholder only.
 */
const val DEFAULT_SERVER_BASE_URL: String = "http://localhost:8080"

/**
 * Shared JSON contract. `ignoreUnknownKeys` implements the forward-compatible
 * unknown-field strategy; stricter validation belongs in API tests and the
 * server contract (T-007).
 */
val AnyDownloadJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.applyAnyDownloadDefaults() {
    expectSuccess = true
    install(ContentNegotiation) {
        json(AnyDownloadJson)
    }
}

/** Creates a platform-default Ktor client configured for the AnyDownload API. */
expect fun defaultHttpClient(): HttpClient
