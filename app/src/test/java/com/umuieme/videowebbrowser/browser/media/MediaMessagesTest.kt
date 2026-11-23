package com.umuieme.videowebbrowser.browser.media

import com.umuieme.videowebbrowser.input.MediaAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMessagesTest {

    private val valid =
        """{"v":1,"type":"state","playing":true,"currentTime":12.5,"duration":600,"inFullscreen":false,"area":2073600,"ev":"play"}"""

    @Test
    fun `parses a valid state`() {
        assertEquals(
            AgentMessage.State(
                playing = true,
                currentTimeSec = 12.5,
                durationSec = 600.0,
                inFullscreen = false,
                visibleArea = 2_073_600.0,
                event = AgentEvent.Play,
            ),
            MediaMessages.parse(valid),
        )
    }

    @Test
    fun `null duration means live or unknown`() {
        val parsed = MediaMessages.parse(valid.replace("\"duration\":600", "\"duration\":null"))
        assertNull((parsed as AgentMessage.State).durationSec)
    }

    @Test
    fun `parses gone`() {
        assertEquals(AgentMessage.Gone, MediaMessages.parse("""{"v":1,"type":"gone"}"""))
    }

    @Test
    fun `rejects wrong or missing version`() {
        assertNull(MediaMessages.parse(valid.replace("\"v\":1", "\"v\":2")))
        assertNull(MediaMessages.parse(valid.replace("\"v\":1", "\"v\":\"1\"")))
        assertNull(MediaMessages.parse(valid.replace("\"v\":1", "\"v\":1.0")))
        assertNull(MediaMessages.parse(valid.replace("\"v\":1,", "")))
    }

    @Test
    fun `rejects unknown types and events`() {
        assertNull(MediaMessages.parse(valid.replace("\"type\":\"state\"", "\"type\":\"eval\"")))
        assertNull(MediaMessages.parse(valid.replace("\"ev\":\"play\"", "\"ev\":\"navigate\"")))
    }

    @Test
    fun `rejects wrong field types`() {
        assertNull(MediaMessages.parse(valid.replace("\"playing\":true", "\"playing\":\"true\"")))
        assertNull(MediaMessages.parse(valid.replace("\"playing\":true", "\"playing\":1")))
        assertNull(MediaMessages.parse(valid.replace("\"currentTime\":12.5", "\"currentTime\":\"12.5\"")))
        assertNull(MediaMessages.parse(valid.replace("\"inFullscreen\":false,", "")))
    }

    @Test
    fun `rejects out of range numbers`() {
        assertNull(MediaMessages.parse(valid.replace("\"currentTime\":12.5", "\"currentTime\":-1")))
        assertNull(MediaMessages.parse(valid.replace("\"currentTime\":12.5", "\"currentTime\":1e300")))
        assertNull(MediaMessages.parse(valid.replace("\"area\":2073600", "\"area\":1e12")))
    }

    @Test
    fun `rejects garbage and oversized input`() {
        assertNull(MediaMessages.parse(""))
        assertNull(MediaMessages.parse("not json"))
        assertNull(MediaMessages.parse("[1,2,3]"))
        assertNull(MediaMessages.parse(valid.dropLast(1) + ",\"pad\":\"" + "x".repeat(2_000) + "\"}"))
    }

    @Test
    fun `encodes the fixed command set`() {
        fun decode(s: String) = JSONObject(s)

        val toggle = decode(MediaMessages.encodeCommand(MediaAction.TogglePlay))
        assertEquals(1, toggle.getInt("v"))
        assertEquals("cmd", toggle.getString("type"))
        assertEquals("togglePlay", toggle.getString("cmd"))

        assertEquals("play", decode(MediaMessages.encodeCommand(MediaAction.Play)).getString("cmd"))
        assertEquals("pause", decode(MediaMessages.encodeCommand(MediaAction.Pause)).getString("cmd"))
        assertEquals("getState", decode(MediaMessages.encodeGetState()).getString("cmd"))

        val seek = decode(MediaMessages.encodeCommand(MediaAction.SeekBy(-30)))
        assertEquals("seekBy", seek.getString("cmd"))
        assertEquals(-30, seek.getInt("seconds"))
    }

    @Test
    fun `seek commands are clamped`() {
        val seek = JSONObject(MediaMessages.encodeCommand(MediaAction.SeekBy(100_000)))
        assertEquals(MediaMessages.MAX_SEEK_SEC, seek.getInt("seconds"))
    }
}
