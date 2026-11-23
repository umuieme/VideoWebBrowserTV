package com.umuieme.videowebbrowser

import android.app.Application

/**
 * Custom Application, registered in AndroidManifest.xml. Android creates it once per process,
 * before any Activity, which makes it the natural owner of the [AppContainer].
 */
class VideoWebBrowserApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Start parsing the ad list now, in the background, so it's usually ready before the
        // first page load issues its requests.
        container.loadAdBlocklist()
    }
}
