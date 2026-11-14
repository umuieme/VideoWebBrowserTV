package com.umuieme.videowebbrowser.browser

import android.app.Activity
import android.os.SystemClock
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.umuieme.videowebbrowser.R

/**
 * TEMPORARY (Milestone 2). Milestone 6 replaces this with real Back handling: close site popups
 * first, hold Back = app menu.
 *
 * Back goes back in WebView history when possible; otherwise the first press shows a toast and
 * a second press within [EXIT_WINDOW_MS] exits.
 */
class TemporaryBackCallback(private val activity: Activity) : OnBackPressedCallback(true) {

    /** Set by BrowserScreen while a WebView is on screen; null otherwise. */
    var webView: WebView? = null

    private var lastBackPressAt = 0L

    override fun handleOnBackPressed() {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
            return
        }
        // elapsedRealtime, not currentTimeMillis: it can't jump when the TV syncs its clock.
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressAt <= EXIT_WINDOW_MS) {
            activity.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(activity, R.string.back_again_to_exit, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val EXIT_WINDOW_MS = 2_000L
    }
}
