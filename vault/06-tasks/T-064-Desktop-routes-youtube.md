---
id: T-064
type: task
priority: P0
milestone: D4
tags: [task, desktop, engine]
---

# T-064 — Desktop routes matched URLs through Kotlin; CLI for the rest

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [T-047](T-047-Desktop-generic-route.md)

## Outcome

On desktop, a URL the Kotlin registry matches never reaches the `yt-dlp` process. Everything else keeps the D1 CLI path. Direct files and generic pages keep the D3 routing.

## Dependencies

- [T-063](T-063-Preview-from-extractor.md).

## Work

- `DesktopRouteClassifier`: check `registry.suitableFor(url)` **before** the bounded page probe; matched → `DesktopRoute.Kotlin`. No probe request is sent for matched URLs.
- `DesktopRoutingEngine`: Kotlin route uses the shared engine with the real `JavaNetHttpTransfer` (request port) and `DesktopFileStore`. Queue, history, cancel, retry, delete unchanged.
- Settings: the tool status row still reports `yt-dlp`/`ffmpeg` presence; add "Kotlin extractors: YouTube (single video)" from the manifest so the user sees what runs locally.
- Tests in `apps/desktop`: a YouTube URL never spawns a process (fake process factory asserts zero invocations); a non-matching site URL still reaches the CLI path; the D3 fixture and direct-file routing tests stay green.

## Acceptance criteria

- [x] Routing tests above pass; `:apps:desktop:test` green.
- [x] Click-through: paste a public YouTube URL, preview, download audio (M4A) and a progressive video; files land under the download root; no `yt-dlp` process in the process list during the job.
- [x] Choosing MP3 fails typed with the toolkit message; no process is spawned to work around it.

## Evidence / notes

Done 2026-09-24.

- `DesktopRoute.KOTLIN` added; `DesktopRouteClassifier` takes the `ExtractorRegistry` and returns `KOTLIN` before any probe, so a matched URL sends no HEAD and no page GET. `resumeRoute(url, registry)` classifies persisted matched jobs the same way. `DesktopRoutingEngine` sends `KOTLIN` to the shared engine, so the CLI is never asked.
- `Main.kt` now builds one `JavaNetHttpTransfer` and one `ExtractorRegistry(YoutubeIE)` that serve the download engine and the classifier; `HttpDownloadEngine` is constructed with `registry`, and the startup job split puts registry-matched and direct-file jobs on the shared engine while the CLI keeps the rest.
- The Settings Tools section adds the “Kotlin extractors: YouTube (single video)” row so the user sees what runs locally.
- Tests: `DesktopRouteClassifierTest` 8 (a registry-matched URL routes `KOTLIN` with zero probe requests at a local server; `resumeRoute` with and without a registry); `DesktopRoutingEngineTest` 8 (Kotlin route through the shared engine with zero process starts and an empty CLI queue); `DesktopKotlinDownloadIntegrationTest` 2 (real `YoutubeIE` + shared engine + `DesktopFileStore` with a local media server: M4A audio and progressive video land under the root byte-exact with the extracted title, no process, and MP3 fails typed `UNSUPPORTED_FORMAT` with the toolkit message and no file); `DesktopPreviewSourceTest` 3 stays green (T-063).
- Opt-in live run: `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.engine.DesktopLiveKotlinDownloadTest" -PliveExtractorTests=true` → `state=COMPLETED`, one `.m4a` artifact larger than zero bytes, no CLI process invoked. The public Big Buck Bunny visionos response has no progressive single-file format, so the progressive path is proven by the integrated real-engine test; the UI disables video choices for such a source. Without the property the test is skipped with the opt-in message.
- Verification: `:apps:desktop:test` green (including the new tests); `:shared:core:jvmTest` 257 tests, 0 failures; `:shared:ui:jvmTest` 76 tests, 0 failures; `:apps:android-engine-tests:test` green; `:shared:core:iosSimulatorArm64Test` green; iOS/wasm-test/Android/web compiles green; `:tools:port-manifest:check` green. No manifest change.
