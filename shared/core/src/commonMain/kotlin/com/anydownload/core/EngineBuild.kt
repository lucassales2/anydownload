/*
 * Engine build identity — AnyDownload (T-017, E-26)
 *
 * The app never self-updates. Settings show the pinned upstream yt-dlp tag
 * the Kotlin engine is translated from and the Kotlin build that runs it.
 * The pin is fixed by ADR-014 and the loop prompt; it is a constant, not a
 * runtime lookup.
 */
package com.anydownload.core

object EngineBuild {
    /** The upstream yt-dlp tag every translation records. */
    const val PINNED_TAG: String = "2026.08.19"

    /** The Kotlin build the engine is compiled with, for the Settings row. */
    val KOTLIN_VERSION: String = KotlinVersion.CURRENT.toString()
}
