package com.umuieme.videowebbrowser.browser.policy

import com.umuieme.videowebbrowser.browser.policy.BlockReason.InvalidUrl
import com.umuieme.videowebbrowser.browser.policy.BlockReason.NoGesture
import com.umuieme.videowebbrowser.browser.policy.BlockReason.PopUp
import com.umuieme.videowebbrowser.browser.policy.BlockReason.Redirect
import com.umuieme.videowebbrowser.browser.policy.BlockReason.UnsupportedScheme
import com.umuieme.videowebbrowser.browser.policy.NavigationDecision.Allow
import com.umuieme.videowebbrowser.browser.policy.NavigationDecision.Ask
import com.umuieme.videowebbrowser.browser.policy.NavigationDecision.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The policy as a rule table: each row is one request and the decision we expect. Rows are
 * grouped by the rule that should decide them; the row label shows up in failure messages.
 */
class NavigationPolicyTest {

    private val context = PolicyContext(
        homeDomains = setOf("videosite.com"),
        allowlist = setOf("trusted.org"),
        allowedOnce = setOf("onetime.net"),
    )

    private data class Row(
        val label: String,
        val url: String,
        val mainFrame: Boolean = true,
        val gesture: Boolean = false,
        val redirect: Boolean = false,
        val newWindow: Boolean = false,
        val trusted: Boolean = false,
        val expected: NavigationDecision,
    )

    private val table = listOf(
        // Rule 1: non-http(s) schemes are blocked everywhere, even with a gesture or in an iframe.
        Row("intent link", "intent://scan/#Intent;scheme=zxing;end", gesture = true, expected = Block(UnsupportedScheme, null)),
        Row("market link", "market://details?id=com.ad", gesture = true, expected = Block(UnsupportedScheme, null)),
        Row("tel link", "tel:123", gesture = true, expected = Block(UnsupportedScheme, null)),
        Row("javascript url", "javascript:void(0)", expected = Block(UnsupportedScheme, null)),
        Row("intent in iframe", "intent://x#Intent;end", mainFrame = false, expected = Block(UnsupportedScheme, null)),
        Row("intent new window", "intent://x#Intent;end", newWindow = true, expected = Block(UnsupportedScheme, null)),
        Row("scheme only, no colon", "not a url", expected = Block(UnsupportedScheme, null)),
        Row("uppercase scheme is still http", "HTTPS://videosite.com/", expected = Allow),

        // Rule 2: iframes are allowed whatever their origin, gesture or redirect.
        Row("cross-site iframe", "https://player.cdn.io/embed/1", mainFrame = false, expected = Allow),
        Row("iframe redirect, no gesture", "https://ads.example/x", mainFrame = false, redirect = true, expected = Allow),

        // http(s) with no host can't be matched to a site.
        Row("http without host", "http:///path", gesture = true, expected = Block(InvalidUrl, null)),

        // Rule 3: home, allowlisted and allowed-once sites are allowed in any form.
        Row("home exact", "https://videosite.com/watch/1", expected = Allow),
        Row("home subdomain", "https://m.videosite.com/", expected = Allow),
        Row("home redirect, no gesture", "https://cdn.videosite.com/v", redirect = true, expected = Allow),
        Row("home with port and userinfo", "https://u:p@videosite.com:8443/", expected = Allow),
        Row("allowlisted", "https://www.trusted.org/a", expected = Allow),
        Row("allowlisted redirect", "https://trusted.org/a", redirect = true, expected = Allow),
        Row("allowed once", "https://onetime.net/", gesture = true, expected = Allow),
        Row("same-site new window", "https://videosite.com/popup", newWindow = true, gesture = true, expected = Allow),
        Row("allowlisted new window", "https://trusted.org/", newWindow = true, expected = Allow),

        // Rule 4: cross-site new windows are blocked; no question even with a gesture.
        Row("pop-up with gesture", "https://ads.example/land", newWindow = true, gesture = true, expected = Block(PopUp, "ads.example")),
        Row("pop-up without gesture", "https://ads.example/land", newWindow = true, expected = Block(PopUp, "ads.example")),
        Row("pop-up trusted redirect", "https://ads.example/", newWindow = true, redirect = true, trusted = true, expected = Block(PopUp, "ads.example")),

        // Rule 5: server redirects of an app-started load are allowed.
        Row("home moved to other domain", "https://videosite.tv/", redirect = true, trusted = true, expected = Allow),
        // ...but trust doesn't cover a non-redirect navigation without gesture.
        Row("trusted flag, script nav", "https://evil.example/", trusted = true, expected = Block(NoGesture, "evil.example")),

        // Rule 6: a user's click to another site asks.
        Row("cross-site click", "https://news.example.co.uk/story", gesture = true, expected = Ask("example.co.uk")),
        Row("not-quite home", "https://videosite.com.evil.io/", gesture = true, expected = Ask("evil.io")),

        // Rule 7: everything else cross-site is blocked.
        Row("script redirect", "https://ads.example/", expected = Block(NoGesture, "ads.example")),
        Row("redirect after click", "https://tracker.example/", gesture = true, redirect = true, expected = Block(Redirect, "tracker.example")),
        Row("redirect without gesture", "https://tracker.example/", redirect = true, expected = Block(Redirect, "tracker.example")),
        Row("IP address", "http://192.168.1.5:8080/", expected = Block(NoGesture, "192.168.1.5")),
    )

    @Test
    fun `rule table`() {
        for (row in table) {
            val request = NavigationRequest(
                url = row.url,
                isMainFrame = row.mainFrame,
                hasGesture = row.gesture,
                isRedirect = row.redirect,
                fromNewWindow = row.newWindow,
                trustedRedirect = row.trusted,
            )
            assertEquals(row.label, row.expected, NavigationPolicy.decide(request, context))
        }
    }

    @Test
    fun `allowlist changes take effect on the next decision`() {
        val request = NavigationRequest("https://later.example/", isMainFrame = true, hasGesture = true, isRedirect = false)
        assertEquals(Ask("later.example"), NavigationPolicy.decide(request, context))
        assertEquals(Allow, NavigationPolicy.decide(request, context.copy(allowlist = setOf("later.example"))))
    }

    @Test
    fun `IP home matches only itself`() {
        val ipContext = PolicyContext(homeDomains = setOf("192.168.1.5"))
        val same = NavigationRequest("http://192.168.1.5/x", isMainFrame = true, hasGesture = false, isRedirect = false)
        val other = same.copy(url = "http://192.168.1.6/x")
        assertEquals(Allow, NavigationPolicy.decide(same, ipContext))
        assertEquals(Block(NoGesture, "192.168.1.6"), NavigationPolicy.decide(other, ipContext))
    }

    @Test
    fun `domainOf returns the registrable domain for http(s) only`() {
        assertEquals("videosite.com", NavigationPolicy.domainOf("https://www.videosite.com/a?b#c"))
        assertEquals("::1", NavigationPolicy.domainOf("http://[::1]:8080/"))
        assertNull(NavigationPolicy.domainOf("intent://x"))
        assertNull(NavigationPolicy.domainOf("about:blank"))
    }
}
