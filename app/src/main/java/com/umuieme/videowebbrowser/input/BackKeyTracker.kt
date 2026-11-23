package com.umuieme.videowebbrowser.input

/** What a Back key press turned out to be. */
enum class BackPress { Short, Long }

/**
 * Tells a short Back press from a held one, from timestamped key DOWN/UP events. Pure Kotlin
 * with no timers, like [RemoteInputController]: the caller schedules a wake-up at
 * [nextDeadlineMs] and calls [onTick], so the long press fires while Back is still held (the
 * menu opens without waiting for the release).
 *
 * Why our own timer instead of counting auto-repeat DOWNs: remotes differ in when (and whether)
 * they repeat Back. Repeats are still checked, as a backup if the wake-up runs late.
 *
 * Every press yields exactly one result: [BackPress.Long] once (from [onTick], a repeat, or a
 * late UP), or [BackPress.Short] on the UP. Presses that started before we saw them yield nothing.
 */
class BackKeyTracker(private val longPressMs: Long = InputTuning.BACK_LONG_PRESS_MS) {

    private var downAtMs: Long? = null
    private var longFired = false

    /** When the caller should call [onTick], or null if Back isn't held (or already fired). */
    val nextDeadlineMs: Long?
        get() = downAtMs?.takeIf { !longFired }?.plus(longPressMs)

    fun onDown(timeMs: Long, isRepeat: Boolean): BackPress? {
        if (!isRepeat) {
            // A second DOWN without an UP (shouldn't happen) keeps the first press's start.
            if (downAtMs == null) {
                downAtMs = timeMs
                longFired = false
            }
            return null
        }
        // A repeat of a press we never saw start (e.g. held while the app launched): ignore.
        return checkLong(timeMs)
    }

    fun onTick(nowMs: Long): BackPress? = checkLong(nowMs)

    fun onUp(timeMs: Long): BackPress? {
        val downAt = downAtMs ?: return null
        val fired = longFired
        reset()
        return when {
            fired -> null
            // The tick may run late; the UP's timestamp decides, so results are deterministic.
            timeMs - downAt >= longPressMs -> BackPress.Long
            else -> BackPress.Short
        }
    }

    /** Forgets the current press, e.g. when the app pauses or the UP is cancelled. */
    fun reset() {
        downAtMs = null
        longFired = false
    }

    private fun checkLong(nowMs: Long): BackPress? {
        val deadline = nextDeadlineMs ?: return null
        if (nowMs < deadline) return null
        longFired = true
        return BackPress.Long
    }
}
