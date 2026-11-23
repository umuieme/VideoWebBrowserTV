package com.umuieme.videowebbrowser.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Timestamps are fake milliseconds. */
class BackKeyTrackerTest {

    private val tracker = BackKeyTracker(longPressMs = 600)

    @Test
    fun `quick press is a short press on key up`() {
        assertNull(tracker.onDown(1_000, isRepeat = false))
        assertEquals(1_600L, tracker.nextDeadlineMs)
        assertEquals(BackPress.Short, tracker.onUp(1_150))
        assertNull(tracker.nextDeadlineMs)
    }

    @Test
    fun `held press fires long once at the deadline and nothing on release`() {
        tracker.onDown(0, isRepeat = false)
        assertNull(tracker.onTick(599))
        assertEquals(BackPress.Long, tracker.onTick(600))
        assertNull(tracker.nextDeadlineMs)
        assertNull(tracker.onTick(700))
        assertNull(tracker.onDown(800, isRepeat = true))
        assertNull(tracker.onUp(1_500))
    }

    @Test
    fun `auto-repeat fires long if the timer is late`() {
        tracker.onDown(0, isRepeat = false)
        assertNull(tracker.onDown(400, isRepeat = true))
        assertEquals(BackPress.Long, tracker.onDown(650, isRepeat = true))
        assertNull(tracker.onUp(700))
    }

    @Test
    fun `late timer and no repeats - the key up timestamp decides`() {
        tracker.onDown(0, isRepeat = false)
        assertEquals(BackPress.Long, tracker.onUp(600))
        tracker.onDown(1_000, isRepeat = false)
        assertEquals(BackPress.Short, tracker.onUp(1_599))
    }

    @Test
    fun `repeats and up of a press we never saw start are ignored`() {
        assertNull(tracker.onDown(0, isRepeat = true))
        assertNull(tracker.onDown(5_000, isRepeat = true))
        assertNull(tracker.onUp(6_000))
    }

    @Test
    fun `reset forgets the press`() {
        tracker.onDown(0, isRepeat = false)
        tracker.reset()
        assertNull(tracker.nextDeadlineMs)
        assertNull(tracker.onTick(1_000))
        assertNull(tracker.onUp(1_000))
    }
}
