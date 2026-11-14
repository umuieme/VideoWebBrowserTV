package com.umuieme.videowebbrowser.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * App-wide Compose for TV theme. TV UIs are dark by convention (less glare in a dim room),
 * so there is no light scheme.
 */
@Composable
fun VideoWebBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
