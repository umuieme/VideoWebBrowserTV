package com.umuieme.videowebbrowser.browser

import androidx.activity.OnBackPressedCallback

/**
 * Back while a video is full screen. Enabled only while full screen is showing.
 *
 * How OnBackPressedDispatcher picks a callback: it keeps callbacks in the order they were added
 * and, on Back, calls only the most recently added one that is *enabled*. MainActivity adds the
 * Milestone 2 [TemporaryBackCallback] in onCreate; this one is added later (from Compose, when
 * the browser screen appears), so while it's enabled it wins and the browsing callback never
 * sees the press. Disabled (no full screen), the dispatcher skips it and browsing Back works as
 * before.
 *
 * Starts disabled; the owner toggles [isEnabled].
 */
class FullscreenBackCallback(private val onBack: () -> Unit) : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = onBack()
}
