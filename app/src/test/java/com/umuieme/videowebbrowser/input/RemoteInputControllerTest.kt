package com.umuieme.videowebbrowser.input

import com.umuieme.videowebbrowser.input.InputCommand.Click
import com.umuieme.videowebbrowser.input.InputCommand.DirectionsChanged
import com.umuieme.videowebbrowser.input.InputCommand.LongPressOk
import com.umuieme.videowebbrowser.input.InputCommand.Media
import com.umuieme.videowebbrowser.input.InputCommand.PointerVisibility
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
    fun `media keys become media commands in pointer mode`() {
        assertEquals(listOf(Media(MediaAction.TogglePlay)), controller.onKeyDown(RemoteKey.PlayPause, 0).commands)
        assertEquals(KeyResult.ConsumedNoOp, controller.onKeyUp(RemoteKey.PlayPause, 50))
        assertEquals(listOf(Media(MediaAction.Play)), controller.onKeyDown(RemoteKey.Play, 100).commands)
        assertEquals(listOf(Media(MediaAction.Pause)), controller.onKeyDown(RemoteKey.Pause, 200).commands)
        assertEquals(listOf(Media(MediaAction.SeekBy(10))), controller.onKeyDown(RemoteKey.FastForward, 300).commands)
        assertEquals(listOf(Media(MediaAction.SeekBy(-10))), controller.onKeyDown(RemoteKey.Rewind, 400).commands)
    }

    @Test
    fun `media key auto-repeat does not toggle again`() {
        controller.onKeyDown(RemoteKey.PlayPause, 0)
        assertEquals(KeyResult.ConsumedNoOp, controller.onKeyDown(RemoteKey.PlayPause, 500, isRepeat = true))
    }

    @Test
    fun `media keys work in both full-screen modes`() {
        controller.enterFullscreen()
        assertEquals(listOf(Media(MediaAction.TogglePlay)), controller.onKeyDown(RemoteKey.PlayPause, 0).commands)
        controller.onKeyDown(RemoteKey.Up, 10)
        assertEquals(InputMode.FullscreenPointer, controller.mode)
        assertEquals(listOf(Media(MediaAction.Pause)), controller.onKeyDown(RemoteKey.Pause, 20).commands)
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
        for (mode in listOf(InputMode.Scroll, InputMode.DPad)) {
            val c = RemoteInputController(initialMode = mode)
            assertFalse(c.onKeyDown(RemoteKey.Up, 0).consumed)
            assertFalse(c.onKeyUp(RemoteKey.Ok, 0).consumed)
            assertFalse(c.onKeyDown(RemoteKey.PlayPause, 0).consumed)
        }
    }

    // --- Full-screen video ---

    @Test
    fun `entering full screen switches to video control and hides the pointer`() {
        controller.onKeyDown(RemoteKey.Left, 0) // held in pointer mode
        val commands = controller.enterFullscreen()
        assertEquals(listOf(DirectionsChanged(emptySet()), PointerVisibility(false)), commands)
        assertEquals(InputMode.FullscreenVideo, controller.mode)
    }

    @Test
    fun `OK toggles play on key down and its up is swallowed`() {
        controller.enterFullscreen()
        val down = controller.onKeyDown(RemoteKey.Ok, 0)
        assertEquals(listOf(Media(MediaAction.TogglePlay)), down.commands)
        assertTrue(controller.onKeyDown(RemoteKey.Ok, 400, isRepeat = true).commands.isEmpty())
        assertEquals(KeyResult.ConsumedNoOp, controller.onKeyUp(RemoteKey.Ok, 600))
        // No long press in video mode.
        assertNull(controller.nextDeadlineMs)
    }

    @Test
    fun `OK pressed in pointer mode and released in full screen does nothing`() {
        // The OK that clicked the site's full-screen button.
        controller.onKeyDown(RemoteKey.Ok, 0)
        controller.enterFullscreen()
        assertEquals(KeyResult.ConsumedNoOp, controller.onKeyUp(RemoteKey.Ok, 100))
    }

    @Test
    fun `left and right seek by 10 seconds`() {
        controller.enterFullscreen()
        assertEquals(listOf(Media(MediaAction.SeekBy(-10))), controller.onKeyDown(RemoteKey.Left, 0).commands)
        controller.onKeyUp(RemoteKey.Left, 100)
        assertNull(controller.nextDeadlineMs)
        assertEquals(listOf(Media(MediaAction.SeekBy(10))), controller.onKeyDown(RemoteKey.Right, 200).commands)
    }

    @Test
    fun `held right repeats every interval and steps up to 30 s after 2 s`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Right, 0)
        // Auto-repeat DOWNs never seek; only ticks do.
        assertTrue(controller.onKeyDown(RemoteKey.Right, 50, isRepeat = true).commands.isEmpty())

        assertEquals(InputTuning.SEEK_REPEAT_DELAY_MS, controller.nextDeadlineMs)
        assertTrue(controller.onTick(InputTuning.SEEK_REPEAT_DELAY_MS - 1).isEmpty())

        var t = InputTuning.SEEK_REPEAT_DELAY_MS
        val steps = mutableListOf<Int>()
        while (t <= 3_000) {
            controller.onTick(t).forEach { steps += ((it as Media).action as MediaAction.SeekBy).seconds }
            t = controller.nextDeadlineMs!!
        }
        // 500, 900, 1300, 1700 ms -> 10 s; 2100, 2500, 2900 ms -> 30 s.
        assertEquals(listOf(10, 10, 10, 10, 30, 30, 30), steps)

        controller.onKeyUp(RemoteKey.Right, t)
        assertNull(controller.nextDeadlineMs)
        assertTrue(controller.onTick(t + 1_000).isEmpty())
    }

    @Test
    fun `a late tick seeks once, not in a burst`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Left, 0)
        val commands = controller.onTick(1_500) // three intervals late
        assertEquals(listOf(Media(MediaAction.SeekBy(-10))), commands)
        assertEquals(1_500 + InputTuning.SEEK_REPEAT_INTERVAL_MS, controller.nextDeadlineMs)
    }

    @Test
    fun `releasing the other direction does not stop a held seek`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Right, 0)
        controller.onKeyUp(RemoteKey.Left, 10)
        assertEquals(InputTuning.SEEK_REPEAT_DELAY_MS, controller.nextDeadlineMs)
    }

    @Test
    fun `up or down switches to the pointer, whose stray key up is ignored`() {
        controller.enterFullscreen()
        val down = controller.onKeyDown(RemoteKey.Down, 0)
        assertEquals(listOf(PointerVisibility(true)), down.commands)
        assertEquals(InputMode.FullscreenPointer, controller.mode)
        assertEquals(KeyResult.ConsumedNoOp, controller.onKeyUp(RemoteKey.Down, 100))
    }

    @Test
    fun `pointer over the video moves and clicks like pointer mode`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Up, 0)
        controller.onKeyUp(RemoteKey.Up, 50)

        assertEquals(listOf(DirectionsChanged(setOf(Direction.Right))), controller.onKeyDown(RemoteKey.Right, 100).commands)
        controller.onKeyUp(RemoteKey.Right, 200)
        controller.onKeyDown(RemoteKey.Ok, 300)
        assertEquals(listOf(Click), controller.onKeyUp(RemoteKey.Ok, 400).commands)
    }

    @Test
    fun `long OK over the video neither clicks nor long-presses`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Up, 0)
        controller.onKeyDown(RemoteKey.Ok, 100)
        assertTrue(controller.onTick(600).isEmpty())
        assertTrue(controller.onKeyUp(RemoteKey.Ok, 900).commands.isEmpty())
    }

    @Test
    fun `back in pointer-over-video returns to video control`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Up, 0)
        controller.onKeyDown(RemoteKey.Left, 10) // moving when Back arrives

        val back = controller.onFullscreenBack()
        assertTrue(back.consumed)
        assertEquals(listOf(DirectionsChanged(emptySet()), PointerVisibility(false)), back.commands)
        assertEquals(InputMode.FullscreenVideo, controller.mode)
    }

    @Test
    fun `back in video control is left to the caller to exit full screen`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Right, 0)
        assertFalse(controller.onFullscreenBack().consumed)
        assertEquals(InputMode.FullscreenVideo, controller.mode)
    }

    @Test
    fun `exiting full screen returns to pointer mode and stops seeking`() {
        controller.enterFullscreen()
        controller.onKeyDown(RemoteKey.Right, 0)
        controller.exitFullscreen()
        assertEquals(InputMode.Pointer, controller.mode)
        assertNull(controller.nextDeadlineMs)
        // The Right UP that follows is an orphan in pointer mode.
        assertEquals(KeyResult.ConsumedNoOp, controller.onKeyUp(RemoteKey.Right, 100))
    }

    @Test
    fun `exit full screen outside full screen is a no-op`() {
        assertTrue(controller.exitFullscreen().isEmpty())
        assertEquals(InputMode.Pointer, controller.mode)
    }

    @Test
    fun `switching mode releases held keys first`() {
        controller.onKeyDown(RemoteKey.Left, 0)
        val commands = controller.switchMode(InputMode.Scroll)
        assertEquals(listOf(DirectionsChanged(emptySet())), commands)
        assertEquals(InputMode.Scroll, controller.mode)
    }
}
