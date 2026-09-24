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

- [ ] Runtime tests pass on JVM, Android (JVM-equivalent or instrumentation), and iOS Simulator.
- [ ] Desktop and iOS click-through: the `web` client's formats appear in Edit and a previously hidden audio format downloads.
- [ ] Android: emulator click-through, or the recorded JVM-equivalent evidence and APK assemble.
- [ ] Settings shows the runtime and version; no script output reaches logs.

## Evidence / notes

Not started.
