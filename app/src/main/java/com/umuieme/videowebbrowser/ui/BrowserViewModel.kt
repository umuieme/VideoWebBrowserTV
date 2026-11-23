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
import com.umuieme.videowebbrowser.browser.policy.SitePermissions
import com.umuieme.videowebbrowser.data.SettingsRepository
import com.umuieme.videowebbrowser.input.InputTuning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The app's single ViewModel. Turns the saved home URL into a [BrowserUiState] and handles
 * setup submissions. It survives configuration changes; the Activity and Compose UI don't.
 */
class BrowserViewModel(private val settings: SettingsRepository) : ViewModel() {

    // Validation error from the last setup submission; it lives here so it isn't lost on recreation.
    private val setupError = MutableStateFlow<UrlError?>(null)

    // True while the user is changing the saved site ("Change site" on an error panel).
    // Kept here, not in Compose, so a configuration change doesn't drop them back into the
    // broken site mid-edit.
    private val changingSite = MutableStateFlow(false)

    val uiState: StateFlow<BrowserUiState> =
        combine(settings.homeUrl, setupError, changingSite) { homeUrl, error, changing ->
            when {
                homeUrl == null -> BrowserUiState.NeedsSetup(error)
                changing -> BrowserUiState.NeedsSetup(error, currentUrl = homeUrl)
                else -> BrowserUiState.Browsing(homeUrl)
            }
        }.stateIn(
            scope = viewModelScope,
            // Stop reading DataStore 5 s after the UI stops collecting (app in background);
            // the last value is kept, so returning to the app never flashes Loading again.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowserUiState.Loading,
        )

    // "Allow once" domains. In memory only: kept across configuration changes (the ViewModel
    // survives those), forgotten when the process ends. That is the "session".
    private val allowedOnce = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The user's per-site choices for the navigation policy. Started eagerly (not
     * WhileSubscribed) so the allowlist is already read from disk by the time the user clicks
     * the first link; it's a tiny set, so keeping it hot costs nothing.
     */
    val sitePermissions: StateFlow<SitePermissions> =
        combine(settings.allowedDomains, allowedOnce) { allowlist, once -> SitePermissions(allowlist, once) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SitePermissions())

    /**
     * The user answered "Allow once" ([always] = false) or "Always allow" for [domain]. The
     * domain is added to the session set in both cases, so it applies immediately even before
     * DataStore finishes writing.
     */
    fun allowSite(domain: String, always: Boolean) {
        allowedOnce.update { it + domain }
        if (always) viewModelScope.launch { settings.addAllowedDomain(domain) }
    }

    /**
     * Validates [input] and saves it. On success we don't change the state ourselves: the
     * repository's Flow emits the new URL and [uiState] becomes Browsing on its own.
     */
    fun submitUrl(input: String) {
        when (val result = UrlNormalizer.normalize(input)) {
            is UrlResult.Valid -> {
                setupError.value = null
                viewModelScope.launch {
                    settings.saveHomeUrl(result.url)
                    // After the save, so the browser never flashes the old URL. Also covers
                    // re-submitting the same URL, where DataStore emits nothing new.
                    changingSite.value = false
                }
            }
            is UrlResult.Invalid -> setupError.value = result.error
        }
    }

    /** Called when the user edits the URL, so a stale error doesn't stay on screen. */
    fun clearSetupError() {
        setupError.value = null
    }

    private val _menuHintVisible = MutableStateFlow(false)
    private var menuHintJob: Job? = null

    /**
     * True for a moment after a long Back press. Placeholder for the app menu (Milestone 7),
     * which will replace this with real "menu open" state.
     */
    val menuHintVisible: StateFlow<Boolean> = _menuHintVisible.asStateFlow()

    /** Hold Back detected. Shows the hint; a second request restarts its timer. */
    fun onMenuRequested() {
        menuHintJob?.cancel()
        _menuHintVisible.value = true
        menuHintJob = viewModelScope.launch {
            delay(InputTuning.MENU_HINT_VISIBLE_MS.milliseconds)
            _menuHintVisible.value = false
        }
    }

    /** Opens the setup screen prefilled with the saved URL, e.g. when that site won't load. */
    fun changeSite() {
        setupError.value = null
        changingSite.value = true
    }

    /** Back from "Change site": return to the browser with the saved URL unchanged. */
    fun cancelChangeSite() {
        setupError.value = null
        changingSite.value = false
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
