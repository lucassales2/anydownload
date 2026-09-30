package com.anydownlod.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.AppGraph
import com.anydownlod.core.SharedLinkInbox
import com.anydownlod.core.fake.InMemoryAppGraph
import kotlin.test.Test

/** T-020: a pending shared link opens the preview once. */
@OptIn(ExperimentalTestApi::class)
class SharedLinkUiTest {

    @Test
    fun aPendingSharedLinkOpensThePreview() = runComposeUiTest {
        val inbox = SharedLinkInbox()
        val base = InMemoryAppGraph()
        val graph = object : AppGraph by base {
            override val sharedLinkInbox: SharedLinkInbox = inbox
        }
        setContent { App(graph) }

        inbox.offer("Fixture title https://fixtures.example/watch?v=abcdefghijk")
        waitForIdle()

        onNodeWithTag("preview-back").assertExists()
    }
}
