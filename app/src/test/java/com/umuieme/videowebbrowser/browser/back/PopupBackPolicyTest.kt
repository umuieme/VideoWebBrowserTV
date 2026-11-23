package com.umuieme.videowebbrowser.browser.back

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupBackPolicyTest {

    private val policy = PopupBackPolicy(stuckWindowMs = 2_500)

    @Test
    fun `parses every close-modal result`() {
        assertEquals(CloseModalResult.Clicked("1"), CloseModalResult.parse("""{"result":"clicked","key":"1"}"""))
        assertEquals(CloseModalResult.Closed("2"), CloseModalResult.parse("""{"result":"closed","key":"2"}"""))
        assertEquals(CloseModalResult.Escape("3"), CloseModalResult.parse("""{"result":"esc","key":"3"}"""))
        assertEquals(CloseModalResult.Stuck, CloseModalResult.parse("""{"result":"stuck","key":"3"}"""))
        assertEquals(CloseModalResult.None, CloseModalResult.parse("""{"result":"none"}"""))
    }

    @Test
    fun `garbage and missing keys are failures`() {
        assertEquals(CloseModalResult.Failed, CloseModalResult.parse(null))
        assertEquals(CloseModalResult.Failed, CloseModalResult.parse("null"))
        assertEquals(CloseModalResult.Failed, CloseModalResult.parse("not json"))
        assertEquals(CloseModalResult.Failed, CloseModalResult.parse("""{"result":"esc"}"""))
        assertEquals(CloseModalResult.Failed, CloseModalResult.parse("""{"result":"other","key":"1"}"""))
    }

    @Test
    fun `closing a popup uses up the press`() {
        assertEquals(PageBackAction.Done, policy.decide(CloseModalResult.Clicked("1"), 0))
        assertEquals(PageBackAction.Done, policy.decide(CloseModalResult.Closed("2"), 5_000))
    }

    @Test
    fun `popup without close control gets Escape`() {
        assertEquals(PageBackAction.SendEscape, policy.decide(CloseModalResult.Escape("1"), 0))
    }

    @Test
    fun `no popup, stuck popup or no answer goes back`() {
        assertEquals(PageBackAction.GoBack, policy.decide(CloseModalResult.None, 0))
        assertEquals(PageBackAction.GoBack, policy.decide(CloseModalResult.Stuck, 0))
        assertEquals(PageBackAction.GoBack, policy.decide(CloseModalResult.Failed, 0))
    }

    @Test
    fun `the popup just tried is reported as stuck key only within the window`() {
        assertNull(policy.stuckKey(0))
        policy.decide(CloseModalResult.Escape("7"), 1_000)
        assertEquals("7", policy.stuckKey(1_000))
        assertEquals("7", policy.stuckKey(3_500))
        assertNull(policy.stuckKey(3_501))
    }

    @Test
    fun `back can never get stuck - second press within the window goes back, then forgets`() {
        // Press 1: popup ignores Escape. Press 2 (script sees stuckKey and answers "stuck"): back.
        assertEquals(PageBackAction.SendEscape, policy.decide(CloseModalResult.Escape("7"), 0))
        assertEquals("7", policy.stuckKey(1_000))
        assertEquals(PageBackAction.GoBack, policy.decide(CloseModalResult.Stuck, 1_000))
        assertNull(policy.stuckKey(1_100))
    }

    @Test
    fun `a different popup after a closed one is tried normally`() {
        policy.decide(CloseModalResult.Clicked("1"), 0)
        assertEquals("1", policy.stuckKey(500)) // passed to the script; it only matches popup 1
        assertEquals(PageBackAction.Done, policy.decide(CloseModalResult.Clicked("2"), 500))
        assertEquals("2", policy.stuckKey(600))
    }

    @Test
    fun `exit needs a second press within the window`() {
        val exit = ExitConfirmation(windowMs = 2_000)
        assertFalse(exit.onBack(0))
        assertTrue(exit.onBack(2_000))
        // After exiting (or a timeout) it asks again.
        assertFalse(exit.onBack(2_100))
        assertFalse(exit.onBack(4_200))
        assertTrue(exit.onBack(4_300))
    }
}
