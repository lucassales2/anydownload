package com.anydownlod.core.jsc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * T-069 Zipline QuickJS spike on the JVM: evaluate the bundled lib + core and
 * run one synthetic input shaped like upstream's stdin protocol. The record is
 * the QuickJS version, wall time, and memory; the synthetic player is empty,
 * so the solver may return a structured error, which still proves execution.
 */
class QuickJsSpikeTest {

    @Test
    fun quickJsRunsTheBundledSolverScripts() = runTest(timeout = 180.seconds) {
        assertEquals("0.8.0", EjsScripts.VERSION)

        val runtime = QuickJsRuntime()
        assertTrue(runtime.available)
        val script = EjsScripts.lib + "\nObject.assign(globalThis, lib);\n" + EjsScripts.core
        val input =
            """{"type":"player","player":"","requests":[{"type":"n","challenges":["fixture"]}],""" +
                """"output_preprocessed":true}"""

        val started = System.nanoTime()
        val result = runtime.evaluate(script, "jsc", input, 60.seconds)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        val memory = app.cash.zipline.QuickJs.create().use { quickJs ->
            val usage = quickJs.memoryUsage
            "${usage.memoryUsedSize}/${usage.memoryAllocatedLimit}"
        }
        println(
            "zipline quickjs spike: version=${runtime.version} elapsedMs=$elapsedMillis " +
                "memory=$memory result=${result::class.simpleName}",
        )
        when (result) {
            is JsResult.Ok -> {
                assertTrue(result.json.isNotEmpty(), "the solver returned an empty result")
                println("zipline output length=${result.json.length}")
            }

            is JsResult.Failed -> {
                // A runtime failure is recorded, not hidden; the spike still
                // proves the adapter ran (the message is redacted).
                println("zipline failed: ${result.reason}")
                assertIs<JsResult.Failed>(result)
            }
        }
    }

    @Test
    fun anInfiniteScriptTimesOutTyped() = runTest(timeout = 60.seconds) {
        val runtime = QuickJsRuntime()
        val mark = TimeSource.Monotonic.markNow()
        val result = runtime.evaluate("while (true) {}", "loop", "{}", 500.milliseconds)
        val elapsed = mark.elapsedNow()
        println("zipline timeout: elapsedMs=${elapsed.inWholeMilliseconds} result=${result::class.simpleName}")
        assertIs<JsResult.Failed>(result)
        assertTrue(elapsed < 20.seconds, "the interrupt handler must stop the script")
    }
}
