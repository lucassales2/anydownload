package com.anydownlod.core

import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ToolStatus

/**
 * Everything the shared screens need, wired once by each host.
 *
 * Android, iOS, and web pass the in-memory graph. Desktop replaces the engine,
 * storage, and tool probe with host implementations while keeping the same
 * interfaces.
 */
interface AppGraph {
    val engine: DownloadEngine
    val subscriptions: SubscriptionRepository
    val settings: SettingsRepository
    val toolProbe: ToolProbe

    /**
     * Loads title, thumbnail, and related metadata for one URL before a
     * download. Hosts without an extractor report [UnavailableMediaPreviewSource].
     */
    val previews: MediaPreviewSource get() = UnavailableMediaPreviewSource

    /**
     * Fetches a preview thumbnail. Returns null when the host cannot load
     * images or the URL is not an http(s) image. Never throws.
     */
    val loadThumbnail: suspend (String) -> ByteArray? get() = { null }

    /** Opens a source URL in the host browser. No-op where no browser exists. */
    val openUrl: (String) -> Unit get() = {}

    /** Opens a registered artifact. No-op until the host has real paths. */
    val openFile: (Artifact) -> Unit get() = {}

    /** Reveals a registered artifact in the host file browser. No-op until real paths exist. */
    val revealFile: (Artifact) -> Unit get() = {}

    /**
     * Opens the host directory chooser. Returns the chosen absolute path, or
     * null when the host cannot pick one (Android, iOS, web, or user cancel).
     */
    val pickFolder: () -> String? get() = { null }

    /**
     * Opens the host file chooser for a cookie file. Returns the chosen path,
     * or null when unavailable. T-035 wires the desktop implementation; shared
     * code never reads the file.
     */
    val pickCookieFile: () -> String? get() = { null }

    /**
     * A load-time explanation to show in the shell (for example a corrupt
     * state file that was moved aside). Null when everything loaded cleanly.
     */
    val startupWarning: String? get() = null

    /** Local cookie-file management; desktop replaces the unavailable default. */
    val cookieStore: CookieStore get() = CookieStore.Unavailable
}
