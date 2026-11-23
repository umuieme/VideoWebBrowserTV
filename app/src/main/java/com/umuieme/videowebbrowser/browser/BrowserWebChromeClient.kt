package com.umuieme.videowebbrowser.browser

import android.graphics.Bitmap
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import com.umuieme.videowebbrowser.browser.policy.NavigationGuard

/**
 * WebChromeClient: load progress, full-screen video, pop-up windows and web permission requests.
 */
class BrowserWebChromeClient(
    private val listener: PageListener,
    private val fullscreenHost: FullscreenHost,
    private val guard: NavigationGuard,
) : WebChromeClient() {

    // Without this, a <video> with no poster shows WebView's grey "play" placeholder before
    // playback starts. A 1x1 transparent bitmap keeps it black instead.
    private val transparentPoster: Bitmap by lazy { createBitmap(1, 1) }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        listener.onProgressChanged(newProgress)
    }

    override fun getDefaultVideoPoster(): Bitmap = transparentPoster

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        fullscreenHost.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        fullscreenHost.onHideCustomView()
    }

    /**
     * The page wants a new window (window.open after a click, or a target=_blank link). The
     * guard catches its URL with a throwaway WebView and then either opens it here (same site)
     * or drops it; no second window is ever shown. Returning false means "no window".
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
        guard.onCreateWindow(view, resultMsg)

    /**
     * Pages ask for camera, microphone, MIDI or protected media through this. Only protected
     * media (EME/Widevine DRM, needed by paid streaming sites) is granted; a TV browser has no
     * business giving sites anything else. Must answer every request, or the page waits forever.
     */
    override fun onPermissionRequest(request: PermissionRequest) {
        val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        if (allowed.isEmpty()) {
            Log.d(TAG, "Denied ${request.resources.joinToString()} for ${request.origin}")
            request.deny()
        } else {
            // grant() takes the subset to allow; anything else in the request stays denied.
            request.grant(allowed.toTypedArray())
        }
    }

    /**
     * Location requests are always denied, without remembering (retain = false) so nothing is
     * stored. Geolocation is also disabled in WebSettings; this covers any WebView version
     * that still asks.
     */
    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    private companion object {
        const val TAG = "BrowserChrome"
    }
}
