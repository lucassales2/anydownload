/*
 * Desktop browser snapshot — AnyDownload (T-018)
 *
 * Owner decision, 2026-09-30: Chrome, Firefox, and Safari may be copied into
 * the app-private `cookies.txt` after a separate consent screen. Snapshot is
 * one-shot: the database is copied to a temp file, read once, and both the
 * snapshot and the connection are closed. Nothing is re-read per request and
 * the stored file gets no app-level cipher.
 */
package com.anydownload.desktop.cookies

import com.anydownload.core.BrowserChoice
import com.anydownload.core.BrowserCookieImport
import com.anydownload.core.CookieOperationResult
import com.anydownload.core.cookies.NetscapeCookieFile
import com.anydownload.desktop.store.DesktopCookieStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopBrowserCookieImport(
    private val store: DesktopCookieStore,
    private val locateStore: (BrowserChoice) -> Path? = BrowserStoreLocator::default,
    private val chromeSecret: () -> String? = ChromeSecrets::default,
) : BrowserCookieImport {

    override suspend fun snapshot(browser: BrowserChoice): CookieOperationResult = withContext(Dispatchers.IO) {
        val location = locateStore(browser)
            ?: return@withContext failure("No ${label(browser)} cookie store was found on this computer.")

        val snapshotFile = runCatching {
            Files.createTempFile("anydownlod-browser-", ".snapshot")
        }.getOrNull() ?: return@withContext failure("The browser cookie store could not be read.")

        try {
            runCatching {
                Files.copy(location, snapshotFile, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                return@withContext failure("The browser cookie store could not be read.")
            }

            val cookies = runCatching {
                when (browser) {
                    BrowserChoice.CHROME -> ChromeCookieStore.read(snapshotFile, chromeSecret())
                    BrowserChoice.FIREFOX -> FirefoxCookieStore.read(snapshotFile)
                    BrowserChoice.SAFARI -> SafariCookieStore.read(snapshotFile)
                }
            }.getOrElse {
                return@withContext failure("The browser cookie store could not be read.")
            }
            if (cookies.isEmpty()) {
                return@withContext failure("No cookies were found in ${label(browser)}.")
            }

            store.importText(NetscapeCookieFile.format(cookies.map { it.toNetscape() }))
        } finally {
            runCatching { Files.deleteIfExists(snapshotFile) }
        }
    }

    private fun label(browser: BrowserChoice): String = when (browser) {
        BrowserChoice.CHROME -> "Chrome"
        BrowserChoice.FIREFOX -> "Firefox"
        BrowserChoice.SAFARI -> "Safari"
    }

    private fun failure(message: String) = CookieOperationResult(success = false, message = message)
}
