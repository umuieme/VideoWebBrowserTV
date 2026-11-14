package com.umuieme.videowebbrowser.input

import android.view.KeyEvent

/** Something that wants first look at key events. Returns true if it consumed the event. */
fun interface KeyHandler {
    fun onKeyEvent(event: KeyEvent): Boolean
}

/**
 * Activity-scoped hand-off point between MainActivity.dispatchKeyEvent and whichever screen
 * currently wants raw remote keys. Screens register while they are shown (from a Compose
 * DisposableEffect) and unregister when they leave, so the Activity needs no knowledge of
 * screens and a screen that isn't shown can never steal keys.
 *
 * Handlers form a stack: the most recently registered one is asked first. That covers the
 * brief overlap while one screen leaves and another arrives, and lets an overlay (the app
 * menu, Milestone 6) sit on top of the browser later.
 *
 * Main thread only, like all key dispatch.
 */
class KeyRouter {

    private val handlers = ArrayList<KeyHandler>()

    fun register(handler: KeyHandler) {
        handlers.remove(handler)
        handlers.add(handler)
    }

    /** Removes only [handler]; another screen's handler is never affected. */
    fun unregister(handler: KeyHandler) {
        handlers.remove(handler)
    }

    /** Offers [event] to handlers, newest first. Returns true if one consumed it. */
    fun dispatch(event: KeyEvent): Boolean {
        // Iterate over a copy: a handler may register/unregister while handling the event.
        for (handler in handlers.asReversed().toList()) {
            if (handler.onKeyEvent(event)) return true
        }
        return false
    }
}
