package com.umuieme.videowebbrowser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.FullscreenSession
import com.umuieme.videowebbrowser.browser.FullscreenVideoState
import com.umuieme.videowebbrowser.browser.PageListener
import com.umuieme.videowebbrowser.browser.PageScripts
import com.umuieme.videowebbrowser.browser.WebViewFactory
import com.umuieme.videowebbrowser.browser.media.MediaController
import com.umuieme.videowebbrowser.browser.policy.AdBlockerHolder
import com.umuieme.videowebbrowser.browser.policy.NavigationGuard
import com.umuieme.videowebbrowser.browser.policy.SitePermissions
import com.umuieme.videowebbrowser.input.KeyRouter
import com.umuieme.videowebbrowser.ui.pointer.PointerState
import com.umuieme.videowebbrowser.ui.policy.LeaveSiteDialog
import com.umuieme.videowebbrowser.ui.policy.PolicyHintOverlay
import com.umuieme.videowebbrowser.ui.policy.PolicyUiState
import com.umuieme.videowebbrowser.ui.video.OsdState
import com.umuieme.videowebbrowser.ui.video.VideoOsd

/**
 * Full-screen WebView showing [url], with a thin progress bar and a main-frame error panel.
 *
 * @param keyRouter where pointer mode registers for remote keys while the page is shown.
 * @param onWebViewChanged receives the live WebView (or null when it goes away) so the
 *   Activity's Back handling can close site popups and go back in history.
 * @param onChangeSite offered on every error panel, so a site that never loads can't trap the
 *   user (the URL is saved, so restarting the app would just open it again).
 * @param sitePermissions latest allowlist and allowed-once sets, from the ViewModel.
 * @param onAllowSite the user allowed a domain in the ask dialog (`always` = Always allow).
 * @param adBlockerHolder the app-wide ad list, empty until loaded.
 * @param pageScripts on-demand page scripts; scroll mode uses the scroll locator.
 */
@Composable
fun BrowserScreen(
    url: String,
    keyRouter: KeyRouter,
    onWebViewChanged: (WebView?) -> Unit,
    onChangeSite: () -> Unit,
    sitePermissions: SitePermissions,
    onAllowSite: (domain: String, always: Boolean) -> Unit,
    adBlockerHolder: AdBlockerHolder,
    pageScripts: PageScripts,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pageState = remember { PageState() }
    // Bumped by "Retry" (WebView unavailable, or its renderer died) to create a new WebView.
    var creationAttempt by rememberSaveable { mutableIntStateOf(0) }

    // Per WebView: full-screen video state and the media agent's controller. Both hold
    // references tied to one WebView instance (its custom view, its frames' reply proxies).
    val fullscreen = remember(creationAttempt) { FullscreenVideoState() }
    val mediaController = remember(creationAttempt) { MediaController() }
    // Navigation policy for this WebView, and the UI state (hint, ask dialog) it drives.
    val policyUi = remember(creationAttempt) { PolicyUiState() }
    val guard = remember(creationAttempt) { NavigationGuard(adBlockerHolder, policyUi) }
    // SideEffect runs after every successful composition, so the guard always sees the
    // latest allowlist (e.g. after "Always allow" or a change made in settings later).
    SideEffect { guard.updatePermissions(sitePermissions) }

    // Created once per attempt and kept across recompositions. Never create a WebView inside
    // AndroidView's update block or the composable body: that would rebuild it and reload the page.
    val webViewResult = remember(creationAttempt) {
        WebViewFactory.create(context, pageState, fullscreen, mediaController, guard, policyUi::onDownloadBlocked)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val webView = webViewResult.getOrNull()
        if (webView == null) {
            MessagePanel(
                title = stringResource(R.string.webview_unavailable_title),
                message = stringResource(R.string.webview_unavailable_message),
                onRetry = { creationAttempt++ },
                onChangeSite = onChangeSite,
            )
        } else if (pageState.rendererGone) {
            // Leaving WebViewHost out of composition runs its onDispose, which removes and
            // destroys the dead WebView. Retry builds a new one. No automatic reload: a page that
            // crashes the renderer every time would otherwise loop forever.
            MessagePanel(
                title = stringResource(R.string.renderer_gone_title),
                message = stringResource(R.string.renderer_gone_message),
                onRetry = {
                    pageState.rendererGone = false
                    creationAttempt++
                },
                onChangeSite = onChangeSite,
            )
        } else {
            key(webView) {
                WebViewHost(
                    webView, url, pageState, fullscreen, mediaController, guard, policyUi, keyRouter,
                    pageScripts, onWebViewChanged, onChangeSite, onAllowSite,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WebViewHost(
    webView: WebView,
    url: String,
    pageState: PageState,
    fullscreen: FullscreenVideoState,
    mediaController: MediaController,
    guard: NavigationGuard,
    policyUi: PolicyUiState,
    keyRouter: KeyRouter,
    pageScripts: PageScripts,
    onWebViewChanged: (WebView?) -> Unit,
    onChangeSite: () -> Unit,
    onAllowSite: (domain: String, always: Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val osd = remember(webView) { OsdState() }
    val activeVideo by mediaController.activeVideo.collectAsStateWithLifecycle()
    val fullscreenSession = fullscreen.current
    // Remembered here (per WebView), not inside RemoteInputLayer: the pointer keeps its position
    // across page loads and across an error panel coming and going.
    val pointerState = remember(webView) { PointerState() }
    val currentOnWebViewChanged by rememberUpdatedState(onWebViewChanged)
    val error = pageState.mainFrameError
    val askOpen = policyUi.pendingAsk != null

    // Ties the WebView to the Activity lifecycle and destroys it when this screen leaves.
    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Resume/pause JS timers, animations and media with the Activity, so a video
                // doesn't keep playing (and the CPU working) behind the TV's home screen.
                // pauseTimers/resumeTimers are process-wide; fine with a single WebView.
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.resumeTimers()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    webView.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        currentOnWebViewChanged(webView)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentOnWebViewChanged(null)
            // Never destroy a WebView that is still in full screen: end it first (detaches the
            // custom view and tells WebView).
            fullscreen.exitByUser()
            // Destroys a pop-up catcher that may still be waiting for its URL.
            guard.release()
            // Detach first: destroy() on a WebView still attached to a window logs errors
            // and can leak. Then release its renderer and memory.
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Load only when the WebView or the URL changes, not on every recomposition. Through the
    // guard, so this URL becomes the home site and its server redirects are trusted.
    LaunchedEffect(webView, url) { guard.loadTrusted(webView, url, isHome = true) }

    AndroidView(
        // Return the already-created WebView rather than building one here, so its lifetime
        // is owned by the remember/DisposableEffect above and not by AndroidView.
        factory = {
            webView.apply {
                // Give it D-pad focus once it is attached to the window.
                post { requestFocus() }
            }
        },
        // Runs again whenever `error` or `askOpen` changes. Hiding the WebView takes it out of
        // D-pad focus order so the Retry button can't lose focus to it. While the ask dialog is
        // open the WebView is made unfocusable (which also clears its focus), so D-pad focus
        // search can only land on the dialog's buttons; afterwards focus comes back here.
        update = { view ->
            view.visibility = if (error == null) View.VISIBLE else View.INVISIBLE
            view.isFocusable = !askOpen
            view.isFocusableInTouchMode = !askOpen
            if (error == null && !askOpen && !view.hasFocus()) view.requestFocus()
        },
        modifier = Modifier.fillMaxSize(),
    )

    LoadingBar(
        progress = pageState.progress,
        visible = pageState.isLoading && error == null,
        modifier = Modifier.align(Alignment.TopStart),
    )

    // Full-screen video: above the WebView, below the OSD and pointer.
    if (fullscreenSession != null) {
        key(fullscreenSession) { FullscreenVideoHost(fullscreenSession) }
    }
    FullscreenWindowEffects(hostView, webView, isFullscreen = fullscreenSession != null)

    // Keep the screen on only while the active video plays. Without a known video (agent
    // unsupported, or a frame it can't see) keep it on while full screen is showing instead.
    val keepScreenOn = activeVideo?.playing == true || (fullscreenSession != null && activeVideo == null)
    DisposableEffect(hostView, keepScreenOn) {
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }

    VideoOsd(state = osd, video = activeVideo, modifier = Modifier.fillMaxSize())

    // Remote input only while the page itself is visible. With the error panel or the ask
    // dialog up, the layer leaves composition, its key handler unregisters (KeyRouter then has
    // nothing to offer keys to), and the buttons get normal Compose D-pad focus.
    if (error == null && !askOpen) {
        RemoteInputLayer(
            webView = webView,
            fullscreen = fullscreen,
            mediaController = mediaController,
            osd = osd,
            state = pointerState,
            keyRouter = keyRouter,
            pageScripts = pageScripts,
            modifier = Modifier.fillMaxSize(),
        )
    } else if (error != null) {
        MessagePanel(
            title = stringResource(R.string.browser_load_error_title),
            message = error.description,
            onRetry = {
                pageState.mainFrameError = null
                guard.loadTrusted(webView, error.failedUrl, isHome = error.failedUrl == url)
            },
            onChangeSite = onChangeSite,
        )
    }

    // Policy UI on top of everything: the hint never takes focus; the dialog takes it while open.
    PolicyHintOverlay(state = policyUi, modifier = Modifier.fillMaxSize())
    policyUi.pendingAsk?.let { ask ->
        fun allow(always: Boolean) {
            policyUi.resolveAsk() ?: return
            onAllowSite(ask.domain, always)
            guard.allowOnceNow(ask.domain)
            guard.loadTrusted(webView, ask.url)
        }
        LeaveSiteDialog(
            domain = ask.domain,
            onAllowOnce = { allow(always = false) },
            onAlwaysAllow = { allow(always = true) },
            onBlock = { policyUi.resolveAsk() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Shows the view WebView handed us in onShowCustomView, filling the screen. Keyed by session in
 * the caller, so a new session gets a fresh AndroidView. The view's lifetime belongs to
 * FullscreenVideoState (it detaches the view when full screen ends), not to AndroidView.
 */
@Composable
private fun FullscreenVideoHost(session: FullscreenSession) {
    AndroidView(
        factory = {
            session.view.apply {
                // A View can have only one parent; be defensive in case WebView left it attached.
                (parent as? ViewGroup)?.removeView(this)
                setBackgroundColor(android.graphics.Color.BLACK)
                isFocusable = true
                // Focus moves here so fallback key events and typing go to the video.
                post { requestFocus() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Window-level side effects of full screen: system bars and giving focus back afterwards. */
@Composable
private fun FullscreenWindowEffects(hostView: View, webView: WebView, isFullscreen: Boolean) {
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            // Bars are already hidden app-wide (MainActivity); re-assert in case a dialog or
            // the IME brought them back, so the video gets every pixel.
            hostView.context.findWindow()?.let { window ->
                WindowCompat.getInsetsController(window, hostView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        } else if (!webView.hasFocus()) {
            // The full-screen view had focus and is gone; give it back to the page.
            webView.requestFocus()
        }
    }
}

/** Compose hands us the Activity's context, possibly wrapped (e.g. for theming). */
private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/** Thin bar across the top while a page loads. */
@Composable
private fun LoadingBar(progress: Int, visible: Boolean, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(targetValue = progress / 100f, label = "loadProgress")
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(3.dp)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/**
 * Centered title, message, a focused Retry button and a Change site button next to it.
 * Used for load errors, renderer crashes and a missing WebView.
 */
@Composable
private fun MessagePanel(title: String, message: String, onRetry: () -> Unit, onChangeSite: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(58.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            // A Row, so Left/Right moves between the buttons with the D-pad.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                    Text(stringResource(R.string.browser_retry))
                }
                Button(onClick = onChangeSite) {
                    Text(stringResource(R.string.browser_change_site))
                }
            }
        }
    }
}

private data class MainFrameError(val failedUrl: String, val description: String)

/**
 * Compose-observable page state, written by the WebView clients through [PageListener].
 * WebView callbacks arrive on the main thread, so writing snapshot state here is safe.
 */
@Stable
private class PageState : PageListener {
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var mainFrameError by mutableStateOf<MainFrameError?>(null)
    var rendererGone by mutableStateOf(false)

    override fun onPageStarted(url: String) {
        isLoading = true
        mainFrameError = null
    }

    override fun onPageFinished(url: String) {
        isLoading = false
    }

    override fun onProgressChanged(progress: Int) {
        this.progress = progress
        if (progress < 100) isLoading = true
    }

    override fun onMainFrameError(failedUrl: String, description: String) {
        mainFrameError = MainFrameError(failedUrl, description)
    }

    override fun onRenderProcessGone() {
        isLoading = false
        mainFrameError = null
        rendererGone = true
    }
}
