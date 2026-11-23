package com.umuieme.videowebbrowser.browser

/**
 * Page events the UI cares about. The WebView clients call these; BrowserScreen turns them
 * into Compose state. Keeps android.webkit callback details out of the UI code.
 */
interface PageListener {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)

    /** 0–100, from WebChromeClient. */
    fun onProgressChanged(progress: Int)

    /** The top-level page failed to load (no network, DNS failure, timeout...). */
    fun onMainFrameError(failedUrl: String, description: String)

    /**
     * The WebView's renderer process crashed or was killed (usually out of memory). The WebView
     * is dead: it must be removed and destroyed, and a new one created to continue.
     */
    fun onRenderProcessGone()
}
