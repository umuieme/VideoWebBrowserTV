package com.umuieme.videowebbrowser.ui.video

import com.umuieme.videowebbrowser.input.MediaAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsdTest {

    @Test
    fun `media time format`() {
        assertEquals("0:00", MediaTimeFormat.format(0.0))
        assertEquals("0:05", MediaTimeFormat.format(5.9))
        assertEquals("12:34", MediaTimeFormat.format(754.0))
        assertEquals("1:02:03", MediaTimeFormat.format(3723.0))
        assertEquals("0:00", MediaTimeFormat.format(-3.0))
        assertEquals("0:00", MediaTimeFormat.format(Double.NaN))
    }

    @Test
    fun `toggle label predicts the result only when the state is known`() {
        assertEquals(OsdEvent.Paused, osdEventFor(MediaAction.TogglePlay, playingBefore = true))
        assertEquals(OsdEvent.Playing, osdEventFor(MediaAction.TogglePlay, playingBefore = false))
        assertEquals(OsdEvent.PlayPause, osdEventFor(MediaAction.TogglePlay, playingBefore = null))
        assertEquals(OsdEvent.Playing, osdEventFor(MediaAction.Play, playingBefore = null))
        assertEquals(OsdEvent.Paused, osdEventFor(MediaAction.Pause, playingBefore = null))
        assertEquals(OsdEvent.Seek(-10), osdEventFor(MediaAction.SeekBy(-10), playingBefore = null))
    }

    @Test
    fun `consecutive seeks in one direction add up while visible`() {
        val osd = OsdState()
        osd.show(OsdEvent.Seek(10))
        osd.show(OsdEvent.Seek(10))
        osd.show(OsdEvent.Seek(30))
        assertEquals(OsdEvent.Seek(50), osd.event)

        // Changing direction starts a new total.
        osd.show(OsdEvent.Seek(-10))
        assertEquals(OsdEvent.Seek(-10), osd.event)

        // After hiding, a new seek starts from scratch.
        osd.hide()
        assertFalse(osd.visible)
        osd.show(OsdEvent.Seek(-10))
        assertEquals(OsdEvent.Seek(-10), osd.event)
        assertTrue(osd.visible)
    }

    @Test
    fun `every show restarts the timer`() {
        val osd = OsdState()
        osd.show(OsdEvent.Paused)
        val first = osd.showCount
        osd.show(OsdEvent.Paused)
        assertEquals(first + 1, osd.showCount)
    }
}
