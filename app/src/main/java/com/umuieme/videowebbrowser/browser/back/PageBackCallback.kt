package com.umuieme.videowebbrowser.browser.back

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import com.umuieme.videowebbrowser.browser.PageScripts
import com.umuieme.videowebbrowser.input.InputTuning

/**
 * Steps 4–6 of the Back chain (see [BackStepCallback] for the whole chain) in one callback:
 *
 * 4. close the topmost site popup (close-modal.js), or send Escape if it has no close control;
 * 5. otherwise WebView history back;
 * 6. otherwise "Press Back again to exit".
 *
 * Why one callback: whether a popup is open is only known after the script answers, and a
 * dispatcher callback can't hand a press it already took to the next one. So this callback is
 * always enabled (the lowest one in the chain, added in MainActivity.onCreate) and walks the
 * three steps itself.
 *
 * **Async:** `evaluateJavascript` answers later, on the main thread. While an answer is pending,
 * further Back presses are ignored (a frozen page can't queue up a burst of navigations), and a
 * timeout makes sure a page that never answers can't swallow Back: after
 * [InputTuning.CLOSE_MODAL_TIMEOUT_MS] we continue as if there were no popup. A token discards
 * the late answer of a request that already timed out.
 *
 * Main thread only.
 *
 * @param onExit finishes the Activity.
 * @param onExitHint shows "Press Back again to exit".
 */
class PageBackCallback(
    private val scripts: PageScripts,
    private val onExit: () -> Unit,
    private val onExitHint: () -> Unit,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : OnBackPressedCallback(true) {

    /** The page's WebView while the browser screen shows one; null on other screens. */
    var webView: WebView? = null
        set(value) {
            if (field !== value) cancelPending()
            field = value
        }

    private val popups = PopupBackPolicy()
    private val exit = ExitConfirmation()
    private val handler = Handler(Looper.getMainLooper())

    private var pendingToken = 0
    private var pendingTimeout: Runnable? = null

    override fun handleOnBackPressed() {
        if (pendingTimeout != null) return // still waiting for the previous press's answer
        val view = webView
        // No page, or the page is hidden behind the error panel: no popups to close.
        if (view == null || !view.isShown) {
            goBackOrExit(view)
            return
        }
        val token = ++pendingToken
        val timeout = Runnable { onCloseResult(token, CloseModalResult.Failed) }
        pendingTimeout = timeout
        handler.postDelayed(timeout, InputTuning.CLOSE_MODAL_TIMEOUT_MS)
        view.evaluateJavascript(scripts.closeModal(popups.stuckKey(clock()))) { json ->
            onCloseResult(token, CloseModalResult.parse(json))
        }
    }

    private fun onCloseResult(token: Int, result: CloseModalResult) {
        if (token != pendingToken || pendingTimeout == null) return // stale or already timed out
        cancelPending()
        when (popups.decide(result, clock())) {
            PageBackAction.Done -> Unit
            PageBackAction.SendEscape -> webView?.let(::sendEscape)
            PageBackAction.GoBack -> goBackOrExit(webView)
        }
    }

    private fun goBackOrExit(view: WebView?) {
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else if (exit.onBack(clock())) {
            onExit()
        } else {
            onExitHint()
        }
    }

    /**
     * A native Escape key DOWN + UP, dispatched straight to the WebView (not through the
     * Activity, so our own key handling never sees it). The page gets a trusted `keydown` with
     * `key === "Escape"`, which is how most popup libraries close.
     */
    private fun sendEscape(view: WebView) {
        val now = SystemClock.uptimeMillis()
        view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0))
        view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0))
    }

    private fun cancelPending() {
        pendingTimeout?.let(handler::removeCallbacks)
        pendingTimeout = null
    }
}
