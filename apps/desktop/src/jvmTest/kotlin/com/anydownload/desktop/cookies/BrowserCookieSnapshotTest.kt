package com.anydownload.desktop.cookies

import com.anydownload.core.BrowserChoice
import com.anydownload.desktop.store.DesktopCookieStore
import com.anydownload.desktop.store.DesktopStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The T-018 browser snapshot against synthetic stores built in the test. No
 * default test reads a real browser profile: the locator is always injected.
 * Every cookie is `fake_session` / `fake_value`.
 */
class BrowserCookieSnapshotTest {

    private val fakeCookie = "fake_session" to "fake_value"

    private fun tempState(): Path = Files.createTempDirectory("anydownlod-browser-cookies-")

    private fun cookieStore(state: Path): DesktopCookieStore =
        DesktopCookieStore(DesktopStore(state, now = { 1L }))

    // ------------------------------------------------------------- Firefox

    private fun writeFirefoxStore(path: Path) {
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE moz_cookies (host TEXT, path TEXT, expiry INTEGER, name TEXT, " +
                        "value TEXT, isSecure INTEGER)",
                )
                statement.executeUpdate(
                    "INSERT INTO moz_cookies VALUES ('.example.com', '/', 0, '${fakeCookie.first}', " +
                        "'${fakeCookie.second}', 0)",
                )
            }
        }
    }

    @Test
    fun firefoxSyntheticStoreBecomesTheStoredNetscapeFile() = runBlocking {
        val state = tempState()
        val database = state.resolve("cookies.sqlite")
        writeFirefoxStore(database)
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { database },
        )

        val result = importer.snapshot(BrowserChoice.FIREFOX)

        assertTrue(result.success)
        val stored = Files.readString(state.resolve("cookies.txt"))
        assertTrue(stored.contains(".example.com\tTRUE\t/\tFALSE\t0\tfake_session\tfake_value"))
        assertFalse(stored.contains(database.toString()))
    }

    // -------------------------------------------------------------- Chrome

    private fun writeChromeStore(path: Path, secret: String) {
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE cookies (host_key TEXT, path TEXT, expires_utc INTEGER, name TEXT, " +
                        "encrypted_value BLOB, value TEXT, is_secure INTEGER)",
                )
            }
            val expiresUtc = (1_700_000_000L + 11_644_473_600L) * 1_000_000L
            connection.prepareStatement("INSERT INTO cookies VALUES (?, ?, ?, ?, ?, ?, ?)").use { prepared ->
                prepared.setString(1, "media.example.com")
                prepared.setString(2, "/")
                prepared.setLong(3, expiresUtc)
                prepared.setString(4, fakeCookie.first)
                prepared.setBytes(5, encryptForChrome(fakeCookie.second, secret))
                prepared.setString(6, "")
                prepared.setInt(7, 0)
                prepared.executeUpdate()
            }
        }
    }

    /** The test-side inverse of [ChromeCookieStore.decrypt]. */
    private fun encryptForChrome(plain: String, secret: String): ByteArray {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(secret.toCharArray(), "saltysalt".toByteArray(), 1003, 128))
            .encoded
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(ByteArray(16) { 0x20 }),
        )
        return "v10".encodeToByteArray() + cipher.doFinal(plain.encodeToByteArray())
    }

    @Test
    fun chromeSyntheticStoreDecryptsWithTheInjectedSecret() = runBlocking {
        val state = tempState()
        val database = state.resolve("Cookies")
        val secret = "fixture-safe-storage"
        writeChromeStore(database, secret)
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { database },
            chromeSecret = { secret },
        )

        val result = importer.snapshot(BrowserChoice.CHROME)

        assertTrue(result.success)
        val stored = Files.readString(state.resolve("cookies.txt"))
        assertTrue(stored.contains("media.example.com\tFALSE\t/\tFALSE\t1700000000\tfake_session\tfake_value"))
    }

    @Test
    fun chromeWithoutASecretFailsTypedAndDoesNotStoreCiphertext() = runBlocking {
        val state = tempState()
        val database = state.resolve("Cookies")
        writeChromeStore(database, "some-secret")
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { database },
            chromeSecret = { null },
        )

        val result = importer.snapshot(BrowserChoice.CHROME)

        assertTrue(result.success.not())
        assertFalse(Files.exists(state.resolve("cookies.txt")))
    }

    // -------------------------------------------------------------- Safari

    /** Builds one synthetic binarycookies page with the three fixture rows. */
    private fun safariBinaryCookies(): ByteArray {
        val rows = listOf(
            arrayOf(".example.com", "fake_session", "/", "fake_value", "0"),
        )
        val recordCount = rows.size
        val headerSize = 4 + 4 + 4 * recordCount

        val records = ArrayList<ByteArray>(recordCount)
        val offsets = IntArray(recordCount)
        var cursor = headerSize
        for (index in rows.indices) {
            val row = rows[index]
            offsets[index] = cursor
            val domain = (row[0] + "\u0000").encodeToByteArray()
            val name = (row[1] + "\u0000").encodeToByteArray()
            val path = (row[2] + "\u0000").encodeToByteArray()
            val value = (row[3] + "\u0000").encodeToByteArray()
            val record = ByteBuffer.allocate(56 + domain.size + name.size + path.size + value.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(56 + domain.size + name.size + path.size + value.size) // size
                .putInt(0) // version
                .putInt(row[4].toInt()) // flags
                .putInt(0) // has_port
                .putInt(56) // domain offset (record + 56)
                .putInt(56 + domain.size) // name offset
                .putInt(56 + domain.size + name.size) // path offset
                .putInt(56 + domain.size + name.size + path.size) // value offset
                .putInt(0) // comment offset
                .putInt(0) // end offset
                .putDouble(0.0) // expiry: session
                .putDouble(0.0) // creation
                .put(domain)
                .put(name)
                .put(path)
                .put(value)
                .array()
            records += record
            cursor += record.size
        }

        val page = ByteBuffer.allocate(headerSize + records.sumOf { it.size })
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0) // page header
            .putInt(recordCount)
            .apply {
                for (offset in offsets) putInt(offset)
                for (record in records) put(record)
            }
            .array()

        return ByteBuffer.allocate(8 + 4 + page.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put("cook".encodeToByteArray())
            .putInt(1) // one page
            .putInt(page.size)
            .put(page)
            .array()
    }

    @Test
    fun safariSyntheticBinaryCookiesBecomeTheStoredNetscapeFile() = runBlocking {
        val state = tempState()
        val database = state.resolve("Cookies.binarycookies")
        Files.write(database, safariBinaryCookies())
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { database },
        )

        val result = importer.snapshot(BrowserChoice.SAFARI)

        assertTrue(result.success)
        val stored = Files.readString(state.resolve("cookies.txt"))
        assertTrue(stored.contains(".example.com\tTRUE\t/\tFALSE\t0\tfake_session\tfake_value"))
    }

    // ----------------------------------------------------------- failures

    @Test
    fun missingBrowserStoreGetsATypedFailureWithoutTouchingTheStoredFile() = runBlocking {
        val state = tempState()
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { null },
        )

        val result = importer.snapshot(BrowserChoice.SAFARI)

        assertTrue(result.success.not())
        assertFalse(Files.exists(state.resolve("cookies.txt")))
        assertTrue(result.message.orEmpty().contains("Safari"))
    }

    @Test
    fun unreadableBrowserStoreFailsTypedAndDoesNotPartiallyImport() = runBlocking {
        val state = tempState()
        val database = state.resolve("Cookies.binarycookies")
        Files.writeString(database, "not a browser cookie store")
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { database },
        )

        val result = importer.snapshot(BrowserChoice.FIREFOX)

        assertTrue(result.success.not())
        assertFalse(Files.exists(state.resolve("cookies.txt")))
    }

    @Test
    fun snapshotReplacesAnExistingCookieFile() = runBlocking {
        val state = tempState()
        Files.writeString(
            state.resolve("cookies.txt"),
            "# Netscape HTTP Cookie File\nold.example.com\tFALSE\t/\tFALSE\t0\tfake_old\tfake_old_value\n",
        )
        val database = state.resolve("cookies.sqlite")
        writeFirefoxStore(database)
        val store = cookieStore(state)
        val importer = DesktopBrowserCookieImport(
            store = store,
            locateStore = { database },
        )

        assertTrue(importer.snapshot(BrowserChoice.FIREFOX).success)

        val stored = Files.readString(state.resolve("cookies.txt"))
        assertNotNull(store.storedFilePath())
        assertTrue(stored.contains("fake_session"))
        assertFalse(stored.contains("fake_old"))
    }
}
