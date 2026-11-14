package com.umuieme.videowebbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {

    /** Asserts that [input] normalizes to the URL [expectedUrl]. */
    private fun assertValid(input: String, expectedUrl: String) {
        val actual = UrlNormalizer.normalize(input)
        assertEquals("input: \"$input\"", UrlResult.Valid(expectedUrl), actual)
    }

    /** Asserts that [input] is rejected with [expectedError]. */
    private fun assertInvalid(input: String, expectedError: UrlError) {
        val actual = UrlNormalizer.normalize(input)
        assertEquals("input: \"$input\"", UrlResult.Invalid(expectedError), actual)
    }

    // Normal input

    @Test
    fun `keeps a full https url unchanged`() {
        assertValid("https://example.com/watch?v=1", "https://example.com/watch?v=1")
    }

    @Test
    fun `keeps http when the user typed it`() {
        assertValid("http://example.com", "http://example.com")
    }

    @Test
    fun `lower-cases the scheme but not the path`() {
        assertValid("HTTPS://example.com/Video", "https://example.com/Video")
    }

    // Missing scheme

    @Test
    fun `adds https to a bare domain`() {
        assertValid("example.com", "https://example.com")
    }

    @Test
    fun `adds https to a domain with path and query`() {
        assertValid("m.example.com/tv?x=1", "https://m.example.com/tv?x=1")
    }

    @Test
    fun `treats host colon port as a host, not a scheme`() {
        assertValid("192.168.1.20:8096/web", "https://192.168.1.20:8096/web")
        assertValid("localhost:8080", "https://localhost:8080")
        assertValid("example.com:8443", "https://example.com:8443")
    }

    // Whitespace

    @Test
    fun `trims surrounding whitespace`() {
        assertValid("  example.com \n", "https://example.com")
    }

    @Test
    fun `blank and whitespace-only input is blank`() {
        assertInvalid("", UrlError.BLANK)
        assertInvalid("   \t ", UrlError.BLANK)
    }

    @Test
    fun `whitespace inside the url is invalid`() {
        assertInvalid("exa mple.com", UrlError.INVALID)
    }

    // Invalid

    @Test
    fun `single word without a dot is invalid`() {
        assertInvalid("youtube", UrlError.INVALID)
    }

    @Test
    fun `scheme without host is invalid`() {
        assertInvalid("https://", UrlError.INVALID)
    }

    @Test
    fun `empty host labels are invalid`() {
        assertInvalid("example..com", UrlError.INVALID)
        assertInvalid(".com", UrlError.INVALID)
    }

    @Test
    fun `illegal characters are invalid`() {
        assertInvalid("https://exa<mple>.com", UrlError.INVALID)
    }

    // Non-http schemes

    @Test
    fun `rejects non-http schemes`() {
        assertInvalid("ftp://example.com", UrlError.UNSUPPORTED_SCHEME)
        assertInvalid("file:///sdcard/video.mp4", UrlError.UNSUPPORTED_SCHEME)
        assertInvalid("javascript:alert(1)", UrlError.UNSUPPORTED_SCHEME)
        assertInvalid("intent://scan#Intent;end", UrlError.UNSUPPORTED_SCHEME)
        assertInvalid("about:blank", UrlError.UNSUPPORTED_SCHEME)
    }
}
