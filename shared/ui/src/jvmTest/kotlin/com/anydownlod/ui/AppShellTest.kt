package com.anydownlod.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.fake.InMemoryAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Headless click-through of the shell. This replaces the manual desktop
 * click-through on machines where OS input automation is not permitted: the
 * same composables receive real click events through the Compose test runtime.
 */
@OptIn(ExperimentalTestApi::class)
class AppShellTest {

    @Test
    fun tabsShowDistinctEmptyStatesAndSettingsReturnsToTheSameTab() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithText("Add a URL above to start a download.").assertExists()

        onNodeWithText("Completed").performClick()
        onNodeWithText("Finished downloads appear here.").assertExists()

        onNodeWithText("Subscriptions").performClick()
        onNodeWithText("Checks run while the app is open.", substring = true)
            .assertExists()

        onNodeWithText("Settings").performClick()
        onNodeWithText("Filenames").assertExists()
        onNodeWithText("Close").performClick()

        // Closing Settings comes back to Subscriptions, not the first tab.
        onNodeWithText("Checks run while the app is open.", substring = true)
            .assertExists()
    }

    @Test
    fun themeControlIsLabeledAndWritesThroughSettings() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithText("Theme").assertExists()
        onNodeWithText("Dark").performClick()

        assertEquals(ThemePreference.DARK, graph.settings.settings.value.theme)

        onNodeWithText("Light").performClick()
        assertEquals(ThemePreference.LIGHT, graph.settings.settings.value.theme)
    }
}
