package com.umuieme.videowebbrowser.browser.media

import android.util.Log
import com.umuieme.videowebbrowser.BuildConfig
import com.umuieme.videowebbrowser.input.MediaAction

/**
 * Carries out a [MediaAction] through the right path ([MediaRoutePolicy]): the agent when a
 * video is known, native keys as a fallback, or not at all (the caller lets the key through).
 */
class MediaRouter(
    private val controller: MediaController,
    private val fallback: NativeMediaKeyFallback,
) {
    fun perform(action: MediaAction, fullscreen: Boolean): MediaRoute {
        val route = MediaRoutePolicy.choose(
            agentAvailable = controller.agentAvailable,
            hasActiveVideo = controller.activeVideo.value != null,
            fullscreen = fullscreen,
        )
        val effective = when (route) {
            MediaRoute.Agent -> if (controller.send(action)) route else fallbackOrPassThrough(action)
            MediaRoute.NativeKeys -> fallbackOrPassThrough(action)
            MediaRoute.PassThrough -> route
        }
        if (BuildConfig.DEBUG) Log.d(MediaController.TAG, "$action -> $effective (fullscreen=$fullscreen)")
        return effective
    }

    private fun fallbackOrPassThrough(action: MediaAction) =
        if (fallback.send(action)) MediaRoute.NativeKeys else MediaRoute.PassThrough
}
