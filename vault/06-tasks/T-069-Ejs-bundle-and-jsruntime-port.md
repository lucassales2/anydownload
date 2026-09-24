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

- [x] Build fails on a tampered script (test flips one byte in a temp copy and runs the verifier).
- [x] `EjsScripts.VERSION == "0.8.0"` and hashes match upstream values exactly.
- [x] Spike results table for JVM, Android, iOS Simulator with QuickJS version and timings; decision written for T-071.
- [x] Licensing rows and NOTICE updated; no runtime download code.

## Evidence / notes

Done 2026-09-24.

- Bundle: `third_party/yt-dlp-ejs/0.8.0/` holds `yt.solver.core.js`, `yt.solver.core.min.js`, `yt.solver.lib.js`, `yt.solver.lib.min.js`, the EJS `UNLICENSE`, and `HASHES.sha512` with the four upstream hashes copied from `yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py` at tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`). Upstream hashes with **SHA3-512** (`jsc/_builtin/ejs.py` uses `hashlib.sha3_512`), not SHA-512; the file name follows the task note and the header records the algorithm. The two NPM wrapper variants (deno/bun) are not bundled; their upstream hashes are listed for reference.
- Build: `:shared:core:verifyEjsBundle` recomputes SHA3-512 for every listed file and fails on a missing or tampered script; `:shared:core:generateEjsScripts` emits `EjsScripts.kt` (`VERSION`, `core`, `lib` from the verified minified pair) into a generated common source set, and every Kotlin compilation depends on it. There is no runtime download path.
- Port: `com.anydownlod.core.jsc.JsRuntime` with `JsResult.Ok(json)`/`JsResult.Failed(reason)` and the `NoJsRuntime` stage-1 fallback. `QuickJsRuntime` (Zipline `app.cash.zipline:zipline:1.27.0`, Apache-2.0 + QuickJS MIT) implements the port for JVM, Android, and iOS; it evaluates the script, calls the requested entry point with the JSON input, and returns the JSON result.
- Tests: `EjsBundleVerifierTest` 3 (bundled files match the upstream hashes; the generated constants match the upstream minified hashes; a one-byte tamper in a temp copy is detected), `JsRuntimeTest` 1 (`NoJsRuntime` never pretends), `QuickJsSpikeTest` 1 on JVM and 1 on iOS Simulator.

Spike results (synthetic input shaped like upstream's stdin protocol: `{"type":"player","player":"","requests":[{"type":"n","challenges":["fixture"]}],"output_preprocessed":true}`; the empty player means the solver cannot solve, which still proves execution):

| Target | QuickJS version | Wall time | Memory | Result |
| --- | --- | --- | --- | --- |
| JVM (`:shared:core:jvmTest`) | `2021-03-27` (Zipline 1.27.0) | 506 ms | 93,735 bytes used, unlimited limit | lib + core evaluated; the entry call throws `not a function` for the empty player (runtime OK) |
| Android (`:apps:android:compileDebugKotlin`) | same Zipline binding | not run — no AVD/device in this environment | — | adapter compiles; run the spike on a device in T-071 |
| iOS Simulator (`:shared:core:iosSimulatorArm64Test`) | `2021-03-27` | 60 ms | — | lib + core evaluated; same synthetic input throws (runtime OK) |

Decision for T-071: use Zipline QuickJS on JVM, Android, and iOS (JVM and iOS spikes pass; Android compiles the same API). Zipline's QuickJS reports `2021-03-27`, older than the `2025-04-26` / quickjs-ng `0.12.0` that yt-dlp-ejs recommends for speed (R-16); if solver wall time becomes a problem, evaluate a newer QuickJS binding or JavaScriptCore for iOS at T-071. The current `withTimeout` is best-effort around a blocking native call; T-071 should use `QuickJs.interruptHandler` (or a dedicated worker) for a hard bound.

Licensing: `vault/04-delivery/Security-and-licensing.md` rows for yt-dlp-ejs (SHA3-512 correction, bundle provenance, build verification) and Zipline (spike result, version gap, notices) updated; `shared/core/NOTICE.md` records both. No runtime download code exists. No manifest change (third-party bundle, not a translated yt-dlp module).
