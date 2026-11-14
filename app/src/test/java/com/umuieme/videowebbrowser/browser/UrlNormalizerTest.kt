package com.umuieme.videowebbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {

    private fun valid(url: String) = UrlResult.Valid(url)
    private fun invalid(error: UrlError) = UrlResult.Invalid(error)

    // Normal input

    @Test
    fun `keeps a full https url unchanged`() {
        assertEquals(valid("https://example.com/watch?v=1"), UrlNormalizer.normalize("https://example.com/watch?v=1"))
    }

    @Test
    fun `keeps http when the user typed it`() {
        assertEquals(valid("http://example.com"), UrlNormalizer.normalize("http://example.com"))
    }

    @Test
    fun `lower-cases the scheme but not the path`() {
        assertEquals(valid("https://example.com/Video"), UrlNormalizer.normalize("HTTPS://example.com/Video"))
    }

    // Missing scheme

    @Test
    fun `adds https to a bare domain`() {
        assertEquals(valid("https://example.com"), UrlNormalizer.normalize("example.com"))
    }

    @Test
    fun `adds https to a domain with path and query`() {
        assertEquals(valid("https://m.example.com/tv?x=1"), UrlNormalizer.normalize("m.example.com/tv?x=1"))
    }

    @Test
    fun `treats host colon port as a host, not a scheme`() {
        assertEquals(valid("https://192.168.1.20:8096/web"), UrlNormalizer.normalize("192.168.1.20:8096/web"))
        assertEquals(valid("https://localhost:8080"), UrlNormalizer.normalize("localhost:8080"))
        assertEquals(valid("https://example.com:8443"), UrlNormalizer.normalize("example.com:8443"))
    }

    // Whitespace

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(valid("https://example.com"), UrlNormalizer.normalize("  example.com \n"))
    }

    @Test
    fun `blank and whitespace-only input is blank`() {
        assertEquals(invalid(UrlError.BLANK), UrlNormalizer.normalize(""))
        assertEquals(invalid(UrlError.BLANK), UrlNormalizer.normalize("   \t "))
    }

    @Test
    fun `whitespace inside the url is invalid`() {
        assertEquals(invalid(UrlError.INVALID), UrlNormalizer.normalize("exa mple.com"))
    }

    // Invalid

    @Test
    fun `single word without a dot is invalid`() {
        assertEquals(invalid(UrlError.INVALID), UrlNormalizer.normalize("youtube"))
    }

    @Test
    fun `scheme without host is invalid`() {
        assertEquals(invalid(UrlError.INVALID), UrlNormalizer.normalize("https://"))
    }

    @Test
    fun `empty host labels are invalid`() {
        assertEquals(invalid(UrlError.INVALID), UrlNormalizer.normalize("example..com"))
        assertEquals(invalid(UrlError.INVALID), UrlNormalizer.normalize(".com"))
    }

    @Test
    fun `illegal characters are invalid`() {
        assertEquals(invalid(UrlError.INVALID), UrlNormalizer.normalize("https://exa<mple>.com"))
    }

    // Non-http schemes

    @Test
    fun `rejects non-http schemes`() {
        assertEquals(invalid(UrlError.UNSUPPORTED_SCHEME), UrlNormalizer.normalize("ftp://example.com"))
        assertEquals(invalid(UrlError.UNSUPPORTED_SCHEME), UrlNormalizer.normalize("file:///sdcard/video.mp4"))
        assertEquals(invalid(UrlError.UNSUPPORTED_SCHEME), UrlNormalizer.normalize("javascript:alert(1)"))
        assertEquals(invalid(UrlError.UNSUPPORTED_SCHEME), UrlNormalizer.normalize("intent://scan#Intent;end"))
        assertEquals(invalid(UrlError.UNSUPPORTED_SCHEME), UrlNormalizer.normalize("about:blank"))
    }
}
