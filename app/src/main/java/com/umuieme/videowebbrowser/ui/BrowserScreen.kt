package com.umuieme.videowebbrowser.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.PageListener
import com.umuieme.videowebbrowser.browser.WebViewFactory

/**
 * Full-screen WebView showing [url], with a thin progress bar and a main-frame error panel.
 *
 * @param onWebViewChanged receives the live WebView (or null when it goes away) so the
 *   Activity's Back handling can call canGoBack/goBack.
 */
@Composable
fun BrowserScreen(
    url: String,
    onWebViewChanged: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pageState = remember { PageState() }
    // Bumped by "Retry" on the unavailable screen to attempt creating the WebView again.
    var creationAttempt by rememberSaveable { mutableIntStateOf(0) }

    // Created once per attempt and kept across recompositions. Never create a WebView inside
    // AndroidView's update block or the composable body: that would rebuild it and reload the page.
    val webViewResult = remember(creationAttempt) { WebViewFactory.create(context, pageState) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val webView = webViewResult.getOrNull()
        if (webView == null) {
            MessagePanel(
                title = stringResource(R.string.webview_unavailable_title),
                message = stringResource(R.string.webview_unavailable_message),
                onRetry = { creationAttempt++ },
            )
        } else {
            key(webView) {
                WebViewHost(webView, url, pageState, onWebViewChanged)
            }
        }
    }
}

@Composable
private fun BoxScope.WebViewHost(
    webView: WebView,
    url: String,
    pageState: PageState,
    onWebViewChanged: (WebView?) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnWebViewChanged by rememberUpdatedState(onWebViewChanged)
    val error = pageState.mainFrameError

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
            // Detach first: destroy() on a WebView still attached to a window logs errors
            // and can leak. Then release its renderer and memory.
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Load only when the WebView or the URL changes, not on every recomposition.
    LaunchedEffect(webView, url) { webView.loadUrl(url) }

    AndroidView(
        // Return the already-created WebView rather than building one here, so its lifetime
        // is owned by the remember/DisposableEffect above and not by AndroidView.
        factory = {
            webView.apply {
                // Give it D-pad focus once it is attached to the window.
                post { requestFocus() }
            }
        },
        // Runs again whenever `error` changes. Hiding the WebView takes it out of D-pad focus
        // order so the Retry button can't lose focus to it.
        update = { view ->
            view.visibility = if (error == null) View.VISIBLE else View.INVISIBLE
            if (error == null && !view.hasFocus()) view.requestFocus()
        },
        modifier = Modifier.fillMaxSize(),
    )

    LoadingBar(
        progress = pageState.progress,
        visible = pageState.isLoading && error == null,
        modifier = Modifier.align(Alignment.TopStart),
    )

    if (error != null) {
        MessagePanel(
            title = stringResource(R.string.browser_load_error_title),
            message = error.description,
            onRetry = {
                pageState.mainFrameError = null
                webView.loadUrl(error.failedUrl)
            },
        )
    }
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

/** Centered title, message and a focused Retry button. Used for load errors and missing WebView. */
@Composable
private fun MessagePanel(title: String, message: String, onRetry: () -> Unit) {
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
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                Text(stringResource(R.string.browser_retry))
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
}
