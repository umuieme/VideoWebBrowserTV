package com.umuieme.videowebbrowser.ui.pointer

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umuieme.videowebbrowser.input.Direction
import com.umuieme.videowebbrowser.input.PointerBounds
import com.umuieme.videowebbrowser.input.Vec2

/**
 * UI-level state of the on-screen pointer, remembered by BrowserScreen per WebView.
 *
 * Why not in the ViewModel's StateFlow: [position] changes every frame while moving. Through a
 * StateFlow, every collector would recompose 60 times a second, and transient view state would
 * leak into app state that is meant to survive configuration changes. Here it is plain Compose
 * snapshot state that only the overlay's draw lambda reads, so a frame of movement costs one
 * redraw of one layer and no recomposition at all.
 */
@Stable
class PointerState {

    /** Pointer tip in dp, relative to the WebView's top-left corner. Read it only in draw. */
    var position by mutableStateOf(Vec2.Zero)

    /** Seconds of continuous holding; drives acceleration. Not observed by Compose. */
    var holdSec = 0f

    /** The WebView's size in dp; null until the first layout. Not observed by Compose. */
    var bounds: PointerBounds? = null
        private set

    /** Arrow directions currently held. The frame loop runs while this is non-empty. */
    var held by mutableStateOf(emptySet<Direction>())

    /** Logical visibility. [alpha] animates towards it. */
    var visible by mutableStateOf(true)

    /**
     * True in scroll mode: the overlay draws the scroll ring and the pointer never auto-hides
     * (the user must always see that arrows scroll instead of move).
     */
    var scrollMode by mutableStateOf(false)

    /** Bumped on every pointer activity; restarts the idle auto-hide timer. */
    var activityCount by mutableIntStateOf(0)
        private set

    /** Overlay opacity, read in a graphicsLayer so fading doesn't recompose either. */
    val alpha = Animatable(1f)

    /** Click ring progress 0 → 1; at 1 (rest) nothing is drawn. */
    val clickPulse = Animatable(1f)

    /** First call centres the pointer; later calls (view resized) keep it but clamp it inside. */
    fun updateBounds(widthDp: Float, heightDp: Float) {
        val newBounds = PointerBounds(widthDp, heightDp)
        val first = bounds == null
        bounds = newBounds
        position = if (first) newBounds.center else newBounds.clamp(position)
    }

    fun markActivity() {
        activityCount++
    }
}
