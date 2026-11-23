package com.umuieme.videowebbrowser.ui.policy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.policy.PolicyListener
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** A cross-site link the user clicked, waiting for Allow once / Always allow / Block. */
data class PendingAsk(val url: String, val domain: String)

sealed interface PolicyHint {
    data class Blocked(val domain: String) : PolicyHint
    data object DownloadBlocked : PolicyHint
}

/**
 * Compose-observable state for the policy's UI, written by NavigationGuard through
 * [PolicyListener] (on the main thread, so writing snapshot state is safe). One per WebView,
 * like PageState.
 */
@Stable
class PolicyUiState : PolicyListener {
    var pendingAsk by mutableStateOf<PendingAsk?>(null)
        private set

    /** The latest hint. Kept after it hides, so the text stays put during the fade-out. */
    var hint by mutableStateOf<PolicyHint?>(null)
        private set

    var hintVisible by mutableStateOf(false)
        private set

    /** Bumped on every hint, so the same hint twice in a row restarts its timer. */
    var hintCount by mutableIntStateOf(0)
        private set

    override fun onBlocked(domain: String) = showHint(PolicyHint.Blocked(domain))

    override fun onDownloadBlocked() = showHint(PolicyHint.DownloadBlocked)

    override fun onAsk(url: String, domain: String): Boolean {
        if (pendingAsk != null) return false
        pendingAsk = PendingAsk(url, domain)
        return true
    }

    /** Closes the dialog and returns what it was asking about. */
    fun resolveAsk(): PendingAsk? = pendingAsk.also { pendingAsk = null }

    fun hideHint() {
        hintVisible = false
    }

    private fun showHint(newHint: PolicyHint) {
        hint = newHint
        hintVisible = true
        hintCount++
    }

    companion object {
        const val HINT_VISIBLE_MS = 2_500L
    }
}

/**
 * Brief "Blocked: example.com" at the top of the screen (the video OSD uses the bottom). Plain
 * drawing with no focusable modifier, like the OSD, so it never takes focus from the page.
 */
@Composable
fun PolicyHintOverlay(state: PolicyUiState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.hintCount) {
        if (!state.hintVisible) return@LaunchedEffect
        delay(PolicyUiState.HINT_VISIBLE_MS.milliseconds)
        state.hideHint()
    }
    val text = when (val hint = state.hint) {
        is PolicyHint.Blocked -> stringResource(R.string.policy_blocked, hint.domain)
        PolicyHint.DownloadBlocked -> stringResource(R.string.policy_download_blocked)
        null -> return
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = state.hintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * "<domain> wants to open": Allow once / Always allow / Block. An in-window overlay (not a
 * Compose Dialog, which would open a second window and bring the system bars back).
 *
 * Remote navigation: the buttons are ordinary Compose focus targets in a Row, so Left/Right
 * move between them and OK presses one. Block gets focus first, so pressing OK by reflex is
 * the safe choice. BackHandler registers an OnBackPressedCallback while this is shown; being
 * the newest callback, it runs before the app's other Back handling, and Back means Block.
 *
 * The caller must make the WebView unfocusable and remove the pointer's key handler while this
 * is shown, so arrows reach Compose focus here instead (see BrowserScreen).
 */
@Composable
fun LeaveSiteDialog(
    domain: String,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { blockFocus.requestFocus() }
    BackHandler(onBack = onBlock)

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.policy_ask_title, domain),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.policy_ask_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            // focusGroup: D-pad focus search treats the row as one unit, so focus enters at the
            // remembered button and moves within it with Left/Right.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusGroup()) {
                OutlinedButton(onClick = onAllowOnce) { Text(stringResource(R.string.policy_allow_once)) }
                OutlinedButton(onClick = onAlwaysAllow) { Text(stringResource(R.string.policy_always_allow)) }
                Button(onClick = onBlock, modifier = Modifier.focusRequester(blockFocus)) {
                    Text(stringResource(R.string.policy_block))
                }
            }
        }
    }
}
