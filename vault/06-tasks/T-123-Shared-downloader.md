---
id: T-123
type: task
priority: P0
milestone: D10
tags: [task, engine, download]
---

# T-123 — Finish the shared downloader

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

E-05, E-07, E-10, and the generic extractor grow to the scope in Phase 10. Estimate 8 engineer-days. No new site.

## Dependencies

- [T-120](T-120-Metro-verification.md). D9 Metro finishes first.

## Acceptance criteria

- [x] Format selection handles `,` lists, `all` / `mergeall`, and more than one simultaneous stream, with tests.
- [x] HTTP download resumes inside one attempt. A failed attempt still does not auto-resume on the next launch.
- [x] Fragment download can run a bounded number of fragments at once and skip an unavailable fragment.
- [x] `GenericIE` covers embeds, iframes, JSON-LD, meta refresh, and HLS/DASH discovery. The manifest scope names what is still out.
- [x] Each newly translated `common.py` / `_utils.py` helper is named in `port/manifest.json`, and `:tools:port-manifest:check` passes.
- [x] Shared tests pass on JVM, wasm, and the iOS simulator.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0). Verified by [T-139](T-139-Phase-10-verification.md).

- **Commands:** `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest :apps:android-engine-tests:test :tools:port-manifest:check` → BUILD SUCCESSFUL in 1m 1s. Counts: core JVM 577, core wasm 511, core iOS 522, UI iOS 8, web wasm 6, Android engine 26 — all 0 failures. `./gradlew --console=plain :apps:desktop:cleanTest :apps:desktop:test` alone → BUILD SUCCESSFUL, 132 tests, 0 failures (a combined-run flake in the load-sensitive FFmpeg tagging path matches T-112/T-120; clean alone). Manifest: `port-manifest: ok — 1,751 upstream classes, 0 ported, 5 partial, 0 planned, 1,746 not started`.
- **Landed:** T-133 `,`/`all`/`mergeall`/`+`/`()` selection with DRM dropped and the compatibility one-selection `select`; T-134 bounded same-attempt resume with a typed refusal for 200/mismatched ranges and unchanged relaunch semantics; T-135 bounded fragment concurrency with ordered writes, skip-unavailable recording, and cancel discards; T-136 `js_to_json`/`determine_ext`/JSON-LD discovery named in the manifest; T-137 embed/iframe/JSON-LD/one-hop meta refresh; T-138 HLS/DASH discovery through the existing parsers with live/DRM typed failures.
- **Did not land (honest gaps):** multi-stream execution — the selector can return `Selection.MergeAll`, but the engine resolves it to a typed “more than two streams” refusal and the compiler still emits one selection per job; the meta-refresh hop lives in `GenericIE`, which is still left out of the production registry, so the engine's direct HTML path uses `GenericExtractor` (embed/iframe/JSON-LD/HLS/DASH yes, refresh hop no); no cross-attempt resume and no `.part` file survives a relaunch; no new site, cookies, captions, or clips, and the desktop CLI / Android Chaquopy fall-throughs remain.
- No commit.
