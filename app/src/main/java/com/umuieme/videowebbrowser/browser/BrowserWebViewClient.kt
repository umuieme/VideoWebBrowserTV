package com.umuieme.videowebbrowser.browser

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Basic WebViewClient: reports page lifecycle and main-frame errors to [listener].
 * Milestone 6 adds the navigation policy (popup/redirect blocking, allowlist) here.
 *
 * @param calibrationScript JS evaluated after every page load and logged; null in release builds.
 */
class BrowserWebViewClient(
    private val listener: PageListener,
    private val calibrationScript: String?,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Let the WebView load http(s) itself. Other schemes (intent:, market:, tel:...) can't
        // load in a WebView and would only show an ERR_UNKNOWN_URL_SCHEME page, so ignore them.
        val scheme = request.url.scheme?.lowercase()
        return scheme != "http" && scheme != "https"
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        listener.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onPageFinished(url)
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

    companion object {
        /** Logcat tag for the debug-only viewport calibration log. */
        const val CALIBRATION_TAG = "VWB-Calibration"
    }
}
