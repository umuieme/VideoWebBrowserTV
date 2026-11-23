package com.umuieme.videowebbrowser.input

/**
 * Every tunable number for remote input and the pointer, in one place. Distances are in dp so
 * the feel is the same at any screen density; times are in seconds for physics and
 * milliseconds for event timing (KeyEvent timestamps are milliseconds).
 */
object InputTuning {

    // --- Pointer movement (PointerPhysics) ---

    /** Distance the pointer moves the instant an arrow is pressed. A quick tap moves only this. */
    const val TAP_NUDGE_DP = 6f

    /** How long an arrow must be held before continuous movement starts. Keeps taps precise. */
    const val HOLD_DELAY_SEC = 0.15f

    /** Speed when continuous movement starts. */
    const val START_SPEED_DP_PER_SEC = 300f

    /** Top speed, reached [RAMP_DURATION_SEC] after continuous movement starts. */
    const val MAX_SPEED_DP_PER_SEC = 1500f

    /** Time to accelerate from start speed to top speed (smoothstep curve). */
    const val RAMP_DURATION_SEC = 0.8f

    /**
     * Upper limit for one frame's time step. After a dropped frame or GC pause the next frame
     * can arrive late; without the cap the pointer would jump by a whole pause's worth.
     */
    const val MAX_FRAME_DT_SEC = 0.05f

    // --- Keys (RemoteInputController) ---

    /** OK held at least this long is a long press instead of a click. */
    const val LONG_PRESS_MS = 500L

    // --- Full-screen video seeking (RemoteInputController) ---

    /** Seconds per Left/Right press, and per repeat step while held. Also FF/Rewind keys. */
    const val SEEK_STEP_SEC = 10

    /** Seconds per repeat step once Left/Right has been held for [SEEK_ACCELERATE_AFTER_MS]. */
    const val SEEK_STEP_FAST_SEC = 30

    /** Pause after the first seek before a held key starts repeating. Keeps single taps single. */
    const val SEEK_REPEAT_DELAY_MS = 500L

    /**
     * Time between repeat steps while held. Each step is a real seek in the player (buffer
     * flush and refetch), so much faster than this makes players stall instead of seek faster.
     */
    const val SEEK_REPEAT_INTERVAL_MS = 400L

    /** How long Left/Right must be held before steps grow to [SEEK_STEP_FAST_SEC]. */
    const val SEEK_ACCELERATE_AFTER_MS = 2_000L

    // --- Injection (InputInjector) ---

    /** Time between the synthetic touch DOWN and UP of a click. */
    const val CLICK_TAP_DURATION_MS = 50L

    /** Minimum time between hover moves: 33 ms is about 30 Hz, plenty for CSS :hover and players. */
    const val HOVER_MIN_INTERVAL_MS = 33L

    // --- Visibility (RemoteInputLayer) ---

    /** The pointer fades out after this long without pointer activity. */
    const val IDLE_HIDE_MS = 5_000L

    /** Duration of the fade-out. Showing again is instant. */
    const val FADE_OUT_MS = 400

    /** Duration of the ring pulse drawn on click. */
    const val CLICK_PULSE_MS = 300
}
