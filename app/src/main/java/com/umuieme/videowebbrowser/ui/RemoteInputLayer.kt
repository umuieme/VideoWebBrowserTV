package com.umuieme.videowebbrowser.ui

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
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
import com.umuieme.videowebbrowser.BuildConfig
import com.umuieme.videowebbrowser.browser.FullscreenVideoState
import com.umuieme.videowebbrowser.browser.PageScripts
import com.umuieme.videowebbrowser.browser.back.BackStepCallback
import com.umuieme.videowebbrowser.browser.media.MediaController
import com.umuieme.videowebbrowser.browser.media.MediaRoute
import com.umuieme.videowebbrowser.browser.media.MediaRouter
import com.umuieme.videowebbrowser.browser.media.NativeMediaKeyFallback
import com.umuieme.videowebbrowser.browser.scroll.ScrollTarget
import com.umuieme.videowebbrowser.input.Direction
import com.umuieme.videowebbrowser.input.Edge
import com.umuieme.videowebbrowser.input.InputCommand
import com.umuieme.videowebbrowser.input.InputInjector
import com.umuieme.videowebbrowser.input.InputMode
import com.umuieme.videowebbrowser.input.InputTuning
import com.umuieme.videowebbrowser.input.KeyHandler
import com.umuieme.videowebbrowser.input.KeyMap
import com.umuieme.videowebbrowser.input.KeyRouter
import com.umuieme.videowebbrowser.input.MediaAction
import com.umuieme.videowebbrowser.input.PointerBounds
import com.umuieme.videowebbrowser.input.PointerMotion
import com.umuieme.videowebbrowser.input.PointerPhysics
import com.umuieme.videowebbrowser.input.RemoteInputController
import com.umuieme.videowebbrowser.input.ScrollPhysics
import com.umuieme.videowebbrowser.input.Vec2
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

private const val SCROLL_TAG = "VWB-Scroll"

/**
 * Remote control for the browser screen while the page is shown: takes keys from [keyRouter],
 * runs the input state machine, moves and draws the pointer, injects clicks/hover/scrolling,
 * sends media commands, and owns the mode-specific steps of the Back chain.
 *
 * The injection target is the full-screen video view while one is showing, otherwise
 * [webView]. Leaving composition unregisters the key handler and the Back callbacks, so other
 * screens keep normal focus navigation.
 */
@Composable
fun RemoteInputLayer(
    webView: WebView,
    fullscreen: FullscreenVideoState,
    mediaController: MediaController,
    osd: OsdState,
    state: PointerState,
    keyRouter: KeyRouter,
    pageScripts: PageScripts,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val session = remember(state, mediaController) {
        InputSession(
            state, RemoteInputController(), InputInjector(), mediaController, osd, fullscreen, scope, webView,
            pageScripts,
        )
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

    // Back chain steps 1–3 (see BackStepCallback for how the dispatcher orders them). Added in
    // one effect, lowest priority first, so they are always (re-)added together and keep their
    // order even when this layer leaves and re-enters composition. All of them are added after
    // MainActivity's PageBackCallback, so each one, while enabled, wins over popup/history/exit.
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val exitFullscreenBack = remember(session) { BackStepCallback(session::onExitFullscreenBack) }
    val fullscreenPointerBack = remember(session) { BackStepCallback(session::onFullscreenPointerBack) }
    val exitScrollBack = remember(session) { BackStepCallback(session::onExitScrollBack) }
    DisposableEffect(dispatcher, session) {
        dispatcher?.addCallback(exitFullscreenBack) // 3
        dispatcher?.addCallback(fullscreenPointerBack) // 2
        dispatcher?.addCallback(exitScrollBack) // 1
        onDispose {
            exitScrollBack.remove()
            fullscreenPointerBack.remove()
            exitFullscreenBack.remove()
        }
    }
    // Each step enabled exactly when it applies. session.mode is Compose state, so a mode change
    // recomposes this function and SideEffect runs again with the new values.
    val mode = session.mode
    SideEffect {
        exitScrollBack.isEnabled = mode == InputMode.Scroll
        fullscreenPointerBack.isEnabled = mode == InputMode.FullscreenPointer
        exitFullscreenBack.isEnabled = isFullscreen
    }

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

    // Frame loop: runs only while an arrow is held (pointer movement, edge scrolling and scroll
    // mode all use it). withFrameNanos suspends until the next display frame and hands us its
    // timestamp, so motion is synced to vsync and uses the real time between frames. When
    // `moving` turns false, the effect is cancelled.
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

    // Auto-hide: restarts on every activity; never hides while moving or in scroll mode.
    // `visible` is checked inside rather than used as a key: this effect sets it to false itself,
    // and a key change would cancel the effect in the middle of its own fade-out.
    LaunchedEffect(state.activityCount, moving, state.scrollMode) {
        if (moving || state.scrollMode || !state.visible) return@LaunchedEffect
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
 * Glue between key events, the pure controller/physics and the Android-facing injector,
 * page scripts and media router. Remembered once per [PointerState]; all methods run on the
 * main thread.
 */
private class InputSession(
    private val state: PointerState,
    private val controller: RemoteInputController,
    val injector: InputInjector,
    private val media: MediaController,
    private val osd: OsdState,
    private val fullscreen: FullscreenVideoState,
    private val scope: CoroutineScope,
    private val webView: WebView,
    private val scripts: PageScripts,
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

    /** Mirrors controller.mode as Compose state; drives which Back steps are enabled. */
    var mode by mutableStateOf(controller.mode)
        private set

    // --- Scroll gesture state (scroll mode) ---

    /** Bumped per gesture; a locator answer for an older gesture is ignored. */
    private var gestureId = 0

    /** True while scroll-locator.js hasn't answered for the current gesture. */
    private var locating = false

    /** The gesture's locked target, in dp; null until located. */
    private var scrollTargetDp: Vec2? = null

    /** Scroll amount (px) produced while still locating; sent as soon as the target is known. */
    private var pendingDx = 0f
    private var pendingDy = 0f

    /** Per-axis hold times: scroll mode, and the pointer pushing against an edge. */
    private var vHold = 0f
    private var hHold = 0f
    private var vEdgeHold = 0f
    private var hEdgeHold = 0f

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

    // --- Back chain steps 1–3 ---

    fun onExitScrollBack() {
        apply(controller.exitScroll())
    }

    fun onFullscreenPointerBack() {
        apply(controller.fullscreenPointerBack())
    }

    /** Exiting triggers onFullscreenChanged(false) on the next composition: back to pointer mode. */
    fun onExitFullscreenBack() {
        fullscreen.exitByUser()
    }

    // --- Frames ---

    fun onFrame(dtSec: Float) {
        val bounds = state.bounds ?: return
        if (controller.mode == InputMode.Scroll) {
            scrollFrame(dtSec, bounds)
            return
        }
        val step = PointerPhysics.step(PointerMotion(state.position, state.holdSec), state.held, dtSec, bounds)
        val moved = step.motion.position != state.position
        state.position = step.motion.position
        state.holdSec = step.motion.holdSec
        if (moved) hoverAtPointer(force = false)
        // Edge scrolling only on the page; over a full-screen video there is nothing to scroll.
        if (controller.mode == InputMode.Pointer && step.edgesPushed.isNotEmpty()) {
            edgeScroll(step.edgesPushed, dtSec, bounds)
        } else {
            vEdgeHold = 0f
            hEdgeHold = 0f
        }
    }

    /** Scroll mode, arrows held: continuous scrolling at the gesture's locked target. */
    private fun scrollFrame(dtSec: Float, bounds: PointerBounds) {
        val held = state.held
        val v = ScrollPhysics.stepAxis(vHold, ScrollPhysics.sign(held, Direction.Up, Direction.Down), dtSec, bounds.height * density)
        val h = ScrollPhysics.stepAxis(hHold, ScrollPhysics.sign(held, Direction.Left, Direction.Right), dtSec, bounds.width * density)
        vHold = v.holdSec
        hHold = h.holdSec
        sendScroll(vertical = v.distancePx, horizontal = h.distancePx)
    }

    /**
     * Pointer mode, pushed against [edges]: scroll at the pointer in that direction. No locator:
     * the wheel event lands on whatever is under the pointer and chains up to the page.
     */
    private fun edgeScroll(edges: Set<Edge>, dtSec: Float, bounds: PointerBounds) {
        val vSign = (if (Edge.Bottom in edges) 1 else 0) - (if (Edge.Top in edges) 1 else 0)
        val hSign = (if (Edge.Right in edges) 1 else 0) - (if (Edge.Left in edges) 1 else 0)
        val v = ScrollPhysics.stepAxis(vEdgeHold, vSign, dtSec, bounds.height * density, ScrollPhysics::edgeSpeed)
        val h = ScrollPhysics.stepAxis(hEdgeHold, hSign, dtSec, bounds.width * density, ScrollPhysics::edgeSpeed)
        vEdgeHold = v.holdSec
        hEdgeHold = h.holdSec
        val p = state.position
        injector.scroll(p.x * density, p.y * density, vertical = v.distancePx, horizontal = h.distancePx)
    }

    // --- Commands ---

    /** Runs [commands]; returns true if a media command should pass through to the WebView. */
    private fun apply(commands: List<InputCommand>): Boolean {
        var passThrough = false
        for (command in commands) {
            when (command) {
                is InputCommand.DirectionsChanged -> onDirectionsChanged(command)
                InputCommand.Click -> onClick()
                is InputCommand.ModeChanged -> reveal()
                is InputCommand.ScrollGestureStarted -> startScrollGesture(command.held)
                is InputCommand.Media -> if (!onMedia(command.action)) passThrough = true
                is InputCommand.PointerVisibility -> if (command.visible) showPointerOverVideo() else hidePointer()
            }
        }
        deadline = controller.nextDeadlineMs
        mode = controller.mode
        // Kept in sync on every path (also when full screen starts while scrolling).
        state.scrollMode = controller.mode == InputMode.Scroll
        if (controller.mode != InputMode.Scroll) abandonScrollGesture()
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
        val previous = state.held
        val wasIdle = previous.isEmpty()
        state.held = command.held
        if (command.held.isEmpty()) {
            state.holdSec = 0f
            vHold = 0f
            hHold = 0f
            vEdgeHold = 0f
            hEdgeHold = 0f
            // Always tell the page the final resting position, even if throttling skipped it.
            if (controller.mode != InputMode.Scroll) hoverAtPointer(force = true)
            state.markActivity()
            return
        }
        reveal()
        val bounds = state.bounds ?: return
        if (controller.mode == InputMode.Scroll) {
            // Each newly pressed arrow scrolls one fixed step right away; holding adds the
            // continuous scroll from the frame loop after the hold delay.
            for (direction in command.held - previous) scrollTap(direction, bounds)
            return
        }
        if (wasIdle) {
            // Immediate small step: a quick tap moves only this far (fine positioning).
            val nudge = PointerPhysics.nudge(state.position, command.held, bounds)
            state.position = nudge.motion.position
            state.holdSec = 0f
            hoverAtPointer(force = true)
        }
    }

    private fun scrollTap(direction: Direction, bounds: PointerBounds) {
        val vStep = ScrollPhysics.tapStepPx(bounds.height * density)
        val hStep = ScrollPhysics.tapStepPx(bounds.width * density)
        when (direction) {
            Direction.Up -> { vHold = 0f; sendScroll(vertical = -vStep, horizontal = 0f) }
            Direction.Down -> { vHold = 0f; sendScroll(vertical = vStep, horizontal = 0f) }
            Direction.Left -> { hHold = 0f; sendScroll(vertical = 0f, horizontal = -hStep) }
            Direction.Right -> { hHold = 0f; sendScroll(vertical = 0f, horizontal = hStep) }
        }
    }

    // --- Scroll target (scroll-locator.js), located once per gesture ---

    /**
     * First arrow of a gesture: ask the page where to scroll. Until it answers (normally within a
     * frame or two), scroll amounts are collected instead of sent, so the first tap already lands
     * on the right element (e.g. a dialog rather than the page behind it). If it doesn't answer
     * within [InputTuning.SCROLL_LOCATOR_TIMEOUT_MS], the pointer position is used.
     */
    private fun startScrollGesture(held: Set<Direction>) {
        // A previous gesture still waiting (very fast re-press): deliver its amount at the pointer.
        finishLocating(null)
        val id = ++gestureId
        locating = true
        scrollTargetDp = null
        pendingDx = 0f
        pendingDy = 0f
        val bounds = state.bounds
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            finishLocating(null)
            return
        }
        val script = scripts.scrollLocator(
            fx = state.position.x / bounds.width,
            fy = state.position.y / bounds.height,
            dx = ScrollPhysics.sign(held, Direction.Left, Direction.Right),
            dy = ScrollPhysics.sign(held, Direction.Up, Direction.Down),
        )
        webView.evaluateJavascript(script) { json ->
            if (id == gestureId) finishLocating(ScrollTarget.parse(json))
        }
        scope.launch {
            delay(InputTuning.SCROLL_LOCATOR_TIMEOUT_MS.milliseconds)
            if (id == gestureId) finishLocating(null)
        }
    }

    /** Locks the gesture's target ([target] null = the pointer) and sends what was collected. */
    private fun finishLocating(target: ScrollTarget?) {
        if (!locating) return
        locating = false
        val bounds = state.bounds
        scrollTargetDp = if (target != null && bounds != null) {
            Vec2(target.fx * bounds.width, target.fy * bounds.height)
        } else {
            state.position
        }
        if (BuildConfig.DEBUG) Log.d(SCROLL_TAG, "target ${target?.kind ?: "pointer (no answer)"} at $scrollTargetDp")
        val dy = pendingDy
        val dx = pendingDx
        pendingDx = 0f
        pendingDy = 0f
        sendScroll(vertical = dy, horizontal = dx)
    }

    /** Leaving scroll mode: drop the gesture; a late locator answer is then ignored. */
    private fun abandonScrollGesture() {
        if (!locating && scrollTargetDp == null) return
        gestureId++
        locating = false
        scrollTargetDp = null
        pendingDx = 0f
        pendingDy = 0f
    }

    private fun sendScroll(vertical: Float, horizontal: Float) {
        if (vertical == 0f && horizontal == 0f) return
        if (locating) {
            pendingDy += vertical
            pendingDx += horizontal
            return
        }
        val target = scrollTargetDp ?: state.position
        injector.scroll(target.x * density, target.y * density, vertical = vertical, horizontal = horizontal)
    }

    // --- Pointer ---

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
