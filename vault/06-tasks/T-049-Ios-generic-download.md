---
id: T-049
type: task
priority: P0
milestone: D3
tags: [task, ios, engine]
---

# T-049 — iOS downloads a matching HTML page

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

The iOS host downloads the media file referenced by a local HTML fixture into the sandbox. Unresolved HTML still shows extractor-not-implemented. No AVFoundation work.

## Dependencies

- [T-046](T-046-Engine-uses-generic-extractor.md).
- [T-042](T-042-Ios-http-download.md) — NSURLSession transfer and sandbox store stay.

## Work

- The shared extractor runs on the HTML body the iOS transfer already fetched. The media GET uses the same one-hop transfer.
- Foreground-only stays honest. Do not add a background URLSession.
- A site URL the subset cannot resolve still fails typed, with no Python and no CLI.

## Acceptance criteria

- [x] An iOS test or simulator run downloads the fixture media and the job reaches COMPLETED with the file in the sandbox.
- [x] Unresolved HTML fails typed.
- [x] `./gradlew :shared:core:iosSimulatorArm64Test` passes, or the blocker and next action are written here.

## Evidence / notes

Done 2026-09-24.

- No engine change was needed: T-046's shared HTML route (bounded page read → generic extractor → one-hop media GET) already runs inside `HttpDownloadEngine`, which the iOS host wires with the real `IosHttpTransfer` (NSURLSession, one hop per execute so the engine re-validates every destination) and the sandbox `IosFileStore`. This task proved the route on the simulator and added the missing typed checks.
- New opt-in native test `IosEngineTest.realNSURLSessionDownloadsAMatchingHtmlPageAndFailsUnresolvedTyped` (same mechanism as T-042's live fixture: `IOS_LIVE_FIXTURE` env or `/tmp/anydownlod-ios-live.txt`; skipped by default so CI stays fixture-only). It serves three paths on a local server and asserts, through the real transfer and sandbox store:
  - `/watch` (one `<video src="/media/clip.bin">`) → job COMPLETED, artifact `clip.bin` published, `IosFileStore.size("clip.bin") == 4096`;
  - `/page-without-media` → typed `EXTRACTION_FAILURE`, non-retryable, no artifacts.
- Live simulator run (proof): wrote the endpoint file, ran `./gradlew :shared:core:iosSimulatorArm64Test --rerun-tasks` — 124 native tests, 0 failures. The fixture server's request log (with a logging handler) recorded exactly `GET /files/tiny.bin`, `GET /watch`, `GET /media/clip.bin`, `GET /page-without-media` from the simulator during the run, so the HTML path really executed over NSURLSession. The T-042 direct-file live test also passed in the same run. Server and endpoint file were removed afterwards.
- Unresolved HTML fails typed also in the offline default suite (`htmlContentFailsWithTypedExtractorError`, `htmlContentTypeIsNeedsExtractorAndFailsTyped`). Foreground-only honesty kept: nothing adds a background `URLSession`, Python, or CLI on iOS.
- Verification: `./gradlew :shared:core:iosSimulatorArm64Test` — 124 tests, 0 failures (live configured). Default (no endpoint file) runs skip the two opt-in tests; everything else stays green.
