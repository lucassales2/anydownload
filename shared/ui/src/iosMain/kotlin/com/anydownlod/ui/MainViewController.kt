package com.anydownlod.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point consumed by the SwiftUI host in `apps/ios`.
 * The ObjC name is `MainViewControllerKt.MainViewController()`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(offerClipboardCheck = true)
}
