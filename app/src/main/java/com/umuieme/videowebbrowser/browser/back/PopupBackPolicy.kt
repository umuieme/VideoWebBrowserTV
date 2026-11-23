package com.umuieme.videowebbrowser.browser.back

import com.umuieme.videowebbrowser.input.InputTuning
import org.json.JSONException
import org.json.JSONObject

/** What close-modal.js reported. See that script for the meaning of each result. */
sealed interface CloseModalResult {
    data class Clicked(val key: String) : CloseModalResult
    data class Closed(val key: String) : CloseModalResult
    data class Escape(val key: String) : CloseModalResult
    data object Stuck : CloseModalResult
    data object None : CloseModalResult

    /** No usable answer: script error, timeout, or no page. Treated like [None]. */
    data object Failed : CloseModalResult

    companion object {
        /** Parses evaluateJavascript's JSON, e.g. `{"result":"esc","key":"3"}`. */
        fun parse(json: String?): CloseModalResult {
            if (json.isNullOrBlank() || json == "null") return Failed
            return try {
                val obj = JSONObject(json)
                val key = obj.optString("key")
                when (obj.optString("result")) {
                    "clicked" -> if (key.isEmpty()) Failed else Clicked(key)
                    "closed" -> if (key.isEmpty()) Failed else Closed(key)
                    "esc" -> if (key.isEmpty()) Failed else Escape(key)
                    "stuck" -> Stuck
                    "none" -> None
                    else -> Failed
                }
            } catch (_: JSONException) {
                Failed
            }
        }
    }
}

/** What Back does after close-modal.js answered. */
enum class PageBackAction {
    /** A popup was closed; this Back press is used up. */
    Done,

    /** Send a native Escape key to the page (a popup without a close control). */
    SendEscape,

    /** No popup (or a stuck one): go back in history, or the double-press-to-exit logic. */
    GoBack,
}

/**
 * The anti-stuck rule for closing site popups. Pure Kotlin; times are passed in.
 *
 * Every close attempt (clicked, closed, Escape) remembers the popup's key. For
 * [stuckWindowMs] afterwards, [stuckKey] returns that key and is passed into close-modal.js.
 * If the script then finds the *same* popup still on top, it answers "stuck" without clicking
 * again, and Back goes back in history. So a fake close button or a popup that ignores Escape
 * costs one extra Back press, never an endless loop. The check runs in the script (not here)
 * because it must happen *before* the script clicks anything.
 */
class PopupBackPolicy(private val stuckWindowMs: Long = InputTuning.POPUP_STUCK_WINDOW_MS) {

    private var lastKey: String? = null
    private var lastAttemptAtMs = 0L

    /** The popup tried within the window, which close-modal.js must not try again. */
    fun stuckKey(nowMs: Long): String? = lastKey?.takeIf { nowMs - lastAttemptAtMs <= stuckWindowMs }

    fun decide(result: CloseModalResult, nowMs: Long): PageBackAction = when (result) {
        is CloseModalResult.Clicked -> attempted(result.key, nowMs, PageBackAction.Done)
        is CloseModalResult.Closed -> attempted(result.key, nowMs, PageBackAction.Done)
        is CloseModalResult.Escape -> attempted(result.key, nowMs, PageBackAction.SendEscape)
        CloseModalResult.Stuck, CloseModalResult.None, CloseModalResult.Failed -> {
            // Forget: the Back after this navigates, and a popup on the next page is new.
            lastKey = null
            PageBackAction.GoBack
        }
    }

    private fun attempted(key: String, nowMs: Long, action: PageBackAction): PageBackAction {
        lastKey = key
        lastAttemptAtMs = nowMs
        return action
    }
}

/**
 * "Press Back again to exit": the first press asks, a second within [windowMs] confirms.
 * Pure Kotlin; times are passed in.
 */
class ExitConfirmation(private val windowMs: Long = InputTuning.EXIT_WINDOW_MS) {

    private var askedAtMs: Long? = null

    /** Returns true to exit now, false to show the "press again" hint. */
    fun onBack(nowMs: Long): Boolean {
        val asked = askedAtMs
        if (asked != null && nowMs - asked <= windowMs) {
            askedAtMs = null
            return true
        }
        askedAtMs = nowMs
        return false
    }
}
