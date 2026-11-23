package com.umuieme.videowebbrowser.browser.media

/** How a media command is carried out. */
enum class MediaRoute {
    /** Sent to the media agent in the frame that owns the active video. */
    Agent,

    /** Native key events (SPACE, DPAD_LEFT/RIGHT) sent to the current target view. */
    NativeKeys,

    /** Not handled by us: the original key continues to the WebView unchanged. */
    PassThrough,
}

/** Pure decision of which [MediaRoute] to use. Unit-tested. */
object MediaRoutePolicy {

    /**
     * - A known active video always goes through the agent: precise play/pause/seek.
     * - In full screen without one, native keys: the full-screen view holds only the video,
     *   so SPACE/arrows reach the player (its default controls or the site's key handlers).
     * - Agent unsupported by the WebView at all: native keys too, it's the only way we have.
     * - Inline with a working agent but no video: pass the key through. SPACE would scroll
     *   the page down, which is worse than doing nothing.
     */
    fun choose(agentAvailable: Boolean, hasActiveVideo: Boolean, fullscreen: Boolean): MediaRoute = when {
        agentAvailable && hasActiveVideo -> MediaRoute.Agent
        fullscreen || !agentAvailable -> MediaRoute.NativeKeys
        else -> MediaRoute.PassThrough
    }
}
