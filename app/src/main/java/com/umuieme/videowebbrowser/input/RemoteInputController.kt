package com.umuieme.videowebbrowser.input

/**
 * What the remote's keys currently do.
 */
enum class InputMode {
    /** Arrows move an on-screen pointer, OK clicks at it. Milestone 3. */
    Pointer,

    /**
     * Arrows scroll the element under the pointer instead of moving it. Entered and left with a
     * long OK press; OK or Back also leave it. Milestone 6.
     */
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

    /**
     * The controller switched between [InputMode.Pointer] and [InputMode.Scroll] itself (long OK,
     * or OK in scroll mode). Full-screen switches are driven from outside and don't emit this.
     */
    data class ModeChanged(val mode: InputMode) : InputCommand

    /**
     * Scroll mode: the first arrow went down while none was held. The UI locates the scroll
     * target once here and keeps it until every arrow is released. Followed by a
     * [DirectionsChanged] with the same set.
     */
    data class ScrollGestureStarted(val held: Set<Direction>) : InputCommand

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
            if (mode == InputMode.DPad) return KeyResult.NotConsumed
            return if (isRepeat) KeyResult.ConsumedNoOp else KeyResult(true, listOf(InputCommand.Media(action)))
        }
        return when (mode) {
            InputMode.Pointer, InputMode.FullscreenPointer -> pointerKeyDown(key, timeMs, isRepeat)
            InputMode.FullscreenVideo -> videoKeyDown(key, timeMs, isRepeat)
            InputMode.Scroll -> scrollKeyDown(key, isRepeat)
            // Extension point: until D-pad mode is implemented, keys pass through to the page.
            InputMode.DPad -> KeyResult.NotConsumed // Milestone 7
        }
    }

    fun onKeyUp(key: RemoteKey, timeMs: Long): KeyResult {
        if (key.toMediaAction() != null) {
            return if (mode == InputMode.DPad) KeyResult.NotConsumed else KeyResult.ConsumedNoOp
        }
        return when (mode) {
            InputMode.Pointer, InputMode.FullscreenPointer -> pointerKeyUp(key, timeMs)
            InputMode.FullscreenVideo -> videoKeyUp(key)
            InputMode.Scroll -> scrollKeyUp(key)
            InputMode.DPad -> KeyResult.NotConsumed // Milestone 7
        }
    }

    /** Fires time-based commands that are due at [nowMs]: the OK long press and held-seek steps. */
    fun onTick(nowMs: Long): List<InputCommand> {
        val commands = ArrayList<InputCommand>(1)
        val longPressAt = okDownAtMs?.takeIf { !longPressFired }?.plus(longPressMs)
        if (longPressAt != null && nowMs >= longPressAt) {
            longPressFired = true
            // Long press only means something in pointer mode. Entering scroll mode while OK is
            // still held: its repeats and UP then arrive in scroll mode, which ignores them.
            if (mode == InputMode.Pointer) commands += enterScroll()
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
     * Back while the pointer is over a full-screen video: hide it and return to video control.
     * No-op in any other mode (exiting full screen itself is the caller's job).
     */
    fun fullscreenPointerBack(): List<InputCommand> =
        if (mode == InputMode.FullscreenPointer) {
            switchMode(InputMode.FullscreenVideo) + InputCommand.PointerVisibility(false)
        } else {
            emptyList()
        }

    /** Leaves scroll mode for the pointer (Back, or OK). No-op outside scroll mode. */
    fun exitScroll(): List<InputCommand> =
        if (mode == InputMode.Scroll) switchMode(InputMode.Pointer) + InputCommand.ModeChanged(InputMode.Pointer) else emptyList()

    private fun enterScroll(): List<InputCommand> =
        switchMode(InputMode.Scroll) + InputCommand.ModeChanged(InputMode.Scroll)

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
                val commands = when {
                    // Already handled while held (or a long press over full-screen video, which
                    // means nothing): just don't click.
                    alreadyFired -> emptyList()
                    // The tick may run late; the UP's timestamp decides, so results are deterministic.
                    isLong && mode == InputMode.Pointer -> enterScroll()
                    isLong -> emptyList()
                    else -> listOf(InputCommand.Click)
                }
                KeyResult(consumed = true, commands = commands)
            }
            else -> KeyResult.NotConsumed
        }
    }

    // --- Scroll mode ---

    private fun scrollKeyDown(key: RemoteKey, isRepeat: Boolean): KeyResult {
        val direction = key.toDirection()
        return when {
            direction != null -> {
                if (isRepeat && direction !in held) return KeyResult.ConsumedNoOp
                val startsGesture = held.isEmpty()
                if (!held.add(direction)) return KeyResult.ConsumedNoOp
                val snapshot = held.toSet()
                val commands = buildList {
                    if (startsGesture) add(InputCommand.ScrollGestureStarted(snapshot))
                    add(InputCommand.DirectionsChanged(snapshot))
                }
                KeyResult(consumed = true, commands = commands)
            }
            // Acts on DOWN so leaving feels instant; the UP then arrives in pointer mode as an
            // UP-without-DOWN and is ignored. Repeats belong to the OK that *entered* scroll mode
            // (still held after the long press) and must not leave it again.
            key == RemoteKey.Ok -> if (isRepeat) KeyResult.ConsumedNoOp else KeyResult(true, exitScroll())
            else -> KeyResult.NotConsumed
        }
    }

    private fun scrollKeyUp(key: RemoteKey): KeyResult {
        val direction = key.toDirection()
        return when {
            direction != null -> if (held.remove(direction)) changed() else KeyResult.ConsumedNoOp
            // The UP of the OK that entered scroll mode.
            key == RemoteKey.Ok -> KeyResult.ConsumedNoOp
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
