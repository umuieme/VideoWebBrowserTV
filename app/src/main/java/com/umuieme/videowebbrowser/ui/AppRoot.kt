package com.umuieme.videowebbrowser.ui

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.PageScripts
import com.umuieme.videowebbrowser.browser.policy.AdBlockerHolder
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
    adBlockerHolder: AdBlockerHolder,
    pageScripts: PageScripts,
    viewModel: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory),
) {
    // Collects only while the Activity is at least STARTED, so nothing runs in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sitePermissions by viewModel.sitePermissions.collectAsStateWithLifecycle()
    val menuHintVisible by viewModel.menuHintVisible.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            // Plain black for the few milliseconds settings take to load; avoids flashing setup.
            BrowserUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black))
            is BrowserUiState.NeedsSetup -> SetupScreen(
                error = state.error,
                onSubmit = viewModel::submitUrl,
                onInputChanged = viewModel::clearSetupError,
                initialUrl = state.currentUrl,
                onCancel = if (state.canCancel) viewModel::cancelChangeSite else null,
            )
            is BrowserUiState.Browsing -> BrowserScreen(
                url = state.url,
                keyRouter = keyRouter,
                onWebViewChanged = onWebViewChanged,
                onChangeSite = viewModel::changeSite,
                sitePermissions = sitePermissions,
                onAllowSite = viewModel::allowSite,
                adBlockerHolder = adBlockerHolder,
                pageScripts = pageScripts,
            )
        }
        MenuHint(visible = menuHintVisible, modifier = Modifier.fillMaxSize())
    }
}

/**
 * "Menu coming in Milestone 7", shown after a long Back press. Drawn above every screen, with
 * no focusable modifier, so it never takes focus or keys from what's underneath.
 */
@Composable
private fun MenuHint(visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = stringResource(R.string.menu_coming),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}
