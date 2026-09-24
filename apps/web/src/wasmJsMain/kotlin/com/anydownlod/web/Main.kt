package com.anydownlod.web

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.anydownlod.ui.App
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // The T-072 CDP hook is installed only with `?solverHook=1`.
    val hookGraph = if (window.location.search.contains("solverHook=1")) WebAppGraph() else null
    hookGraph?.browserJsRuntime?.installSolverHook(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    ComposeViewport(document.body!!) {
        // The web host uses the real local graph; downloads go through the
        // browser extension (see apps/web-extension). The page never fetches
        // an arbitrary origin itself.
        val graph = remember { hookGraph ?: WebAppGraph() }
        App(graph = graph)
    }
}