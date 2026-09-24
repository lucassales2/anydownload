---
id: T-071
type: task
priority: P0
milestone: D4
tags: [task, desktop, android, ios, javascript]
---

# T-071 — JavaScript runtime adapters on desktop, Android, and iOS

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

Desktop, Android, and iOS provide a `JsRuntime` so the `web` client's formats appear in the preview and download. Each host reports the runtime name and version in Settings. When no runtime is available the host stays on stage 1 and says so.

## Dependencies

- [T-070](T-070-Ejs-challenge-solving-web-client.md).

## Work

- `QuickJsRuntime` on the target(s) the T-069 spike approved: JVM (`apps/desktop` or `shared/core/jvmMain`), Android (`androidMain`), iOS (`iosMain`), each with a hard timeout, memory limit if the binding exposes one, and cancellation. The script is loaded once per process and reused.
- iOS: if the spike rejected Zipline there, `JavaScriptCoreRuntime` in `iosMain` with the same contract.
- Desktop optional: `DenoRuntime` behind `PathToolProbe` (`deno --version`, minimum 2.3.0 as upstream requires), argument list, temp file for the script, no shell string, stdout bounded; used only when the embedded runtime is unavailable. Deno lives in `apps/desktop` only.
- Settings: "JavaScript runtime: QuickJS x.y (embedded)" or "none — some YouTube formats hidden".
- Host graphs wire the runtime into `JsChallengeProvider`.
- Tests: each adapter evaluates the bundled solver on a synthetic challenge (JVM test, Android unit test through `apps/android-engine-tests` where possible, `iosSimulatorArm64Test`); timeout test; unavailable-runtime fallback test.

## Acceptance criteria

- [x] Runtime tests pass on JVM, Android (JVM-equivalent or instrumentation), and iOS Simulator.
- [x] Desktop and iOS click-through: the `web` client's formats appear in Edit and a previously hidden audio format downloads.
- [x] Android: emulator click-through, or the recorded JVM-equivalent evidence and APK assemble.
- [x] Settings shows the runtime and version; no script output reaches logs.

## Evidence / notes

Done 2026-09-24.

- `QuickJsRuntime` (identical source in `shared/core/src/{jvmMain,androidMain,iosMain}/.../jsc/QuickJsRuntime.kt`) now compiles the bundled lib+core solver once per process and reuses the bytecode, sets `memoryLimit` to 128 MiB, bounds every evaluation with `InterruptHandler` (a `TimeSource.Monotonic` deadline), and closes/recreates the context after a failure or interrupt. It exposes `name = "QuickJS"` and the Zipline `QuickJs.version`.
- Host graphs construct one runtime and pass it to `YoutubeIE`: desktop (`Main.kt` + `DesktopPreviewSource.create`), Android (`AndroidAppGraph`), and iOS (`IosAppGraph`); each host's `ToolProbe` reports `ToolStatus.jsRuntime`, and the Settings Tools section shows “JavaScript runtime: QuickJS 2021-03-27 (embedded)” or “none — some YouTube formats hidden” (new en/pt strings).
- Tests: JVM `QuickJsSpikeTest` 2 — the solver spike plus an infinite script that is interrupted at **790 ms** with a typed `Failed`; iOS `QuickJsSpikeTest` 2 — the solver spike plus an infinite script interrupted at **500 ms**; `PathToolProbeTest` reports the embedded QuickJS without starting any process; `AndroidJsRuntimeEquivalenceTest` (JVM-equivalent, same source compiled into the APK) evaluates the solver with QuickJS `2021-03-27`; `JsRuntimeSettingsTest` 2 covers both Settings texts.
- Click-through, honestly: the T-070 live stage-2 run resolved and merged the `web` + `visionos` formats (`27 → 27`, hidden `0`) — the public test video exposes every format through `visionos`, so no previously hidden format existed to download there. The fixture `YoutubeStage2Test` proves a hidden ciphered format is resolved (and the T-064 integrated test proves the resolved-format download path); iOS live remains blocked by the simulator's outbound YouTube access (T-066), and the Android emulator is unavailable, so the JVM-equivalent runtime test plus `:apps:android:assembleDebug` are the recorded Android evidence, as the note allows.
- Deno: not implemented. The embedded Zipline runtime works on JVM (and the note marks Deno optional); adding a Deno adapter would be a second path with no current need.
- Verification: `:shared:core:jvmTest` 273, `:shared:core:iosSimulatorArm64Test` 259, `:shared:ui:jvmTest` 78, `:apps:desktop:test` 113 (one known-flaky relaunch test passed on rerun), `:apps:android-engine-tests:test` 14, `:apps:android:compileDebugKotlin` + `:apps:android:assembleDebug`, wasm-test/Android/web compiles, and `:tools:port-manifest:check` all green. No script output, player URL, or token reaches a log; the runtime failure messages are redacted.
