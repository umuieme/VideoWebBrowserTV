package com.umuieme.videowebbrowser.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuieme.videowebbrowser.input.KeyRouter

/**
 * Picks the screen from the ViewModel's state. With three screens and no back stack between
 * them, a `when` is all the "navigation" we need; a navigation library would add a dependency
 * and a second source of truth for which screen is showing.
 */
@Composable
fun AppRoot(
    keyRouter: KeyRouter,
    onWebViewChanged: (WebView?) -> Unit,
    viewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory),
) {
    // Collects only while the Activity is at least STARTED, so nothing runs in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        // Plain black for the few milliseconds settings take to load; avoids flashing setup.
        BrowserUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black))
        is BrowserUiState.NeedsSetup -> SetupScreen(
            error = state.error,
            onSubmit = viewModel::submitUrl,
            onInputChanged = viewModel::clearSetupError,
        )
        is BrowserUiState.Browsing -> BrowserScreen(
            url = state.url,
            keyRouter = keyRouter,
            onWebViewChanged = onWebViewChanged,
        )
    }
}
