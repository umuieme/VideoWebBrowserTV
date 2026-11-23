package com.umuieme.videowebbrowser.browser.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveVideoSelectorTest {

    private fun video(
        playing: Boolean = false,
        inFullscreen: Boolean = false,
        area: Double = 100.0,
        interactedAt: Long = 0,
    ) = VideoState(
        playing = playing,
        currentTimeSec = 0.0,
        durationSec = null,
        inFullscreen = inFullscreen,
        visibleArea = area,
        lastInteractionMs = interactedAt,
    )

    @Test
    fun `no frames, no video`() {
        assertNull(ActiveVideoSelector.select(emptyMap<String, VideoState>()))
    }

    @Test
    fun `full screen beats playing, larger and more recent`() {
        val frames = mapOf(
            "playingBig" to video(playing = true, area = 1e6, interactedAt = 99),
            "fullscreenPaused" to video(inFullscreen = true, area = 10.0),
        )
        assertEquals("fullscreenPaused", ActiveVideoSelector.select(frames))
    }

    @Test
    fun `playing beats a larger paused video`() {
        val frames = mapOf(
            "pausedBig" to video(area = 1e6, interactedAt = 50),
            "playingSmall" to video(playing = true, area = 10.0),
        )
        assertEquals("playingSmall", ActiveVideoSelector.select(frames))
    }

    @Test
    fun `largest visible wins among paused videos`() {
        val frames = mapOf(
            "small" to video(area = 100.0, interactedAt = 50),
            "large" to video(area = 5_000.0),
        )
        assertEquals("large", ActiveVideoSelector.select(frames))
    }

    @Test
    fun `most recent interaction breaks a tie`() {
        val frames = mapOf(
            "older" to video(playing = true, area = 500.0, interactedAt = 10),
            "newer" to video(playing = true, area = 500.0, interactedAt = 20),
        )
        assertEquals("newer", ActiveVideoSelector.select(frames))
    }

    @Test
    fun `invisible paused videos are never picked`() {
        assertNull(ActiveVideoSelector.select(mapOf("hiddenAd" to video(area = 0.0, interactedAt = 5))))
    }

    @Test
    fun `invisible but playing or full screen videos are still candidates`() {
        assertEquals("audioLike", ActiveVideoSelector.select(mapOf("audioLike" to video(playing = true, area = 0.0))))
        assertEquals("fs", ActiveVideoSelector.select(mapOf("fs" to video(inFullscreen = true, area = 0.0))))
    }
}
