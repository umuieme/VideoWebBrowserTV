package com.umuieme.videowebbrowser.input

import com.umuieme.videowebbrowser.input.InputCommand.Click
import com.umuieme.videowebbrowser.input.InputCommand.DirectionsChanged
import com.umuieme.videowebbrowser.input.InputCommand.LongPressOk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** All timestamps are fake milliseconds; the controller never reads a clock itself. */
class RemoteInputControllerTest {

    private val controller = RemoteInputController(longPressMs = 500)

    @Test
    fun `arrow down and up emit direction changes and are consumed`() {
        val down = controller.onKeyDown(RemoteKey.Up, 0)
        assertTrue(down.consumed)
        assertEquals(listOf(DirectionsChanged(setOf(Direction.Up))), down.commands)

        val up = controller.onKeyUp(RemoteKey.Up, 100)
        assertTrue(up.consumed)
        assertEquals(listOf(DirectionsChanged(emptySet())), up.commands)
    }

    @Test
    fun `two arrows held give a diagonal set`() {
        controller.onKeyDown(RemoteKey.Up, 0)
        val second = controller.onKeyDown(RemoteKey.Right, 10)
        assertEquals(listOf(DirectionsChanged(setOf(Direction.Up, Direction.Right))), second.commands)

        val releaseOne = controller.onKeyUp(RemoteKey.Up, 20)
        assertEquals(listOf(DirectionsChanged(setOf(Direction.Right))), releaseOne.commands)
    }

    @Test
    fun `auto-repeat downs are consumed without commands`() {
        controller.onKeyDown(RemoteKey.Left, 0)
        val repeat = controller.onKeyDown(RemoteKey.Left, 450, isRepeat = true)
        assertTrue(repeat.consumed)
        assertTrue(repeat.commands.isEmpty())
    }

    @Test
    fun `repeat down for a key pressed before we took over is ignored`() {
        val repeat = controller.onKeyDown(RemoteKey.Down, 0, isRepeat = true)
        assertTrue(repeat.consumed)
        assertTrue(repeat.commands.isEmpty())

        val okRepeat = controller.onKeyDown(RemoteKey.Ok, 0, isRepeat = true)
        assertTrue(okRepeat.commands.isEmpty())
        assertTrue(controller.onKeyUp(RemoteKey.Ok, 100).commands.isEmpty())
    }

    @Test
    fun `up without a matching down is consumed silently`() {
        val arrowUp = controller.onKeyUp(RemoteKey.Right, 0)
        assertTrue(arrowUp.consumed)
        assertTrue(arrowUp.commands.isEmpty())

        // E.g. OK pressed on the setup screen's "Open" button, released after Browsing appeared.
        val okUp = controller.onKeyUp(RemoteKey.Ok, 0)
        assertTrue(okUp.consumed)
        assertTrue(okUp.commands.isEmpty())
    }

    @Test
    fun `short OK clicks on key up, not on key down`() {
        val down = controller.onKeyDown(RemoteKey.Ok, 1_000)
        assertTrue(down.consumed)
        assertTrue(down.commands.isEmpty())

        val up = controller.onKeyUp(RemoteKey.Ok, 1_120)
        assertEquals(listOf(Click), up.commands)
    }

    @Test
    fun `OK held fires LongPressOk at the deadline and no click on release`() {
        controller.onKeyDown(RemoteKey.Ok, 1_000)
        assertEquals(1_500L, controller.nextDeadlineMs)

        assertTrue(controller.onTick(1_499).isEmpty())
        assertEquals(listOf(LongPressOk), controller.onTick(1_500))
        assertNull(controller.nextDeadlineMs)
        assertTrue(controller.onTick(1_600).isEmpty()) // fires only once

        val up = controller.onKeyUp(RemoteKey.Ok, 2_000)
        assertTrue(up.consumed)
        assertTrue(up.commands.isEmpty())
    }

    @Test
    fun `late tick - the key up timestamp decides long press`() {
        controller.onKeyDown(RemoteKey.Ok, 0)
        assertEquals(listOf(LongPressOk), controller.onKeyUp(RemoteKey.Ok, 500).commands)

        controller.onKeyDown(RemoteKey.Ok, 1_000)
        assertEquals(listOf(Click), controller.onKeyUp(RemoteKey.Ok, 1_499).commands)
    }

    @Test
    fun `OK auto-repeat does not restart the long press timer`() {
        controller.onKeyDown(RemoteKey.Ok, 0)
        controller.onKeyDown(RemoteKey.Ok, 400, isRepeat = true)
        assertEquals(500L, controller.nextDeadlineMs)
    }

    @Test
    fun `media keys pass through in pointer mode`() {
        assertFalse(controller.onKeyDown(RemoteKey.PlayPause, 0).consumed)
        assertFalse(controller.onKeyUp(RemoteKey.PlayPause, 50).consumed)
        assertFalse(controller.onKeyDown(RemoteKey.FastForward, 0).consumed)
        assertFalse(controller.onKeyDown(RemoteKey.Rewind, 0).consumed)
    }

    @Test
    fun `reset releases held arrows and pending OK`() {
        controller.onKeyDown(RemoteKey.Up, 0)
        controller.onKeyDown(RemoteKey.Ok, 0)

        assertEquals(listOf(DirectionsChanged(emptySet())), controller.reset())
        assertNull(controller.nextDeadlineMs)
        // The UPs that arrive afterwards do nothing.
        assertTrue(controller.onKeyUp(RemoteKey.Ok, 100).commands.isEmpty())
        assertTrue(controller.onKeyUp(RemoteKey.Up, 100).commands.isEmpty())
        // Nothing held: reset has nothing to undo.
        assertTrue(controller.reset().isEmpty())
    }

    @Test
    fun `unimplemented modes pass keys through`() {
        for (mode in listOf(InputMode.Scroll, InputMode.FullscreenVideo, InputMode.DPad)) {
            val c = RemoteInputController(initialMode = mode)
            assertFalse(c.onKeyDown(RemoteKey.Up, 0).consumed)
            assertFalse(c.onKeyUp(RemoteKey.Ok, 0).consumed)
        }
    }

    @Test
    fun `switching mode releases held keys first`() {
        controller.onKeyDown(RemoteKey.Left, 0)
        val commands = controller.switchMode(InputMode.Scroll)
        assertEquals(listOf(DirectionsChanged(emptySet())), commands)
        assertEquals(InputMode.Scroll, controller.mode)
    }
}
