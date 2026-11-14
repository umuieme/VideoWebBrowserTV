package com.umuieme.videowebbrowser.input

/**
 * What the remote's keys currently do. Only [Pointer] is implemented; the others are the
 * extension points later milestones fill in.
 */
enum class InputMode {
    /** Arrows move an on-screen pointer, OK clicks at it. Milestone 3. */
    Pointer,

    /** Arrows scroll the element under the pointer (entered with a long OK press). Milestone 4. */
    Scroll,

    /** A video is full screen: OK = play/pause, Left/Right = seek. Milestone 5. */
    FullscreenVideo,

    /** Keys go to the page unchanged, for sites with their own TV navigation. Milestone 7. */
    DPad,
}

enum class Direction { Up, Down, Left, Right }

/** Output of the controller; the UI layer turns these into pointer movement and injected input. */
sealed interface InputCommand {
    /** The set of held arrow directions changed. Empty = stop moving. */
    data class DirectionsChanged(val held: Set<Direction>) : InputCommand

    /** Short OK press finished (emitted on key UP). */
    data object Click : InputCommand

    /** OK has been held for [InputTuning.LONG_PRESS_MS]. Emitted once, while still held. */
    data object LongPressOk : InputCommand
}

/** Whether the key event was consumed (so it must not reach the WebView) plus resulting commands. */
data class KeyResult(val consumed: Boolean, val commands: List<InputCommand> = emptyList()) {
    companion object {
        val NotConsumed = KeyResult(consumed = false)
        val ConsumedNoOp = KeyResult(consumed = true)
    }
}

/**
 * Pure-Kotlin state machine that turns timestamped key downs/ups into [InputCommand]s.
 * No Android or Compose types and no timers of its own, so it is fully unit-testable with fake
 * timestamps. Time-based behaviour (long press) is driven by the caller: it schedules a wake-up
 * at [nextDeadlineMs] and then calls [onTick].
 *
 * Timestamps must share one clock; the app passes KeyEvent.eventTime / SystemClock.uptimeMillis.
 */
class RemoteInputController(
    initialMode: InputMode = InputMode.Pointer,
    private val longPressMs: Long = InputTuning.LONG_PRESS_MS,
) {
    var mode: InputMode = initialMode
        private set

    private val held = LinkedHashSet<Direction>()

    /** When OK went down, or null if OK isn't held (or its press started before we saw it). */
    private var okDownAtMs: Long? = null
    private var longPressFired = false

    /** When the caller should call [onTick] next, or null if nothing is pending. */
    val nextDeadlineMs: Long?
        get() = okDownAtMs?.takeIf { !longPressFired }?.plus(longPressMs)

    /**
     * @param isRepeat true for the auto-repeat DOWNs Android sends while a key is held
     *   (KeyEvent.repeatCount > 0).
     */
    fun onKeyDown(key: RemoteKey, timeMs: Long, isRepeat: Boolean = false): KeyResult = when (mode) {
        InputMode.Pointer -> pointerKeyDown(key, timeMs, isRepeat)
        // Extension points: until a mode is implemented, keys pass through to the page.
        InputMode.Scroll -> KeyResult.NotConsumed // Milestone 4
        InputMode.FullscreenVideo -> KeyResult.NotConsumed // Milestone 5
        InputMode.DPad -> KeyResult.NotConsumed // Milestone 7
    }

    fun onKeyUp(key: RemoteKey, timeMs: Long): KeyResult = when (mode) {
        InputMode.Pointer -> pointerKeyUp(key, timeMs)
        InputMode.Scroll -> KeyResult.NotConsumed // Milestone 4
        InputMode.FullscreenVideo -> KeyResult.NotConsumed // Milestone 5
        InputMode.DPad -> KeyResult.NotConsumed // Milestone 7
    }

    /** Fires time-based commands that are due at [nowMs] (currently only the OK long press). */
    fun onTick(nowMs: Long): List<InputCommand> {
        val deadline = nextDeadlineMs ?: return emptyList()
        if (nowMs < deadline) return emptyList()
        longPressFired = true
        return listOf(InputCommand.LongPressOk)
    }

    /**
     * Forgets all held keys, e.g. when the app pauses and the matching UPs may never arrive.
     * Returns the commands needed to bring the UI back to rest.
     */
    fun reset(): List<InputCommand> {
        val wasMoving = held.isNotEmpty()
        held.clear()
        okDownAtMs = null
        longPressFired = false
        return if (wasMoving) listOf(InputCommand.DirectionsChanged(emptySet())) else emptyList()
    }

    /** Switches mode, first releasing everything held in the old one. Used from Milestone 4. */
    fun switchMode(newMode: InputMode): List<InputCommand> {
        val commands = reset()
        mode = newMode
        return commands
    }

    // --- Pointer mode ---

    private fun pointerKeyDown(key: RemoteKey, timeMs: Long, isRepeat: Boolean): KeyResult {
        val direction = key.toDirection()
        return when {
            direction != null -> {
                // A repeat DOWN for a key we never saw go down started before this screen took
                // over (e.g. held on the setup screen); ignore it rather than start moving.
                if (isRepeat && direction !in held) return KeyResult.ConsumedNoOp
                if (held.add(direction)) changed() else KeyResult.ConsumedNoOp
            }
            key == RemoteKey.Ok -> {
                if (!isRepeat && okDownAtMs == null) {
                    okDownAtMs = timeMs
                    longPressFired = false
                }
                KeyResult.ConsumedNoOp
            }
            // Media keys aren't used by pointer mode; let the page (or player) have them.
            else -> KeyResult.NotConsumed
        }
    }

    private fun pointerKeyUp(key: RemoteKey, timeMs: Long): KeyResult {
        val direction = key.toDirection()
        return when {
            direction != null ->
                // An UP without a DOWN (pressed before we took over) is swallowed silently.
                if (held.remove(direction)) changed() else KeyResult.ConsumedNoOp
            key == RemoteKey.Ok -> {
                val downAt = okDownAtMs ?: return KeyResult.ConsumedNoOp
                val alreadyFired = longPressFired
                okDownAtMs = null
                longPressFired = false
                val command = when {
                    alreadyFired -> null
                    // The tick may run late; the UP's timestamp decides, so results are deterministic.
                    timeMs - downAt >= longPressMs -> InputCommand.LongPressOk
                    else -> InputCommand.Click
                }
                KeyResult(consumed = true, commands = listOfNotNull(command))
            }
            else -> KeyResult.NotConsumed
        }
    }

    private fun changed() =
        KeyResult(consumed = true, commands = listOf(InputCommand.DirectionsChanged(held.toSet())))

    private fun RemoteKey.toDirection(): Direction? = when (this) {
        RemoteKey.Up -> Direction.Up
        RemoteKey.Down -> Direction.Down
        RemoteKey.Left -> Direction.Left
        RemoteKey.Right -> Direction.Right
        else -> null
    }
}
