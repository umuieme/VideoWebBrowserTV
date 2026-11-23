package com.umuieme.videowebbrowser.browser.policy

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Finds out where a pop-up wants to go, without ever showing it.
 *
 * WebView doesn't tell us the URL of a new window. With setSupportMultipleWindows(true),
 * window.open() and target=_blank links call WebChromeClient.onCreateWindow, which must hand
 * back a WebView through a [WebView.WebViewTransport]; WebView then navigates *that* view to
 * the pop-up's URL. So we hand it a throwaway WebView that is never attached to the screen,
 * read the first URL it is asked to load, cancel that load, and destroy it.
 *
 * The throwaway view has JavaScript off (the default) and no media agent, so nothing the
 * pop-up contains ever runs. Main thread only.
 *
 * @param onUrl receives the pop-up's URL once; the caller runs the navigation policy on it.
 */
class PopupCatcher(private val onUrl: (String) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var current: Catch? = null

    // A pop-up opened as window.open('') and then filled with document.write() never navigates;
    // without a timeout its WebView would live until the next pop-up.
    private val timeout = Runnable { current?.dispose() }

    /**
     * Call from onCreateWindow and return its result. Returns false (meaning "no window") if the
     * temporary WebView can't be created or [resultMsg] isn't the expected transport message.
     */
    fun catchWindow(opener: WebView, resultMsg: Message): Boolean {
        current?.dispose() // At most one at a time: a newer pop-up replaces an older one.
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val temp = try {
            WebView(opener.context)
        } catch (e: Exception) {
            // Same failure modes as the main WebView (provider updating); just drop the pop-up.
            Log.w(TAG, "Couldn't create pop-up catcher", e)
            return false
        }
        val catch = Catch(temp)
        temp.webViewClient = catch
        transport.webView = temp
        current = catch
        handler.postDelayed(timeout, TIMEOUT_MS)
        resultMsg.sendToTarget() // Tells WebView to start loading the pop-up into `temp`.
        return true
    }

    /** Destroys any pending catcher; call when the main WebView goes away. */
    fun release() {
        current?.dispose()
    }

    /** One caught pop-up: its throwaway WebView, and the client that reads its first URL. */
    private inner class Catch(private val view: WebView) : WebViewClient() {
        private var disposed = false

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            capture(request.url.toString())
            return true // Cancel: the pop-up never loads anything.
        }

        // Backup: some WebView versions start the first load without asking
        // shouldOverrideUrlLoading. about:blank is the empty initial document, not a target.
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url != ABOUT_BLANK) capture(url)
        }

        /**
         * All WebViews in the app share one renderer process. If it dies while this catcher
         * exists, every WebView gets this callback, and one that doesn't return true crashes the
         * app. Ours just goes away; the main WebView's client shows the crash panel.
         */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            dispose()
            return true
        }

        private fun capture(url: String) {
            if (disposed) return // Already captured, timed out or replaced by a newer pop-up.
            dispose()
            onUrl(url)
        }

        /** Idempotent: callbacks, the timeout and release() may all race to call it. */
        fun dispose() {
            if (disposed) return
            disposed = true
            if (current === this) {
                current = null
                handler.removeCallbacks(timeout)
            }
            // Posted, never inline: we're often inside one of this WebView's own callbacks,
            // and destroying a WebView from within its own callback is unsafe.
            handler.post {
                view.stopLoading()
                view.destroy()
            }
        }
    }

    private companion object {
        const val TAG = "VWB-Popup"
        const val TIMEOUT_MS = 3_000L
        const val ABOUT_BLANK = "about:blank"
    }
}
