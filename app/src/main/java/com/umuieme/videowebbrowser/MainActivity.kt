package com.umuieme.videowebbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.umuieme.videowebbrowser.browser.TemporaryBackCallback
import com.umuieme.videowebbrowser.ui.AppRoot
import com.umuieme.videowebbrowser.ui.theme.VideoWebBrowserTheme

/**
 * The app's single Activity. It owns window-level concerns (full screen, Back) and hosts
 * Compose; screens live in the ui package.
 */
class MainActivity : ComponentActivity() {

    // TEMPORARY (Milestone 2): replaced by real Back handling in Milestone 6.
    private val backCallback = TemporaryBackCallback(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind where system bars would be, then hide them: the site gets every pixel.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Registered with the Activity as lifecycle owner, so it is removed automatically when
        // the Activity is destroyed. Compose BackHandlers registered later take priority.
        onBackPressedDispatcher.addCallback(this, backCallback)

        setContent {
            VideoWebBrowserTheme {
                AppRoot(onWebViewChanged = { backCallback.webView = it })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the IME or system overlays can bring bars back; hide them again on return.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
