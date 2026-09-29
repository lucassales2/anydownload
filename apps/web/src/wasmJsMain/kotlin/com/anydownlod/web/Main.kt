package com.anydownlod.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.anydownlod.ui.App
import dev.zacsweers.metro.createGraph
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // One graph for the page. The T-072 CDP hook is installed only with
    // `?solverHook=1`, on the graph's own runtime so the solver the engine
    // uses and the hook share one instance.
    val graph = createGraph<WebAppGraph>()
    if (window.location.search.contains("solverHook=1")) {
        graph.browserJsRuntime.installSolverHook(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    ComposeViewport(document.body!!) {
        // The web host uses the real local graph; downloads go through the
        // browser extension (see apps/web-extension). The page never fetches
        // an arbitrary origin itself.
        App(graph = graph)
    }
}
