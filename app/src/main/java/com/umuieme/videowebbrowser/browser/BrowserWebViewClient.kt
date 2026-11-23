package com.umuieme.videowebbrowser.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.policy.NavigationGuard
import java.io.ByteArrayInputStream

/**
 * WebViewClient: page lifecycle and main-frame errors go to [listener]; navigation and ad
 * request decisions go to [guard].
 *
 * @param calibrationScript JS evaluated after every page load and logged; null in release builds.
 * @param onMainFrameStarted called when the top-level page starts a new document.
 */
class BrowserWebViewClient(
    private val listener: PageListener,
    private val guard: NavigationGuard,
    private val calibrationScript: String?,
    private val onMainFrameStarted: () -> Unit = {},
) : WebViewClient() {

    private data class SslFailure(val url: String, val primaryError: Int)

    /** Latest certificate failure, waiting to see whether it was the page itself (see onReceivedSslError). */
    private var sslFailure: SslFailure? = null

    /**
     * Called before WebView follows a link, a script navigation or a server redirect (in any
     * frame). Returning true cancels it. Not called for our own loadUrl(), for back/forward,
     * or for form POSTs. All decisions live in NavigationPolicy, via the guard.
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        guard.shouldOverride(request)

    /**
     * Called for every resource request (scripts, images, XHR, iframes) on a WebView *background*
     * thread, many in parallel, and it delays the request while it runs: keep it fast. Returning
     * null lets WebView fetch normally. For a listed ad domain we answer ourselves with an empty
     * 200, so the request never leaves the device and the page sees a harmless empty file
     * (a network error would make some ad scripts retry in a loop).
     *
     * Injected scripts and the media agent's message channel are not network requests, so they
     * never pass through here.
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (!guard.shouldBlockRequest(request.url.host, request.isForMainFrame)) return null
        // A new stream each time: a response body can only be read once.
        return WebResourceResponse("text/plain", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Main frame only. Every frame's media state belongs to the old document now.
        onMainFrameStarted()
        // A failure remembered for some other URL belongs to an earlier load.
        if (sslFailure?.url != url) sslFailure = null
        guard.onPageStarted(url)
        listener.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        guard.onPageFinished(url)
        listener.onPageFinished(url)
        reportSslFailureIfPage(view, url)
        calibrationScript?.let { script ->
            // evaluateJavascript returns the script's last value, JSON-encoded.
            view.evaluateJavascript(script) { result -> Log.d(CALIBRATION_TAG, "$result url=$url") }
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // Sub-resource failures (an ad, an image) are normal on real sites; only a failed
        // top-level document deserves an error screen.
        if (request.isForMainFrame) {
            listener.onMainFrameError(request.url.toString(), error.description.toString())
        }
    }

    /**
     * A TLS certificate problem (expired, not yet valid, untrusted). We never proceed: that
     * would defeat HTTPS. The default behaviour also cancels, but silently: onReceivedError is
     * *not* called and onPageFinished still is, so a failed main page left a black screen with
     * no error panel. A wrong TV clock is the usual cause (certificates are checked against it),
     * hence the date hint.
     *
     * SslError has no "is main frame" flag, and this arrives *before* onPageStarted for the same
     * load (which clears any error). So we only remember the failure here and report it from
     * [onPageFinished] if the page that finished is the URL that failed. A failed sub-resource
     * (an ad, an image) never matches the page URL and is just dropped, like other sub-resource
     * failures.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        Log.w(TAG, "SSL error ${error.primaryError} for ${error.url}")
        sslFailure = SslFailure(error.url, error.primaryError)
    }

    private fun reportSslFailureIfPage(view: WebView, url: String) {
        val failure = sslFailure ?: return
        sslFailure = null
        if (failure.url != url) return
        val message = when (failure.primaryError) {
            SslError.SSL_NOTYETVALID, SslError.SSL_EXPIRED, SslError.SSL_DATE_INVALID -> R.string.ssl_error_date
            else -> R.string.ssl_error_untrusted
        }
        listener.onMainFrameError(url, view.context.getString(message))
    }

    /**
     * Without this override, a renderer crash or out-of-memory kill takes the whole app down.
     * Returning true means "handled": we must stop using this WebView, which BrowserScreen does
     * by destroying it and offering Retry (a fresh WebView).
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.e(TAG, "Renderer gone (crashed=${detail.didCrash()}, priority=${detail.rendererPriorityAtExit()})")
        listener.onRenderProcessGone()
        return true
    }

    companion object {
        private const val TAG = "BrowserWebViewClient"

        /** Logcat tag for the debug-only viewport calibration log. */
        const val CALIBRATION_TAG = "VWB-Calibration"
    }
}
