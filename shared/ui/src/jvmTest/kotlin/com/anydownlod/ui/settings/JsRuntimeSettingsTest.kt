package com.anydownlod.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.ToolAvailability
import com.anydownlod.core.domain.ToolStatus
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.App
import kotlin.test.Test

/**
 * T-071: Settings reports the embedded JavaScript runtime, or says honestly
 * that some YouTube formats stay hidden.
 */
@OptIn(ExperimentalTestApi::class)
class JsRuntimeSettingsTest {

    private class FixedToolProbe(private val status: ToolStatus) : ToolProbe {
        override suspend fun probe(): ToolStatus = status
    }

    @Test
    fun settingsShowsTheEmbeddedRuntimeVersion() = runComposeUiTest {
        val graph = InMemoryAppGraph(
            toolProbe = FixedToolProbe(
                ToolStatus(jsRuntime = ToolAvailability(available = true, version = "QuickJS 2021-03-27 (embedded)")),
            ),
        )
        setContent { App(graph) }
        onNodeWithText("Settings").performClick()
        onNodeWithTag("settings-tool-jsruntime").assertExists()
        onNodeWithText("JavaScript runtime: QuickJS 2021-03-27 (embedded)").assertExists()
    }

    @Test
    fun settingsSaysWhenNoRuntimeIsAvailable() = runComposeUiTest {
        val graph = InMemoryAppGraph(
            toolProbe = FixedToolProbe(ToolStatus(jsRuntime = ToolAvailability(available = false))),
        )
        setContent { App(graph) }
        onNodeWithText("Settings").performClick()
        onNodeWithText("JavaScript runtime: none — some YouTube formats hidden").assertExists()
    }
}
