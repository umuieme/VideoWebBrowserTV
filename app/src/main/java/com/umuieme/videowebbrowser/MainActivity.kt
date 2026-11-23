package com.umuieme.videowebbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.umuieme.videowebbrowser.browser.TemporaryBackCallback
import com.umuieme.videowebbrowser.input.KeyRouter
import com.umuieme.videowebbrowser.ui.AppRoot
import com.umuieme.videowebbrowser.ui.theme.VideoWebBrowserTheme

/**
 * The app's single Activity. It owns window-level concerns (full screen, Back) and hosts
 * Compose; screens live in the ui package.
 */
class MainActivity : ComponentActivity() {

    // TEMPORARY (Milestone 2): replaced by real Back handling in Milestone 5. Back while a video
    // is full screen is handled first by FullscreenBackCallback (added later, so it has priority).
    private val backCallback = TemporaryBackCallback(this)

    // Lives as long as the Activity. Screens register key handlers on it while they're shown.
    private val keyRouter = KeyRouter()

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
                AppRoot(
                    keyRouter = keyRouter,
                    onWebViewChanged = { backCallback.webView = it },
                )
            }
        }
    }

    /**
     * First stop for every key event in the window, before the focused view sees it. The
     * registered screen handler (pointer mode) gets first pick; anything it doesn't consume,
     * including Back, continues through the normal path: focused view, then
     * OnBackPressedDispatcher for Back.
     *
     * RestrictedApi is a lint false positive: androidx.core's ComponentActivity re-declares the
     * public framework method Activity.dispatchKeyEvent with @RestrictTo, and lint then flags
     * every app override. Overriding it is standard, public API.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Key events synthesized by an IME (or `adb shell input`) carry the virtual keyboard's
        // device id. They are typing, not remote navigation, so they pass through untouched.
        if (event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD && keyRouter.dispatch(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
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
