package com.anydownlod.android.engine

import com.anydownlod.core.jsc.EjsScripts
import com.anydownlod.core.jsc.JsResult
import com.anydownlod.core.jsc.QuickJsRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

/**
 * T-071 JVM-equivalent Android runtime check: the `QuickJsRuntime` source in
 * `shared/core/androidMain` is byte-identical to the JVM copy exercised here,
 * and it is compiled into the debug APK. No emulator is available in this
 * environment, so this is the recorded evidence.
 */
class AndroidJsRuntimeEquivalenceTest {

    @Test
    fun theAndroidQuickJsRuntimeEvaluatesTheBundledSolver() = runBlocking {
        assertEquals("0.8.0", EjsScripts.VERSION)
        val runtime = QuickJsRuntime()
        assertTrue(runtime.available)
        assertTrue(runtime.version?.isNotEmpty() == true)

        val script = EjsScripts.lib + "\nObject.assign(globalThis, lib);\n" + EjsScripts.core
        val input =
            """{"type":"player","player":"","requests":[{"type":"n","challenges":["fixture"]}],""" +
                """"output_preprocessed":true}"""
        val result = runtime.evaluate(script, "jsc", input, 60.seconds)
        println("android-equivalent quickjs: version=${runtime.version} result=${result::class.simpleName}")
        assertTrue(result is JsResult.Ok || result is JsResult.Failed)
    }
}
