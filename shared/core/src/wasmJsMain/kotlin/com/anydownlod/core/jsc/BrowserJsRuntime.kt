/*
 * Browser JavaScript runtime — AnyDownload (T-072)
 *
 * The Compose/Wasm page runs the bundled yt-dlp-ejs solver in its own
 * JavaScript. The solver runs in a dedicated Worker built from a Blob URL so a
 * slow solve cannot block the UI, the page CSP does not need `unsafe-eval`,
 * and a timeout terminates the worker. The extension still performs every
 * network request; it never runs the solver.
 */
package com.anydownlod.core.jsc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.js.JsAny
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BrowserJsRuntime : JsRuntime {

    override val available: Boolean = true
    override val name: String = "browser"

    override suspend fun evaluate(
        script: String,
        entry: String,
        input: String,
        timeout: Duration,
    ): JsResult {
        val source = workerSource(script, entry)
        return try {
            withTimeout(timeout) {
                suspendCancellableCoroutine { continuation ->
                    val worker = createWorker(source)
                    continuation.invokeOnCancellation { terminateWorker(worker) }
                    onWorkerMessage(worker) { data ->
                        if (continuation.isActive) {
                            terminateWorker(worker)
                            continuation.resume(parse(data))
                        }
                    }
                    postWorkerMessage(worker, input)
                }
            }
        } catch (_: TimeoutCancellationException) {
            JsResult.Failed("The JavaScript runtime timed out.")
        } catch (error: Throwable) {
            JsResult.Failed(
                "The JavaScript runtime failed: " +
                    (error.message?.take(120) ?: error::class.simpleName),
            )
        }
    }

    /** The Worker program: the solver script plus the message handler. */
    internal fun workerSource(script: String, entry: String): String = buildString {
        append(script)
        append('\n')
        append(
            """
            self.onmessage = (event) => {
              try {
                const result = $entry(JSON.parse(event.data));
                self.postMessage(JSON.stringify(result));
              } catch (error) {
                self.postMessage(JSON.stringify({ type: 'error', error: String((error && error.message) || error) }));
              }
            };
            """.trimIndent(),
        )
    }

    private fun parse(data: String): JsResult = runCatching {
        val element = Json.parseToJsonElement(data)
        val objectValue = element as? JsonObject
        if (objectValue != null && objectValue["type"]?.jsonPrimitive?.contentOrNull == "error") {
            JsResult.Failed("The challenge solver reported an error.")
        } else {
            JsResult.Ok(data)
        }
    }.getOrElse { JsResult.Failed("The challenge solver returned an unusable result.") }

    /**
     * Test-only page hook (T-072 CDP run): installs `window.__anydownloadSolve`
     * as a Promise-returning wrapper around the real Kotlin runtime. The hook
     * is installed only when the page is opened with `?solverHook=1`.
     */
    fun installSolverHook(scope: CoroutineScope) {
        installSolveHook { input, resolve ->
            scope.launch {
                val script = EjsScripts.lib + "\nObject.assign(globalThis, lib);\n" + EjsScripts.core
                val result = evaluate(script, "jsc", input, 60.seconds)
                resolve(
                    when (result) {
                        is JsResult.Ok -> result.json
                        is JsResult.Failed -> """{"type":"error","error":"runtime"}"""
                    },
                )
            }
        }
    }
}

@JsFun(
    """(source) => {
        const blob = new Blob([source], { type: 'text/javascript' });
        const url = URL.createObjectURL(blob);
        return new Worker(url);
    }""",
)
private external fun createWorker(source: String): JsAny

@JsFun("(worker, input) => worker.postMessage(input)")
private external fun postWorkerMessage(worker: JsAny, input: String)

@JsFun(
    """(worker, callback) => {
        worker.onmessage = (event) => callback(event.data);
        worker.onerror = () => callback(JSON.stringify({ type: 'error', error: 'worker' }));
    }""",
)
private external fun onWorkerMessage(worker: JsAny, callback: (String) -> Unit)

@JsFun("(worker) => worker.terminate()")
private external fun terminateWorker(worker: JsAny)

@JsFun("""(solve) => { window.__anydownloadSolve = (input) => new Promise((resolve) => solve(input, resolve)); }""")
private external fun installSolveHook(solve: (String, (String) -> Unit) -> Unit)
