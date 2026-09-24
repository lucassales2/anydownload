package com.anydownlod.core.jsc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest

/**
 * T-069 Zipline QuickJS spike on the iOS Simulator. Same synthetic input as the
 * JVM spike; the empty player means the solver may return a structured error,
 * which still proves the runtime executed the bundled scripts.
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

        val mark = TimeSource.Monotonic.markNow()
        val result = runtime.evaluate(script, "jsc", input, 60.seconds)
        val elapsedMillis = mark.elapsedNow().inWholeMilliseconds

        println(
            "zipline ios spike: version=${runtime.version} elapsedMs=$elapsedMillis " +
                "result=${result::class.simpleName}",
        )
        assertTrue(result is JsResult.Ok || result is JsResult.Failed)
        if (result is JsResult.Failed) println("zipline ios failed: ${result.reason}")
    }

    @Test
    fun anInfiniteScriptTimesOutTyped() = runTest(timeout = 60.seconds) {
        val runtime = QuickJsRuntime()
        val mark = TimeSource.Monotonic.markNow()
        val result = runtime.evaluate("while (true) {}", "loop", "{}", 500.milliseconds)
        val elapsed = mark.elapsedNow()
        println("zipline ios timeout: elapsedMs=${elapsed.inWholeMilliseconds} result=${result::class.simpleName}")
        assertIs<JsResult.Failed>(result)
        assertTrue(elapsed < 20.seconds, "the interrupt handler must stop the script")
    }
}
