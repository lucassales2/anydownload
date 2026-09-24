---
id: T-066
type: task
priority: P0
milestone: D4
tags: [task, ios, engine]
---

# T-066 — iOS downloads a YouTube video into the sandbox

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [T-049](T-049-Ios-generic-download.md)

## Outcome

The iOS app previews and downloads a YouTube single video (progressive or audio-only) through the shared engine with `NSURLSession`. Unmatched site URLs still fail typed. Foreground-only remains acceptable.

## Dependencies

- [T-063](T-063-Preview-from-extractor.md).

## Work

- `IosHttpTransfer`: request port (method, headers, body, range) with `NSMutableURLRequest`; keep manual redirect handling and cooperative cancellation.
- iOS graph: registry, `ExtractorMediaPreviewSource`, shared engine, sandbox `Documents` store — all already wired for D3; add the registry.
- Kotlin/Native tests in `IosEngineTest`: fixture extractor case completes; the request port carries a `POST` body to the local fixture server behind the existing `-PiosLiveFixturePort` opt-in.
- Simulator click-through with a public YouTube URL: preview, audio download, file present in the sandbox, cancel discards. Record Xcode and simulator versions.
- The `AnyDownloadKit` framework must still build for `iosArm64` and `iosSimulatorArm64`.

## Acceptance criteria

- [ ] `./gradlew :shared:core:iosSimulatorArm64Test` passes, including the request-port fixture case.
- [ ] Simulator click-through recorded with versions; no background `URLSession` added; suspension behavior still documented.
- [ ] `xcodebuild` for the simulator destination succeeds.

## Evidence / notes

Not started.
