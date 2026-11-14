package com.umuieme.videowebbrowser.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapTest {

    @Test
    fun `arrows map to directions`() {
        assertEquals(RemoteKey.Up, KeyMap.fromKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(RemoteKey.Down, KeyMap.fromKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(RemoteKey.Left, KeyMap.fromKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(RemoteKey.Right, KeyMap.fromKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun `center, enter and numpad enter all map to Ok`() {
        assertEquals(RemoteKey.Ok, KeyMap.fromKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(RemoteKey.Ok, KeyMap.fromKeyCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(RemoteKey.Ok, KeyMap.fromKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    @Test
    fun `media keys map to media remote keys`() {
        assertEquals(RemoteKey.PlayPause, KeyMap.fromKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(RemoteKey.PlayPause, KeyMap.fromKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(RemoteKey.PlayPause, KeyMap.fromKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(RemoteKey.FastForward, KeyMap.fromKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(RemoteKey.Rewind, KeyMap.fromKeyCode(KeyEvent.KEYCODE_MEDIA_REWIND))
    }

    @Test
    fun `back and unrelated keys are not mapped`() {
        assertNull(KeyMap.fromKeyCode(KeyEvent.KEYCODE_BACK))
        assertNull(KeyMap.fromKeyCode(KeyEvent.KEYCODE_HOME))
        assertNull(KeyMap.fromKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(KeyMap.fromKeyCode(KeyEvent.KEYCODE_A))
    }
}
