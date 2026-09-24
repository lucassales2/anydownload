package com.anydownlod.core.platform

/**
 * Web (Compose/Wasm) [HttpTransfer] for T-039.
 *
 * The page must never fetch arbitrary origins (CORS makes it futile and the
 * extension owns host permissions), so this transfer performs **no request**
 * and returns a typed [HttpResponse.Unavailable]. The UI shows an honest
 * “extension required” state. [T-043](../T-043) wires the extension bridge,
 * which will route through this same interface.
 */
class WebExtensionTransfer : HttpTransfer {
    override suspend fun execute(url: String): HttpResponse =
        HttpResponse.Unavailable("The browser extension is required to download files.")
}