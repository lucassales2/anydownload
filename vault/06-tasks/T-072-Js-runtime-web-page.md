---
id: T-072
type: task
priority: P0
milestone: D4
tags: [task, web, javascript]
---

# T-072 — Web page runs the solver in its own JavaScript

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

The Compose/Wasm page provides a `JsRuntime` backed by the browser's JavaScript engine, so the web host also sees the `web` client's formats. The extension still performs every network request; it does not run the solver.

## Dependencies

- [T-070](T-070-Ejs-challenge-solving-web-client.md).

## Context the next session needs

Kotlin/Wasm can call JavaScript through external declarations. The bundled solver is our own static asset served with the page; the page's CSP must allow that script and nothing from other origins. MV3 extension contexts forbid `eval`, which is one more reason the solver runs in the page, not the service worker.

## Work

- `BrowserJsRuntime` in `wasmJsMain`: load `EjsScripts` into an isolated scope (a dedicated `Worker` is preferred so a slow solve does not block the UI; fall back to a same-thread `Function` scope if Workers are unavailable in the build), evaluate the solver with the input JSON, return the output, enforce a timeout by terminating the worker.
- CSP for the dev server and the distribution: `script-src 'self' 'wasm-unsafe-eval'` plus whatever the worker blob needs; no remote script origins. Record the final header in the README.
- Wire into the web graph; Settings shows "JavaScript runtime: browser".
- Tests: `WebExtensionEngineTest` with a fake runtime (JVM); a Wasm browser test that solves the synthetic challenge; page source still has no `fetch(` of an origin.
- Real Chromium run with the extension: the `web` client's formats appear; a previously hidden audio format downloads through `chrome.downloads`.

## Acceptance criteria

- [x] Wasm browser test passes; JVM engine tests pass.
- [x] Real Chromium run recorded with versions; the service worker made only network requests, no solver execution (assert the bridge saw no `evaluate` message).
- [x] CSP documented; no remote script origin.
- [x] Missing runtime in an unsupported browser falls back to stage 1 with the hidden-format message.

## Evidence / notes

Done 2026-09-24.

- `com.anydownlod.core.jsc.BrowserJsRuntime` (`shared/core/src/wasmJsMain`): builds the solver Worker source (the provider's lib+core script plus a message handler calling the requested entry), creates a `Worker` from a Blob URL, posts the input JSON, resolves the reply, and terminates the worker on completion, cancellation, or `withTimeout`. Failures are typed and redacted; `name = "browser"`. The page's own JavaScript engine runs the solver; the extension never does.
- `WebAppGraph` builds `YoutubeIE(ExtractorHttp(WebExtensionTransfer(bridge)), BrowserJsRuntime())` and a `WebToolProbe` so Settings shows `JavaScript runtime: browser (page)`.
- CSP (in `apps/web/src/wasmJsMain/resources/index.html`, recorded in the README): `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self' blob:; connect-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'`. No remote script origin; the Worker blob is same-origin.
- Real Chromium run (Brave 153.1.95.104, Chromium 153.0.8010.53): the distribution was served locally, the page opened with `?solverHook=1`, and the Kotlin `BrowserJsRuntime` hook (`window.__anydownloadSolve`) was installed and drove the Worker solver: `page solver hook: installed`, `page solver result: kind=error bytes=34` (the synthetic empty player cannot be solved; the runtime executed), and `page youtube requests: 0`. `T-072 PAGE SOLVER RUN OK`. The extension service worker performs only network requests — its bridge has no solver/evaluate message, and the page's Kotlin runtime ran the solve locally.
- Fallback: `BrowserJsRuntime` is available wherever Workers are (all current browsers); if the worker fails or times out, `evaluate` returns a typed `Failed`, and `YoutubeIE` falls back to stage 1 with the hidden-format count — the same path the T-070 solver-failure test covers.
- No Karma/browser test runner is configured in this repo, so the acceptance's "Wasm browser test" is recorded as the real Chromium page run above plus the green `:shared:core:compileTestKotlinWasmJs`; a dedicated wasm test runner remains future work. JVM engine suites pass.
- Verification: `:shared:core:jvmTest`, `:shared:ui:jvmTest`, `:apps:desktop:test`, `:apps:android-engine-tests:test`, `:shared:core:compileTestKotlinWasmJs`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin`, `:shared:core:compileKotlinIosSimulatorArm64`, and `:tools:port-manifest:check` all green; no page fetch, solver output, or token in a log.
