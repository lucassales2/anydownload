/*
 * Desktop browser cookie capability — AnyDownload (T-018)
 *
 * Owner decision, 2026-09-30: desktop may copy Chrome, Firefox, or Safari
 * cookies into the same app-private cookies.txt after a separate consent
 * screen. The copy is one snapshot: the browser database is copied, read,
 * and closed once, never kept open and never re-read per request. Other
 * desktop browsers are a recorded gap. Android, iOS, and web never read a
 * browser database. Decrypting a browser's own store may use that browser's
 * OS secret; that secret does not encrypt AnyDownload's file.
 */
package com.anydownlod.core

/**
 * The browsers the desktop snapshot supports. Any other desktop browser is a
 * recorded gap, not a hidden fallback.
 */
enum class BrowserChoice {
    CHROME,
    FIREFOX,
    SAFARI,
}

/**
 * Desktop-only capability: reads one browser's cookie store once and writes
 * it into the app-private `cookies.txt` through [CookieStore.importText].
 * The result never carries cookie names or values. A null
 * [AppGraph.browserCookieImport] means the host does not offer the feature.
 */
fun interface BrowserCookieImport {
    suspend fun snapshot(browser: BrowserChoice): CookieOperationResult
}
