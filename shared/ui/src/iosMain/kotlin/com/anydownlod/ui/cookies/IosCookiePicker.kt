/*
 * iOS cookie-file picker — AnyDownload (T-018)
 *
 * Presents the system document picker as a copy (`asCopy = true`) and
 * resolves with the temporary copy's path. The picker never parses the file
 * and never shows its contents; `IosCookieStore` validates and stores it,
 * then removes the temporary copy. There is no browser-database read here.
 */
package com.anydownlod.ui.cookies

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

class IosCookiePicker {

    // The picker's delegate is weak; hold it until the sheet closes.
    private var heldDelegate: NSObject? = null

    suspend fun pick(): String? = suspendCancellableCoroutine { continuation ->
        val presenter = topViewController()
        if (presenter == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val pickerDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                controller.dismissViewControllerAnimated(true, null)
                heldDelegate = null
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                continuation.resume(url?.path)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                controller.dismissViewControllerAnimated(true, null)
                heldDelegate = null
                continuation.resume(null)
            }
        }
        heldDelegate = pickerDelegate
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypePlainText, UTTypeData),
            asCopy = true,
        )
        picker.delegate = pickerDelegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) {
            controller = controller.presentedViewController
        }
        return controller
    }
}
