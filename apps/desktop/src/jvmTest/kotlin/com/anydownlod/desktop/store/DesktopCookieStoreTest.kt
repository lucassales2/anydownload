package com.anydownlod.desktop.store

import java.nio.file.Files
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
}
