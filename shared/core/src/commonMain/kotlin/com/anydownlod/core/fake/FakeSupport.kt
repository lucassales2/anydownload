package com.anydownlod.core.fake

/**
 * Deterministic id source for the in-memory fakes. Each fake gets its own
 * counter so tests can compare ids without randomness.
 */
internal fun defaultIdGenerator(): () -> String {
    var counter = 0L
    return { "local-${++counter}" }
}

/** Best-effort host for display; null when the URL has no host. */
internal fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return null
    val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return host.ifEmpty { null }
}
