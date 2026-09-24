package com.anydownlod.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point consumed by the SwiftUI host in `apps/ios`.
 * The ObjC name is `MainViewControllerKt.MainViewController()`.
 *
 * The iOS host uses the real local graph: shared HTTP engine, NSURLSession
 * transport, sandbox file store. Direct files download into Documents;
 * non-direct URLs fail with the typed extractor-not-implemented error.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val graph = remember { IosAppGraph() }
    App(graph = graph, offerClipboardCheck = true)
}