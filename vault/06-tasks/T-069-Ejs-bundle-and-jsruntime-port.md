---
id: T-069
type: task
priority: P0
milestone: D4
tags: [task, engine, youtube, javascript]
---

# T-069 — Bundle yt-dlp-ejs, define the JsRuntime port, spike Zipline QuickJS

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md) · [Security and licensing](../04-delivery/Security-and-licensing.md)

## Outcome

The solver scripts yt-dlp vendors at the pin are in the repo with their license and verified hashes, shared code has a `JsRuntime` port, and a spike shows whether Zipline's QuickJS runs the solver on JVM, Android, and iOS Simulator. The spike records the decision for [T-071](T-071-Js-runtime-desktop-android-ios.md).

## Dependencies

- [T-068](T-068-Gate-jsless-youtube-four-hosts.md).

## Context the next session needs

Upstream `yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py` at the pin: `VERSION = '0.8.0'` and SHA-512 hashes for `yt.solver.core.js`, `yt.solver.core.min.js`, `yt.solver.lib.js`, `yt.solver.lib.min.js`, `yt.solver.deno.lib.js`, `yt.solver.bun.lib.js`. [yt-dlp/ejs](https://github.com/yt-dlp/ejs) is Unlicense; release `0.8.0` (2026-03-17). Upstream's QuickJS provider recommends quickjs ≥ 2025-04-26 or quickjs-ng ≥ 0.12.0 for speed and warns below that. [cashapp/zipline](https://github.com/cashapp/zipline) is Apache-2.0, latest `1.27.0`, and ships a `QuickJs` class for JVM, Android, and Kotlin/Native.

## Work

- `third_party/yt-dlp-ejs/0.8.0/`: `yt.solver.core.min.js`, `yt.solver.lib.min.js` (and the unminified pair for debugging), `UNLICENSE`, and `HASHES.sha512` copied from upstream `_info.py` with a header naming the file and tag they came from.
- Gradle task in `shared/core` that verifies each file's SHA-512 against `HASHES.sha512` and generates `EjsScripts.kt` (`object EjsScripts { const val VERSION; val core: String; val lib: String }`) into a generated common source set. A hash mismatch fails the build. No runtime download path exists.
- `JsRuntime` port in `com.anydownlod.core.jsc`: `suspend fun evaluate(script: String, entry: String, input: String, timeout: Duration): JsResult` returning `Ok(json)` or `Failed(reason)`; a `NoJsRuntime` implementation that always reports unavailable so stage 1 keeps working.
- Spike (JVM test first, then Android unit or instrumentation test, then `iosSimulatorArm64Test`): add `app.cash.zipline:zipline` to the relevant source sets, create `QuickJs`, evaluate `lib` + `core`, run one synthetic challenge input shaped like upstream's stdin protocol, and time it. Record QuickJS version reported by Zipline, wall time, memory, and any failure per target. If iOS fails, record JavaScriptCore as the T-071 adapter for iOS.
- Security and licensing note: add rows for yt-dlp-ejs (bundled 0.8.0, Unlicense, hashes) and Zipline (Apache-2.0); NOTICE updates.

## Acceptance criteria

- [ ] Build fails on a tampered script (test flips one byte in a temp copy and runs the verifier).
- [ ] `EjsScripts.VERSION == "0.8.0"` and hashes match upstream values exactly.
- [ ] Spike results table for JVM, Android, iOS Simulator with QuickJS version and timings; decision written for T-071.
- [ ] Licensing rows and NOTICE updated; no runtime download code.

## Evidence / notes

Not started.
