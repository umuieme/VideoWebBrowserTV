package com.umuieme.videowebbrowser.browser

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.graphics.createBitmap

/**
 * Basic WebChromeClient: reports load progress. Milestone 5 adds full-screen video
 * (onShowCustomView / onHideCustomView) here.
 */
class BrowserWebChromeClient(private val listener: PageListener) : WebChromeClient() {

    // Without this, a <video> with no poster shows WebView's grey "play" placeholder before
    // playback starts. A 1x1 transparent bitmap keeps it black instead.
    private val transparentPoster: Bitmap by lazy { createBitmap(1, 1) }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        listener.onProgressChanged(newProgress)
    }

    override fun getDefaultVideoPoster(): Bitmap = transparentPoster
}
