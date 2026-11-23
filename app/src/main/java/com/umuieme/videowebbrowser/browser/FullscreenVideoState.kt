package com.umuieme.videowebbrowser.browser

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Receives WebView's full-screen requests. Implemented by [FullscreenVideoState]. */
interface FullscreenHost {
    fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideCustomView()
}

/** The view WebView handed us for full screen, and how to tell WebView we closed it. */
class FullscreenSession(val view: View, val callback: WebChromeClient.CustomViewCallback)

/**
 * Compose-observable full-screen video state, one per WebView (remembered by BrowserScreen).
 *
 * When a page calls `requestFullscreen()`, WebView doesn't resize itself. It hands the app a
 * separate View through WebChromeClient.onShowCustomView and expects the app to show it over
 * everything. Leaving full screen can start from either side:
 * - the page or WebView (exitFullscreen(), the player's button): WebView calls onHideCustomView;
 * - us (Back): we call `callback.onCustomViewHidden()` so WebView updates the page.
 *
 * Both paths go through [end], which runs its cleanup exactly once: state is cleared first, so
 * if WebView calls onHideCustomView again from inside onCustomViewHidden (some versions do),
 * the second call finds nothing to do.
 *
 * Why here and not in the ViewModel: [FullscreenSession.view] is a View holding the Activity
 * context. A ViewModel outlives views, so keeping one there leaks it and is wrong after
 * recreation. Like PageState and PointerState, this is screen-level UI state.
 */
@Stable
class FullscreenVideoState : FullscreenHost {

    var current by mutableStateOf<FullscreenSession?>(null)
        private set

    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (current != null) {
            // Already showing one. Refuse the new request rather than swap views under the
            // user; telling WebView it was hidden makes the page leave full screen cleanly.
            Log.w(TAG, "Full-screen request while already full screen; refused")
            callback.onCustomViewHidden()
            return
        }
        current = FullscreenSession(view, callback)
    }

    /** WebView or the page left full screen. */
    override fun onHideCustomView() = end(notifyWebView = false)

    /** Our own exit path (Back). */
    fun exitByUser() = end(notifyWebView = true)

    private fun end(notifyWebView: Boolean) {
        val session = current ?: return // Already ended: the exactly-once guard.
        current = null
        // Detach right away (don't wait for the next composition): WebView may reuse or destroy
        // the view as soon as it's told, and a View can only have one parent.
        (session.view.parent as? ViewGroup)?.removeView(session.view)
        if (notifyWebView) session.callback.onCustomViewHidden()
    }

    private companion object {
        const val TAG = "FullscreenVideo"
    }
}
