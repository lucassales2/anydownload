package com.anydownlod.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlPolicyTest {

    private fun rejected(url: String): UrlRejectReason {
        val result = UrlPolicy.check(url)
        assertTrue(result is UrlCheck.Rejected, "expected $url to be rejected, got $result")
        return (result as UrlCheck.Rejected).reason
    }

    private fun allowed(url: String) {
        assertTrue(UrlPolicy.check(url) is UrlCheck.Allowed, "expected $url to be allowed")
    }

    @Test
    fun allowsPublicHttpAndHttpsFixtureUrls() {
        allowed("https://example.com/files/tiny.bin")
        allowed("http://fixtures.example.com/assets/tiny.bin")
        allowed("https://example.com:8443/files/tiny.bin")
        allowed("https://8.8.8.8/dns")
        allowed("https://172.32.5.5/not-in-172.16-31")
        allowed("https://[2606:4700:4700::1111]/ipv6")
    }

    @Test
    fun rejectsNonHttpSchemesAndMissingHosts() {
        assertEquals(UrlRejectReason.UnsupportedScheme, rejected("ftp://example.com/a"))
        assertEquals(UrlRejectReason.UnsupportedScheme, rejected("file:///etc/passwd"))
        assertEquals(UrlRejectReason.UnsupportedScheme, rejected("javascript:alert(1)"))
        assertEquals(UrlRejectReason.MissingHost, rejected("https:///path"))
        assertEquals(UrlRejectReason.MissingHost, rejected("https://"))
    }

    @Test
    fun rejectsUserInfoCredentials() {
        assertEquals(UrlRejectReason.UserInfo, rejected("https://user:pass@example.com/a"))
        assertEquals(UrlRejectReason.UserInfo, rejected("http://token@example.com/a"))
    }

    @Test
    fun rejectsPrivateLoopbackLinkLocalAndReservedIpv4() {
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://127.0.0.1/admin"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://localhost:8080/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://10.1.2.3/a"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://192.168.0.1/a"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://172.16.5.5/a"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://172.31.255.255/a"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://169.254.10.1/a"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://0.0.0.0/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://100.64.1.1/cgnat"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://224.0.0.1/multicast"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("https://255.255.255.255/broadcast"))
    }

    @Test
    fun rejectsLoopbackAndPrivateIpv6() {
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://[::1]/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://[::ffff:127.0.0.1]/mapped"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://[fe80::1]/link-local"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://[fd00::1234]/ula"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://[ff02::1]/multicast"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://[2001:db8::1]/doc"))
    }

    @Test
    fun allowsPublicIpv4MappedAddress() {
        allowed("https://[::ffff:8.8.8.8]/mapped-public")
    }

    @Test
    fun rejectsLocalHostNames() {
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://localhost/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://printer.local/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://router.internal/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://host.localdomain/x"))
        assertEquals(UrlRejectReason.BlockedDestination, rejected("http://mysql.localhost:3306/x"))
    }

    @Test
    fun rejectsMalformedPorts() {
        assertEquals(UrlRejectReason.Malformed, rejected("https://example.com:port/x"))
        assertEquals(UrlRejectReason.Malformed, rejected("https://example.com:99999/x"))
    }

    @Test
    fun extractsHostForSourceHostColumn() {
        assertEquals("example.com", UrlPolicy.hostOf("https://example.com/files/tiny.bin"))
        assertEquals("cdn.fixtures.example.net", UrlPolicy.hostOf("https://cdn.fixtures.example.net/asset.bin?x=1#f"))
        assertEquals(null, UrlPolicy.hostOf("not-a-url"))
    }

    @Test
    fun cleanlySetsRedirectBudget() {
        assertEquals(10, UrlPolicy.MAX_REDIRECTS)
    }
}