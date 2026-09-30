package com.anydownlod.android

/**
 * The Android host's async cookie-file pick (T-018).
 *
 * `MainActivity` registers the system document picker, copies the chosen
 * document into the app cache, and answers this bridge with the copy's path.
 * The pure `AndroidCookieStore` then validates and moves it into app-private
 * state, removing the cache copy. The default returns null, so a graph built
 * without an activity (or a test) imports nothing.
 */
class AndroidCookiePickerBridge(val pick: suspend () -> String?) {
    companion object {
        val Unavailable = AndroidCookiePickerBridge { null }
    }
}
