package com.umuieme.videowebbrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.umuieme.videowebbrowser.BuildConfig
import com.umuieme.videowebbrowser.browser.media.MediaController

/**
 * The one place a WebView is created and configured, so every setting is visible together.
 */
object WebViewFactory {

    private const val TAG = "WebViewFactory"
    private const val CALIBRATION_ASSET = "js/calibration.js"
    private const val MEDIA_AGENT_ASSET = "js/media-agent.js"

    /**
     * Creates a configured WebView, or a failure if the system WebView provider is missing,
     * disabled or mid-update. In those cases the WebView constructor (and CookieManager) throw
     * instead of returning, and an uncaught throw would crash the app on launch.
     *
     * @param context must be the Activity context: the WebView uses it for IME, dialogs and theming.
     * @param fullscreenHost receives full-screen video requests.
     * @param mediaController gets the media agent installed into this WebView.
     */
    fun create(
        context: Context,
        listener: PageListener,
        fullscreenHost: FullscreenHost,
        mediaController: MediaController,
    ): Result<WebView> =
        try {
            val webView = WebView(context)
            try {
                webView.configure(listener, fullscreenHost, mediaController)
            } catch (e: Exception) {
                webView.destroy() // Don't leak a half-configured instance.
                throw e
            }
            Result.success(webView)
        } catch (e: Exception) {
            Log.e(TAG, "WebView unavailable", e)
            Result.failure(e)
        }

    // JavaScript is required: practically every video site is a JS app.
    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configure(
        listener: PageListener,
        fullscreenHost: FullscreenHost,
        mediaController: MediaController,
    ) {
        settings.apply {
            // Run site JavaScript; video players, menus and most navigation depend on it.
            javaScriptEnabled = true
            // localStorage/sessionStorage: players store volume, quality and login state here.
            domStorageEnabled = true
            // Allow <video autoplay> and play() without a tap. On a TV there is no tap; the
            // remote's keys don't count as a user gesture, so without this videos stay paused.
            mediaPlaybackRequiresUserGesture = false
            // Honour the page's <meta viewport> and, if it has none, lay out at desktop width...
            useWideViewPort = true
            // ...then zoom out so that layout fits the screen instead of opening zoomed in.
            loadWithOverviewMode = true
            // Some sites still load media or players over http from an https page. Compatibility
            // mode allows the "passive" ones (images, video) and blocks scripts, like Chrome does.
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // window.open / target=_blank load in this same WebView instead of needing a second
            // window we would have to manage. Milestone 6 decides which of those to block.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            // Pages have no business reading local files or content providers.
            allowFileAccess = false
            allowContentAccess = false
        }

        // Logins and "remember me" need cookies. Third-party cookies are needed by embedded
        // players (an iframe from another domain) to keep their own session and consent state.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@configure, true)
        }

        // Black behind pages and during loads, instead of a white flash on a dark TV screen.
        setBackgroundColor(Color.BLACK)

        // Hardware acceleration: on by default for the whole app (and stated explicitly in the
        // manifest). We deliberately don't call setLayerType(LAYER_TYPE_HARDWARE): that renders
        // the WebView into an extra offscreen buffer, which costs memory and can break video.

        // The WebView must be focusable to receive D-pad keys from the remote.
        isFocusable = true
        isFocusableInTouchMode = true

        // Before any loadUrl: document-start scripts and message listeners only apply to
        // documents created after they are added.
        mediaController.install(this, loadAsset(context, MEDIA_AGENT_ASSET))

        webViewClient = BrowserWebViewClient(
            listener = listener,
            calibrationScript = loadCalibrationScript(context),
            onMainFrameStarted = mediaController::onMainFrameNavigation,
        )
        webChromeClient = BrowserWebChromeClient(listener, fullscreenHost)
    }

    private fun loadAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    /** Debug builds only; release builds skip this, so the calibration code never runs there. */
    private fun loadCalibrationScript(context: Context): String? {
        if (!BuildConfig.DEBUG) return null
        return loadAsset(context, CALIBRATION_ASSET)
    }
}
