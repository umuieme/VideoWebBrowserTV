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

    // --- Keys (RemoteInputController, BackKeyTracker) ---

    /** OK held at least this long is a long press (toggles scroll mode) instead of a click. */
    const val LONG_PRESS_MS = 500L

    /**
     * Back held at least this long opens the app menu instead of going back. Longer than the OK
     * long press: an accidental menu is more disruptive than an accidental scroll mode.
     */
    const val BACK_LONG_PRESS_MS = 600L

    // --- Scrolling (ScrollPhysics). Distances are fractions of the viewport (the WebView's
    // height for vertical scrolling, its width for horizontal), so the feel is the same at any
    // page zoom and screen size. ---

    /** One arrow tap scrolls this fraction of the viewport. 1/3 keeps context on screen. */
    const val SCROLL_TAP_FRACTION = 1f / 3f

    /**
     * How long an arrow must be held before continuous scrolling starts. Longer than a typical
     * tap (~100–200 ms on a remote), so one tap is exactly one step.
     */
    const val SCROLL_HOLD_DELAY_SEC = 0.3f

    /** Continuous scroll speed when it starts, in viewports per second. */
    const val SCROLL_START_SPEED = 0.6f

    /** Top continuous scroll speed, in viewports per second. */
    const val SCROLL_MAX_SPEED = 2.5f

    /** Time to accelerate from start to top speed (smoothstep curve). */
    const val SCROLL_RAMP_DURATION_SEC = 1.0f

    /**
     * How long scroll mode waits for scroll-locator.js before falling back to the pointer
     * position. The script normally answers within a frame or two.
     */
    const val SCROLL_LOCATOR_TIMEOUT_MS = 150L

    // --- Back (PageBackCallback) ---

    /**
     * If Back finds the same site popup it tried to close within this time, the popup ignored
     * us (fake close button, Escape not handled): go back in history instead of trying again.
     */
    const val POPUP_STUCK_WINDOW_MS = 2_500L

    /** How long Back waits for close-modal.js (a busy page) before acting as if there is no popup. */
    const val CLOSE_MODAL_TIMEOUT_MS = 1_000L

    /** Second Back within this time exits the app. */
    const val EXIT_WINDOW_MS = 2_000L

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

    /** How long the "menu coming" hint stays after a long Back press. */
    const val MENU_HINT_VISIBLE_MS = 2_500L
}
