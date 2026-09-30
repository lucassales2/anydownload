/*
 * iOS shared-link bridge — AnyDownload (T-020)
 *
 * The Swift host forwards an `anydownload://` URL to [offerIosSharedLink].
 * The graph exposes [IosSharedLinkInbox.inbox], so a link that arrives before
 * the Compose graph exists is still delivered. A full share-sheet extension
 * is a separate Xcode target and is recorded as the iOS limit; the scheme and
 * a Shortcut cover the same intake until then.
 */
package com.anydownlod.ui

import com.anydownlod.core.SharedLinkInbox

object IosSharedLinkInbox {
    val inbox: SharedLinkInbox = SharedLinkInbox()
}

/** Swift entry point: `IosSharedLinkBridgeKt.offerIosSharedLink(text:)`. */
fun offerIosSharedLink(text: String?) {
    IosSharedLinkInbox.inbox.offer(text)
}
