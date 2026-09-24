package com.anydownlod.core.platform

/**
 * A tiny cross-platform critical section for the shared engine's mutable job
 * bookkeeping.
 *
 * - JVM / Android: a real JVM `synchronized` block.
 * - Native / Wasm: the block runs directly. Those hosts wire a single-threaded
 *   (main-confined or cooperative) [kotlinx.coroutines.CoroutineScope], which
 *   matches Kotlin/Native and web execution models; the JVM desktop host is
 *   the only target that shares state across threads today.
 *
 * Non-local returns are not allowed inside [block] (this helper is not
 * inline), so callers use labeled returns where needed.
 */
expect fun <T> engineCriticalSection(lock: Any, block: () -> T): T