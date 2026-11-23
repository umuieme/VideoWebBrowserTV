package com.umuieme.videowebbrowser.browser.policy

import android.os.Message
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.umuieme.videowebbrowser.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/** The user's per-site choices, owned by the ViewModel. Both sets hold registrable domains. */
data class SitePermissions(
    val allowlist: Set<String> = emptySet(),
    val allowedOnce: Set<String> = emptySet(),
)

/** What the navigation guard tells the UI. Called on the main thread. */
interface PolicyListener {
    /** A navigation or pop-up to [domain] was blocked: show a brief hint. */
    fun onBlocked(domain: String)

    /**
     * The user clicked a link to another site. Return true if the ask dialog is now showing;
     * false (another question is already open) makes the guard block it instead.
     */
    fun onAsk(url: String, domain: String): Boolean

    /** A download was ignored. */
    fun onDownloadBlocked()
}

/**
 * Android-side glue between one WebView's callbacks and the pure [NavigationPolicy] and
 * [AdBlocker]. Owns the live context the policy needs (home domains, the latest permissions,
 * the app-started load currently in progress) and the pop-up catcher. One per WebView.
 *
 * Threads: everything runs on the main thread except [shouldBlockRequest], which WebView calls
 * on its background network threads. That method only reads [exemptDomains] (a `@Volatile`
 * reference to an immutable set, replaced as a whole on the main thread) and the
 * [AdBlockerHolder], so it needs no locks.
 */
class NavigationGuard(
    private val adBlockerHolder: AdBlockerHolder,
    private val listener: PolicyListener,
) {
    private var homeDomains: Set<String> = emptySet()
    private var permissions = SitePermissions()

    /**
     * Set by [loadTrusted] until the page finishes. While set, server redirects are allowed
     * (see NavigationPolicy rule 5). `isHome` also adds the landing domain to [homeDomains].
     */
    private var trustedLoad: TrustedLoad? = null

    @Volatile
    private var exemptDomains: Set<String> = emptySet()

    private val blockedOnPage = AtomicInteger()

    private val popupCatcher = PopupCatcher(::onNewWindowUrl)

    /** The WebView this guard serves; set by [loadTrusted], used to open same-site pop-ups. */
    private var webView: WebView? = null

    /** Latest allowlist and allowed-once sets. Takes effect for the next navigation and request. */
    fun updatePermissions(newPermissions: SitePermissions) {
        permissions = newPermissions
        refreshExemptions()
    }

    /**
     * Records a domain as allowed once right away. The ViewModel's copy arrives a frame later
     * through [updatePermissions]; without this, the load started right after "Allow once"
     * could race it and be asked about again.
     */
    fun allowOnceNow(domain: String) {
        permissions = permissions.copy(allowedOnce = permissions.allowedOnce + domain)
        refreshExemptions()
    }

    /**
     * Loads [url] as an app-started load: its server redirects are trusted. Use for the home
     * page ([isHome] = true), Retry, approved Ask targets and same-site pop-ups. Note that
     * loadUrl() itself never goes through shouldOverrideUrlLoading; only its redirects do.
     */
    fun loadTrusted(webView: WebView, url: String, isHome: Boolean = false) {
        this.webView = webView
        if (isHome) {
            homeDomains = setOfNotNull(NavigationPolicy.domainOf(url))
            refreshExemptions()
        }
        trustedLoad = TrustedLoad(isHome)
        webView.loadUrl(url)
    }

    /** shouldOverrideUrlLoading: true means "cancel this navigation". */
    fun shouldOverride(request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val decision = NavigationPolicy.decide(
            NavigationRequest(
                url = url,
                isMainFrame = request.isForMainFrame,
                hasGesture = request.hasGesture(),
                isRedirect = request.isRedirect,
                trustedRedirect = trustedLoad != null,
            ),
            context(),
        )
        return when (decision) {
            NavigationDecision.Allow -> false
            is NavigationDecision.Block -> {
                onBlockedDecision(decision, url)
                true
            }
            is NavigationDecision.Ask -> {
                if (!listener.onAsk(url, decision.domain)) listener.onBlocked(decision.domain)
                true
            }
        }
    }

    /** WebChromeClient.onCreateWindow: catch the pop-up's URL with a throwaway WebView. */
    fun onCreateWindow(opener: WebView, resultMsg: Message): Boolean {
        webView = opener
        return popupCatcher.catchWindow(opener, resultMsg)
    }

    private fun onNewWindowUrl(url: String) {
        val request = NavigationRequest(
            url = url,
            isMainFrame = true,
            // onCreateWindow only happens after a gesture (JS can't open windows on its own).
            hasGesture = true,
            isRedirect = false,
            fromNewWindow = true,
        )
        when (val decision = NavigationPolicy.decide(request, context())) {
            // Same site: open it here, replacing the current page, like a link would.
            NavigationDecision.Allow -> webView?.let { loadTrusted(it, url) }
            is NavigationDecision.Block -> onBlockedDecision(decision, url)
            // The policy never asks for new windows; treat it as blocked if that ever changes.
            is NavigationDecision.Ask -> listener.onBlocked(decision.domain)
        }
    }

    /**
     * shouldInterceptRequest, on a WebView background thread: true means "answer with an empty
     * response". Main-frame documents belong to the navigation policy, not the ad list.
     */
    fun shouldBlockRequest(host: String?, isMainFrame: Boolean): Boolean {
        if (isMainFrame) return false
        val blocker = adBlockerHolder.adBlocker ?: return false // Not loaded yet: allow.
        val blocked = blocker.isBlocked(host, exemptDomains)
        if (blocked && BuildConfig.DEBUG) blockedOnPage.incrementAndGet()
        return blocked
    }

    /** Main-frame onPageStarted: [url] is where the load actually landed after redirects. */
    fun onPageStarted(url: String) {
        blockedOnPage.set(0)
        if (trustedLoad?.isHome == true) {
            NavigationPolicy.domainOf(url)?.let { landed ->
                if (landed !in homeDomains) {
                    homeDomains = homeDomains + landed
                    refreshExemptions()
                    debugLog { "home redirected to $landed; treating it as home too" }
                }
            }
        }
    }

    fun onPageFinished(url: String) {
        trustedLoad = null
        debugLog { "blocked ${blockedOnPage.get()} ad requests on $url" }
    }

    /** Call when the WebView is destroyed. */
    fun release() {
        popupCatcher.release()
        webView = null
    }

    private fun context() = PolicyContext(homeDomains, permissions.allowlist, permissions.allowedOnce)

    private fun refreshExemptions() {
        exemptDomains = homeDomains + permissions.allowlist + permissions.allowedOnce
    }

    private fun onBlockedDecision(decision: NavigationDecision.Block, url: String) {
        debugLog { "blocked ${decision.reason}: $url" }
        // intent:, market: and other schemes have no domain to show: they are dropped silently.
        decision.domain?.let(listener::onBlocked)
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private class TrustedLoad(val isHome: Boolean)

    private companion object {
        const val TAG = "VWB-Policy"
    }
}
