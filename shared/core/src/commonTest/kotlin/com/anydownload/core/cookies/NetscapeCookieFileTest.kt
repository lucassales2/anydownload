package com.anydownload.core.cookies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Format validation and parsing. All fixtures are synthetic (`fake_session`
 * / `fake_value`); no real profile, browser store, or personal file is read.
 */
class NetscapeCookieFileTest {

    private fun row(
        domain: String = ".example.com",
        includeSubdomains: String = "TRUE",
        path: String = "/",
        secure: String = "FALSE",
        expires: Long = 0,
        name: String = "fake_session",
        value: String = "fake_value",
    ): String = listOf(domain, includeSubdomains, path, secure, expires.toString(), name, value)
        .joinToString("\t")

    @Test
    fun validatesNetscapeHeaderFile() {
        val text = "# Netscape HTTP Cookie File\n${row()}\n"
        assertNull(NetscapeCookieFile.validate(text))
    }

    @Test
    fun validatesShortHttpHeaderFile() {
        val text = "# HTTP Cookie File\n${row()}\n"
        assertNull(NetscapeCookieFile.validate(text))
    }

    @Test
    fun validatesHeaderOnlyFile() {
        // The owner rule accepts the header even with no data rows; the jar
        // then reports EMPTY / ALL_EXPIRED, which is an error state.
        assertNull(NetscapeCookieFile.validate("# Netscape HTTP Cookie File\n"))
    }

    @Test
    fun validatesTabbedRowWithoutHeader() {
        assertNull(NetscapeCookieFile.validate(row() + "\n"))
    }

    @Test
    fun rejectsBlankText() {
        assertEquals(NetscapeCookieFileError.EMPTY, NetscapeCookieFile.validate("   \n\t\n"))
    }

    @Test
    fun rejectsTextThatIsNotACookieFile() {
        assertEquals(
            NetscapeCookieFileError.NOT_A_COOKIE_FILE,
            NetscapeCookieFile.validate("just some notes, not a cookie file"),
        )
    }

    @Test
    fun rejectsShortTabbedRows() {
        assertEquals(
            NetscapeCookieFileError.NOT_A_COOKIE_FILE,
            NetscapeCookieFile.validate(".example.com\tTRUE\t/\tFALSE\t0\tname\n"),
        )
    }

    @Test
    fun rejectsOversizedText() {
        val text = "# Netscape HTTP Cookie File\n" + "x".repeat(NetscapeCookieFile.MAX_BYTES.toInt())
        assertEquals(NetscapeCookieFileError.TOO_LARGE, NetscapeCookieFile.validate(text))
    }

    @Test
    fun parseReadsEveryField() {
        val text = "# Netscape HTTP Cookie File\n" +
            row(
                domain = ".example.com",
                includeSubdomains = "TRUE",
                path = "/private",
                secure = "TRUE",
                expires = 1_900_000_000,
                name = "fake_session",
                value = "fake_value",
            )

        val cookie = NetscapeCookieFile.parse(text).single()

        assertEquals(".example.com", cookie.domain)
        assertTrue(cookie.includeSubdomains)
        assertEquals("/private", cookie.path)
        assertTrue(cookie.secure)
        assertEquals(1_900_000_000, cookie.expiresAtEpochSeconds)
        assertEquals("fake_session", cookie.name)
        assertEquals("fake_value", cookie.value)
    }

    @Test
    fun parseSkipsCommentsBlanksAndMalformedRows() {
        val text = buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine()
            appendLine("# a comment")
            appendLine(".example.com\tTRUE\t/\tFALSE\tfake_expiry\tname\tvalue")
            appendLine(".example.com\tTRUE\t/\tFALSE\t0\tname")
            appendLine(row())
        }

        assertEquals(1, NetscapeCookieFile.parse(text).size)
    }

    @Test
    fun parseReadsHttpOnlyRows() {
        val text = "# Netscape HTTP Cookie File\n" +
            "#HttpOnly_.example.com\tTRUE\t/\tFALSE\t0\tfake_session\tfake_value\n"

        val cookie = NetscapeCookieFile.parse(text).single()

        assertEquals(".example.com", cookie.domain)
        assertEquals("fake_session", cookie.name)
    }

    @Test
    fun parseSkipsRowsWithIllegalNamesOrValues() {
        val text = buildString {
            appendLine(row(name = "bad;name", value = "fake_value"))
            appendLine(row(name = "good_name", value = "bad;value"))
            appendLine(row(name = "good_name", value = "bad value"))
            appendLine(row(name = "bad\nname", value = "fake_value"))
            appendLine(row(name = "good_name", value = "fake_value"))
        }

        assertEquals(1, NetscapeCookieFile.parse(text).size)
    }

    @Test
    fun parseKeepsSessionZeroExpiry() {
        val cookie = NetscapeCookieFile.parse(row(expires = 0)).single()
        assertEquals(0, cookie.expiresAtEpochSeconds)
        assertTrue(!cookie.isExpired(nowEpochSeconds = Long.MAX_VALUE))
    }

    @Test
    fun parseNormalizesNothingOutsideTheFieldsItReads() {
        // A path that does not start with "/" stays as stored; it simply will
        // not match a request path. This keeps the file's bytes meaningful.
        val cookie = NetscapeCookieFile.parse(row(path = "relative")).single()
        assertEquals("relative", cookie.path)
        assertNotNull(cookie)
    }

    @Test
    fun formatWritesRowsThatParseBack() {
        val text = NetscapeCookieFile.format(
            listOf(
                NetscapeCookie(".example.com", true, "/", false, 0, "fake_session", "fake_value"),
                NetscapeCookie("host.example.com", false, "/private", true, 1_900_000_000, "fake_other", "fake_other_value"),
            ),
        )

        val parsed = NetscapeCookieFile.parse(text)

        assertEquals(2, parsed.size)
        assertEquals("fake_session", parsed[0].name)
        assertEquals("fake_value", parsed[0].value)
        assertTrue(parsed[0].includeSubdomains)
        assertEquals("/private", parsed[1].path)
        assertTrue(parsed[1].secure)
        assertEquals(1_900_000_000, parsed[1].expiresAtEpochSeconds)
    }

    @Test
    fun formatSkipsRowsThatWouldCorruptTheFile() {
        val text = NetscapeCookieFile.format(
            listOf(
                NetscapeCookie(".example.com", true, "/", false, 0, "bad;name", "fake_value"),
                NetscapeCookie(".example.com", true, "/", false, 0, "fake_session", "fake_value"),
            ),
        )

        assertTrue(!text.contains("bad;name"))
        assertEquals(1, NetscapeCookieFile.parse(text).size)
    }

    @Test
    fun formatOfAnEmptyListIsStillAValidHeaderFile() {
        val text = NetscapeCookieFile.format(emptyList())
        assertNull(NetscapeCookieFile.validate(text))
        assertTrue(NetscapeCookieFile.parse(text).isEmpty())
    }
}
