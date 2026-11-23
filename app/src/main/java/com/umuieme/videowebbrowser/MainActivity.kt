package com.umuieme.videowebbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.umuieme.videowebbrowser.browser.back.PageBackCallback
import com.umuieme.videowebbrowser.input.BackKeyTracker
import com.umuieme.videowebbrowser.input.BackPress
import com.umuieme.videowebbrowser.input.KeyRouter
import com.umuieme.videowebbrowser.ui.AppRoot
import com.umuieme.videowebbrowser.ui.BrowserViewModel
import com.umuieme.videowebbrowser.ui.theme.VideoWebBrowserTheme

/**
 * The app's single Activity. It owns window-level concerns (full screen, the Back key) and
 * hosts Compose; screens live in the ui package.
 */
class MainActivity : ComponentActivity() {

    private val container get() = (application as VideoWebBrowserApp).container

    // Lowest step of the Back chain (close popup / history / exit). Added first, in onCreate, so
    // every callback added later (scroll mode, full screen, dialogs) is asked before it.
    private val pageBackCallback by lazy {
        PageBackCallback(
            scripts = container.pageScripts,
            onExit = ::finish,
            onExitHint = { Toast.makeText(this, R.string.back_again_to_exit, Toast.LENGTH_SHORT).show() },
        )
    }

    // Lives as long as the Activity. Screens register key handlers on it while they're shown.
    private val keyRouter = KeyRouter()

    // Hold Back = menu. The tracker is pure; this Handler is its timer on the main thread.
    private val backKeys = BackKeyTracker()
    private val backTimer = Handler(Looper.getMainLooper())
    private val backTick = Runnable { onBackPress(backKeys.onTick(SystemClock.uptimeMillis())) }

    // The same instance AppRoot gets from viewModel(): both ask this Activity's ViewModelStore
    // for BrowserViewModel with the default key.
    private val viewModel by lazy { ViewModelProvider(this, BrowserViewModel.Factory)[BrowserViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind where system bars would be, then hide them: the site gets every pixel.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Registered with the Activity as lifecycle owner, so it is removed automatically when
        // the Activity is destroyed.
        onBackPressedDispatcher.addCallback(this, pageBackCallback)

        setContent {
            VideoWebBrowserTheme {
                AppRoot(
                    keyRouter = keyRouter,
                    onWebViewChanged = { pageBackCallback.webView = it },
                    adBlockerHolder = container.adBlockerHolder,
                    pageScripts = container.pageScripts,
                    viewModel = viewModel,
                )
            }
        }
    }

    /**
     * First stop for every key event in the window, before the focused view sees it.
     *
     * - **Back** is always taken here, so it never reaches the WebView directly (WebView would
     *   otherwise navigate back on its own and skip our chain). A short press goes to
     *   OnBackPressedDispatcher, which runs the Back chain; a hold opens the menu. This relies on
     *   targetSdk 35: from 36, predictive back delivers Back outside dispatchKeyEvent (see
     *   CLAUDE.md). The on-screen keyboard still closes on Back: Android offers the key to the
     *   IME before the Activity sees it.
     * - Other keys go to the registered screen handler (pointer/scroll mode) first; anything it
     *   doesn't consume continues to the focused view.
     *
     * RestrictedApi is a lint false positive: androidx.core's ComponentActivity re-declares the
     * public framework method Activity.dispatchKeyEvent with @RestrictTo, and lint then flags
     * every app override. Overriding it is standard, public API.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            onBackKey(event)
            return true
        }
        // Key events synthesized by an IME (or `adb shell input`) carry the virtual keyboard's
        // device id. They are typing, not remote navigation, so they pass through untouched.
        if (event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD && keyRouter.dispatch(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onBackKey(event: KeyEvent) {
        val press = when (event.action) {
            KeyEvent.ACTION_DOWN -> backKeys.onDown(event.eventTime, isRepeat = event.repeatCount > 0)
            KeyEvent.ACTION_UP ->
                // Canceled: the system took the gesture over; it was never a press for us.
                if (event.isCanceled) null.also { backKeys.reset() } else backKeys.onUp(event.eventTime)
            else -> null
        }
        onBackPress(press)
    }

    private fun onBackPress(press: BackPress?) {
        // Re-arm the timer for the tracker's next deadline (none once the press is resolved).
        backTimer.removeCallbacks(backTick)
        backKeys.nextDeadlineMs?.let { backTimer.postAtTime(backTick, it) }
        when (press) {
            BackPress.Short -> onBackPressedDispatcher.onBackPressed()
            BackPress.Long -> viewModel.onMenuRequested()
            null -> Unit
        }
    }

    override fun onPause() {
        super.onPause()
        // A Back held while leaving the app never sends its UP to us.
        backKeys.reset()
        backTimer.removeCallbacks(backTick)
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
