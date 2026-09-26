---
id: T-077
type: task
priority: P0
milestone: D5
tags: [task, engine, kmp, toolkit]
---

# T-077 — Engine executes a merge

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

When the host can merge, a video download selects the best video stream plus the best audio stream, downloads both to temp files, and publishes one artifact. When the host cannot merge, selection stays the best single file, as in D4. Covers the execution half of E-05.

## Dependencies

- [T-075](T-075-Media-toolkit-contract.md) — capabilities and the merge call.
- [T-076](T-076-Desktop-ffmpeg.md) — the first real toolkit, used by the desktop test.

## Context the next session needs

`OptionsToSpec.compileVideo` returns `CompiledSpec.SingleFile` with spec `b` (or a height/codec filter on `b`). `FormatSelector` already evaluates `bv*+ba/b` to `Selection.Merge`. `HttpDownloadEngine.extractAndDownload` downloads one format URL. Keep that single-file branch. The compiler still must not emit more than one `+`, and must not emit a merge for audio-only options.

## Work

- When `ToolkitCapabilities.canMerge` is true, video options compile to `bv*+ba/b` with the existing profile and codec filters applied to the video side, and the single-file spec as the fallback after `/`. When `canMerge` is false, keep today's single-file spec.
- On `Selection.Merge`, download each side with the existing direct-file path into temps (ranges, headers, and chunk size from that format). Call `MediaToolkit.merge`. Publish one artifact whose extension is the container the toolkit wrote. Delete temps on success, failure, and cancel.
- Progress phase is `merging` with unknown percent. Incompatible codecs and a missing toolkit fail `UNSUPPORTED_FORMAT` and leave no artifact.
- Unit-test the compiler both ways, and an engine test with a fake toolkit that concatenates two local fixture bodies into one file. Desktop test uses `DesktopFfmpegToolkit` on a local fixture, not a live site.

## Acceptance criteria

- [x] Compiler emits one merge only when `canMerge` is true, and the D4 single-file spec otherwise.
- [x] Engine publishes one artifact from two temps and deletes temps on cancel.
- [x] A host with `canMerge = false` still downloads a single file and never calls `merge`.
- [x] D2/D3/D4 single-file and generic tests stay green.

## Evidence / notes

Done on 2026-09-25.

Changed:

- `shared/core/.../format/OptionsToSpec.kt` — `compile(options, canMerge = false)`. When `canMerge` is true, video options compile to one merge (`bv*+ba/b`, `wv*+wa/w`, or `bv*[height<=N]+ba/b[height<=N]`) with the profile filters on the video side and the D4 single-file spec after `/`; audio options are unchanged and never merge.
- `shared/core/.../engine/HttpDownloadEngine.kt` — new `toolkit: MediaToolkit = UnavailableToolkit` constructor parameter; `FormatResolution.Merge`; `downloadAndMerge` downloads each side through the same direct-file path into temps, sets the `merging` phase (unknown percent), merges into a `mp4` temp created with `createTempFile("mp4")`, and publishes one artifact. Temps are discarded on success, failure, and cancel. `toolkitJobError` maps `ToolUnavailable`/`IncompatibleStreams` to `UNSUPPORTED_FORMAT` and `Io` to retryable `POSTPROCESSING_FAILURE`.
- `shared/core/.../platform/FileStore.kt` — `createTempFile(extension)` (default keeps the old name) and `mediaFilePath(temp)` (default throws); JVM, Android, and iOS stores implement both, and `DesktopFileStore` delegates.
- `shared/core/.../engine/WebExtensionEngine.kt` — the `Merge` branch fails typed with the web-gap message.

Tests added/updated:

- `OptionsToSpecTest` — merge compilation for best/worst/height/MP4 and the split-pair selection; the old no-merge sweep still covers `canMerge = false`.
- `EngineExtractionTest` — `resolveSelection` both ways and one `+` only when capable.
- `EngineMergeTest` (new, common) — 6 tests: two temps become one artifact, a non-merge host never calls `merge`, incompatible/missing-tool/Io failures are typed with no artifact, and cancel deletes both temps and the destination.
- `DesktopEngineMergeTest` (new) — the real `DesktopFfmpegToolkit` merges a generated local split pair through the engine; `ffprobe` shows one H.264 video and one AAC audio stream and no temp survives.
- `FfmpegFixtures` — shared local lavfi fixture/ffprobe helpers; `DesktopFfmpegToolkitTest` uses them.

Commands run:

- `./gradlew :shared:core:jvmTest` — 311 tests, 0 failures, 0 errors (was 303 before this task).
- `./gradlew :shared:core:iosSimulatorArm64Test` — 293 tests, 0 failures.
- `./gradlew :apps:desktop:test` — 120 tests, 13 skipped (existing opt-in live/oracle tests), 0 failures.
- `./gradlew :apps:android-engine-tests:test :shared:ui:jvmTest :apps:android:assembleDebug` — BUILD SUCCESSFUL; 14 + 78 tests.
- `./gradlew :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:compileAndroidMain :apps:web:wasmJsMainClasses :apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `node --test apps/web-extension/test/bridge.test.mjs` — 22 pass, 0 fail.

The desktop app wiring (toolkit in `DesktopApp.open`/`AppGraph`) is left to T-078/T-079, which need the capabilities for Edit and the click-through. `:apps:android:testDebugUnitTest` still fails on a pre-existing `kotlin.test.Test` classpath resolution in that source set; D4's approved Android commands (`:apps:android-engine-tests:test`, `:apps:android:assembleDebug`) pass.
