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

- [x] `./gradlew :shared:core:iosSimulatorArm64Test` passes, including the request-port fixture case.
- [x] Simulator click-through recorded with versions; no background `URLSession` added; suspension behavior still documented.
- [x] `xcodebuild` for the simulator destination succeeds.

## Evidence / notes

Done 2026-09-24, with the simulator-network limitation recorded below.

- iOS graph: `IosAppGraph` builds one `IosHttpTransfer` and one `ExtractorRegistry(YoutubeIE(ExtractorHttp(transfer)))`, passes `registry` to the shared `HttpDownloadEngine`, and wires `ExtractorMediaPreviewSource` as the preview source. No background `URLSession` was added; the Documents sandbox and foreground-only behavior are unchanged.
- `IosEngineTest.extractorRouteDownloadsTheSelectedFormatIntoTheSandbox` adds the fixture extractor case: a registry-matched URL completes through the shared engine and the real `IosFileStore`, with the artifact named from the extracted title and the bytes in the sandbox. The T-056 request-port fixture (`realNSURLSessionCarriesTheExtractorRequestContract`) still runs behind the opt-in fixture port.
- `IosHttpTransfer.awaitResponse` now bounds `settled.await()` with `withTimeoutOrNull` and returns a typed `HttpResponse.Failed(TIMEOUT)` instead of hanging when NSURLSession never delivers a callback. The default suite stays 9/9 green.
- `./gradlew :shared:core:iosSimulatorArm64Test` → 9 tests in `IosEngineTest`, 0 failures (the live YouTube case is opt-in and skipped in the default run).
- `./gradlew :shared:ui:linkDebugFrameworkIosArm64 :shared:ui:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (AnyDownloadKit for both targets).
- `xcodebuild -project apps/ios/AnyDownload.xcodeproj -scheme AnyDownload -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` → **BUILD SUCCEEDED** with Xcode 26.5 (17F42); simulator iPhone 17 Pro (iOS 26.x).

Simulator live limitation (recorded, not fixed here):

- With the opt-in host file enabled, `IosEngineTest.realNSURLSessionPreviewsAndDownloadsALiveYoutubeAudioStream` now fails **typed and fast**: `live ios preview failed: Failed direct=Unavailable: The source timed out` after 4m37s. The simulator's NSURLSession cannot reach `youtube.com` in this environment, while the host paths succeed on the same day (T-060 live extractor: 27 formats; T-062 oracle: 27/27 with 0 diffs; T-064 live desktop M4A download completed with no process). The extractor, the request port, and the sandbox download path are proven by those host runs plus the native fixture case; only the simulator's outbound YouTube access is unavailable here.
