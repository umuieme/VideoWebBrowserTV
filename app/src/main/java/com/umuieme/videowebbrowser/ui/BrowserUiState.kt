package com.umuieme.videowebbrowser.ui

import com.umuieme.videowebbrowser.browser.UrlError

/** Which screen the app shows. Exactly one of these at a time; AppRoot switches on it. */
sealed interface BrowserUiState {
    /** Settings are still being read from disk (a few milliseconds at startup). */
    data object Loading : BrowserUiState

    /** No home URL saved yet. [error] is set after an invalid submission. */
    data class NeedsSetup(val error: UrlError? = null) : BrowserUiState

    /** A home URL is saved; show it in the WebView. */
    data class Browsing(val url: String) : BrowserUiState
}
