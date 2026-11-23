package com.umuieme.videowebbrowser.ui

import com.umuieme.videowebbrowser.browser.UrlError

/** Which screen the app shows. Exactly one of these at a time; AppRoot switches on it. */
sealed interface BrowserUiState {
    /** Settings are still being read from disk (a few milliseconds at startup). */
    data object Loading : BrowserUiState

    /**
     * Ask for the site URL: on first run, or when the user chose "Change site".
     *
     * @param error set after an invalid submission.
     * @param currentUrl the saved URL when changing site (prefills the field), null on first run.
     *   Non-null also means Back can return to the browser.
     */
    data class NeedsSetup(
        val error: UrlError? = null,
        val currentUrl: String? = null,
    ) : BrowserUiState {
        val canCancel: Boolean get() = currentUrl != null
    }

    /** A home URL is saved; show it in the WebView. */
    data class Browsing(val url: String) : BrowserUiState
}
