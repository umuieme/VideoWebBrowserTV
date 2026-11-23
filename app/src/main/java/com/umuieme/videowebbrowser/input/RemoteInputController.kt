package com.umuieme.videowebbrowser.input

/**
 * What the remote's keys currently do.
 */
enum class InputMode {
    /** Arrows move an on-screen pointer, OK clicks at it. Milestone 3. */
    Pointer,

    /** Arrows scroll the element under the pointer (entered with a long OK press). Milestone 5. */
    Scroll,

    /** A video is full screen: OK = play/pause, Left/Right = seek, Up/Down = [FullscreenPointer]. */
    FullscreenVideo,

    /**
     * Pointer over the full-screen video (for subtitles, quality and other player buttons).
     * Behaves like [Pointer]; Back returns to [FullscreenVideo].
     */
    FullscreenPointer,

    /** Keys go to the page unchanged, for sites with their own TV navigation. Milestone 7. */
    DPad,
}

enum class Direction { Up, Down, Left, Right }

/** What the remote asks the active video to do. Executed by the media layer (agent or fallback). */
sealed interface MediaAction {
    data object TogglePlay : MediaAction
    data object Play : MediaAction
    data object Pause : MediaAction

    /** Relative seek; negative = backwards. */
    data class SeekBy(val seconds: Int) : MediaAction
}

/** Output of the controller; the UI layer turns these into pointer movement, injected input and media commands. */
sealed interface InputCommand {
    /** The set of held arrow directions changed. Empty = stop moving. */
    data class DirectionsChanged(val held: Set<Direction>) : InputCommand

    /** Short OK press finished (emitted on key UP). */
    data object Click : InputCommand

    /** OK has been held for [InputTuning.LONG_PRESS_MS]. Emitted once, while still held. */
    data object LongPressOk : InputCommand

    /** Control the active video. */
    data class Media(val action: MediaAction) : InputCommand

    /** Show or hide the pointer overlay (switching between full-screen video and pointer). */
    data class PointerVisibility(val visible: Boolean) : InputCommand
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
 * timestamps. Time-based behaviour (OK long press, held-seek repeats) is driven by the caller:
 * it schedules a wake-up at [nextDeadlineMs] and then calls [onTick].
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

    /** Held seek in full-screen video: -1 = Left, +1 = Right, 0 = none. */
    private var seekSign = 0
    private var seekDownAtMs = 0L
    private var nextSeekAtMs: Long? = null

    /** When the caller should call [onTick] next, or null if nothing is pending. */
    val nextDeadlineMs: Long?
        get() {
            val longPress = okDownAtMs?.takeIf { !longPressFired }?.plus(longPressMs)
            val seek = nextSeekAtMs
            return when {
                longPress == null -> seek
                seek == null -> longPress
                else -> minOf(longPress, seek)
            }
        }

    /**
     * @param isRepeat true for the auto-repeat DOWNs Android sends while a key is held
     *   (KeyEvent.repeatCount > 0).
     */
    fun onKeyDown(key: RemoteKey, timeMs: Long, isRepeat: Boolean = false): KeyResult {
        // Media keys mean the same thing in every mode that handles keys at all.
        key.toMediaAction()?.let { action ->
            if (mode == InputMode.Scroll || mode == InputMode.DPad) return KeyResult.NotConsumed
            return if (isRepeat) KeyResult.ConsumedNoOp else KeyResult(true, listOf(InputCommand.Media(action)))
        }
        return when (mode) {
            InputMode.Pointer, InputMode.FullscreenPointer -> pointerKeyDown(key, timeMs, isRepeat)
            InputMode.FullscreenVideo -> videoKeyDown(key, timeMs, isRepeat)
            // Extension points: until a mode is implemented, keys pass through to the page.
            InputMode.Scroll -> KeyResult.NotConsumed // Milestone 5
            InputMode.DPad -> KeyResult.NotConsumed // Milestone 7
        }
    }

    fun onKeyUp(key: RemoteKey, timeMs: Long): KeyResult {
        if (key.toMediaAction() != null) {
            return if (mode == InputMode.Scroll || mode == InputMode.DPad) KeyResult.NotConsumed else KeyResult.ConsumedNoOp
        }
        return when (mode) {
            InputMode.Pointer, InputMode.FullscreenPointer -> pointerKeyUp(key, timeMs)
            InputMode.FullscreenVideo -> videoKeyUp(key)
            InputMode.Scroll -> KeyResult.NotConsumed // Milestone 5
            InputMode.DPad -> KeyResult.NotConsumed // Milestone 7
        }
    }

    /** Fires time-based commands that are due at [nowMs]: the OK long press and held-seek steps. */
    fun onTick(nowMs: Long): List<InputCommand> {
        val commands = ArrayList<InputCommand>(1)
        val longPressAt = okDownAtMs?.takeIf { !longPressFired }?.plus(longPressMs)
        if (longPressAt != null && nowMs >= longPressAt) {
            longPressFired = true
            // Long press only means something in pointer mode (scroll mode, Milestone 5).
            if (mode == InputMode.Pointer) commands += InputCommand.LongPressOk
        }
        val seekAt = nextSeekAtMs
        if (seekAt != null && nowMs >= seekAt && seekSign != 0) {
            val step = if (nowMs - seekDownAtMs >= InputTuning.SEEK_ACCELERATE_AFTER_MS) {
                InputTuning.SEEK_STEP_FAST_SEC
            } else {
                InputTuning.SEEK_STEP_SEC
            }
            commands += InputCommand.Media(MediaAction.SeekBy(seekSign * step))
            // Schedule from now, not from the missed deadline: a late tick gives one step, not a burst.
            nextSeekAtMs = nowMs + InputTuning.SEEK_REPEAT_INTERVAL_MS
        }
        return commands
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
        stopSeeking()
        return if (wasMoving) listOf(InputCommand.DirectionsChanged(emptySet())) else emptyList()
    }

    /** Switches mode, first releasing everything held in the old one. */
    fun switchMode(newMode: InputMode): List<InputCommand> {
        val commands = reset()
        mode = newMode
        return commands
    }

    /** A video went full screen: keys now control it and the pointer hides. */
    fun enterFullscreen(): List<InputCommand> =
        switchMode(InputMode.FullscreenVideo) + InputCommand.PointerVisibility(false)

    /** Full screen ended (either way): back to the normal pointer. No-op if not in full screen. */
    fun exitFullscreen(): List<InputCommand> =
        if (mode.isFullscreen) switchMode(InputMode.Pointer) else emptyList()

    /**
     * Back while full screen is showing. Consumed (with commands) when Back only steps from the
     * pointer back to video control; not consumed when the caller should exit full screen.
     */
    fun onFullscreenBack(): KeyResult = when (mode) {
        InputMode.FullscreenPointer ->
            KeyResult(true, switchMode(InputMode.FullscreenVideo) + InputCommand.PointerVisibility(false))
        else -> KeyResult.NotConsumed
    }

    // --- Pointer mode (also the pointer over a full-screen video) ---

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
                val isLong = alreadyFired || timeMs - downAt >= longPressMs
                val command = when {
                    // Long press has no meaning over a full-screen video; just don't click.
                    isLong && mode == InputMode.FullscreenPointer -> null
                    alreadyFired -> null
                    // The tick may run late; the UP's timestamp decides, so results are deterministic.
                    isLong -> InputCommand.LongPressOk
                    else -> InputCommand.Click
                }
                KeyResult(consumed = true, commands = listOfNotNull(command))
            }
            else -> KeyResult.NotConsumed
        }
    }

    // --- Full-screen video control ---

    private fun videoKeyDown(key: RemoteKey, timeMs: Long, isRepeat: Boolean): KeyResult {
        // Repeats never start anything: held-seek steps come from onTick, so they don't depend
        // on the remote's auto-repeat rate (or on it sending repeats at all).
        if (isRepeat) return KeyResult.ConsumedNoOp
        return when (key) {
            // Acts on DOWN so play/pause feels instant. No long press here, so no need to wait.
            RemoteKey.Ok -> media(MediaAction.TogglePlay)
            RemoteKey.Left, RemoteKey.Right -> {
                val sign = if (key == RemoteKey.Left) -1 else 1
                seekSign = sign
                seekDownAtMs = timeMs
                nextSeekAtMs = timeMs + InputTuning.SEEK_REPEAT_DELAY_MS
                media(MediaAction.SeekBy(sign * InputTuning.SEEK_STEP_SEC))
            }
            RemoteKey.Up, RemoteKey.Down -> {
                // Show the pointer at its last position. This press only switches modes; the
                // matching UP arrives in FullscreenPointer as an UP-without-DOWN and is ignored.
                val commands = switchMode(InputMode.FullscreenPointer) + InputCommand.PointerVisibility(true)
                KeyResult(true, commands)
            }
            else -> KeyResult.NotConsumed
        }
    }

    private fun videoKeyUp(key: RemoteKey): KeyResult {
        val sign = when (key) {
            RemoteKey.Left -> -1
            RemoteKey.Right -> 1
            // OK/Up/Down act on DOWN. Their UPs are swallowed, including the OK UP that follows the
            // click on the site's full-screen button (pressed in pointer mode, released here).
            RemoteKey.Ok, RemoteKey.Up, RemoteKey.Down -> return KeyResult.ConsumedNoOp
            else -> return KeyResult.NotConsumed
        }
        if (seekSign == sign) stopSeeking()
        return KeyResult.ConsumedNoOp
    }

    private fun stopSeeking() {
        seekSign = 0
        nextSeekAtMs = null
    }

    private fun media(action: MediaAction) = KeyResult(true, listOf(InputCommand.Media(action)))

    private fun changed() =
        KeyResult(consumed = true, commands = listOf(InputCommand.DirectionsChanged(held.toSet())))

    private fun RemoteKey.toDirection(): Direction? = when (this) {
        RemoteKey.Up -> Direction.Up
        RemoteKey.Down -> Direction.Down
        RemoteKey.Left -> Direction.Left
        RemoteKey.Right -> Direction.Right
        else -> null
    }

    private fun RemoteKey.toMediaAction(): MediaAction? = when (this) {
        RemoteKey.PlayPause -> MediaAction.TogglePlay
        RemoteKey.Play -> MediaAction.Play
        RemoteKey.Pause -> MediaAction.Pause
        RemoteKey.FastForward -> MediaAction.SeekBy(InputTuning.SEEK_STEP_SEC)
        RemoteKey.Rewind -> MediaAction.SeekBy(-InputTuning.SEEK_STEP_SEC)
        else -> null
    }
}

/** True for both full-screen modes (video control and pointer over the video). */
val InputMode.isFullscreen: Boolean
    get() = this == InputMode.FullscreenVideo || this == InputMode.FullscreenPointer
