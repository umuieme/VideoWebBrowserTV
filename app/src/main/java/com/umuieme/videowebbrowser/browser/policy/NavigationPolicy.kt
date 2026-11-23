package com.umuieme.videowebbrowser.browser.policy

/**
 * One navigation WebView asks us about.
 *
 * @param isMainFrame true for the top-level page, false for an iframe.
 * @param hasGesture true when the navigation follows a real user action (our injected clicks
 *   count; page timers and script-only redirects don't).
 * @param isRedirect true for a server (HTTP 3xx) redirect of a navigation already in progress.
 * @param fromNewWindow true when the page asked for a new window (window.open, target=_blank).
 * @param trustedRedirect true while a load the *app* started (home page, Retry, an approved
 *   "Allow once") is still redirecting. Only server redirects benefit from it; see [NavigationPolicy].
 */
data class NavigationRequest(
    val url: String,
    val isMainFrame: Boolean,
    val hasGesture: Boolean,
    val isRedirect: Boolean,
    val fromNewWindow: Boolean = false,
    val trustedRedirect: Boolean = false,
)

/**
 * What the policy knows about the user's choices. All sets hold registrable domains
 * (see [DomainUtils.registrableDomain]).
 *
 * @param homeDomains the chosen site, plus the domain it redirected to at launch, if any.
 * @param allowlist "Always allow", persisted in DataStore.
 * @param allowedOnce "Allow once", in memory for this app session.
 */
data class PolicyContext(
    val homeDomains: Set<String>,
    val allowlist: Set<String> = emptySet(),
    val allowedOnce: Set<String> = emptySet(),
)

enum class BlockReason {
    /** intent:, market:, tel:, javascript: and anything else that isn't http(s). */
    UnsupportedScheme,

    /** http(s) URL without a usable host. */
    InvalidUrl,

    /** A new window to another site. Pop-ups are never worth a question. */
    PopUp,

    /** A navigation to another site that no user action caused (script timer, ad script). */
    NoGesture,

    /** A redirect hop to another site that the app didn't start. */
    Redirect,
}

sealed interface NavigationDecision {
    data object Allow : NavigationDecision

    /** @param domain registrable domain of the target, null when it has none (other schemes). */
    data class Block(val reason: BlockReason, val domain: String?) : NavigationDecision

    /** The user clicked a link to another site: ask before leaving. */
    data class Ask(val domain: String) : NavigationDecision
}

/**
 * Decides whether a navigation keeps the user on their chosen site. Pure Kotlin: no WebView,
 * no Android types, so every rule is covered by unit tests. Rules, first match wins:
 *
 * 1. Not http(s) → Block. A WebView can't show intent:/market: anyway; it would only display an
 *    error page, or an ad would try to launch the Play Store.
 * 2. Iframe (not main frame, not a new window) → Allow. Embedded players are cross-origin
 *    iframes; they are governed by ad-domain request blocking only.
 * 3. Same site as home, allowlisted, or allowed once → Allow.
 * 4. New window to another site → Block (no question for pop-ups).
 * 5. Server redirect of a load the app started → Allow (e.g. home URL `site.com` → `site.tv`,
 *    or an approved short link redirecting to its target).
 * 6. Another site, user gesture, not a redirect → Ask.
 * 7. Anything else (no gesture, or a redirect chain) → Block.
 */
object NavigationPolicy {

    fun decide(request: NavigationRequest, context: PolicyContext): NavigationDecision {
        val parts = UrlParts.parse(request.url)
        if (parts?.scheme != "http" && parts?.scheme != "https") {
            return NavigationDecision.Block(BlockReason.UnsupportedScheme, domain = null)
        }
        if (!request.isMainFrame && !request.fromNewWindow) return NavigationDecision.Allow

        val domain = DomainUtils.registrableDomain(parts.host)
            ?: return NavigationDecision.Block(BlockReason.InvalidUrl, domain = null)
        if (domain in context.homeDomains || domain in context.allowlist || domain in context.allowedOnce) {
            return NavigationDecision.Allow
        }
        return when {
            request.fromNewWindow -> NavigationDecision.Block(BlockReason.PopUp, domain)
            request.isRedirect && request.trustedRedirect -> NavigationDecision.Allow
            request.hasGesture && !request.isRedirect -> NavigationDecision.Ask(domain)
            request.isRedirect -> NavigationDecision.Block(BlockReason.Redirect, domain)
            else -> NavigationDecision.Block(BlockReason.NoGesture, domain)
        }
    }

    /** Registrable domain of [url]'s host, or null for non-http(s) or host-less URLs. */
    fun domainOf(url: String): String? {
        val parts = UrlParts.parse(url) ?: return null
        if (parts.scheme != "http" && parts.scheme != "https") return null
        return DomainUtils.registrableDomain(parts.host)
    }
}

/**
 * Scheme and host of a URL. Hand-parsed rather than java.net.URI, which throws on characters
 * that real pages put in URLs (`|`, unescaped spaces); and android.net.Uri isn't usable in JVM
 * unit tests.
 */
internal data class UrlParts(val scheme: String, val host: String?) {
    companion object {
        fun parse(url: String): UrlParts? {
            val trimmed = url.trim()
            val colon = trimmed.indexOf(':')
            if (colon <= 0) return null
            val scheme = trimmed.substring(0, colon).lowercase()
            if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
            val rest = trimmed.substring(colon + 1)
            if (!rest.startsWith("//")) return UrlParts(scheme, host = null)

            // //user:pass@host:port/path?query#fragment → host:port
            val authority = rest.substring(2).takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
            val hostAndPort = authority.substringAfterLast('@')
            val host = if (hostAndPort.startsWith("[")) {
                hostAndPort.substringBefore(']') + "]" // IPv6 literal; DomainUtils strips brackets.
            } else {
                hostAndPort.substringBefore(':')
            }
            return UrlParts(scheme, DomainUtils.normalizeHost(host))
        }
    }
}
