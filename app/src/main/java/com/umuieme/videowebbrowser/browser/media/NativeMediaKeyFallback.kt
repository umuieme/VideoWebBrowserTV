package com.umuieme.videowebbrowser.browser.media

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import com.umuieme.videowebbrowser.input.MediaAction

/**
 * Fallback when the agent can't be used: sends key presses a player understands to [target]
 * (the full-screen view, or the WebView). SPACE toggles play/pause in HTML5 default controls
 * and in most site players; Left/Right seek (5–10 s depending on the player).
 *
 * Sent straight to the view's dispatchKeyEvent, so they never pass through
 * MainActivity.dispatchKeyEvent and can't loop back into our own key handling.
 */
class NativeMediaKeyFallback(private val target: () -> View?) {

    /** Returns false if there is no target view. */
    fun send(action: MediaAction): Boolean {
        val view = target() ?: return false
        val keyCode = when (action) {
            // There is no "play only" key a page understands; toggling is the best we can do.
            MediaAction.TogglePlay, MediaAction.Play, MediaAction.Pause -> KeyEvent.KEYCODE_SPACE
            is MediaAction.SeekBy ->
                if (action.seconds < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        }
        val downTime = SystemClock.uptimeMillis()
        view.dispatchKeyEvent(keyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode))
        view.dispatchKeyEvent(keyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode))
        return true
    }

    private fun keyEvent(downTime: Long, eventTime: Long, action: Int, keyCode: Int) = KeyEvent(
        downTime, eventTime, action, keyCode,
        /* repeat = */ 0,
        /* metaState = */ 0,
        KeyCharacterMap.VIRTUAL_KEYBOARD,
        /* scancode = */ 0,
        /* flags = */ 0,
        InputDevice.SOURCE_KEYBOARD,
    )
}
