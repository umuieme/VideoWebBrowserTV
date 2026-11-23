package com.umuieme.videowebbrowser.browser

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.graphics.createBitmap

/**
 * WebChromeClient: load progress, full-screen video and web permission requests.
 */
class BrowserWebChromeClient(
    private val listener: PageListener,
    private val fullscreenHost: FullscreenHost,
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

    private companion object {
        const val TAG = "BrowserChrome"
    }
}
