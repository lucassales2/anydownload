import SwiftUI

@main
struct AnyDownloadApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // T-020: an `anydownload://...?url=<link>` open forwards the
                // link to the shared inbox; the Compose preview does the rest.
                .onOpenURL { url in
                    let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
                    let value = components?.queryItems?.first(where: { $0.name == "url" })?.value
                    IosSharedLinkBridgeKt.offerIosSharedLink(text: value ?? url.absoluteString)
                }
        }
    }
}
