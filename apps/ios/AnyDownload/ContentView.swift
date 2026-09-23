import AnyDownloadKit
import SwiftUI
import UIKit

/// Hosts the shared Compose Multiplatform UI inside SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Top-level Kotlin function `MainViewController()` in com.anydownlod.ui.
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose handles keyboard insets itself.
            .ignoresSafeArea(.keyboard)
    }
}
