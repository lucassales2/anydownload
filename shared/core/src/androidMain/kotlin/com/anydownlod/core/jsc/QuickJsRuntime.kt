/*
 * Zipline QuickJS adapter — AnyDownload (T-069 spike, T-071 host runtime)
 *
 * `app.cash.zipline:zipline` (Apache-2.0, with the QuickJS MIT notice) embeds
 * QuickJS for JVM, Android, and Kotlin/Native. This adapter implements the
 * shared [JsRuntime] port: the lib+core solver script is compiled once per
 * process and reused, every evaluation is bounded by an interrupt handler, and
 * a failed or interrupted context is closed and recreated.
 */
package com.anydownlod.core.jsc

import app.cash.zipline.InterruptHandler
import app.cash.zipline.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.TimeSource

class QuickJsRuntime : JsRuntime {

    override val available: Boolean = true
    override val name: String = "QuickJS"
    override val version: String? get() = QuickJs.version

    private val mutex = Mutex()
    private var quickJs: QuickJs? = null
    private var compiledScript: ByteArray? = null

    override suspend fun evaluate(
        script: String,
        entry: String,
        input: String,
        timeout: Duration,
    ): JsResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                val runtime = quickJs ?: QuickJs.create().also { created ->
                    created.memoryLimit = MEMORY_LIMIT_BYTES
                    quickJs = created
                }
                val mark = TimeSource.Monotonic.markNow()
                runtime.interruptHandler = object : InterruptHandler {
                    override fun poll(): Boolean = mark.elapsedNow() > timeout
                }
                val bytecode = compiledScript ?: runtime.compile(script, "solver.js").also { compiled ->
                    compiledScript = compiled
                }
                runtime.execute(bytecode)
                val result = runtime.evaluate("JSON.stringify($entry($input))", "entry.js")
                JsResult.Ok(result?.toString() ?: "null")
            } catch (error: Throwable) {
                // A failed or interrupted context is not reusable; close it and
                // let the next call create a fresh one.
                reset()
                JsResult.Failed(
                    "The JavaScript runtime failed: " +
                        (error.message?.take(120) ?: error::class.simpleName),
                )
            }
        }
    }

    private fun reset() {
        runCatching { quickJs?.close() }
        quickJs = null
        compiledScript = null
    }

    private companion object {
        const val MEMORY_LIMIT_BYTES: Long = 128L * 1024 * 1024
    }
}
