package com.umuieme.videowebbrowser.browser.media

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.umuieme.videowebbrowser.BuildConfig
import com.umuieme.videowebbrowser.input.MediaAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kotlin side of the media agent: installs it into a WebView, keeps the latest video state per
 * frame and sends commands to the frame that owns the active video.
 *
 * How the pieces fit:
 * - [install] calls `addDocumentStartJavaScript(agent, "*")`: WebView runs media-agent.js at
 *   the start of every document in every frame (main page and iframes, any origin), before the
 *   page's own scripts.
 * - It also calls `addWebMessageListener("vwbMedia", "*", this)`: WebView defines a
 *   `vwbMedia` object in those frames. `vwbMedia.postMessage(text)` arrives in [onPostMessage]
 *   on the main thread, together with a [JavaScriptReplyProxy] belonging to *that frame*.
 *   `replyProxy.postMessage(text)` sends a message event back to that frame only. We keep the
 *   proxy as the frame's identity and address; posting to a frame that has gone away is
 *   silently ignored by WebView.
 *
 * One instance per WebView. Main thread only (WebView calls the listener on the UI thread).
 */
class MediaController(
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : WebViewCompat.WebMessageListener {

    /** False when the installed WebView lacks the needed features; callers then use the fallback. */
    var agentAvailable: Boolean = false
        private set

    private val frames = LinkedHashMap<JavaScriptReplyProxy, VideoState>()
    private var activeFrame: JavaScriptReplyProxy? = null

    private val _activeVideo = MutableStateFlow<VideoState?>(null)

    /** State of the video the remote currently controls, or null if none is known. */
    val activeVideo: StateFlow<VideoState?> = _activeVideo.asStateFlow()

    /**
     * Installs the agent into [webView]. Must run before the first page load: both APIs only
     * affect documents created after the call. Returns whether the agent is available.
     */
    fun install(webView: WebView, agentScript: String): Boolean {
        // The installed WebView (updated through Play, not with Android) decides; check at runtime.
        // Both features are needed; check each right before its call (lint verifies this shape).
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (supported) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                // Listener first, so the vwbMedia object already exists when the script runs.
                WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, ALL_ORIGINS, this)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, agentScript, ALL_ORIGINS)
            }
        }
        agentAvailable = supported
        debugLog { "media agent ${if (supported) "installed" else "unsupported by this WebView; using key fallback"}" }
        return supported
    }

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (message.type != WebMessageCompat.TYPE_STRING) return
        val parsed = message.data?.let(MediaMessages::parse)
        when (parsed) {
            null -> debugLog { "dropped invalid message from $sourceOrigin" }
            AgentMessage.Gone -> frames.remove(replyProxy)
            is AgentMessage.State -> onState(replyProxy, parsed)
        }
        recompute()
    }

    /** The main frame started a new document: every frame's state is now stale. */
    fun onMainFrameNavigation() {
        frames.clear()
        recompute()
    }

    /** Sends [action] to the active video's frame. Returns false if there is no active video. */
    // A reply proxy only exists if WEB_MESSAGE_LISTENER was supported in install().
    @SuppressLint("RequiresFeature")
    fun send(action: MediaAction): Boolean {
        val proxy = activeFrame ?: return false
        proxy.postMessage(MediaMessages.encodeCommand(action))
        // Commands count as interaction: the video we just controlled stays the preferred one.
        frames[proxy]?.let { frames[proxy] = it.copy(lastInteractionMs = clock()) }
        recompute()
        return true
    }

    private fun onState(proxy: JavaScriptReplyProxy, state: AgentMessage.State) {
        val previous = frames[proxy]
        // Bound memory: an ad-heavy page (or a hostile one) can't make us track unlimited frames.
        if (previous == null && frames.size >= MAX_FRAMES) return
        val interactedAt = if (state.event.isInteraction) clock() else previous?.lastInteractionMs ?: 0L
        frames[proxy] = VideoState(
            playing = state.playing,
            currentTimeSec = state.currentTimeSec,
            durationSec = state.durationSec,
            inFullscreen = state.inFullscreen,
            visibleArea = state.visibleArea,
            lastInteractionMs = interactedAt,
        )
    }

    private fun recompute() {
        val selected = ActiveVideoSelector.select(frames)
        activeFrame = selected
        _activeVideo.value = selected?.let(frames::get)
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    companion object {
        const val TAG = "VWB-Media"

        /** Name of the JS object WebView defines in each frame (window.vwbMedia). */
        const val BRIDGE_NAME = "vwbMedia"

        /** Every frame, any origin: video players usually live in third-party iframes. */
        private val ALL_ORIGINS = setOf("*")

        private const val MAX_FRAMES = 32
    }
}
