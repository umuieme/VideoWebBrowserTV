package com.umuieme.videowebbrowser.input

import kotlin.math.sqrt

/** A 2D point or vector in dp. Own tiny type so the physics has no Compose/Android dependency. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

/** The area the pointer may occupy, in dp, from (0, 0) to (width, height). */
data class PointerBounds(val width: Float, val height: Float) {
    val center get() = Vec2(width / 2f, height / 2f)
    fun clamp(p: Vec2) = Vec2(p.x.coerceIn(0f, width), p.y.coerceIn(0f, height))
}

enum class Edge { Left, Top, Right, Bottom }

/** Pointer position plus how long continuous movement has been held (drives acceleration). */
data class PointerMotion(val position: Vec2, val holdSec: Float = 0f)

/**
 * @param edgesPushed edges the pointer is sitting on while the held direction points into them.
 */
data class PointerStep(val motion: PointerMotion, val edgesPushed: Set<Edge> = emptySet())

/**
 * Time-based pointer movement. Everything takes the real elapsed time (dt, in seconds), so
 * speed is the same at 30, 60 or 120 fps and after a dropped frame. All constants come from
 * [InputTuning].
 *
 * Speed profile while an arrow is held:
 * ```
 *   0 ............ HOLD_DELAY ........................ HOLD_DELAY + RAMP ...
 *   (nudge only)   START_SPEED --smoothstep--> MAX_SPEED -----------------
 * ```
 */
object PointerPhysics {

    /** Unit vector for the held directions; diagonals are normalized, opposites cancel. */
    fun direction(held: Set<Direction>): Vec2 {
        var x = 0f
        var y = 0f
        if (Direction.Left in held) x -= 1f
        if (Direction.Right in held) x += 1f
        if (Direction.Up in held) y -= 1f // Screen y grows downwards.
        if (Direction.Down in held) y += 1f
        val length = sqrt(x * x + y * y)
        return if (length == 0f) Vec2.Zero else Vec2(x / length, y / length)
    }

    /** Speed in dp/s after [holdSec] seconds of holding. Zero during the hold delay. */
    fun speedAt(holdSec: Float): Float {
        if (holdSec < InputTuning.HOLD_DELAY_SEC) return 0f
        val t = ((holdSec - InputTuning.HOLD_DELAY_SEC) / InputTuning.RAMP_DURATION_SEC).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t) // smoothstep: gentle start, gentle arrival at top speed
        return InputTuning.START_SPEED_DP_PER_SEC +
            (InputTuning.MAX_SPEED_DP_PER_SEC - InputTuning.START_SPEED_DP_PER_SEC) * eased
    }

    /** The immediate [InputTuning.TAP_NUDGE_DP] move when an arrow is first pressed. */
    fun nudge(position: Vec2, held: Set<Direction>, bounds: PointerBounds): PointerStep {
        val dir = direction(held)
        val clamped = bounds.clamp(position + dir * InputTuning.TAP_NUDGE_DP)
        return PointerStep(PointerMotion(clamped, holdSec = 0f), edgesPushed(clamped, dir, bounds))
    }

    /** Advances [motion] by one frame of [dtSec] seconds while [held] directions are pressed. */
    fun step(motion: PointerMotion, held: Set<Direction>, dtSec: Float, bounds: PointerBounds): PointerStep {
        if (held.isEmpty()) return PointerStep(PointerMotion(bounds.clamp(motion.position), holdSec = 0f))
        val dt = dtSec.coerceIn(0f, InputTuning.MAX_FRAME_DT_SEC)
        val newHold = motion.holdSec + dt
        // Trapezoid rule: average of the speeds at the start and end of the frame. Accurate even
        // in the frame where the hold delay ends or the speed is still ramping.
        val distance = (speedAt(motion.holdSec) + speedAt(newHold)) / 2f * dt
        val dir = direction(held)
        val clamped = bounds.clamp(motion.position + dir * distance)
        return PointerStep(PointerMotion(clamped, newHold), edgesPushed(clamped, dir, bounds))
    }

    private fun edgesPushed(p: Vec2, dir: Vec2, bounds: PointerBounds): Set<Edge> {
        if (dir == Vec2.Zero) return emptySet()
        val edges = mutableSetOf<Edge>()
        if (dir.x < 0f && p.x <= 0f) edges += Edge.Left
        if (dir.x > 0f && p.x >= bounds.width) edges += Edge.Right
        if (dir.y < 0f && p.y <= 0f) edges += Edge.Top
        if (dir.y > 0f && p.y >= bounds.height) edges += Edge.Bottom
        return edges
    }
}
