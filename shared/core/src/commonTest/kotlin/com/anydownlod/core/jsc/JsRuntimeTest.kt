package com.anydownlod.core.jsc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class JsRuntimeTest {

    @Test
    fun noRuntimeReportsUnavailableAndNeverPretends() = runTest {
        assertFalse(NoJsRuntime.available)
        val result = NoJsRuntime.evaluate("script", "jsc", "{}", 5.seconds)
        val failed = assertIs<JsResult.Failed>(result)
        assertEquals("No JavaScript runtime is available.", failed.reason)
    }
}
