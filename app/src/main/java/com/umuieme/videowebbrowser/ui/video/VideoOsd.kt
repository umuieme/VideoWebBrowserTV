package com.umuieme.videowebbrowser.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.browser.media.VideoState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Small, unobtrusive feedback at the bottom of the screen after play/pause/seek: the action and,
 * when known, current time / duration. It's plain drawing with no focusable or clickable
 * modifiers, so it can never take D-pad focus away from the WebView or full-screen view.
 *
 * @param video the active video's live state; the time updates as the agent reports.
 */
@Composable
fun VideoOsd(state: OsdState, video: VideoState?, modifier: Modifier = Modifier) {
    LaunchedEffect(state.showCount) {
        if (!state.visible) return@LaunchedEffect
        delay(OsdState.VISIBLE_MS.milliseconds)
        state.hide()
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(bottom = 48.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                state.event?.let { event ->
                    Text(
                        text = event.label(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
                video?.let { v ->
                    val current = MediaTimeFormat.format(v.currentTimeSec)
                    val duration = v.durationSec
                    Text(
                        text = if (duration != null) {
                            stringResource(R.string.osd_time, current, MediaTimeFormat.format(duration))
                        } else {
                            current
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OsdEvent.label(): String = when (this) {
    OsdEvent.Playing -> stringResource(R.string.osd_playing)
    OsdEvent.Paused -> stringResource(R.string.osd_paused)
    OsdEvent.PlayPause -> stringResource(R.string.osd_play_pause)
    is OsdEvent.Seek ->
        if (seconds >= 0) {
            stringResource(R.string.osd_seek_forward, seconds)
        } else {
            stringResource(R.string.osd_seek_back, -seconds)
        }
}
