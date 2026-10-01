package com.anydownload.android.engine.cookies

import java.nio.file.Files
import java.nio.file.Path
import com.anydownload.core.CookieErrorReason
import com.anydownload.core.CookieStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android cookie store on the JVM. Every fixture is synthetic
 * (`fake_session` / `fake_value`); no real profile or account is involved.
 */
class AndroidCookieStoreTest {

    private val fixture = "# Netscape HTTP Cookie File\n" +
        "media.example.com\tFALSE\t/\tFALSE\t0\tfake_session\tfake_value\n"

    private fun tempDir(prefix: String): Path = Files.createTempDirectory("anydownlod-$prefix-")

    @Test
    fun importCopiesAValidFileAndReportsStatus() {
        val state = tempDir("cookie-state")
        val source = state.resolve("exported.txt")
        Files.writeString(source, fixture)
        val store = AndroidCookieStore(state)

        val result = store.import(source.toString())

        assertTrue(result.success)
        assertEquals(fixture, Files.readString(state.resolve("cookies.txt")))
        assertNotNull(store.storedFilePath())
        assertTrue(Files.exists(source), "a file outside the import directory is not touched")
    }

    @Test
    fun replaceOverwritesTheStoredFile() {
        val state = tempDir("cookie-state")
        val first = state.resolve("first.txt")
        val second = state.resolve("second.txt")
        Files.writeString(first, fixture)
        Files.writeString(
            second,
            "# Netscape HTTP Cookie File\nmedia.example.com\tFALSE\t/\tFALSE\t0\tfake_replaced\tfake_value\n",
        )
        val store = AndroidCookieStore(state)

        assertTrue(store.import(first.toString()).success)
        assertTrue(store.import(second.toString()).success)

        assertEquals(Files.readString(second), Files.readString(store.filePath))
        assertTrue(Files.readString(store.filePath).contains("fake_replaced"))
    }

    @Test
    fun deleteRemovesTheFileAndStatus() {
        val state = tempDir("cookie-state")
        val source = state.resolve("exported.txt")
        Files.writeString(source, fixture)
        val store = AndroidCookieStore(state)
        assertTrue(store.import(source.toString()).success)

        val deleted = store.delete()

        assertTrue(deleted.success)
        assertFalse(Files.exists(store.filePath))
        assertNull(store.storedFilePath())
        assertNull(store.currentJar())
    }

    @Test
    fun oversizedFileIsRejectedWithoutCopying() {
        val state = tempDir("cookie-state")
        val source = state.resolve("big.txt")
        Files.writeString(source, "# Netscape HTTP Cookie File\n" + "x".repeat(1024 * 1024 + 10))
        val store = AndroidCookieStore(state)

        val result = store.import(source.toString())

        assertFalse(result.success)
        assertFalse(Files.exists(store.filePath))
        assertNull(store.storedFilePath())
    }

    @Test
    fun nonNetscapeTextIsRejectedWithoutCopying() {
        val state = tempDir("cookie-state")
        val source = state.resolve("notes.txt")
        Files.writeString(source, "just some notes, not a cookie file")
        val store = AndroidCookieStore(state)

        val result = store.import(source.toString())

        assertFalse(result.success)
        assertEquals("That file does not look like a Netscape cookie file.", result.message)
        assertFalse(Files.exists(store.filePath))
    }

    @Test
    fun emptyFileIsRejected() {
        val state = tempDir("cookie-state")
        val source = state.resolve("empty.txt")
        Files.writeString(source, "")
        val store = AndroidCookieStore(state)

        val result = store.import(source.toString())

        assertFalse(result.success)
        assertEquals("The cookie file is empty.", result.message)
        assertFalse(Files.exists(store.filePath))
    }

    @Test
    fun pickedCopyInsideTheImportDirectoryIsDeletedAfterImport() {
        val state = tempDir("cookie-state")
        val importDir = tempDir("cookie-import")
        val picked = importDir.resolve("cookie-import.txt")
        Files.writeString(picked, fixture)
        val store = AndroidCookieStore(state, importDirectory = importDir)

        assertTrue(store.import(picked.toString()).success)

        assertTrue(Files.exists(state.resolve("cookies.txt")))
        assertFalse(Files.exists(picked), "the picked credential copy does not linger in the cache")
    }

    @Test
    fun pickedCopyIsDeletedEvenWhenTheFileIsRejected() {
        val state = tempDir("cookie-state")
        val importDir = tempDir("cookie-import")
        val picked = importDir.resolve("cookie-import.txt")
        Files.writeString(picked, "not a cookie file")
        val store = AndroidCookieStore(state, importDirectory = importDir)

        assertFalse(store.import(picked.toString()).success)

        assertFalse(Files.exists(picked))
    }

    @Test
    fun currentJarParsesTheStoredFileAndMatches() {
        val state = tempDir("cookie-state")
        val source = state.resolve("exported.txt")
        Files.writeString(source, fixture)
        val store = AndroidCookieStore(state)
        assertTrue(store.import(source.toString()).success)

        val jar = assertNotNull(store.currentJar())
        assertEquals(
            "fake_session=fake_value",
            jar.headerFor("https://media.example.com/files/tiny.bin", nowEpochSeconds = 0),
        )
    }

    @Test
    fun currentJarIsNullWhenNoFileIsStored() {
        val store = AndroidCookieStore(tempDir("cookie-state"))
        assertNull(store.currentJar())
        assertNull(store.storedFilePath())
    }

    @Test
    fun statusMovesFromNotConfiguredToConfiguredAndBack() {
        val state = tempDir("cookie-state")
        val source = state.resolve("exported.txt")
        Files.writeString(source, fixture)
        val store = AndroidCookieStore(state)

        assertEquals(CookieStatus.NotConfigured, store.status())
        assertTrue(store.import(source.toString()).success)
        assertEquals(CookieStatus.Configured, store.status())
        store.delete()
        assertEquals(CookieStatus.NotConfigured, store.status())
    }

    @Test
    fun restartFromTheSyntheticFileShowsConfigured() {
        val state = tempDir("cookie-state")
        val source = state.resolve("exported.txt")
        Files.writeString(source, fixture)
        assertTrue(AndroidCookieStore(state).import(source.toString()).success)

        // A new store instance over the same app-private directory, as after
        // a process restart.
        assertEquals(CookieStatus.Configured, AndroidCookieStore(state).status())
    }

    @Test
    fun expiredFileIsAnErrorStateWithoutSecrets() {
        val state = tempDir("cookie-state")
        val source = state.resolve("expired.txt")
        Files.writeString(
            source,
            "# Netscape HTTP Cookie File\n.example.com\tTRUE\t/\tFALSE\t1\tfake_session\tfake_value\n",
        )
        val store = AndroidCookieStore(state)
        assertTrue(store.import(source.toString()).success)

        val status = store.status()

        assertEquals(CookieStatus.Error(CookieErrorReason.ALL_EXPIRED), status)
        assertFalse(status.toString().contains("fake_session"))
        assertFalse(status.toString().contains("fake_value"))
    }

    @Test
    fun headerOnlyFileIsAnErrorState() {
        val state = tempDir("cookie-state")
        val store = AndroidCookieStore(state)
        assertTrue(store.importText("# Netscape HTTP Cookie File\n").success)

        assertEquals(CookieStatus.Error(CookieErrorReason.NO_COOKIES), store.status())
    }

    @Test
    fun unreadableStoredFileIsAnErrorStateEvenWithTheFlagSet() {
        val state = tempDir("cookie-state")
        Files.createDirectories(state)
        Files.writeString(state.resolve("cookies.txt"), "not a cookie file")

        assertEquals(CookieStatus.Error(CookieErrorReason.UNREADABLE), AndroidCookieStore(state).status())
    }
}
