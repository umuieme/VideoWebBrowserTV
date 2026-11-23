package com.umuieme.videowebbrowser.input

/** One axis after one frame: the new hold time and the signed distance to scroll, in px. */
data class AxisStep(val holdSec: Float, val distancePx: Float)

/**
 * Time-based scroll amounts for scroll mode and edge scrolling. Pure Kotlin, like
 * [PointerPhysics]: every function takes the real elapsed time, so the speed is the same at
 * any frame rate. The two axes are independent (each has its own hold time), so pressing Right
 * while Down is already held starts the horizontal scroll slowly instead of at full speed.
 *
 * Sign convention everywhere: positive = scroll down / right, like `window.scrollBy`.
 *
 * Speed profile while an arrow is held (in viewports per second):
 * ```
 *   tap step at t=0 ... HOLD_DELAY ............. HOLD_DELAY + RAMP ...
 *   (nothing more)      START_SPEED --smoothstep--> MAX_SPEED ---------
 * ```
 * All constants are in [InputTuning].
 */
object ScrollPhysics {

    /** -1, 0 or +1 for one axis; opposite keys held together cancel out. */
    fun sign(held: Set<Direction>, negative: Direction, positive: Direction): Int =
        (if (positive in held) 1 else 0) - (if (negative in held) 1 else 0)

    /** Distance of one arrow tap, in px, for a viewport [viewportPx] long on that axis. */
    fun tapStepPx(viewportPx: Float): Float = viewportPx * InputTuning.SCROLL_TAP_FRACTION

    /** Hold speed in viewports/s after [holdSec] of holding. Zero during the hold delay. */
    fun holdSpeed(holdSec: Float): Float {
        if (holdSec < InputTuning.SCROLL_HOLD_DELAY_SEC) return 0f
        return ramp(holdSec - InputTuning.SCROLL_HOLD_DELAY_SEC)
    }

    /**
     * Edge-scroll speed in viewports/s after the pointer has been pushed against an edge for
     * [pushSec]. No delay: reaching the edge already took a deliberate hold.
     */
    fun edgeSpeed(pushSec: Float): Float = ramp(pushSec)

    /**
     * Advances one axis by a frame of [dtSec]. [sign] 0 (axis not held) resets the hold time.
     * [speed] maps hold time to viewports/s ([holdSpeed] or [edgeSpeed]).
     */
    fun stepAxis(
        holdSec: Float,
        sign: Int,
        dtSec: Float,
        viewportPx: Float,
        speed: (Float) -> Float = ::holdSpeed,
    ): AxisStep {
        if (sign == 0) return AxisStep(0f, 0f)
        val dt = dtSec.coerceIn(0f, InputTuning.MAX_FRAME_DT_SEC)
        val newHold = holdSec + dt
        // Trapezoid rule, as in PointerPhysics: exact enough across the delay/ramp boundaries.
        val viewports = (speed(holdSec) + speed(newHold)) / 2f * dt
        return AxisStep(newHold, sign * viewports * viewportPx)
    }

    private fun ramp(sinceStartSec: Float): Float {
        val t = (sinceStartSec / InputTuning.SCROLL_RAMP_DURATION_SEC).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t) // smoothstep
        return InputTuning.SCROLL_START_SPEED +
            (InputTuning.SCROLL_MAX_SPEED - InputTuning.SCROLL_START_SPEED) * eased
    }
}
