package com.anydownload.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.anydownload.core.ToolProbe
import com.anydownload.core.domain.ToolAvailability
import com.anydownload.core.domain.ToolStatus
import com.anydownload.core.fake.InMemoryAppGraph
import com.anydownload.ui.App
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
