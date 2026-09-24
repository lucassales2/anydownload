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

- [ ] Wasm browser test passes; JVM engine tests pass.
- [ ] Real Chromium run recorded with versions; the service worker made only network requests, no solver execution (assert the bridge saw no `evaluate` message).
- [ ] CSP documented; no remote script origin.
- [ ] Missing runtime in an unsupported browser falls back to stage 1 with the hidden-format message.

## Evidence / notes

Not started.
