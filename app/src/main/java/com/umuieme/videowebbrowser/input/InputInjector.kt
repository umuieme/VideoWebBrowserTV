package com.umuieme.videowebbrowser.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Turns pointer actions into native MotionEvents delivered to a target View: the WebView, or
 * the full-screen video view while one is showing (RemoteInputLayer reassigns [target]).
 *
 * Why native MotionEvents and not JavaScript `element.dispatchEvent(new MouseEvent(...))`:
 * - **Trusted input.** Chromium treats these exactly like a finger or mouse: the DOM events
 *   have `isTrusted == true`. Script-created events are untrusted, and many players and
 *   anti-clickjacking checks ignore them.
 * - **User activation.** A real tap counts as a user gesture, which browsers require before
 *   `video.play()` with sound, `requestFullscreen()`, opening the on-screen keyboard for a text
 *   field, or opening popups. Synthetic JS events never grant that.
 * - **Cross-origin iframes.** Embedded players usually live in an iframe from another domain.
 *   Our injected JS can't reach into it (same-origin policy), but a native event is hit-tested
 *   by the browser engine and lands in whatever frame is under the coordinates.
 * - **No page cooperation needed.** It works even when a page's own scripts are broken or hostile.
 *
 * Coordinates are in the target's local pixels. Main thread only.
 */
class InputInjector(target: View? = null) {

    /** Changing the target finishes any half-sent click and restarts the hover sequence. */
    var target: View? = target
        set(value) {
            if (field === value) return
            flushPendingUp()
            hoverStarted = false
            field = value
        }

    private var hoverStarted = false
    private var lastHoverAtMs = 0L

    private var pendingUp: Runnable? = null
    private var pendingUpView: View? = null

    // MotionEvent.obtain copies these, so one instance each can be reused for every event.
    private val pointerProperties = arrayOf(MotionEvent.PointerProperties())
    private val pointerCoords = arrayOf(MotionEvent.PointerCoords())

    /**
     * Taps at ([x], [y]): touch DOWN now, UP [InputTuning.CLICK_TAP_DURATION_MS] later. A real
     * gap between the two looks like a finger tap to page scripts (some ignore 0 ms taps).
     */
    fun click(x: Float, y: Float) {
        val view = target ?: return
        flushPendingUp() // Never leave a DOWN without its UP.
        // Key focus must be on the WebView for the keyboard to open on a tapped text field and
        // for typed text to go to it.
        view.requestFocus()

        val downTime = SystemClock.uptimeMillis()
        dispatchTouch(view, MotionEvent.ACTION_DOWN, downTime, downTime, x, y)
        val up = Runnable {
            pendingUp = null
            pendingUpView = null
            dispatchTouch(view, MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), x, y)
        }
        pendingUp = up
        pendingUpView = view
        view.postDelayed(up, InputTuning.CLICK_TAP_DURATION_MS)
    }

    /**
     * Moves the mouse to ([x], [y]) so pages see `mousemove`/`:hover` (menus, player controls).
     * Throttled to [InputTuning.HOVER_MIN_INTERVAL_MS] unless [force] (used for the final
     * position when movement stops, so the page always learns where the pointer came to rest).
     * The first hover on a target is preceded by HOVER_ENTER, as a real mouse would send.
     */
    fun hover(x: Float, y: Float, nowMs: Long = SystemClock.uptimeMillis(), force: Boolean = false) {
        val view = target ?: return
        if (!hoverStarted) {
            dispatchHover(view, MotionEvent.ACTION_HOVER_ENTER, nowMs, x, y)
            hoverStarted = true
        } else if (!force && nowMs - lastHoverAtMs < InputTuning.HOVER_MIN_INTERVAL_MS) {
            return
        }
        lastHoverAtMs = nowMs
        dispatchHover(view, MotionEvent.ACTION_HOVER_MOVE, nowMs, x, y)
    }

    /**
     * Mouse-wheel scroll at ([x], [y]) by [vertical] / [horizontal] px (positive = down / right,
     * like `scrollBy`).
     *
     * Why native wheel events and not JavaScript `element.scrollBy()`:
     * - **Cross-origin iframes.** The browser engine hit-tests the event and scrolls whatever is
     *   under (x, y), including an embedded page from another domain our JS can't touch.
     * - **Natural scroll chaining.** An inner list at its end passes the rest of the scroll to its
     *   parent and finally the page, exactly like a real mouse; JS would have to reimplement that.
     * - **Trusted.** Pages see a real `wheel` event; lazy-loading lists and carousels that react to
     *   wheel/scroll input behave as with a mouse. Smooth-scroll animation comes from the engine.
     *
     * Units: the wheel axes count "notches". Chromium turns one notch into
     * [ViewConfiguration.getScaledVerticalScrollFactor] px (horizontal likewise), the same factor
     * Android's own scroll views use, so dividing by it makes the requested px land as px.
     */
    fun scroll(x: Float, y: Float, vertical: Float, horizontal: Float) {
        val view = target ?: return
        if (vertical == 0f && horizontal == 0f) return
        val config = ViewConfiguration.get(view.context)
        // AXIS_VSCROLL is positive for "wheel up" (content moves down), the opposite of our
        // convention, so it is negated. AXIS_HSCROLL is positive for "right" per the docs.
        val vNotches = -vertical / config.scaledVerticalScrollFactor
        val hNotches = HSCROLL_SIGN * horizontal / config.scaledHorizontalScrollFactor
        val now = SystemClock.uptimeMillis()
        val event = obtain(
            view, MotionEvent.ACTION_SCROLL, now, now, x, y,
            toolType = MotionEvent.TOOL_TYPE_MOUSE,
            source = InputDevice.SOURCE_MOUSE,
            pressure = 0f,
            vScroll = vNotches,
            hScroll = hNotches,
        )
        try {
            // Like hover, a scroll is a "generic" motion event, not a touch.
            view.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Drops the target without sending anything more. Call before the target is destroyed. */
    fun release() {
        pendingUpView?.removeCallbacks(pendingUp)
        pendingUp = null
        pendingUpView = null
        hoverStarted = false
        target = null
    }

    /** Sends a pending touch UP right away instead of waiting for its delay. */
    private fun flushPendingUp() {
        val up = pendingUp ?: return
        pendingUpView?.removeCallbacks(up)
        up.run()
    }

    private fun dispatchTouch(view: View, action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val event = obtain(
            view, action, downTime, eventTime, x, y,
            toolType = MotionEvent.TOOL_TYPE_FINGER,
            source = InputDevice.SOURCE_TOUCHSCREEN,
            pressure = 1f,
        )
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle() // MotionEvents are pooled; return it once dispatch has finished.
        }
    }

    private fun dispatchHover(view: View, action: Int, time: Long, x: Float, y: Float) {
        val event = obtain(
            view, action, time, time, x, y,
            toolType = MotionEvent.TOOL_TYPE_MOUSE,
            source = InputDevice.SOURCE_MOUSE,
            pressure = 0f,
        )
        try {
            // Hover is a "generic" motion event, not a touch: View routes pointer-source hover
            // actions from dispatchGenericMotionEvent to dispatchHoverEvent/onHoverEvent.
            view.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    @Suppress("LongParameterList")
    private fun obtain(
        view: View,
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        toolType: Int,
        source: Int,
        pressure: Float,
        vScroll: Float = 0f,
        hScroll: Float = 0f,
    ): MotionEvent {
        pointerProperties[0].apply {
            clear()
            id = 0
            this.toolType = toolType
        }
        pointerCoords[0].apply {
            clear()
            // Keep inside the view: a coordinate exactly at width/height is outside its bounds.
            this.x = x.coerceIn(0f, (view.width - 1).coerceAtLeast(0).toFloat())
            this.y = y.coerceIn(0f, (view.height - 1).coerceAtLeast(0).toFloat())
            this.pressure = pressure
            size = 1f
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
            setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            /* pointerCount = */ 1,
            pointerProperties,
            pointerCoords,
            /* metaState = */ 0,
            /* buttonState = */ 0,
            /* xPrecision = */ 1f,
            /* yPrecision = */ 1f,
            /* deviceId = */ 0,
            /* edgeFlags = */ 0,
            source,
            /* flags = */ 0,
        )
    }

    private companion object {
        /**
         * Multiplier for AXIS_HSCROLL. Android documents positive as "right"; if horizontal rows
         * scroll the wrong way on the TV, flip this to -1f.
         */
        const val HSCROLL_SIGN = 1f
    }
}
