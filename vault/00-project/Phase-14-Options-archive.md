---
type: phase
status: done
milestone: D14
tags: [project, engine, options]
---

# Phase 14 — Options, names, archive, network

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30. 8 engineer-days planned.** Depends on D13.

## Done looks like

Typed presets overlay in order, and the options for one attempt are immutable. The output-template language (`prepare_filename`) is confined to the download root; the D8 safe-title name stays the default. A download-archive file sits beside app history (E-25). Proxy, rate limit, and sleep exist (E-22). A per-job free-form proxy string is rejected (Q-09). Settings show the pinned tag and the Kotlin build. There is no self-update (E-26).

## Task

[T-017](../06-tasks/T-017-Options-and-presets.md).

## Verification

Landed 2026-09-30 with T-017: the typed `PresetOverlay` resolves ordered presets with form-wins and blank clearing, drops unknown keys, and runs a post-merge safety pass; the engine computes immutable effective options once per attempt; `DownloadArchive` skips repeats and records successes with a desktop file-backed store; global rate-limit and sleep settings are honored by the engine; the pinned yt-dlp tag and Kotlin build show in Settings with no self-update; an invalid settings reload keeps the last known-good configuration. E-22, E-25, and E-26 record the scope.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL; core 716, ui 105, desktop 146 (15 live-only skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL.

Limits: the global proxy is mapped by the desktop CLI but not yet read by the Kotlin transfer adapters; Android and iOS file-backed archives are not wired; output templates stay the D8 safe-title default in the Kotlin engine; free-form yt-dlp JSON, shell/exec hooks, plugins, and arbitrary paths stay disabled.
