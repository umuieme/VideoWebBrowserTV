package com.umuieme.videowebbrowser.browser

import java.net.URI
import java.net.URISyntaxException

/** Why user input could not be turned into a URL. The UI maps each case to a message. */
enum class UrlError { BLANK, INVALID, UNSUPPORTED_SCHEME }

sealed interface UrlResult {
    data class Valid(val url: String) : UrlResult
    data class Invalid(val error: UrlError) : UrlResult
}

/**
 * Turns what the user typed on the setup screen into a loadable http(s) URL.
 *
 * Pure Kotlin with java.net.URI only (no android.* imports), so it runs in plain JVM unit tests.
 */
object UrlNormalizer {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    // "name:" at the start, using the characters RFC 3986 allows in a scheme.
    private val SCHEME_PREFIX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$")
    private val ALL_DIGITS = Regex("^\\d+(/.*)?$")

    fun normalize(input: String): UrlResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return UrlResult.Invalid(UrlError.BLANK)
        if (trimmed.any { it.isWhitespace() }) return UrlResult.Invalid(UrlError.INVALID)

        val withScheme = if (hasScheme(trimmed)) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return UrlResult.Invalid(UrlError.INVALID)
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES) return UrlResult.Invalid(UrlError.UNSUPPORTED_SCHEME)
        if (!isPlausibleHost(uri.host)) return UrlResult.Invalid(UrlError.INVALID)

        // Lower-case only the scheme; paths and queries can be case-sensitive.
        return UrlResult.Valid(scheme + withScheme.substring(scheme!!.length))
    }

    /**
     * True if the input already starts with a scheme. "localhost:8080" and "example.com:8080/x"
     * look like "scheme:rest" but the part after ':' is a port, so they don't count.
     */
    private fun hasScheme(input: String): Boolean {
        if (input.contains("://")) return true
        val match = SCHEME_PREFIX.find(input) ?: return false
        val afterColon = match.groupValues[2]
        return !ALL_DIGITS.matches(afterColon)
    }

    /**
     * Requires a dotted host name or IP ("example.com", "192.168.1.20") or "localhost".
     * Single words like "youtube" are rejected: they are almost always a typo for a full domain.
     */
    private fun isPlausibleHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host.startsWith("[")) return true // IPv6 literal; URI already validated it.
        val labels = host.split('.')
        return (labels.size >= 2) && labels.none { it.isEmpty() }
    }
}
