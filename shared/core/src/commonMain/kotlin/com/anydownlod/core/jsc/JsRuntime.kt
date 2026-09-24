package com.anydownlod.core.jsc

import kotlin.time.Duration

/** One JavaScript evaluation outcome. */
sealed interface JsResult {
    /** The entry point returned a JSON value. */
    data class Ok(val json: String) : JsResult

    /** The runtime is missing, timed out, or the script failed; redacted. */
    data class Failed(val reason: String) : JsResult
}

/**
 * The shared JavaScript runtime port (T-069).
 *
 * One adapter per host implements this: Zipline QuickJS on JVM/Android/iOS
 * (T-071) and the web page's own JavaScript (T-072). `evaluate` runs [script]
 * and then calls the global function [entry] with [input] parsed as JSON,
 * returning the JSON-stringified result. Implementations must be bounded by
 * [timeout] and must never log script output or challenge material.
 */
interface JsRuntime {
    /** True when this host can run the solver. */
    val available: Boolean

    /** Short adapter name for Settings (`QuickJS`, `JavaScriptCore`, `none`). */
    val name: String get() = "none"

    /** The runtime version when it exposes one. */
    val version: String? get() = null

    suspend fun evaluate(
        script: String,
        entry: String,
        input: String,
        timeout: Duration,
    ): JsResult
}

/**
 * The stage-1 fallback: no JavaScript runtime is wired, so callers keep the
 * JS-less path. It never pretends to solve a challenge.
 */
object NoJsRuntime : JsRuntime {
    override val available: Boolean = false
    override val name: String = "none"

    override suspend fun evaluate(script: String, entry: String, input: String, timeout: Duration): JsResult =
        JsResult.Failed("No JavaScript runtime is available.")
}
