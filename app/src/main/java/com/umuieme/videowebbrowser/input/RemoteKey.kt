package com.umuieme.videowebbrowser.input

/**
 * The remote keys the app understands, independent of Android key codes. Only the arrows, OK
 * and Play/Pause exist on every remote (see CLAUDE.md); FastForward and Rewind are mapped for
 * remotes that have them. Back is deliberately absent: it is handled by OnBackPressedDispatcher.
 */
enum class RemoteKey { Up, Down, Left, Right, Ok, PlayPause, FastForward, Rewind }
