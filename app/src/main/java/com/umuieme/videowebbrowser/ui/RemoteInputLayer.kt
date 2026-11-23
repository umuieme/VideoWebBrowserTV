package com.umuieme.videowebbrowser.ui

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.umuieme.videowebbrowser.browser.FullscreenBackCallback
import com.umuieme.videowebbrowser.browser.FullscreenVideoState
import com.umuieme.videowebbrowser.browser.media.MediaController
import com.umuieme.videowebbrowser.browser.media.MediaRoute
import com.umuieme.videowebbrowser.browser.media.MediaRouter
import com.umuieme.videowebbrowser.browser.media.NativeMediaKeyFallback
import com.umuieme.videowebbrowser.input.Edge
import com.umuieme.videowebbrowser.input.InputCommand
import com.umuieme.videowebbrowser.input.InputInjector
import com.umuieme.videowebbrowser.input.InputTuning
import com.umuieme.videowebbrowser.input.KeyHandler
import com.umuieme.videowebbrowser.input.KeyMap
import com.umuieme.videowebbrowser.input.KeyRouter
import com.umuieme.videowebbrowser.input.MediaAction
import com.umuieme.videowebbrowser.input.PointerMotion
import com.umuieme.videowebbrowser.input.PointerPhysics
import com.umuieme.videowebbrowser.input.RemoteInputController
import com.umuieme.videowebbrowser.input.isFullscreen
import com.umuieme.videowebbrowser.ui.pointer.PointerOverlay
import com.umuieme.videowebbrowser.ui.pointer.PointerState
import com.umuieme.videowebbrowser.ui.video.OsdState
import com.umuieme.videowebbrowser.ui.video.osdEventFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "RemoteInputLayer"

/**
 * Remote control for the browser screen while the page is shown: takes keys from [keyRouter],
 * runs the input state machine, moves and draws the pointer, injects clicks/hover, sends media
 * commands, and handles Back while a video is full screen.
 *
 * The injection target is the full-screen video view while one is showing, otherwise
 * [webView]. Leaving composition unregisters the key handler and the Back callback, so other
 * screens keep normal focus navigation.
 */
@Composable
fun RemoteInputLayer(
    webView: View,
    fullscreen: FullscreenVideoState,
    mediaController: MediaController,
    osd: OsdState,
    state: PointerState,
    keyRouter: KeyRouter,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember(state, mediaController) {
        InputSession(state, RemoteInputController(), InputInjector(), mediaController, osd, fullscreen, scope)
    }
    val fullscreenView = fullscreen.current?.view
    session.injector.target = fullscreenView ?: webView
    // Density only changes with display settings; refresh it on recomposition.
    session.density = LocalDensity.current.density

    // Register for keys while shown; unregister (and drop anything held) when we leave.
    DisposableEffect(keyRouter, session) {
        keyRouter.register(session)
        onDispose {
            keyRouter.unregister(session)
            session.resetInput()
            session.injector.release()
        }
    }

    // Input mode follows full screen: entering switches to video control, leaving (by any
    // path: Back, the player's button, page navigation) returns to the pointer.
    val isFullscreen = fullscreenView != null
    LaunchedEffect(isFullscreen, session) { session.onFullscreenChanged(isFullscreen) }

    // Second Back callback, enabled only while full screen (see FullscreenBackCallback for how
    // the dispatcher chooses). Added from here so its lifetime matches this screen.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backCallback = remember(session) { FullscreenBackCallback(session::onFullscreenBack) }
    DisposableEffect(dispatcher, backCallback) {
        dispatcher?.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }
    SideEffect { backCallback.isEnabled = isFullscreen }

    // A key held while the app goes to the background never sends its UP to us.
    // Forget held keys on pause so the pointer can't keep drifting when we come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) session.resetInput()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Frame loop: runs only while an arrow is held. withFrameNanos suspends until the next
    // display frame and hands us its timestamp, so movement is synced to vsync and uses the
    // real time between frames. When `moving` turns false, the effect is cancelled.
    val moving = state.held.isNotEmpty()
    LaunchedEffect(moving, session) {
        if (!moving) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                session.onFrame(dtSec = (now - lastFrameNanos) / 1_000_000_000f)
                lastFrameNanos = now
            }
        }
    }

    // Timer for the controller's deadlines (OK long press, held-seek steps). The controller is
    // pure and has no timers, so we wake it at its deadline; it then reports the next one.
    LaunchedEffect(session.deadline, session) {
        val deadline = session.deadline ?: return@LaunchedEffect
        delay((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L).milliseconds)
        session.tick()
    }

    // Auto-hide: restarts on every activity; never hides while moving. `visible` is checked
    // inside rather than used as a key: this effect sets it to false itself, and a key change
    // would cancel the effect in the middle of its own fade-out.
    LaunchedEffect(state.activityCount, moving) {
        if (moving || !state.visible) return@LaunchedEffect
        delay(InputTuning.IDLE_HIDE_MS.milliseconds)
        state.visible = false
        state.alpha.animateTo(0f, tween(InputTuning.FADE_OUT_MS))
    }

    PointerOverlay(
        state = state,
        modifier = modifier.onSizeChanged { size ->
            // The overlay fills the same Box as the WebView, so its size is the WebView's size.
            state.updateBounds(size.width / session.density, size.height / session.density)
        },
    )
}

/**
 * Hook for "pointer held against a screen edge". Called every frame while an arrow pushes the
 * pointer into [edges]; [dtSec] is the frame time so a future implementation can scroll at a
 * time-based speed. Intentionally a no-op: edge scrolling is planned with scroll mode
 * (Milestone 5).
 */
@Suppress("UNUSED_PARAMETER")
private fun onPointerPushedAgainstEdge(edges: Set<Edge>, dtSec: Float) = Unit

/**
 * Glue between key events, the pure controller/physics and the Android-facing injector and
 * media router. Remembered once per [PointerState]; all methods run on the main thread.
 */
private class InputSession(
    private val state: PointerState,
    private val controller: RemoteInputController,
    val injector: InputInjector,
    private val media: MediaController,
    private val osd: OsdState,
    private val fullscreen: FullscreenVideoState,
    private val scope: CoroutineScope,
) : KeyHandler {

    var density = 1f

    private val router = MediaRouter(media, NativeMediaKeyFallback { injector.target })

    /**
     * Media keys whose DOWN we let through to the WebView (no video known while browsing).
     * Their repeats and UP must go the same way, or the page sees a DOWN without an UP.
     */
    private val passedThrough = HashSet<Int>()

    /** Mirrors controller.nextDeadlineMs as Compose state so the timer effect restarts on change. */
    var deadline by mutableStateOf<Long?>(null)
        private set

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = KeyMap.fromKeyCode(event.keyCode) ?: return false
        if (event.keyCode in passedThrough) {
            if (event.action == KeyEvent.ACTION_UP) passedThrough.remove(event.keyCode)
            return false
        }
        val result = when (event.action) {
            KeyEvent.ACTION_DOWN -> controller.onKeyDown(key, event.eventTime, isRepeat = event.repeatCount > 0)
            KeyEvent.ACTION_UP -> controller.onKeyUp(key, event.eventTime)
            else -> return false
        }
        val passThrough = apply(result.commands)
        if (passThrough) {
            passedThrough += event.keyCode
            return false
        }
        return result.consumed
    }

    fun tick() {
        apply(controller.onTick(SystemClock.uptimeMillis()))
    }

    fun resetInput() {
        apply(controller.reset())
        passedThrough.clear()
    }

    fun onFullscreenChanged(showing: Boolean) {
        apply(if (showing) controller.enterFullscreen() else controller.exitFullscreen())
    }

    /** Back while full screen: pointer → video control, or video control → exit full screen. */
    fun onFullscreenBack() {
        val result = controller.onFullscreenBack()
        apply(result.commands)
        // Exiting triggers onFullscreenChanged(false) on the next composition, which puts the
        // controller back into pointer mode.
        if (!result.consumed) fullscreen.exitByUser()
    }

    fun onFrame(dtSec: Float) {
        val bounds = state.bounds ?: return
        val step = PointerPhysics.step(PointerMotion(state.position, state.holdSec), state.held, dtSec, bounds)
        val moved = step.motion.position != state.position
        state.position = step.motion.position
        state.holdSec = step.motion.holdSec
        if (moved) hoverAtPointer(force = false)
        if (step.edgesPushed.isNotEmpty()) onPointerPushedAgainstEdge(step.edgesPushed, dtSec)
    }

    /** Runs [commands]; returns true if a media command should pass through to the WebView. */
    private fun apply(commands: List<InputCommand>): Boolean {
        var passThrough = false
        for (command in commands) {
            when (command) {
                is InputCommand.DirectionsChanged -> onDirectionsChanged(command)
                InputCommand.Click -> onClick()
                // Milestone 5 turns this into "enter scroll mode".
                InputCommand.LongPressOk -> Log.d(TAG, "Long-press OK (scroll mode comes in Milestone 5)")
                is InputCommand.Media -> if (!onMedia(command.action)) passThrough = true
                is InputCommand.PointerVisibility -> if (command.visible) showPointerOverVideo() else hidePointer()
            }
        }
        deadline = controller.nextDeadlineMs
        return passThrough
    }

    /** Returns false when nothing handled the action (the key should pass through). */
    private fun onMedia(action: MediaAction): Boolean {
        val playingBefore = media.activeVideo.value?.playing
        val route = router.perform(action, fullscreen = controller.mode.isFullscreen)
        if (route == MediaRoute.PassThrough) return false
        osd.show(osdEventFor(action, if (route == MediaRoute.Agent) playingBefore else null))
        return true
    }

    private fun onDirectionsChanged(command: InputCommand.DirectionsChanged) {
        val wasIdle = state.held.isEmpty()
        state.held = command.held
        if (command.held.isEmpty()) {
            state.holdSec = 0f
            // Always tell the page the final resting position, even if throttling skipped it.
            hoverAtPointer(force = true)
            state.markActivity()
            return
        }
        reveal()
        val bounds = state.bounds ?: return
        if (wasIdle) {
            // Immediate small step: a quick tap moves only this far (fine positioning).
            val nudge = PointerPhysics.nudge(state.position, command.held, bounds)
            state.position = nudge.motion.position
            state.holdSec = 0f
            hoverAtPointer(force = true)
        }
    }

    private fun onClick() {
        if (!state.visible) {
            // Hidden pointer: the user can't see where a click would land, so only show it.
            reveal()
            return
        }
        reveal()
        injector.click(state.position.x * density, state.position.y * density)
        scope.launch {
            state.clickPulse.snapTo(0f)
            state.clickPulse.animateTo(1f, tween(InputTuning.CLICK_PULSE_MS))
        }
    }

    /** Shows the pointer instantly (cancelling any fade) and restarts the idle timer. */
    private fun reveal() {
        state.visible = true
        state.markActivity()
        scope.launch { state.alpha.snapTo(1f) }
    }

    /** Up/Down in full-screen video: show the pointer and hover so the player shows its controls. */
    private fun showPointerOverVideo() {
        reveal()
        hoverAtPointer(force = true)
    }

    /** Hides the pointer instantly (video control in full screen). */
    private fun hidePointer() {
        state.visible = false
        scope.launch { state.alpha.snapTo(0f) }
    }

    private fun hoverAtPointer(force: Boolean) {
        injector.hover(state.position.x * density, state.position.y * density, force = force)
    }
}
