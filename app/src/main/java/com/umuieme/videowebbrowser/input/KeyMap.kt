package com.umuieme.videowebbrowser.input

import android.view.KeyEvent

/**
 * Translates Android key codes into [RemoteKey]. Pure Kotlin: KEYCODE_* are compile-time
 * constants, so this runs in plain JVM unit tests without an emulator.
 */
object KeyMap {

    /** Returns the [RemoteKey] for [keyCode], or null for keys the app leaves alone (e.g. Back). */
    fun fromKeyCode(keyCode: Int): RemoteKey? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> RemoteKey.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteKey.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteKey.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteKey.Right
        // Remotes send DPAD_CENTER; USB/Bluetooth keyboards and some remotes send ENTER.
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> RemoteKey.Ok
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RemoteKey.PlayPause
        // Separate Play and Pause keys (some remotes, keyboards, Bluetooth headsets) keep their
        // meaning: "Pause" must never start a paused video.
        KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteKey.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteKey.Pause
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteKey.FastForward
        KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteKey.Rewind
        else -> null
    }
}
