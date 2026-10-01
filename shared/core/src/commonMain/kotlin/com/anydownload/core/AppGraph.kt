package com.anydownload.core

import com.anydownload.core.domain.Artifact
import com.anydownload.core.music.SpotifyAuthService
import com.anydownload.core.music.SpotifyDownloadService
import com.anydownload.core.postprocess.ToolkitCapabilities

/** Opens a source URL in the host browser. */
fun interface UrlOpener {
    operator fun invoke(url: String)
}

/** Opens a registered artifact. */
fun interface FileOpener {
    operator fun invoke(artifact: Artifact)
}

/** Reveals a registered artifact in the host file browser. */
fun interface FileRevealer {
    operator fun invoke(artifact: Artifact)
}

/** Opens the host directory chooser. Returns the chosen path, or null. */
fun interface FolderPicker {
    operator fun invoke(): String?
}

/** Opens the host file chooser for a cookie file. Returns the chosen path, or null. */
fun interface CookieFilePicker {
    suspend fun pick(): String?
}

/**
 * Fetches a preview thumbnail. Returns null when the host cannot load images
 * or the URL is not an http(s) image. Never throws.
 */
fun interface ThumbnailLoader {
    suspend operator fun invoke(url: String): ByteArray?
}

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
     * or null when unavailable. Android and iOS use an async system picker;
     * the store validates and copies the file, and shared code never reads it.
     */
    val pickCookieFile: suspend () -> String?
        get() = { null }

    /**
     * A load-time explanation to show in the shell (for example a corrupt
     * state file that was moved aside). Null when everything loaded cleanly.
     */
    val startupWarning: String? get() = null

    /** Local cookie-file management; desktop replaces the unavailable default. */
    val cookieStore: CookieStore get() = CookieStore.Unavailable

    /**
     * T-019: true on hosts where the OS or browser stops the app after
     * suspension, so the Subscriptions screen says scans stop too.
     */
    val subscriptionsPauseOnSuspend: Boolean get() = false

    /**
     * T-020: the host's incoming share/deep-link inbox; null when the host has
     * no share path. The UI consumes a pending link once.
     */
    val sharedLinkInbox: SharedLinkInbox? get() = null

    /**
     * Desktop-only browser-store snapshot; null on Android, iOS, and web,
     * which never read a browser database.
     */
    val browserCookieImport: BrowserCookieImport? get() = null

    /**
     * What the running host's media toolkit can do. Empty on web and in tests;
     * desktop supplies the FFmpeg capabilities once T-078 wires them. The Edit
     * panel enables a merge or transcode choice only when this says so.
     */
    val toolkitCapabilities: ToolkitCapabilities get() = ToolkitCapabilities.Unavailable

    /**
     * Spotify metadata, matching, and queueing. Null on hosts that have not
     * wired the metadata client and matcher; the UI then treats Spotify input
     * as an unavailable preview instead of pretending it can download.
     */
    val spotify: SpotifyDownloadService? get() = null

    /**
     * On-device Spotify login state for the user library. Null when the host
     * has no secret store; the Settings screen then says login is unavailable.
     */
    val spotifyAuth: SpotifyAuthService? get() = null
}
