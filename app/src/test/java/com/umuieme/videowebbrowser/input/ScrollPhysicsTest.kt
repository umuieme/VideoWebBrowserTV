package com.umuieme.videowebbrowser.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollPhysicsTest {

    private val viewport = 1080f

    @Test
    fun `sign cancels opposite keys`() {
        assertEquals(1, ScrollPhysics.sign(setOf(Direction.Down), Direction.Up, Direction.Down))
        assertEquals(-1, ScrollPhysics.sign(setOf(Direction.Up, Direction.Left), Direction.Up, Direction.Down))
        assertEquals(0, ScrollPhysics.sign(setOf(Direction.Up, Direction.Down), Direction.Up, Direction.Down))
        assertEquals(0, ScrollPhysics.sign(setOf(Direction.Left), Direction.Up, Direction.Down))
    }

    @Test
    fun `a tap scrolls a fixed fraction of the viewport`() {
        assertEquals(viewport * InputTuning.SCROLL_TAP_FRACTION, ScrollPhysics.tapStepPx(viewport), 0.001f)
    }

    @Test
    fun `no continuous scrolling during the hold delay`() {
        assertEquals(0f, ScrollPhysics.holdSpeed(0f))
        assertEquals(0f, ScrollPhysics.holdSpeed(InputTuning.SCROLL_HOLD_DELAY_SEC - 0.01f))
        val step = ScrollPhysics.stepAxis(0f, 1, 0.016f, viewport)
        assertEquals(0f, step.distancePx)
        assertEquals(0.016f, step.holdSec, 1e-6f)
    }

    @Test
    fun `hold speed ramps smoothly from start to max`() {
        val start = InputTuning.SCROLL_HOLD_DELAY_SEC
        assertEquals(InputTuning.SCROLL_START_SPEED, ScrollPhysics.holdSpeed(start), 1e-4f)
        var previous = 0f
        var t = start
        while (t < start + InputTuning.SCROLL_RAMP_DURATION_SEC) {
            val speed = ScrollPhysics.holdSpeed(t)
            assertTrue("speed must never drop", speed >= previous)
            previous = speed
            t += 0.05f
        }
        assertEquals(InputTuning.SCROLL_MAX_SPEED, ScrollPhysics.holdSpeed(start + InputTuning.SCROLL_RAMP_DURATION_SEC + 5f), 1e-4f)
    }

    @Test
    fun `edge speed has no delay`() {
        assertEquals(InputTuning.SCROLL_START_SPEED, ScrollPhysics.edgeSpeed(0f), 1e-4f)
        val step = ScrollPhysics.stepAxis(0f, -1, 0.016f, viewport, ScrollPhysics::edgeSpeed)
        assertTrue("edge scroll moves up from the first frame", step.distancePx < 0f)
    }

    @Test
    fun `distance is frame-rate independent`() {
        val hold = InputTuning.SCROLL_HOLD_DELAY_SEC + InputTuning.SCROLL_RAMP_DURATION_SEC + 1f // at max speed
        fun scrolled(fps: Int): Float {
            var total = 0f
            repeat(fps) { total += ScrollPhysics.stepAxis(hold, 1, 1f / fps, viewport).distancePx }
            return total
        }
        val expected = InputTuning.SCROLL_MAX_SPEED * viewport
        assertEquals(expected, scrolled(30), expected * 0.001f)
        assertEquals(expected, scrolled(60), expected * 0.001f)
    }

    @Test
    fun `a long frame is capped`() {
        val hold = InputTuning.SCROLL_HOLD_DELAY_SEC + InputTuning.SCROLL_RAMP_DURATION_SEC + 1f
        val capped = ScrollPhysics.stepAxis(hold, 1, 2f, viewport)
        val expected = InputTuning.SCROLL_MAX_SPEED * viewport * InputTuning.MAX_FRAME_DT_SEC
        assertEquals(expected, capped.distancePx, 0.01f)
    }

    @Test
    fun `releasing an axis resets its hold time`() {
        assertEquals(AxisStep(0f, 0f), ScrollPhysics.stepAxis(3f, 0, 0.016f, viewport))
    }
}
