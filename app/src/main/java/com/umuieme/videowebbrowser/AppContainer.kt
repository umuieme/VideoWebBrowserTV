package com.umuieme.videowebbrowser

import android.content.Context
import com.umuieme.videowebbrowser.data.SettingsRepository

/**
 * Manual dependency injection: the one place that knows how to build app-wide objects.
 * Created once by [VideoWebBrowserApp]; anything that needs a dependency asks the container
 * instead of constructing it, so every caller shares the same instance.
 */
class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }
}
