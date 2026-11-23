package com.umuieme.videowebbrowser.browser.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    private val list = """
        # Title: test list
        # License: MIT

        ads.example.com
        Tracker.NET   # inline comment
        cdn.videosite.com
        doubleclick.net
    """.trimIndent()

    private val blocker = AdBlocker.parse(list.reader().buffered())
    private val exempt = setOf("videosite.com", "trusted.org")

    @Test
    fun `parse skips comments and blank lines and lowercases`() {
        assertEquals(4, blocker.size)
        assertTrue(blocker.isBlocked("tracker.net", emptySet()))
    }

    @Test
    fun `listed host is blocked`() {
        assertTrue(blocker.isBlocked("ads.example.com", exempt))
        assertTrue(blocker.isBlocked("doubleclick.net", exempt))
    }

    @Test
    fun `subdomains of a listed domain are blocked`() {
        assertTrue(blocker.isBlocked("a.b.ads.example.com", exempt))
        assertTrue(blocker.isBlocked("stats.g.doubleclick.net", exempt))
    }

    @Test
    fun `parents and siblings of a listed domain are not blocked`() {
        assertFalse(blocker.isBlocked("example.com", exempt))
        assertFalse(blocker.isBlocked("www.example.com", exempt))
        assertFalse(blocker.isBlocked("notdoubleclick.net", exempt))
    }

    @Test
    fun `home site and allowlisted domains are never blocked`() {
        assertTrue(blocker.isBlocked("cdn.videosite.com", emptySet()))
        assertFalse(blocker.isBlocked("cdn.videosite.com", exempt))
        val withAllowlistedAdDomain = exempt + "doubleclick.net"
        assertFalse(blocker.isBlocked("stats.g.doubleclick.net", withAllowlistedAdDomain))
    }

    @Test
    fun `host case, trailing dot and null are handled`() {
        assertTrue(blocker.isBlocked("ADS.Example.COM.", exempt))
        assertFalse(blocker.isBlocked(null, exempt))
        assertFalse(blocker.isBlocked("", exempt))
    }

    @Test
    fun `holder starts empty so nothing is blocked before loading`() {
        val holder = AdBlockerHolder()
        assertNull(holder.adBlocker)
        holder.adBlocker = blocker
        assertEquals(blocker, holder.adBlocker)
    }
}
