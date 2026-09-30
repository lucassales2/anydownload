---
type: phase
status: done
milestone: D12
tags: [project, engine, youtube]
---

# Phase 12 — YouTube done at the pin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30. 15 engineer-days planned.** Depends on D11, which is Done. The YouTube package at the pin is 36 files and 541 KB. D4 and D8 ported single video (visionos and web) and a playlist first page capped at 50.

## Done looks like

The 21 `Youtube*` classes in `port/upstream-extractors.json` are accounted for in the manifest at pin `2026.08.19`. Remaining innertube clients the pin uses, playlist continuations, channels, mixes, and `--playlist-items` are translated. PO tokens have a provider interface (E-13); formats that need a token stay dropped until a provider is configured. Subtitle tracks and chapter metadata sit on the info dict. File download of those tracks is D13. Live YouTube and live HLS playlists are in (E-21). The playlist cap of 50 stays the default; a higher cap requires an explicit user limit.

Harness cases cover the URL forms upstream tests at this pin. The desktop oracle still diffs normalized fields only. iOS live network may stay blocked in the simulator; the fixture path is the evidence, as in [T-074](../06-tasks/T-074-Phase-4-verification.md).

## Task

[T-124](../06-tasks/T-124-Youtube-done.md).

## Verification

Landed 2026-09-30 with T-124: all 21 `Youtube*` classes have rows in `port/manifest.json`; the visionos and web innertube clients, playlist continuations, `/channel/<UC id>` and `/@handle` tabs, `list=RD...` mixes, and `--playlist-items` are translated; E-13 has a `PoTokenProvider` interface (GVS-gated web formats are dropped until a provider is configured); subtitle tracks and chapter markers sit on the info dict (files are D13); live YouTube exposes its HLS manifest as an `m3u8_native` format and the engine follows live HLS until `#EXT-X-ENDLIST` or cancel. Out and typed: `/c/` and `/user/` (no `navigation/resolve_url`), feeds, the authed innertube clients, `--live-from-start`, DASH live, and SABR-only streams. The playlist default cap is 50; an explicit user limit may be higher. iOS live network stays a simulator gap; the fixture paths are the evidence.

Commands, 2026-09-30 (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL; core 650, ui 105, desktop 142 (15 live-only skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 9 partial, 14 planned.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL.
