package com.umuieme.videowebbrowser.browser.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRoutePolicyTest {

    @Test
    fun `known video goes to the agent, inline or full screen`() {
        assertEquals(MediaRoute.Agent, MediaRoutePolicy.choose(agentAvailable = true, hasActiveVideo = true, fullscreen = false))
        assertEquals(MediaRoute.Agent, MediaRoutePolicy.choose(agentAvailable = true, hasActiveVideo = true, fullscreen = true))
    }

    @Test
    fun `full screen without a known video uses native keys`() {
        assertEquals(MediaRoute.NativeKeys, MediaRoutePolicy.choose(agentAvailable = true, hasActiveVideo = false, fullscreen = true))
    }

    @Test
    fun `unsupported agent always uses native keys`() {
        assertEquals(MediaRoute.NativeKeys, MediaRoutePolicy.choose(agentAvailable = false, hasActiveVideo = false, fullscreen = false))
        assertEquals(MediaRoute.NativeKeys, MediaRoutePolicy.choose(agentAvailable = false, hasActiveVideo = false, fullscreen = true))
    }

    @Test
    fun `inline page without video passes the key through instead of scrolling with space`() {
        assertEquals(MediaRoute.PassThrough, MediaRoutePolicy.choose(agentAvailable = true, hasActiveVideo = false, fullscreen = false))
    }
}
