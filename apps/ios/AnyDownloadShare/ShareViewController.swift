import UIKit
import UniformTypeIdentifiers

/// T-020 iOS share sheet: reads the shared URL or text once and opens the
/// host app through the `anydownload://share?url=` scheme. The extension
/// stores nothing and performs no network request.
class ShareViewController: UIViewController {

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard
            let item = extensionContext?.inputItems.first as? NSExtensionItem,
            let provider = item.attachments?.first
        else {
            complete()
            return
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] value, _ in
                let url = value as? URL
                DispatchQueue.main.async { self?.openHost(with: url?.absoluteString) }
            }
        } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] value, _ in
                let text = value as? String
                DispatchQueue.main.async { self?.openHost(with: text) }
            }
        } else {
            complete()
        }
    }

    private func openHost(with value: String?) {
        defer { complete() }
        guard
            let value,
            let encoded = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
            let url = URL(string: "anydownload://share?url=\(encoded)")
        else {
            return
        }
        extensionContext?.open(url, completionHandler: nil)
    }

    private func complete() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}
