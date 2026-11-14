package com.umuieme.videowebbrowser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.umuieme.videowebbrowser.VideoWebBrowserApp
import com.umuieme.videowebbrowser.browser.UrlError
import com.umuieme.videowebbrowser.browser.UrlNormalizer
import com.umuieme.videowebbrowser.browser.UrlResult
import com.umuieme.videowebbrowser.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app's single ViewModel. Turns the saved home URL into a [BrowserUiState] and handles
 * setup submissions. It survives configuration changes; the Activity and Compose UI don't.
 */
class BrowserViewModel(private val settings: SettingsRepository) : ViewModel() {

    // Validation error from the last setup submission; it lives here so it isn't lost on recreation.
    private val setupError = MutableStateFlow<UrlError?>(null)

    val uiState: StateFlow<BrowserUiState> =
        combine(settings.homeUrl, setupError) { homeUrl, error ->
            if (homeUrl == null) BrowserUiState.NeedsSetup(error) else BrowserUiState.Browsing(homeUrl)
        }.stateIn(
            scope = viewModelScope,
            // Stop reading DataStore 5 s after the UI stops collecting (app in background);
            // the last value is kept, so returning to the app never flashes Loading again.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowserUiState.Loading,
        )

    /**
     * Validates [input] and saves it. On success we don't change the state ourselves: the
     * repository's Flow emits the new URL and [uiState] becomes Browsing on its own.
     */
    fun submitUrl(input: String) {
        when (val result = UrlNormalizer.normalize(input)) {
            is UrlResult.Valid -> {
                setupError.value = null
                viewModelScope.launch { settings.saveHomeUrl(result.url) }
            }
            is UrlResult.Invalid -> setupError.value = result.error
        }
    }

    /** Called when the user edits the URL, so a stale error doesn't stay on screen. */
    fun clearSetupError() {
        setupError.value = null
    }

    companion object {
        /**
         * Tells ViewModelProvider how to build this ViewModel. The framework creates ViewModels
         * itself (so it can keep them across recreation), so constructor arguments must come
         * through a factory. Here the factory pulls them from the AppContainer.
         */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as VideoWebBrowserApp
                BrowserViewModel(app.container.settingsRepository)
            }
        }
    }
}
