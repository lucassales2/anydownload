/*
 * Desktop browser profile locations — AnyDownload (T-018)
 *
 * The default profile paths for the three browsers the owner allowed. A
 * missing file returns null; a caller can inject its own locator in tests.
 * Chrome and Firefox are SQLite files; Safari is `Cookies.binarycookies`.
 * Other desktop browsers are a recorded gap.
 */
package com.anydownlod.desktop.cookies

import com.anydownlod.core.BrowserChoice
import java.nio.file.Files
import java.nio.file.Path

object BrowserStoreLocator {

    fun default(browser: BrowserChoice): Path? = when (browser) {
        BrowserChoice.CHROME -> chromeStores().firstOrNull { Files.isRegularFile(it) }
        BrowserChoice.FIREFOX -> firefoxStores().firstOrNull { Files.isRegularFile(it) }
        BrowserChoice.SAFARI -> safariStores().firstOrNull { Files.isRegularFile(it) }
    }

    private fun home(): Path = Path.of(System.getProperty("user.home").orEmpty())

    private fun osName(): String = System.getProperty("os.name").orEmpty().lowercase()

    private fun chromeStores(): List<Path> = when {
        osName().contains("win") -> {
            val local = System.getenv("LOCALAPPDATA")
            if (local.isNullOrBlank()) {
                emptyList()
            } else {
                listOf(
                    Path.of(local, "Google", "Chrome", "User Data", "Default", "Network", "Cookies"),
                    Path.of(local, "Google", "Chrome", "User Data", "Default", "Cookies"),
                )
            }
        }

        osName().contains("mac") -> listOf(
            home().resolve("Library/Application Support/Google/Chrome/Default/Network/Cookies"),
            home().resolve("Library/Application Support/Google/Chrome/Default/Cookies"),
        )

        else -> listOf(
            home().resolve(".config/google-chrome/Default/Network/Cookies"),
            home().resolve(".config/google-chrome/Default/Cookies"),
        )
    }

    private fun firefoxRoots(): List<Path> = when {
        osName().contains("win") -> listOfNotNull(
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let {
                Path.of(it, "Mozilla", "Firefox", "Profiles")
            },
        )

        osName().contains("mac") -> listOf(home().resolve("Library/Application Support/Firefox/Profiles"))

        else -> listOf(home().resolve(".mozilla/firefox"))
    }

    private fun firefoxStores(): List<Path> = firefoxRoots().flatMap { root ->
        runCatching {
            Files.newDirectoryStream(root).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .map { it.resolve("cookies.sqlite") }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun safariStores(): List<Path> = listOf(
        home().resolve("Library/Cookies/Cookies.binarycookies"),
        home().resolve("Library/Containers/com.apple.Safari/Data/Library/Cookies/Cookies.binarycookies"),
    )
}

/**
 * Chrome's own OS secret. macOS uses the Keychain item Chrome created;
 * Linux tries `secret-tool` and then Chrome's unencrypted-keyring default.
 * Windows DPAPI is not implemented, so a Windows Chrome snapshot fails
 * typed before any write (recorded gap).
 */
object ChromeSecrets {
    fun default(): String? = when {
        System.getProperty("os.name").orEmpty().lowercase().contains("mac") -> macOSSecret()
        System.getProperty("os.name").orEmpty().lowercase().contains("linux") -> linuxSecret()
        else -> null
    }

    private fun macOSSecret(): String? =
        runTool(
            "security",
            "find-generic-password",
            "-w",
            "-a",
            "Chrome",
            "-s",
            "Chrome Safe Storage",
        ) ?: runTool("security", "find-generic-password", "-w", "-s", "Chrome Safe Storage")

    private fun linuxSecret(): String? =
        runTool("secret-tool", "lookup", "application", "chrome") ?: "peanuts"

    private fun runTool(vararg command: String): String? {
        val process = runCatching {
            ProcessBuilder(*command).redirectErrorStream(true).start()
        }.getOrNull() ?: return null
        val output = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
        val finished = runCatching {
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        }.getOrDefault(false)
        if (!finished) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        return output.trim().takeIf { it.isNotEmpty() }
    }
}
