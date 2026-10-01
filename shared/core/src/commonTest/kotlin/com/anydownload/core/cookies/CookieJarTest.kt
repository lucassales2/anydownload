package com.anydownload.core.cookies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Jar matching and header building. Fixtures are synthetic; the values never
 * come from a real profile and are never written anywhere.
 */
class CookieJarTest {

    private val now = 1_800_000_000L

    private fun cookie(
        domain: String = ".example.com",
        includeSubdomains: Boolean = true,
        path: String = "/",
        secure: Boolean = false,
        expires: Long = 0,
        name: String = "fake_session",
        value: String = "fake_value",
    ) = NetscapeCookie(domain, includeSubdomains, path, secure, expires, name, value)

    private fun jar(vararg cookies: NetscapeCookie) = CookieJar(cookies.toList())

    @Test
    fun matchesExactHost() {
        val header = jar(cookie(domain = "example.com", includeSubdomains = false))
            .headerFor("https://example.com/watch?v=1", now)
        assertEquals("fake_session=fake_value", header)
    }

    @Test
    fun dotDomainMatchesTheApexAndSubdomains() {
        val jar = jar(cookie(domain = ".example.com"))
        assertEquals("fake_session=fake_value", jar.headerFor("https://example.com/", now))
        assertEquals("fake_session=fake_value", jar.headerFor("https://media.example.com/", now))
    }

    @Test
    fun hostOnlyCookieDoesNotMatchSubdomains() {
        val jar = jar(cookie(domain = "example.com", includeSubdomains = false))
        assertNull(jar.headerFor("https://media.example.com/", now))
    }

    @Test
    fun includeSubdomainsFlagWithoutADotMatchesSubdomains() {
        val jar = jar(cookie(domain = "example.com", includeSubdomains = true))
        assertEquals("fake_session=fake_value", jar.headerFor("https://media.example.com/", now))
    }

    @Test
    fun aLookalikeDomainNeverMatches() {
        val jar = jar(cookie(domain = ".example.com"))
        assertNull(jar.headerFor("https://notexample.com/", now))
        assertNull(jar.headerFor("https://badexample.com/", now))
    }

    @Test
    fun pathMustPrefixTheRequestPathAtABoundary() {
        val jar = jar(cookie(path = "/private"))
        assertEquals("fake_session=fake_value", jar.headerFor("https://example.com/private/media", now))
        assertNull(jar.headerFor("https://example.com/privateer", now))
        assertNull(jar.headerFor("https://example.com/public", now))
    }

    @Test
    fun slashPathMatchesEverything() {
        val jar = jar(cookie(path = "/"))
        assertEquals("fake_session=fake_value", jar.headerFor("https://example.com/a/b/c", now))
    }

    @Test
    fun secureCookieIsOnlySentOverHttps() {
        val jar = jar(cookie(secure = true))
        assertEquals("fake_session=fake_value", jar.headerFor("https://example.com/", now))
        assertNull(jar.headerFor("http://example.com/", now))
    }

    @Test
    fun expiredRowsAreSkipped() {
        val jar = jar(
            cookie(name = "fake_expired", value = "fake_value", expires = now - 1),
            cookie(name = "fake_session", value = "fake_value", expires = now + 60),
        )
        assertEquals("fake_session=fake_value", jar.headerFor("https://example.com/", now))
    }

    @Test
    fun expiryAtExactlyNowIsExpired() {
        val jar = jar(cookie(expires = now))
        assertNull(jar.headerFor("https://example.com/", now))
    }

    @Test
    fun noMatchMeansNoHeader() {
        assertNull(jar(cookie(domain = ".other.example")).headerFor("https://example.com/", now))
    }

    @Test
    fun headerOrderIsLongestPathThenName() {
        val jar = jar(
            cookie(path = "/", name = "b_short"),
            cookie(path = "/media", name = "z_deep"),
            cookie(path = "/media", name = "a_deep"),
        )
        assertEquals(
            "a_deep=fake_value; z_deep=fake_value; b_short=fake_value",
            jar.headerFor("https://example.com/media/file", now),
        )
    }

    @Test
    fun stateIsReadyAllExpiredOrEmpty() {
        assertEquals(CookieJarState.READY, jar(cookie()).stateAt(now))
        assertEquals(CookieJarState.ALL_EXPIRED, jar(cookie(expires = now - 1)).stateAt(now))
        assertEquals(CookieJarState.EMPTY, CookieJar(emptyList()).stateAt(now))
    }

    @Test
    fun metadataInTheUrlDoesNotBreakParsing() {
        val jar = jar(cookie())
        assertEquals(
            "fake_session=fake_value",
            jar.headerFor("https://Example.COM:8443/media/clip.mp4?token=fake#fragment", now),
        )
    }

    @Test
    fun nonHttpUrlsHaveNoCookies() {
        val jar = jar(cookie())
        assertNull(jar.headerFor("ftp://example.com/file", now))
        assertNull(jar.headerFor("not a url", now))
    }

    @Test
    fun toStringNeverCarriesTheNameOrValue() {
        val text = cookie().toString()
        assertFalse(text.contains("fake_session"))
        assertFalse(text.contains("fake_value"))
    }

    @Test
    fun fromTextUsesTheParser() {
        val text = "# Netscape HTTP Cookie File\n" +
            ".example.com\tTRUE\t/\tFALSE\t0\tfake_session\tfake_value\n"
        val jar = CookieJar.fromText(text)
        assertTrue(jar.stateAt(now) == CookieJarState.READY)
        assertEquals("fake_session=fake_value", jar.headerFor("https://example.com/", now))
    }
}
