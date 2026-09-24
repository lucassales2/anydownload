package com.anydownlod.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.anydownlod.ui.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        // The web host uses the real local graph; downloads go through the
        // browser extension (see apps/web-extension). The page never fetches
        // an arbitrary origin itself.
        val graph = remember { WebAppGraph() }
        App(graph = graph)
    }
}