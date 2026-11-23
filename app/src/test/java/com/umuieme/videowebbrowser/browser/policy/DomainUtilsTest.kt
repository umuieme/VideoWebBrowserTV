package com.umuieme.videowebbrowser.browser.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUtilsTest {

    @Test
    fun `registrable domain keeps the last two labels for ordinary suffixes`() {
        assertEquals("example.com", DomainUtils.registrableDomain("example.com"))
        assertEquals("example.com", DomainUtils.registrableDomain("www.example.com"))
        assertEquals("example.com", DomainUtils.registrableDomain("a.b.c.example.com"))
        assertEquals("example.tv", DomainUtils.registrableDomain("player.example.tv"))
    }

    @Test
    fun `registrable domain keeps three labels for known multi-label suffixes`() {
        assertEquals("bbc.co.uk", DomainUtils.registrableDomain("www.bbc.co.uk"))
        assertEquals("abc.net.au", DomainUtils.registrableDomain("iview.abc.net.au"))
        assertEquals("site.com.au", DomainUtils.registrableDomain("site.com.au"))
        assertEquals("site.co.in", DomainUtils.registrableDomain("m.site.co.in"))
        assertEquals("globo.com.br", DomainUtils.registrableDomain("g1.globo.com.br"))
        assertEquals("example.co.jp", DomainUtils.registrableDomain("www.example.co.jp"))
    }

    @Test
    fun `a bare multi-label suffix is returned as is`() {
        assertEquals("co.uk", DomainUtils.registrableDomain("co.uk"))
    }

    @Test
    fun `case, whitespace and trailing dot are normalized`() {
        assertEquals("example.com", DomainUtils.registrableDomain(" WWW.Example.COM. "))
    }

    @Test
    fun `IP addresses and localhost are returned unchanged`() {
        assertEquals("192.168.1.10", DomainUtils.registrableDomain("192.168.1.10"))
        assertEquals("::1", DomainUtils.registrableDomain("[::1]"))
        assertEquals("fe80::1", DomainUtils.registrableDomain("fe80::1"))
        assertEquals("localhost", DomainUtils.registrableDomain("localhost"))
    }

    @Test
    fun `single-label hosts are returned unchanged`() {
        assertEquals("nas", DomainUtils.registrableDomain("nas"))
    }

    @Test
    fun `null and blank hosts have no registrable domain`() {
        assertNull(DomainUtils.registrableDomain(null))
        assertNull(DomainUtils.registrableDomain(""))
        assertNull(DomainUtils.registrableDomain("  "))
        assertNull(DomainUtils.registrableDomain("."))
    }

    @Test
    fun `same site compares registrable domains`() {
        assertTrue(DomainUtils.isSameSite("www.example.com", "video.example.com"))
        assertTrue(DomainUtils.isSameSite("example.com", "EXAMPLE.com"))
        assertTrue(DomainUtils.isSameSite("a.bbc.co.uk", "b.bbc.co.uk"))
        assertFalse(DomainUtils.isSameSite("example.com", "example.net"))
        assertFalse(DomainUtils.isSameSite("notexample.com", "example.com"))
        // Different sites under the same multi-label suffix.
        assertFalse(DomainUtils.isSameSite("bbc.co.uk", "itv.co.uk"))
    }

    @Test
    fun `IP addresses are only the same site as themselves`() {
        assertTrue(DomainUtils.isSameSite("10.0.0.1", "10.0.0.1"))
        assertFalse(DomainUtils.isSameSite("10.0.0.1", "10.0.0.2"))
        assertFalse(DomainUtils.isSameSite("1.0.0.1", "0.0.1"))
    }

    @Test
    fun `null never matches, not even null`() {
        assertFalse(DomainUtils.isSameSite(null, null))
        assertFalse(DomainUtils.isSameSite(null, "example.com"))
    }

    @Test
    fun `host and parents walks up to the top-level label`() {
        assertEquals(
            listOf("a.ads.example.com", "ads.example.com", "example.com", "com"),
            DomainUtils.hostAndParents("a.ads.example.com").toList(),
        )
        assertEquals(listOf("10.0.0.1"), DomainUtils.hostAndParents("10.0.0.1").toList())
    }
}
