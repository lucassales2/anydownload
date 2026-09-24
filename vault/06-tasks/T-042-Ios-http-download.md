---
id: T-042
type: task
priority: P0
milestone: D2
tags: [task, ios, engine]
---

# T-042 — iOS HTTP download

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

The iOS host downloads a direct HTTP(S) file into the app sandbox through the shared engine. Non-direct URLs fail clearly. Work is foreground-only.

## Dependencies

- [T-039](T-039-Platform-http-and-files.md).

## Work

- Replace the in-memory fake engine on the iOS host with `HttpDownloadEngine` and Native actuals.
- Write under the sandbox download root. Export/share of the finished file can be the existing share sheet or Files; do not invent a second file tree.
- If the app is suspended, the job is interrupted and must not be marked completed on next launch (reuse D1 restart semantics if the iOS store is still the fake; if you add persistence, match the desktop JSON rules).
- NeedsExtractor → typed error. Do not embed Python or a CLI.

## Acceptance criteria

- [x] Simulator or device run, or a Native test, downloads a fixture file and shows it in Completed.
- [x] Cancel leaves no completed file.
- [x] A site/HTML URL shows the extractor-not-implemented error.
- [x] Unsigned simulator build still compiles.

## Evidence / notes

Done on 2026-09-23. Background URLSession stays out of this phase.

Commands run:

- `./gradlew :shared:core:iosSimulatorArm64Test` - 91 native tests, 0 failures (engine + platform tests run in the simulator).
- Live local-fixture run: started `python3 -m http.server 8123 --bind 0.0.0.0` serving a 3 MiB `files/tiny.bin`, pointed the opt-in test at it, ran the suite - the simulator's NSURLSession requested `GET /files/tiny.bin` (server log `200`) and `realNSURLSessionDownloadsALocalFixture` passed with the job in COMPLETED and the artifact on disk. The opt-in test reads the endpoint from `IOS_LIVE_FIXTURE` / a host file and is skipped by default, so default CI stays fixture-only.
- `xcodebuild -project apps/ios/AnyDownload.xcodeproj -scheme AnyDownload -configuration Debug -sdk iphonesimulator -derivedDataPath <scratch-dir> CODE_SIGNING_ALLOWED=NO build` - **BUILD SUCCEEDED** (unsigned).
- `xcrun simctl boot "iPhone 17 Pro"` + `simctl install` + `simctl launch booted com.anydownlod.ios` - the app installed and launched (PID reported).

Test names (`IosEngineTest`, native):

- `directFileStreamsThroughTheRealIosFileStoreAndCompletes` (COMPLETED, artifact on disk via the native store)
- `cancelMidStreamLeavesNoCompletedFile` (CANCELLED, nothing published)
- `htmlContentFailsWithTypedExtractorError` (EXTRACTION_FAILURE)
- `fileStoreWritesPublishesAndRejectsTraversal`
- `realNSURLSessionDownloadsALocalFixture` (opt-in live, actually executed once here)

Delivered:

- `IosHttpTransfer` (shared/core iosMain): in-process NSURLSession, one hop per execute (delegate refuses to auto-follow redirects, so the engine re-validates every destination), streamed chunks through a channel (no whole-file buffer), task cancelled on coroutine cancel.
- `IosFileStore` (shared/core iosMain): POSIX streaming writes under the sandbox root, traversal/absolute-path refusal.
- `IosAppGraph` + `MainViewController` (shared/ui iosMain): the iOS host now passes the real engine to the shared UI. Sandbox root is the Documents directory; `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace` were added so finished files appear in the Files app (no second file tree, no share-sheet invention).
- Suspended-app semantics: the iOS store is still the in-memory fake (no persistence), so an interrupted job cannot be marked Completed on a later launch - it simply is not there, matching the D1-restart clause.
- No Python or CLI on iOS; NeedsExtractor fails in the shared engine with the typed "extractor not implemented" error.
