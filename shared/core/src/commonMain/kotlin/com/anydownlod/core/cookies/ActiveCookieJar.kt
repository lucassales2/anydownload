/*
 * Active cookie jar — AnyDownload (T-018)
 *
 * The engine resolves one jar snapshot per job and puts it on the coroutine
 * context. Extractor, media, and fragment requests then ask the same snapshot
 * for the header of their own URL, so a file replacement or deletion during
 * the job cannot change what an in-flight request sends, and an expired row is
 * skipped at the moment the request is built.
 *
 * The header is credential material. It travels only through the trusted
 * `HttpRequest.cookie` field, after the request-header allowlist; it is never
 * logged, persisted, or included in a job document.
 */
package com.anydownlod.core.cookies

import com.anydownlod.core.platform.HttpRequest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Loads the device's stored cookie file as a parsed jar for one job.
 *
 * Platform code owns the file read (Android/iOS/desktop). A null result means
 * no file is configured. A host that cannot hold cookies leaves this null, so
 * `useCookies` fails typed instead of pretending the request was opted in.
 */
fun interface CookieJarSource {
    suspend fun currentJar(): CookieJar?
}

/** One job's frozen jar snapshot and the wall-clock second it was resolved at. */
class ActiveCookieJar(
    val jar: CookieJar,
    val nowEpochSeconds: Long,
) : AbstractCoroutineContextElement(ActiveCookieJar) {
    companion object Key : CoroutineContext.Key<ActiveCookieJar>
}

/** The active job's Cookie header for [url], or null when no jar is active. */
suspend fun activeCookieHeader(url: String): String? {
    val active = coroutineContext[ActiveCookieJar] ?: return null
    return active.jar.headerFor(url, active.nowEpochSeconds)
}

/**
 * Returns this request with the active jar's Cookie header for its own URL.
 * When no jar is active the request is unchanged. Callers re-apply this after
 * a redirect, so a hop to another host never keeps the previous host's header.
 */
suspend fun HttpRequest.withActiveCookie(): HttpRequest {
    val active = coroutineContext[ActiveCookieJar] ?: return this
    return copy(cookie = active.jar.headerFor(url, active.nowEpochSeconds))
}
