package com.umuieme.videowebbrowser.browser.back

import androidx.activity.OnBackPressedCallback

/**
 * One step of the Back chain that applies only in a known state (scroll mode, pointer over a
 * full-screen video, full screen). Starts disabled; the owner keeps [isEnabled] equal to
 * "this step applies right now".
 *
 * How OnBackPressedDispatcher picks a callback: it keeps callbacks in the order they were added
 * and, on Back, calls only the **most recently added callback that is enabled**. There are no
 * priorities and a callback can't pass the press on, so the chain's order is the order of
 * `addCallback` calls, and a step must be disabled whenever it doesn't apply.
 *
 * The full chain, highest priority first:
 * 1. exit scroll mode                       - BackStepCallback, added last by RemoteInputLayer
 * 2. pointer over full-screen video → video - BackStepCallback
 * 3. exit full screen                       - BackStepCallback, added first by RemoteInputLayer
 * 4–6. close site popup / history / exit    - [PageBackCallback], added by MainActivity.onCreate
 *
 * Dialogs (LeaveSiteDialog) and the setup screen add their own BackHandlers later still, so
 * they sit on top while shown.
 */
class BackStepCallback(private val onBack: () -> Unit) : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = onBack()
}
