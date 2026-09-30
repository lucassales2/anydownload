---
id: T-017
type: task
priority: P1
milestone: D14
tags: [task, configuration, security]
---

# T-017 — Global options, presets and safe overrides

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-19/F-20 configurable options with an approved safety boundary, also supporting F-15 embedding settings.

## Dependencies

- [T-016](T-016-Clips-chapters-SponsorBlock.md). Phase: [Phase 14](../00-project/Phase-14-Options-archive.md). Estimate 8 engineer-days.

## Acceptance criteria

- [x] Define approved typed/API option mappings; test global environment/file precedence, ordered presets, overrides and null clearing.
- [x] Support watched config updates with validation and immutable effective options per attempt; invalid reloads preserve known-good configuration.
- [x] Cover approved rate/proxy/archive/metadata/subtitle/postprocessing controls without exposing executable hooks or arbitrary paths/network overrides.
- [x] Enforce safety after option merging so no layer/null value can disable mandatory protections.
- [x] Record approved parity differences and tests for unknown/unsafe options and conflicting presets.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D14 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). Q-09 is the allowlist. Free-form JSON stays disabled. Shell execution stays out. This phase also covers output templates, the download-archive file, proxy, rate limit, and sleep. Done 2026-09-30.

**Iteration 1 — typed preset overlay and per-attempt safety (2026-09-30).** `PresetOverlay` in common code is now the one resolver for the desktop CLI and the Kotlin engine: selected presets overlay in Settings order, a later preset overrides an earlier one on the same key, the explicit form value wins, a blank later value clears a nullable field, and only the typed allowlist (`PresetOptionKeys.typed`) is accepted — unknown keys such as `proxy`, `exec`, or free-form JSON are dropped. The safety pass after merging always empties `customYtDlpJson` and clears an escaping destination folder; invalid clip/playlist values stay for the engine's typed validation so the job fails visibly. The engine computes the effective options once per attempt (`effectiveOptions`) and passes that immutable set down the whole attempt. `PresetLayering` (desktop) now delegates to the shared overlay.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest --rerun-tasks` — 709 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:test --tests "com.anydownlod.desktop.engine.PresetLayeringTest" --tests "com.anydownlod.desktop.engine.YtDlpArgumentsTest"` — pass.
- `:shared:core` wasm/iOS main+test and Android/desktop compiles — clean.

Next: proxy/rate-limit/sleep controls, the download-archive file beside app history, settings pinned-tag/Kotlin-build display, watched config reload with last-known-good, E-22/E-25/E-26 rows, and final verification.

**Iteration 2 — download archive and engine identity (2026-09-30).** `DownloadArchive` (`ArchiveEntry` as `<extractor> <id>`, `NoDownloadArchive` default) was added; the engine checks the archive after extraction and completes an already-downloaded source as `archived` without a request, then records the entry on a successful completion (media and chapter split). `EngineBuild` exposes the pinned yt-dlp tag `2026.08.19` and the running Kotlin version; the Settings Tools row now shows both (“Kotlin engine: yt-dlp pin 2026.08.19 · Kotlin <build>”) and there is no self-update.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 711, ui 105, desktop 145 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Next: proxy/rate-limit/sleep controls (E-22), the host file-backed archive beside app history (E-25), watched config reload keeping last-known-good, then the final T-017 verification and Phase 14 done.

**Iteration 3 — network controls, archive file, reload safety, verification (2026-09-30).** `AppSettings` gained global `proxyUrl`, `rateLimitKibPerSecond`, `sleepRequestsSeconds`, and `sleepIntervalSeconds`; the engine reads them once per attempt and honors the rate limit (chunk pacing), the per-request sleep (media hops and fragment hops), and the sleep interval before an attempt. A per-job proxy string stays impossible: `DownloadOptions` has no proxy field and presets cannot set one (E-22). `FileDownloadArchive` writes the `<extractor> <id>` lines beside desktop app state and is wired into the desktop graph (E-25). `DesktopStore.load` now keeps the last known-good settings when the settings document is unreadable, with a warning, instead of reverting to defaults. E-22, E-25, and E-26 record the landed scope.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 716, ui 105, desktop 146 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Limits recorded: the global proxy is a Settings field and is mapped by the desktop CLI; the Kotlin transfer adapters do not read it yet (partial, E-22); Android and iOS file-backed archives are not wired yet (partial, E-25); output templates remain the D8 safe-title default in the Kotlin engine, with the template settings used by the desktop CLI.
