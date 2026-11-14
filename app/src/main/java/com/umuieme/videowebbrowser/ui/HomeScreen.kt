package com.umuieme.videowebbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.umuieme.videowebbrowser.R
import com.umuieme.videowebbrowser.ui.theme.VideoWebBrowserTheme

/**
 * Milestone 1 placeholder screen: app name, version, and one focusable button used to
 * confirm that D-pad focus and the OK key work on a real TV.
 */
@Composable
fun HomeScreen(versionName: String, modifier: Modifier = Modifier) {
    val buttonFocus = remember { FocusRequester() }
    var pressCount by rememberSaveable { mutableIntStateOf(0) }

    // Nothing is focused when a screen first appears; move focus to the button so the
    // remote's OK key does something immediately.
    LaunchedEffect(Unit) { buttonFocus.requestFocus() }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.version_label, versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { pressCount++ },
                modifier = Modifier.focusRequester(buttonFocus),
            ) {
                Text(stringResource(R.string.focus_test_button))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (pressCount == 0) {
                    stringResource(R.string.focus_test_hint)
                } else {
                    pluralStringResource(R.plurals.focus_test_count, pressCount, pressCount)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeScreenPreview() {
    VideoWebBrowserTheme {
        HomeScreen(versionName = "0.1.0")
    }
}
