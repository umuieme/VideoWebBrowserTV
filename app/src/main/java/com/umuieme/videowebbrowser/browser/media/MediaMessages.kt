package com.umuieme.videowebbrowser.browser.media

import com.umuieme.videowebbrowser.input.MediaAction
import org.json.JSONException
import org.json.JSONObject

/** A validated message from the media agent (assets/js/media-agent.js) in one frame. */
sealed interface AgentMessage {
    /** The frame's best video and what it is doing. */
    data class State(
        val playing: Boolean,
        val currentTimeSec: Double,
        /** Null for live streams and while unknown. */
        val durationSec: Double?,
        val inFullscreen: Boolean,
        /** Visible area in CSS pixels (clipped to the viewport). */
        val visibleArea: Double,
        val event: AgentEvent,
    ) : AgentMessage

    /** The frame has no video any more, or its document is going away (pagehide). */
    data object Gone : AgentMessage
}

/** Why the agent sent a state. Only a fixed set is accepted. */
enum class AgentEvent(val wire: String, val isInteraction: Boolean) {
    Init("init", false),
    Time("time", false),
    Play("play", true),
    Pause("pause", true),
    Seeked("seeked", true),
    Ended("ended", false),
    Interact("interact", true),
    Fullscreen("fullscreen", true),
    ;

    companion object {
        fun fromWire(value: String): AgentEvent? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Wire format between Kotlin and the media agent. Every message is a small JSON object with
 * `v` (protocol version) and `type`.
 *
 * The agent runs in *every* frame, including ad and tracker iframes, and any script in those
 * frames can call the same `vwbMedia.postMessage`. So everything received is treated as
 * untrusted input: size-limited, strictly typed, numbers finite and in range. A message that
 * fails any check is dropped whole. Messages can only influence which video our keys control,
 * never what the app does.
 */
object MediaMessages {
    const val VERSION = 1

    /** A real state message is about 150 characters. Anything far bigger is not from our agent. */
    const val MAX_MESSAGE_CHARS = 1_024

    /** Upper bound for times: one week. Real values are far smaller; this only rejects garbage. */
    private const val MAX_TIME_SEC = 7 * 24 * 3600.0

    /** Upper bound for visible area: an 8K screen at 1 CSS px per pixel is about 33 M. */
    private const val MAX_AREA = 100_000_000.0

    /** Bound for seek commands so a bug can't send absurd values to a page. */
    const val MAX_SEEK_SEC = 600

    fun parse(raw: String): AgentMessage? {
        if (raw.length > MAX_MESSAGE_CHARS) return null
        val obj = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return null
        }
        // Exactly the integer 1: not "1", not 1.0, not missing.
        if ((obj.opt("v") as? Int) != VERSION) return null
        return when (obj.opt("type")) {
            "state" -> parseState(obj)
            "gone" -> AgentMessage.Gone
            else -> null
        }
    }

    private fun parseState(obj: JSONObject): AgentMessage.State? {
        val playing = obj.opt("playing") as? Boolean ?: return null
        val inFullscreen = obj.opt("inFullscreen") as? Boolean ?: return null
        val currentTime = obj.opt("currentTime").finiteIn(0.0, MAX_TIME_SEC) ?: return null
        val durationRaw = obj.opt("duration")
        val duration = if (durationRaw == null || durationRaw == JSONObject.NULL) {
            null
        } else {
            durationRaw.finiteIn(0.0, MAX_TIME_SEC) ?: return null
        }
        val area = obj.opt("area").finiteIn(0.0, MAX_AREA) ?: return null
        val event = (obj.opt("ev") as? String)?.let(AgentEvent::fromWire) ?: return null
        return AgentMessage.State(playing, currentTime, duration, inFullscreen, area, event)
    }

    /** JSON numbers come back as Integer, Long, Double or BigDecimal depending on the parser. */
    private fun Any?.finiteIn(min: Double, max: Double): Double? {
        val value = (this as? Number)?.toDouble() ?: return null
        return value.takeIf { it.isFinite() && it in min..max }
    }

    /** The only commands Kotlin ever sends. No strings from pages, no URLs, no code. */
    fun encodeCommand(action: MediaAction): String {
        val obj = JSONObject()
            .put("v", VERSION)
            .put("type", "cmd")
        when (action) {
            MediaAction.TogglePlay -> obj.put("cmd", "togglePlay")
            MediaAction.Play -> obj.put("cmd", "play")
            MediaAction.Pause -> obj.put("cmd", "pause")
            is MediaAction.SeekBy -> obj
                .put("cmd", "seekBy")
                .put("seconds", action.seconds.coerceIn(-MAX_SEEK_SEC, MAX_SEEK_SEC))
        }
        return obj.toString()
    }

    /** Asks a frame to report its state right away. */
    fun encodeGetState(): String =
        JSONObject().put("v", VERSION).put("type", "cmd").put("cmd", "getState").toString()
}
