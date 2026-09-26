---
id: T-098
type: task
priority: P0
milestone: D7
tags: [task, ios, twitter]
---

# T-098 — iOS downloads a selected status video fixture

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [T-096](T-096-Gate-selected-media-download.md)

## Outcome

iOS registers `TwitterIE` and runs the shared preview-selection-download path against a redacted simulator fixture. No live X/Twitter call. Work stays foreground-only.

## Dependencies

- [T-096](T-096-Gate-selected-media-download.md).

## Work

- `IosAppGraph`: add `TwitterIE(ExtractorHttp(transfer))` to `extractorRegistry` beside `YoutubeIE`. No AVFoundation or toolkit involvement: the selected variants are progressive single files.
- Simulator test (`:shared:core:iosSimulatorArm64Test`, or an iOS test in `shared/ui` if the graph is the unit under test): preview a two-video fixture status, select both, download through the shared engine, and assert one file per selected video inside the sandbox root and the status URL as the job source.
- No live X/Twitter in this task and no cookies. If the simulator cannot run here, record the exact command and the gap instead of inventing a pass.

## Acceptance criteria

- [x] iOS's registry includes `TwitterIE`; the shared path serves preview and download.
- [x] The simulator fixture downloads one file per selected video and re-extracts at download time.
- [x] No live X/Twitter call, cookie, or token; no media file committed.
- [x] Foreground-only stays documented for the iOS host.

## Evidence / notes

Done 2026-09-25.

What landed:

- `shared/ui/src/iosMain/kotlin/com/anydownlod/ui/IosExtractors.kt`: the iOS registry builder (YouTube then `TwitterIE`). `IosAppGraph` now builds its registry through it, so the simulator test proves the same wiring the app uses.
- `shared/ui/src/iosTest/kotlin/com/anydownlod/ui/IosXStatusTest.kt`: asserts the registry contains `TwitterIE`, previews the fixture status (two stable media ids), downloads both selected videos through the shared `HttpDownloadEngine` and the real sandbox `IosFileStore`, and asserts two files with the fixture bytes, the status URL as the job source, and two guest lookups (preview + download).
- The fixture is synthesized JSON with `*.example` media hosts; no live X/Twitter call, cookie, or token. Work is foreground-only, as documented on `IosAppGraph`.

Verification run 2026-09-25:

- `./gradlew :shared:ui:iosSimulatorArm64Test :shared:core:iosSimulatorArm64Test` — BUILD SUCCESSFUL.
- Counts: UI iOS 5 tests / 0 failures (`IosXStatusTest` 1 / 0); core iOS 444 tests / 0 failures.
