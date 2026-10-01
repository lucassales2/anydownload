package com.anydownload.desktop.store

import java.nio.file.Files
import com.anydownload.core.CookieErrorReason
import com.anydownload.core.CookieStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCookieStoreTest {

    private fun tempDir() = Files.createTempDirectory("anydownlod-cookies-")

    private val fixture = """
        # Netscape HTTP Cookie File
        .example.com	TRUE	/	FALSE	0	fake_session	fake_value
    """.trimIndent()

    @Test
    fun validNetscapeFileIsCopiedAndDeleted() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("exported.txt")
        Files.writeString(source, fixture)
        val store = DesktopStore(stateDirectory, now = { 1L })
        val cookieStore = DesktopCookieStore(store)

        val imported = cookieStore.import(source.toString())

        assertTrue(imported.success)
        val target = Files.readString(stateDirectory.resolve("cookies.txt"))
        assertEquals(fixture, target)
        assertNotNull(cookieStore.storedFilePath())

        val deleted = cookieStore.delete()

        assertTrue(deleted.success)
        assertFalse(Files.exists(stateDirectory.resolve("cookies.txt")))
        assertNull(store.cookieFilePath())
        assertNull(cookieStore.storedFilePath())
    }

    @Test
    fun oversizedFileIsRejectedWithoutCopying() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("big.txt")
        Files.writeString(source, "# Netscape HTTP Cookie File\n" + "x".repeat(1024 * 1024 + 10))
        val cookieStore = DesktopCookieStore(DesktopStore(stateDirectory, now = { 1L }))

        val result = cookieStore.import(source.toString())

        assertFalse(result.success)
        assertFalse(Files.exists(stateDirectory.resolve("cookies.txt")))
    }

    @Test
    fun nonNetscapeTextIsRejectedWithoutCopying() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("notes.txt")
        Files.writeString(source, "just some text, not a cookie file")
        val cookieStore = DesktopCookieStore(DesktopStore(stateDirectory, now = { 1L }))

        val result = cookieStore.import(source.toString())

        assertFalse(result.success)
        assertNull(cookieStore.storedFilePath())
        assertFalse(Files.exists(stateDirectory.resolve("cookies.txt")))
    }

    @Test
    fun tabSeparatedRowWithoutHeaderIsAccepted() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("exported.txt")
        Files.writeString(source, ".example.com\tTRUE\t/\tFALSE\t0\tname\tvalue")
        val cookieStore = DesktopCookieStore(DesktopStore(stateDirectory, now = { 1L }))

        assertTrue(cookieStore.import(source.toString()).success)
    }

    @Test
    fun statusTracksNotConfiguredConfiguredAndError() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("exported.txt")
        Files.writeString(source, fixture)
        val store = DesktopStore(stateDirectory, now = { 1L }).also { it.load() }
        val cookieStore = DesktopCookieStore(store)

        assertEquals(CookieStatus.NotConfigured, cookieStore.status())
        assertTrue(cookieStore.import(source.toString()).success)
        assertEquals(CookieStatus.Configured, cookieStore.status())
        cookieStore.delete()
        assertEquals(CookieStatus.NotConfigured, cookieStore.status())
    }

    @Test
    fun restartLoadsTheFrozenPathAndShowsConfigured() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("exported.txt")
        Files.writeString(source, fixture)
        val first = DesktopStore(stateDirectory, now = { 1L }).also { it.load() }
        assertTrue(DesktopCookieStore(first).import(source.toString()).success)

        // A fresh store over the same state directory, as after a restart.
        val reloaded = DesktopStore(stateDirectory, now = { 1L }).also { it.load() }

        assertEquals(CookieStatus.Configured, DesktopCookieStore(reloaded).status())
        assertTrue(reloaded.cookieFilePath().orEmpty().endsWith("cookies.txt"))
    }

    @Test
    fun anExpiredStoredFileIsErrorWithoutSecrets() {
        val stateDirectory = tempDir()
        val source = stateDirectory.resolve("expired.txt")
        Files.writeString(
            source,
            "# Netscape HTTP Cookie File\n.example.com\tTRUE\t/\tFALSE\t1\tfake_session\tfake_value\n",
        )
        val cookieStore = DesktopCookieStore(DesktopStore(stateDirectory, now = { 1L }))
        assertTrue(cookieStore.import(source.toString()).success)

        val status = cookieStore.status()

        assertEquals(CookieStatus.Error(CookieErrorReason.ALL_EXPIRED), status)
        assertFalse(status.toString().contains("fake_session"))
        assertFalse(status.toString().contains("fake_value"))
    }
}
