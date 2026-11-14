package com.umuieme.videowebbrowser.input

import com.umuieme.videowebbrowser.input.InputTuning.HOLD_DELAY_SEC
import com.umuieme.videowebbrowser.input.InputTuning.MAX_FRAME_DT_SEC
import com.umuieme.videowebbrowser.input.InputTuning.MAX_SPEED_DP_PER_SEC
import com.umuieme.videowebbrowser.input.InputTuning.RAMP_DURATION_SEC
import com.umuieme.videowebbrowser.input.InputTuning.START_SPEED_DP_PER_SEC
import com.umuieme.videowebbrowser.input.InputTuning.TAP_NUDGE_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PointerPhysicsTest {

    private val bounds = PointerBounds(960f, 540f)
    private val center = Vec2(480f, 270f)
    private val eps = 0.01f

    @Test
    fun `tap nudges by the nudge distance`() {
        val step = PointerPhysics.nudge(center, setOf(Direction.Right), bounds)
        assertEquals(Vec2(480f + TAP_NUDGE_DP, 270f), step.motion.position)
    }

    @Test
    fun `diagonal nudge is normalized to the same length`() {
        val p = PointerPhysics.nudge(center, setOf(Direction.Up, Direction.Left), bounds).motion.position
        assertEquals(TAP_NUDGE_DP, hypot(p.x - center.x, p.y - center.y), eps)
        assertTrue(p.x < center.x && p.y < center.y)
    }

    @Test
    fun `opposite directions cancel`() {
        assertEquals(Vec2.Zero, PointerPhysics.direction(setOf(Direction.Left, Direction.Right)))
    }

    @Test
    fun `no continuous movement during the hold delay`() {
        var motion = PointerMotion(center, 0f)
        repeat(3) { // 0.12 s in frames under the dt cap, still inside the 0.15 s hold delay
            motion = PointerPhysics.step(motion, setOf(Direction.Down), 0.04f, bounds).motion
        }
        assertEquals(center, motion.position)
        assertEquals(0.12f, motion.holdSec, eps)
    }

    @Test
    fun `speed ramps from start to max with smoothstep`() {
        assertEquals(0f, PointerPhysics.speedAt(HOLD_DELAY_SEC - 0.01f), eps)
        assertEquals(START_SPEED_DP_PER_SEC, PointerPhysics.speedAt(HOLD_DELAY_SEC), eps)
        // Smoothstep is exactly halfway at the middle of the ramp.
        val mid = (START_SPEED_DP_PER_SEC + MAX_SPEED_DP_PER_SEC) / 2f
        assertEquals(mid, PointerPhysics.speedAt(HOLD_DELAY_SEC + RAMP_DURATION_SEC / 2f), eps)
        assertEquals(MAX_SPEED_DP_PER_SEC, PointerPhysics.speedAt(HOLD_DELAY_SEC + RAMP_DURATION_SEC), eps)
        assertEquals(MAX_SPEED_DP_PER_SEC, PointerPhysics.speedAt(10f), eps)
    }

    @Test
    fun `movement is time based - same distance at 30 and 60 fps`() {
        fun travel(fps: Int): Float {
            var motion = PointerMotion(Vec2(0f, 270f), holdSec = 0f)
            val dt = 1f / fps
            repeat(fps) { // one second
                motion = PointerPhysics.step(motion, setOf(Direction.Right), dt, PointerBounds(10_000f, 540f)).motion
            }
            return motion.position.x
        }
        // ~700 dp travelled; frames straddling the end of the hold delay differ by a few dp at most.
        assertEquals(travel(60), travel(30), 5f)
    }

    @Test
    fun `at top speed a frame moves speed times dt, diagonals normalized`() {
        val start = PointerMotion(center, holdSec = 5f)
        val step = PointerPhysics.step(start, setOf(Direction.Down, Direction.Right), 0.01f, bounds)
        val p = step.motion.position
        assertEquals(MAX_SPEED_DP_PER_SEC * 0.01f, hypot(p.x - center.x, p.y - center.y), eps)
    }

    @Test
    fun `dt is capped so a long pause does not teleport the pointer`() {
        val start = PointerMotion(Vec2(0f, 270f), holdSec = 5f)
        val step = PointerPhysics.step(start, setOf(Direction.Right), 2f, bounds)
        assertEquals(MAX_SPEED_DP_PER_SEC * MAX_FRAME_DT_SEC, step.motion.position.x, eps)
    }

    @Test
    fun `position is clamped and pushed edges are reported`() {
        val start = PointerMotion(Vec2(955f, 2f), holdSec = 5f)
        val step = PointerPhysics.step(start, setOf(Direction.Right, Direction.Up), 0.05f, bounds)
        assertEquals(Vec2(960f, 0f), step.motion.position)
        assertEquals(setOf(Edge.Right, Edge.Top), step.edgesPushed)
    }

    @Test
    fun `no edge reported when moving away from it`() {
        val step = PointerPhysics.step(PointerMotion(Vec2(0f, 270f), 5f), setOf(Direction.Right), 0.01f, bounds)
        assertTrue(step.edgesPushed.isEmpty())
    }

    @Test
    fun `releasing all keys resets hold time`() {
        val step = PointerPhysics.step(PointerMotion(center, 3f), emptySet(), 0.016f, bounds)
        assertEquals(0f, step.motion.holdSec, 0f)
        assertEquals(center, step.motion.position)
    }

    @Test
    fun `bounds clamp and center`() {
        assertEquals(center, bounds.center)
        assertEquals(Vec2(0f, 540f), bounds.clamp(Vec2(-5f, 900f)))
    }
}
