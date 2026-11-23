package com.umuieme.videowebbrowser.ui.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umuieme.videowebbrowser.input.MediaAction

/**
 * The OSD event for [action] carried out through the agent or the fallback.
 *
 * @param playingBefore whether the active video was playing before the action, or null if
 *   unknown (fallback path), in which case a toggle's result can't be predicted.
 */
fun osdEventFor(action: MediaAction, playingBefore: Boolean?): OsdEvent = when (action) {
    MediaAction.Play -> OsdEvent.Playing
    MediaAction.Pause -> OsdEvent.Paused
    MediaAction.TogglePlay -> when (playingBefore) {
        true -> OsdEvent.Paused
        false -> OsdEvent.Playing
        null -> OsdEvent.PlayPause
    }
    is MediaAction.SeekBy -> OsdEvent.Seek(action.seconds)
}

/** What the OSD announces. */
sealed interface OsdEvent {
    data object Playing : OsdEvent
    data object Paused : OsdEvent

    /** Toggled without knowing the result (native-key fallback). */
    data object PlayPause : OsdEvent

    /** Total seek in the current burst, e.g. +40 after four presses. */
    data class Seek(val seconds: Int) : OsdEvent
}

/**
 * State of the on-screen display. [show] restarts the visibility timer (VideoOsd watches
 * [showCount]); consecutive seeks in the same direction add up while the OSD is visible, so
 * holding Right reads "+10 s", "+20 s", "+50 s"... instead of a flickering "+10 s".
 */
@Stable
class OsdState {

    /** Last event shown. Kept after hiding so the fade-out still shows its text. */
    var event by mutableStateOf<OsdEvent?>(null)
        private set

    var visible by mutableStateOf(false)
        private set

    /** Bumped on every [show]; restarts the auto-hide timer. */
    var showCount by mutableIntStateOf(0)
        private set

    fun show(newEvent: OsdEvent) {
        val current = event
        event = if (visible && newEvent is OsdEvent.Seek && current is OsdEvent.Seek &&
            (newEvent.seconds > 0) == (current.seconds > 0)
        ) {
            OsdEvent.Seek(current.seconds + newEvent.seconds)
        } else {
            newEvent
        }
        visible = true
        showCount++
    }

    fun hide() {
        visible = false
    }

    companion object {
        /** How long the OSD stays after the last action. */
        const val VISIBLE_MS = 2_000L
    }
}
