package com.umuieme.videowebbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.umuieme.videowebbrowser.ui.HomeScreen
import com.umuieme.videowebbrowser.ui.theme.VideoWebBrowserTheme

/**
 * The app's single Activity. It only hosts Compose; screens live in the ui package.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoWebBrowserTheme {
                HomeScreen(versionName = BuildConfig.VERSION_NAME)
            }
        }
    }
}
