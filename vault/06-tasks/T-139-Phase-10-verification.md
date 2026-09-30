---
id: T-139
type: task
priority: P0
milestone: D10
tags: [task, verification]
---

# T-139 — Phase 10 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

## Outcome

D10's claims are verified on every host, E-03, E-05, E-07, and E-10 say what actually landed, and [T-123](T-123-Shared-downloader.md) — the phase card — is Done.

## Dependencies

- [T-138](T-138-Generic-hls-dash.md) — Done 2026-09-29.

## Work

- Run the D10 verification:

  ```
  CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest :apps:android-engine-tests:test :tools:port-manifest:check
  ```

  If the desktop FFmpeg tests are load-sensitive in a combined run, run `:apps:desktop:cleanTest :apps:desktop:test` alone as T-112 and T-120 did.
- Update E-03, E-05, E-07, and E-10 in [Ytdlp-equivalence](../01-product/Ytdlp-equivalence.md) to the scope that actually landed. Regenerate the manifest coverage block if any scope text changed (`:tools:port-manifest:run`) and re-check it.
- Check only the [T-123](T-123-Shared-downloader.md) acceptance boxes that actually landed, record what did not land in that note, and move T-123 to Done.
- Set [Phase 10](../00-project/Phase-10-Shared-downloader.md) to `status: done` with a short verification section naming the commands, counts, and limits.
- Do not move any other card and do not commit.

## Acceptance criteria

- [x] The JVM, wasm browser, and iOS simulator suites pass, the manifest check is green, and the commands and counts are in Evidence.
- [x] E-03, E-05, E-07, and E-10 match the landed scope: no shipped feature marked later and no claim without a test.
- [x] T-123's boxes are checked only where true, its note says what did not land, and the card is Done.
- [x] The Phase 10 note is Done and names the limits (no cross-attempt resume, no new site, the CLI and Chaquopy fall-through still exist).
- [x] No commit was made.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Commands:** `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest :apps:android-engine-tests:test :tools:port-manifest:check` → BUILD SUCCESSFUL in 1m 1s. Counts: core JVM 577, core wasm 511, core iOS 522, UI iOS 8, web wasm 6, Android engine 26 — all 0 failures. `./gradlew --console=plain :apps:desktop:cleanTest :apps:desktop:test` alone → BUILD SUCCESSFUL, 132 tests, 0 failures, 15 skipped (the combined-run FFmpeg tagging flake documented in T-112/T-120 reproduces under load and is clean alone). Manifest: `port-manifest: ok — 1,751 upstream classes, 0 ported, 5 partial, 0 planned, 1,746 not started`.
- **Equivalence:** E-03 (new `js_to_json`/`determine_ext`/JSON-LD helpers named), E-05 (`,`, `all`, `mergeall`, `+`, groups; multi-stream execution still refused), E-07 (same-attempt resume, bounded, typed 200/mismatch refusal, relaunch unchanged), and E-10 (bounded concurrency, skip-unavailable, ordered writes) now say what actually landed. No shipped feature is marked later and no row claims a feature without a test.
- **T-123:** all six boxes checked only where true; its note records the two honest gaps (multi-stream execution and the `GenericIE` registry / refresh hop), and the card is Done.
- **Phase 10 note:** `status: done` with the verification commands, counts, and the limits section; the manifest coverage block was already regenerated after T-138 and `:tools:port-manifest:run`/`check` stay green.
- No other card was moved; no commit was made.
